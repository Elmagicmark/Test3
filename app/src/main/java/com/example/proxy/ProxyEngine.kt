package com.example.proxy

import android.util.Log
import com.example.data.local.HttpTransactionEntity
import com.example.data.local.InterceptedRequestEntity
import com.example.data.local.TargetScopeEntity
import com.example.data.model.InterceptAction
import com.example.data.model.ProxySettings
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProxyEngine {

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var proxySettings: ProxySettings = ProxySettings()

    @Volatile
    private var activeScopes: List<TargetScopeEntity> = emptyList()

    @Volatile
    private var bypassDomains: List<String> = emptyList()

    fun updateProxySettings(settings: ProxySettings) {
        proxySettings = settings
        Log.d("ProxyEngine", "Proxy settings updated: $settings")
    }

    fun updateActiveScopes(scopes: List<TargetScopeEntity>) {
        activeScopes = scopes
        Log.d("ProxyEngine", "Active scopes updated: count=${scopes.size}")
    }

    fun updateBypassDomains(domains: List<String>) {
        bypassDomains = domains
        Log.d("ProxyEngine", "Bypass domains updated: count=${domains.size}")
    }

    fun startProxy(
        onTransactionCaptured: (HttpTransactionEntity) -> Unit,
        onInterceptCaptured: (InterceptedRequestEntity, onAction: (InterceptAction) -> Unit) -> Unit,
        onStatsUpdated: (bytes: Long, activeConnIncrement: Int) -> Unit
    ) {
        if (isRunning) return
        isRunning = true

        executor.execute {
            try {
                serverSocket = ServerSocket(proxySettings.port)
                Log.d("ProxyEngine", "Proxy started on port ${proxySettings.port}")

                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    executor.execute {
                        handleClient(clientSocket, onTransactionCaptured, onInterceptCaptured, onStatsUpdated)
                    }
                }
            } catch (e: Exception) {
                Log.e("ProxyEngine", "Proxy error: ${e.message}")
            }
        }
    }

    fun stopProxy() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        executor.shutdown()
        scope.cancel()
        Log.d("ProxyEngine", "Proxy stopped")
    }

    private fun handleClient(
        clientSocket: Socket,
        onTransactionCaptured: (HttpTransactionEntity) -> Unit,
        onInterceptCaptured: (InterceptedRequestEntity, onAction: (InterceptAction) -> Unit) -> Unit,
        onStatsUpdated: (bytes: Long, activeConnIncrement: Int) -> Unit
    ) {
        try {
            clientSocket.soTimeout = 30000
            val inputStream = clientSocket.getInputStream()
            val outputStream = clientSocket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(inputStream))

            val requestLine = reader.readLine() ?: return
            if (requestLine.isBlank()) return

            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val path = parts[1]
            val version = if (parts.size > 2) parts[2] else "HTTP/1.1"

            val headersMap = mutableMapOf<String, String>()
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
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

            val httpClient = buildOkHttpClient()

            if (method == "CONNECT") {
                handleConnectTunnel(
                    hostPort = path,
                    clientSocket = clientSocket,
                    inputStream = inputStream,
                    outputStream = outputStream,
                    httpClient = httpClient,
                    onTransactionCaptured = onTransactionCaptured,
                    onInterceptCaptured = onInterceptCaptured,
                    onStatsUpdated = onStatsUpdated
                )
            } else {
                handleHttpRequest(
                    method = method,
                    path = path,
                    version = version,
                    headersMap = headersMap,
                    requestBodyString = requestBodyString,
                    outputStream = outputStream,
                    httpClient = httpClient,
                    onTransactionCaptured = onTransactionCaptured,
                    onInterceptCaptured = onInterceptCaptured,
                    onStatsUpdated = onStatsUpdated
                )
            }
        } catch (e: Exception) {
            Log.e("ProxyEngine", "Client handling error: ${e.message}")
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun handleHttpRequest(
        method: String,
        path: String,
        version: String,
        headersMap: MutableMap<String, String>,
        requestBodyString: String,
        outputStream: OutputStream,
        httpClient: OkHttpClient,
        onTransactionCaptured: (HttpTransactionEntity) -> Unit,
        onInterceptCaptured: (InterceptedRequestEntity, onAction: (InterceptAction) -> Unit) -> Unit,
        onStatsUpdated: (bytes: Long, activeConnIncrement: Int) -> Unit
    ) {
        val host = headersMap["Host"] ?: "unknown"
        val fullUrl = if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            "http://$host$path"
        }

        val shouldIntercept = proxySettings.isInterceptEnabled
            && proxySettings.shouldInterceptMethod(method)
            && (!proxySettings.enforceScopeOnly || isUrlInScope(fullUrl))

        processHttpTransaction(
            method = method,
            fullUrl = fullUrl,
            headersMap = headersMap,
            requestBodyString = requestBodyString,
            outputStream = outputStream,
            httpClient = httpClient,
            shouldIntercept = shouldIntercept,
            onTransactionCaptured = onTransactionCaptured,
            onInterceptCaptured = onInterceptCaptured,
            onStatsUpdated = onStatsUpdated
        )
    }

    private fun handleConnectTunnel(
        hostPort: String,
        clientSocket: Socket,
        inputStream: InputStream,
        outputStream: OutputStream,
        httpClient: OkHttpClient,
        onTransactionCaptured: (HttpTransactionEntity) -> Unit,
        onInterceptCaptured: (InterceptedRequestEntity, onAction: (InterceptAction) -> Unit) -> Unit,
        onStatsUpdated: (bytes: Long, activeConnIncrement: Int) -> Unit
    ) {
        val parts = hostPort.split(":")
        val targetHost = parts[0]
        val targetPort = if (parts.size > 1) parts[1].toIntOrNull() ?: 443 else 443

        Log.d("ProxyEngine", "[PROXY] CONNECT $hostPort")

        if (shouldBypass(targetHost)) {
            Log.d("ProxyEngine", "[BYPASS] Domain $targetHost is in bypass list. Tunneling directly.")
            executor.execute {
                try {
                    val targetSocket = Socket()
                    targetSocket.connect(InetSocketAddress(targetHost, targetPort), 15000)

                    val connectOk = "HTTP/1.1 200 Connection Established\r\n\r\n"
                    outputStream.write(connectOk.toByteArray(Charsets.UTF_8))
                    outputStream.flush()

                    val clientToTarget = Thread {
                        pipeStreams(clientSocket.getInputStream(), targetSocket.getOutputStream(), onStatsUpdated)
                    }
                    val targetToClient = Thread {
                        pipeStreams(targetSocket.getInputStream(), clientSocket.getOutputStream(), onStatsUpdated)
                    }
                    clientToTarget.start()
                    targetToClient.start()
                    clientToTarget.join()
                    targetToClient.join()
                } catch (e: Exception) {
                    Log.e("ProxyEngine", "[BYPASS] Tunnel error for $hostPort: ${e.message}")
                } finally {
                    try { clientSocket.close() } catch (_: Exception) {}
                }
            }
            return
        }

        val connectOk = "HTTP/1.1 200 Connection Established\r\n\r\n"
        outputStream.write(connectOk.toByteArray(Charsets.UTF_8))
        outputStream.flush()

        var sslClientSocket: javax.net.ssl.SSLSocket? = null
        try {
            Log.d("ProxyEngine", "[MITM] Starting TLS interception for $targetHost")
            val sslContext = com.example.util.CertificateManager.getMitmSslContext()
            val sslFactory = sslContext.socketFactory
            sslClientSocket = sslFactory.createSocket(clientSocket, clientSocket.inetAddress?.hostAddress, clientSocket.port, true) as javax.net.ssl.SSLSocket
            sslClientSocket.useClientMode = false
            sslClientSocket.startHandshake()
            Log.d("ProxyEngine", "[MITM] TLS handshake successful with client for $targetHost")

            val tlsIn = sslClientSocket.inputStream
            val tlsOut = sslClientSocket.outputStream

            while (isRunning && !sslClientSocket.isClosed) {
                val requestLine = readHeaderLine(tlsIn) ?: break
                if (requestLine.isBlank()) continue

                val reqParts = requestLine.split(" ")
                if (reqParts.size < 2) break
                val innerMethod = reqParts[0].uppercase()
                val innerPath = reqParts[1]
                val innerVersion = if (reqParts.size > 2) reqParts[2] else "HTTP/1.1"

                val innerHeadersMap = mutableMapOf<String, String>()
                var innerContentLength = 0
                while (true) {
                    val line = readHeaderLine(tlsIn) ?: break
                    if (line.isBlank()) break
                    val colonIdx = line.indexOf(":")
                    if (colonIdx > 0) {
                        val k = line.substring(0, colonIdx).trim()
                        val v = line.substring(colonIdx + 1).trim()
                        innerHeadersMap[k] = v
                        if (k.equals("Content-Length", ignoreCase = true)) {
                            innerContentLength = v.toIntOrNull() ?: 0
                        }
                    }
                }

                var reqBodyString = ""
                if (innerContentLength > 0) {
                    val bodyBytes = ByteArray(innerContentLength)
                    var totalRead = 0
                    while (totalRead < innerContentLength) {
                        val read = tlsIn.read(bodyBytes, totalRead, innerContentLength - totalRead)
                        if (read <= 0) break
                        totalRead += read
                    }
                    reqBodyString = String(bodyBytes, 0, totalRead, Charsets.UTF_8)
                }

                val fullHttpsUrl = if (innerPath.startsWith("http://") || innerPath.startsWith("https://")) {
                    innerPath
                } else {
                    "https://$targetHost:$targetPort$innerPath"
                }

                Log.d("ProxyEngine", "[HTTP1] $innerMethod $innerPath")
                Log.d("ProxyEngine", "[INTERCEPT] $innerMethod $fullHttpsUrl")

                val shouldIntercept = proxySettings.isInterceptEnabled
                    && proxySettings.shouldInterceptMethod(innerMethod)
                    && (!proxySettings.enforceScopeOnly || isUrlInScope(fullHttpsUrl))

                processHttpTransaction(
                    method = innerMethod,
                    fullUrl = fullHttpsUrl,
                    headersMap = innerHeadersMap,
                    requestBodyString = reqBodyString,
                    outputStream = tlsOut,
                    httpClient = httpClient,
                    shouldIntercept = shouldIntercept,
                    onTransactionCaptured = onTransactionCaptured,
                    onInterceptCaptured = onInterceptCaptured,
                    onStatsUpdated = onStatsUpdated
                )
            }
        } catch (e: Exception) {
            Log.d("ProxyEngine", "[MITM] Fallback/Pass-through for $hostPort due to: ${e.message}")
        } finally {
            try { sslClientSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun processHttpTransaction(
        method: String,
        fullUrl: String,
        headersMap: Map<String, String>,
        requestBodyString: String,
        outputStream: OutputStream,
        httpClient: OkHttpClient,
        shouldIntercept: Boolean,
        onTransactionCaptured: (HttpTransactionEntity) -> Unit,
        onInterceptCaptured: (InterceptedRequestEntity, onAction: (InterceptAction) -> Unit) -> Unit,
        onStatsUpdated: (bytes: Long, activeConnIncrement: Int) -> Unit
    ) {
        val startTime = System.currentTimeMillis()

        if (shouldIntercept) {
            val interceptedRequest = InterceptedRequestEntity(
                method = method,
                url = fullUrl,
                headersJson = headersMap.toString(),
                body = requestBodyString
            )

            var action: InterceptAction? = null
            val latch = java.util.concurrent.CountDownLatch(1)

            onInterceptCaptured(interceptedRequest) { receivedAction ->
                action = receivedAction
                latch.countDown()
            }

            try {
                latch.await(30, TimeUnit.SECONDS)
            } catch (_: Exception) {}

            when (action) {
                is InterceptAction.Drop -> {
                    val dropResponse = "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n"
                    outputStream.write(dropResponse.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                    return
                }
                is InterceptAction.Forward -> {
                    val forwardAction = action as InterceptAction.Forward
                    val modifiedRequest = okhttp3.Request.Builder()
                        .url(forwardAction.url)
                        .method(forwardAction.method, if (forwardAction.body.isNotEmpty()) okhttp3.RequestBody.create(null, forwardAction.body.toByteArray()) else null)
                        .build()

                    httpClient.newCall(modifiedRequest).execute().use { response ->
                        val responseBodyString = response.body?.string() ?: ""
                        val elapsed = System.currentTimeMillis() - startTime

                        val transaction = HttpTransactionEntity(
                            method = forwardAction.method,
                            url = forwardAction.url,
                            statusCode = response.code,
                            responseTimeMs = elapsed,
                            requestHeadersJson = forwardAction.headersJson,
                            requestBody = forwardAction.body,
                            responseHeadersJson = response.headers.toString(),
                            responseBody = responseBodyString,
                            bytesTransferred = (requestBodyString.length + responseBodyString.length).toLong()
                        )

                        onTransactionCaptured(transaction)
                        onStatsUpdated(transaction.bytesTransferred, 0)

                        val statusLine = "HTTP/1.1 ${response.code} ${response.message}\r\n"
                        outputStream.write(statusLine.toByteArray(Charsets.UTF_8))
                        response.headers.forEach { (name, value) ->
                            outputStream.write("$name: $value\r\n".toByteArray(Charsets.UTF_8))
                        }
                        outputStream.write("\r\n".toByteArray(Charsets.UTF_8))
                        outputStream.write(responseBodyString.toByteArray(Charsets.UTF_8))
                        outputStream.flush()
                    }
                    return
                }
                else -> {}
            }
        }

        val requestBuilder = Request.Builder().url(fullUrl).method(method, if (requestBodyString.isNotEmpty()) okhttp3.RequestBody.create(null, requestBodyString.toByteArray()) else null)
        headersMap.forEach { (k, v) ->
            if (!k.equals("Host", ignoreCase = true) && !k.equals("Content-Length", ignoreCase = true)) {
                requestBuilder.header(k, v)
            }
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseBodyString = response.body?.string() ?: ""
            val elapsed = System.currentTimeMillis() - startTime

            val transaction = HttpTransactionEntity(
                method = method,
                url = fullUrl,
                statusCode = response.code,
                responseTimeMs = elapsed,
                requestHeadersJson = headersMap.toString(),
                requestBody = requestBodyString,
                responseHeadersJson = response.headers.toString(),
                responseBody = responseBodyString,
                bytesTransferred = (requestBodyString.length + responseBodyString.length).toLong()
            )

            onTransactionCaptured(transaction)
            onStatsUpdated(transaction.bytesTransferred, 0)

            val statusLine = "HTTP/1.1 ${response.code} ${response.message}\r\n"
            outputStream.write(statusLine.toByteArray(Charsets.UTF_8))
            response.headers.forEach { (name, value) ->
                outputStream.write("$name: $value\r\n".toByteArray(Charsets.UTF_8))
            }
            outputStream.write("\r\n".toByteArray(Charsets.UTF_8))
            outputStream.write(responseBodyString.toByteArray(Charsets.UTF_8))
            outputStream.flush()
        }
    }

    private fun readHeaderLine(inputStream: InputStream): String? {
        val sb = StringBuilder()
        var prev: Int = -1
        while (true) {
            val b = inputStream.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (prev == '\r'.code && b == '\n'.code) {
                return sb.dropLast(1).toString()
            }
            sb.append(b.toChar())
            prev = b
        }
    }

    private fun pipeStreams(from: InputStream, to: OutputStream, onStatsUpdated: (bytes: Long, activeConnIncrement: Int) -> Unit) {
        val buffer = ByteArray(8192)
        try {
            while (isRunning) {
                val read = from.read(buffer)
                if (read <= 0) break
                to.write(buffer, 0, read)
                to.flush()
                onStatsUpdated(read.toLong(), 0)
            }
        } catch (_: Exception) {}
    }

    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun shouldBypass(host: String): Boolean {
        val cleanHost = host.trim().lowercase()
        return bypassDomains.any { pattern ->
            val cleanPattern = pattern.trim().lowercase()
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore("/")
                .substringBefore(":")

            when {
                cleanPattern.startsWith("*.") -> {
                    val suffix = cleanPattern.removePrefix("*.")
                    cleanHost == suffix || cleanHost.endsWith(".$suffix")
                }
                cleanPattern.startsWith("*") -> {
                    cleanHost.contains(cleanPattern.removePrefix("*"))
                }
                else -> {
                    cleanHost == cleanPattern || cleanHost.endsWith(".$cleanPattern")
                }
            }
        }
    }

    private fun isUrlInScope(url: String): Boolean {
        if (activeScopes.isEmpty()) return true
        val urlLower = url.lowercase()
        return activeScopes.any { scope ->
            if (!scope.isInScope) return@any false
            val pattern = scope.pattern.trim().lowercase()
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("*.")
                .removePrefix(".")
            val hostMatch = urlLower.contains(pattern) ||
                    urlLower.contains("*.$pattern")
            hostMatch
        }
    }
}
