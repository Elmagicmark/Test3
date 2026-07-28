package com.example.proxy

import android.util.Log
import com.example.data.local.HttpTransactionEntity
import com.example.data.local.InterceptedRequestEntity
import com.example.data.model.ProxySettings
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.*
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class ProxyEngine {

    private var serverSocket: ServerSocket? = null
    private var proxyJob: Job? = null
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var isRunning = false

    fun startProxy(
        settings: ProxySettings,
        scope: CoroutineScope,
        onTransactionCaptured: (HttpTransactionEntity) -> Unit,
        onInterceptCaptured: (InterceptedRequestEntity) -> Unit,
        onStatsUpdated: (bytes: Long, activeConnIncrement: Int) -> Unit
    ) {
        if (isRunning) stopProxy()

        isRunning = true
        proxyJob = scope.launch(Dispatchers.IO) {
            try {
                val bindAddr = if (settings.host == "0.0.0.0" || settings.host == "127.0.0.1" || settings.host.isBlank() || settings.host.contains("0.0.0.0")) {
                    InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0))
                } else {
                    try {
                        InetAddress.getByName(settings.host)
                    } catch (_: Exception) {
                        InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0))
                    }
                }

                serverSocket = ServerSocket(settings.port, 100, bindAddr)
                Log.d("ProxyEngine", "Proxy Server started listening on 0.0.0.0 (All interfaces):${settings.port}")

                val httpClient = buildOkHttpClient(settings)

                while (isRunning && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        clientSocket.soTimeout = 30000
                        onStatsUpdated(0L, 1)
                        executor.execute {
                            try {
                                handleClientSocket(clientSocket, settings, httpClient, onTransactionCaptured, onInterceptCaptured, onStatsUpdated)
                            } catch (e: Exception) {
                                Log.e("ProxyEngine", "Client handling error: ${e.message}")
                            } finally {
                                try { clientSocket.close() } catch (_: Exception) {}
                                onStatsUpdated(0L, -1)
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e("ProxyEngine", "Accept error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProxyEngine", "Proxy Server crash: ${e.message}")
                isRunning = false
            }
        }
    }

    fun stopProxy() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e("ProxyEngine", "Error closing server socket: ${e.message}")
        }
        proxyJob?.cancel()
        proxyJob = null
    }

    fun isEngineRunning(): Boolean = isRunning

    private fun buildOkHttpClient(settings: ProxySettings): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)

        // Configure Upstream External Proxy if enabled
        if (settings.upstreamProxyEnabled && settings.upstreamProxyHost.isNotBlank()) {
            val upstreamProxy = java.net.Proxy(
                java.net.Proxy.Type.HTTP,
                InetSocketAddress(settings.upstreamProxyHost, settings.upstreamProxyPort)
            )
            builder.proxy(upstreamProxy)
            Log.d("ProxyEngine", "Configured Upstream Proxy -> ${settings.upstreamProxyHost}:${settings.upstreamProxyPort}")
        }

        if (settings.sslBypassEnabled) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                Log.e("ProxyEngine", "SSL Bypass config error: ${e.message}")
            }
        }

        return builder.build()
    }

    private fun readHeaderLine(input: InputStream): String? {
        val bos = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) {
                if (bos.size() == 0) return null
                break
            }
            if (b == '\n'.code) {
                val bytes = bos.toByteArray()
                if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) {
                    return String(bytes, 0, bytes.size - 1, Charsets.UTF_8)
                }
                return String(bytes, Charsets.UTF_8)
            }
            bos.write(b)
        }
        return String(bos.toByteArray(), Charsets.UTF_8)
    }

    private fun handleClientSocket(
        clientSocket: Socket,
        settings: ProxySettings,
        httpClient: OkHttpClient,
        onTransactionCaptured: (HttpTransactionEntity) -> Unit,
        onInterceptCaptured: (InterceptedRequestEntity) -> Unit,
        onStatsUpdated: (bytes: Long, activeConnIncrement: Int) -> Unit
    ) {
        val inputStream = clientSocket.getInputStream()
        val outputStream = clientSocket.getOutputStream()

        val requestLine = readHeaderLine(inputStream) ?: return
        Log.d("ProxyEngine", "Incoming request: $requestLine")

        val parts = requestLine.split(" ")
        if (parts.size < 2) return

        val method = parts[0].uppercase()
        val rawUrl = parts[1]

        val headersMap = mutableMapOf<String, String>()
        var contentLength = 0
        while (true) {
            val line = readHeaderLine(inputStream) ?: break
            if (line.isBlank()) break
            val colonIdx = line.indexOf(":")
            if (colonIdx > 0) {
                val k = line.substring(0, colonIdx).trim()
                val v = line.substring(colonIdx + 1).trim()
                headersMap[k] = v
                if (k.equals("Content-Length", ignoreCase = true)) {
                    contentLength = v.toIntOrNull() ?: 0
                }
            }
        }

        // Handle HTTPS CONNECT tunnel
        if (method == "CONNECT") {
            handleConnectTunnel(rawUrl, clientSocket, inputStream, outputStream, settings, onTransactionCaptured, onStatsUpdated)
            return
        }

        // Handle Standard HTTP Proxy request
        var requestBodyString = ""
        if (contentLength > 0) {
            val bodyBytes = ByteArray(contentLength)
            var totalRead = 0
            while (totalRead < contentLength) {
                val read = inputStream.read(bodyBytes, totalRead, contentLength - totalRead)
                if (read <= 0) break
                totalRead += read
            }
            requestBodyString = String(bodyBytes, 0, totalRead, Charsets.UTF_8)
        }

        val fullUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            val hostHeader = headersMap["Host"] ?: "127.0.0.1"
            "http://$hostHeader$rawUrl"
        }

        val headersJson = "{" + headersMap.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" } + "}"

        if (settings.isInterceptEnabled) {
            val interceptEntity = InterceptedRequestEntity(
                method = method,
                url = fullUrl,
                headersJson = headersJson,
                body = requestBodyString
            )
            onInterceptCaptured(interceptEntity)
        }

        val startTime = System.currentTimeMillis()
        var statusCode = 500
        var responseBodyString = ""
        var responseHeadersJson = "{}"
        var byteCount = 0L

        try {
            val reqBuilder = Request.Builder().url(fullUrl)
            headersMap.forEach { (k, v) ->
                if (!k.equals("Host", ignoreCase = true) && !k.equals("Proxy-Connection", ignoreCase = true)) {
                    reqBuilder.addHeader(k, v)
                }
            }

            if (method in listOf("POST", "PUT", "PATCH")) {
                val mediaType = headersMap["Content-Type"]?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
                reqBuilder.method(method, requestBodyString.toRequestBody(mediaType))
            } else if (method == "DELETE") {
                if (requestBodyString.isNotBlank()) {
                    val mediaType = headersMap["Content-Type"]?.toMediaTypeOrNull()
                    reqBuilder.method("DELETE", requestBodyString.toRequestBody(mediaType))
                } else {
                    reqBuilder.delete()
                }
            } else {
                reqBuilder.method(method, null)
            }

            val okResponse = httpClient.newCall(reqBuilder.build()).execute()
            statusCode = okResponse.code
            responseBodyString = okResponse.body?.string() ?: ""

            val headerSb = StringBuilder("{")
            val respHeaderLines = StringBuilder()
            var first = true
            okResponse.headers.forEach { pair ->
                if (!first) headerSb.append(",")
                headerSb.append("\"").append(pair.first).append("\":\"").append(pair.second.replace("\"", "\\\"")).append("\"")
                respHeaderLines.append("${pair.first}: ${pair.second}\r\n")
                first = false
            }
            headerSb.append("}")
            responseHeadersJson = headerSb.toString()

            // Send HTTP response back to proxy client
            val rawResponse = "HTTP/1.1 $statusCode ${okResponse.message}\r\n" +
                    respHeaderLines.toString() +
                    "Content-Length: ${responseBodyString.toByteArray(Charsets.UTF_8).size}\r\n\r\n" +
                    responseBodyString

            val respBytes = rawResponse.toByteArray(Charsets.UTF_8)
            outputStream.write(respBytes)
            outputStream.flush()

            byteCount = respBytes.size.toLong() + requestBodyString.toByteArray().size
            onStatsUpdated(byteCount, 0)

        } catch (e: Exception) {
            Log.e("ProxyEngine", "Proxy execution error for $fullUrl: ${e.message}")
            val errorResp = "HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain\r\n\r\nProxy Error: ${e.localizedMessage}"
            outputStream.write(errorResp.toByteArray())
            outputStream.flush()
            responseBodyString = "Proxy Error: ${e.localizedMessage}"
            byteCount = errorResp.length.toLong()
        }

        val duration = (System.currentTimeMillis() - startTime).toInt()

        val tx = HttpTransactionEntity(
            url = fullUrl,
            method = method,
            statusCode = statusCode,
            requestHeadersJson = headersJson,
            requestBody = requestBodyString,
            responseHeadersJson = responseHeadersJson,
            responseBody = responseBodyString,
            responseTimeMs = duration.toLong(),
            bytesTransferred = byteCount,
            isIntercepted = settings.isInterceptEnabled
        )

        onTransactionCaptured(tx)
    }

    private fun handleConnectTunnel(
        hostPort: String,
        clientSocket: Socket,
        inputStream: InputStream,
        outputStream: OutputStream,
        settings: ProxySettings,
        onTransactionCaptured: (HttpTransactionEntity) -> Unit,
        onStatsUpdated: (bytes: Long, activeConnIncrement: Int) -> Unit
    ) {
        val parts = hostPort.split(":")
        val targetHost = parts[0]
        val targetPort = if (parts.size > 1) parts[1].toIntOrNull() ?: 443 else 443

        var targetSocket: Socket? = null
        try {
            targetSocket = if (settings.upstreamProxyEnabled && settings.upstreamProxyHost.isNotBlank()) {
                val proxy = java.net.Proxy(
                    java.net.Proxy.Type.HTTP,
                    InetSocketAddress(settings.upstreamProxyHost, settings.upstreamProxyPort)
                )
                val s = Socket(proxy)
                s.connect(InetSocketAddress(targetHost, targetPort), 10000)
                s
            } else {
                val s = Socket()
                s.connect(InetSocketAddress(targetHost, targetPort), 10000)
                s
            }
            targetSocket.soTimeout = 30000

            // Send 200 Connection Established to client
            val connectOk = "HTTP/1.1 200 Connection Established\r\n\r\n"
            outputStream.write(connectOk.toByteArray())
            outputStream.flush()

            val tx = HttpTransactionEntity(
                url = "https://$targetHost:$targetPort",
                method = "CONNECT",
                statusCode = 200,
                requestHeadersJson = "{\"Host\":\"$targetHost:$targetPort\"}",
                requestBody = "",
                responseHeadersJson = "{}",
                responseBody = "HTTPS CONNECT Tunnel Established",
                responseTimeMs = 12L,
                bytesTransferred = 512L,
                isIntercepted = false
            )
            onTransactionCaptured(tx)

            // Pipe streams between client and target
            val t1 = Thread { pipeStreams(inputStream, targetSocket.getOutputStream(), onStatsUpdated) }
            val t2 = Thread { pipeStreams(targetSocket.getInputStream(), outputStream, onStatsUpdated) }
            t1.start()
            t2.start()

            try {
                t1.join()
                t2.join()
            } catch (_: Exception) {}

        } catch (e: Exception) {
            Log.e("ProxyEngine", "CONNECT Tunnel failed to $hostPort: ${e.message}")
            try {
                val errorResp = "HTTP/1.1 502 Bad Gateway\r\n\r\nFailed to connect to $hostPort"
                outputStream.write(errorResp.toByteArray())
                outputStream.flush()
            } catch (_: Exception) {}
        } finally {
            try { targetSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun pipeStreams(input: InputStream, output: OutputStream, onStatsUpdated: (bytes: Long, activeConnIncrement: Int) -> Unit) {
        val buffer = ByteArray(8192)
        try {
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
                onStatsUpdated(read.toLong(), 0)
            }
        } catch (_: Exception) {}
    }
}
