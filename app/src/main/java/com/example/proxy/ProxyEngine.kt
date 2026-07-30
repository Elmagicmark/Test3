package com.example.proxy

import android.content.Context
import android.util.Log
import com.example.data.local.HttpTransactionEntity
import com.example.data.local.InterceptedRequestEntity
import com.example.data.model.ProxySettings
import com.example.util.CertificateManager
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.*
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

sealed class InterceptAction {
    data class Forward(
        val method: String,
        val url: String,
        val headersMap: Map<String, String>,
        val body: String
    ) : InterceptAction()

    data class ForwardDirectResponse(
        val statusCode: Int,
        val headersMap: Map<String, String>,
        val body: String
    ) : InterceptAction()

    object Drop : InterceptAction()
}

/**
 * Local intercepting HTTP/HTTPS proxy.
 *
 * HTTPS traffic previously arrived as a CONNECT tunnel that was blindly relayed
 * byte-for-byte between client and origin (see git history) — meaning every
 * HTTPS transaction only ever logged as method "CONNECT", since the proxy never
 * actually looked inside the encrypted stream. [handleConnectTunnel] now
 * terminates the client's TLS session itself (presenting a per-host leaf
 * certificate signed by the local root CA — see [CertificateManager]),
 * decrypts the real request, and forwards it through the exact same
 * interception/logging pipeline plain HTTP already used — so HTTPS traffic now
 * shows its real method (GET/POST/etc.), same as Reqable/Charles/mitmproxy.
 * This requires the InterceptX root CA to be trusted on the device.
 */
class ProxyEngine(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var proxyJob: Job? = null
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var isRunning = false
    @Volatile private var currentSettings: ProxySettings = ProxySettings()
    @Volatile private var activeScopes: List<com.example.data.local.TargetScopeEntity> = emptyList()

    fun updateSettings(newSettings: ProxySettings) {
        currentSettings = newSettings
        Log.d("ProxyEngine", "Settings updated live: isInterceptEnabled=${newSettings.isInterceptEnabled}, enforceScopeOnly=${newSettings.enforceScopeOnly}")
    }

    fun updateActiveScopes(scopes: List<com.example.data.local.TargetScopeEntity>) {
        activeScopes = scopes
        Log.d("ProxyEngine", "Active scope rules updated: count=${scopes.size}")
    }

    fun isUrlInScope(url: String, settings: ProxySettings, scopes: List<com.example.data.local.TargetScopeEntity>): Boolean {
        val enabledScopes = scopes.filter { it.isEnabled }
        if (enabledScopes.isEmpty()) return true

        val isExcluded = enabledScopes.any { scope ->
            !scope.isInScope && matchesDomainPattern(url, scope.pattern, settings.includeSubdomains)
        }
        if (isExcluded) return false

        val inScopeRules = enabledScopes.filter { it.isInScope }
        if (inScopeRules.isEmpty()) return true

        return inScopeRules.any { scope ->
            matchesDomainPattern(url, scope.pattern, settings.includeSubdomains)
        }
    }

    private fun cleanDomainPattern(pattern: String): String {
        var s = pattern.trim().lowercase()
        s = s.replace("http://", "").replace("https://", "")
        s = s.replace(".*", "").replace("\\.", ".").replace("*", "")
        s = s.replace("\\", "").replace("\"", "").replace("'", "")
        val slashIdx = s.indexOf('/')
        if (slashIdx != -1) s = s.substring(0, slashIdx)
        val portIdx = s.indexOf(':')
        if (portIdx != -1) s = s.substring(0, portIdx)
        return s.trim().trim('.', ' ')
    }

    private fun extractHost(url: String): String {
        if (url.isBlank()) return ""
        var clean = url.trim().lowercase()
        clean = clean.replace("http://", "").replace("https://", "")
        val slashIdx = clean.indexOf('/')
        if (slashIdx != -1) clean = clean.substring(0, slashIdx)
        val portIdx = clean.indexOf(':')
        if (portIdx != -1) clean = clean.substring(0, portIdx)
        return clean.trim().trim('.', ' ')
    }

    fun matchesDomainPattern(urlOrHost: String, pattern: String, includeSubdomains: Boolean = true): Boolean {
        val cleanPattern = cleanDomainPattern(pattern)
        if (cleanPattern.isEmpty()) return false

        val host = extractHost(urlOrHost)
        val urlLower = urlOrHost.trim().lowercase()

        if (host == cleanPattern) return true
        if (includeSubdomains && host.endsWith(".$cleanPattern")) return true
        if (host.contains(cleanPattern)) return true
        if (urlLower.contains(cleanPattern)) return true

        return false
    }

    fun startProxy(
        settings: ProxySettings,
        scope: CoroutineScope,
        onTransactionCaptured: (HttpTransactionEntity) -> Unit,
        onInterceptCaptured: (InterceptedRequestEntity, onAction: (InterceptAction) -> Unit) -> Unit,
        onStatsUpdated: (bytes: Long, activeConnIncrement: Int) -> Unit
    ) {
        if (isRunning) stopProxy()

        currentSettings = settings
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

                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(bindAddr, settings.port), 100)
                serverSocket = socket
                Log.d("ProxyEngine", "Proxy Server started listening on 0.0.0.0 (All interfaces):${settings.port}")

                val httpClient = buildOkHttpClient(settings)

                while (isRunning && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        clientSocket.soTimeout = 30000
                        onStatsUpdated(0L, 1)
                        executor.execute {
                            try {
                                handleClientSocket(clientSocket, httpClient, onTransactionCaptured, onInterceptCaptured, onStatsUpdated)
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

        if (settings.http2Enabled) {
            builder.protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        } else {
            builder.protocols(listOf(Protocol.HTTP_1_1))
        }

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

    /** Reads "Header: value" lines up to the blank line that ends a request/response head. */
    private fun readHeaderBlock(input: InputStream): Pair<MutableMap<String, String>, Int> {
        val headersMap = mutableMapOf<String, String>()
        var contentLength = 0
        while (true) {
            val line = readHeaderLine(input) ?: break
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
        return headersMap to contentLength
    }

    private fun readBody(input: InputStream, contentLength: Int): String {
        if (contentLength <= 0) return ""
        val bodyBytes = ByteArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
            val read = input.read(bodyBytes, totalRead, contentLength - totalRead)
            if (read <= 0) break
            totalRead += read
        }
        return String(bodyBytes, 0, totalRead, Charsets.UTF_8)
    }

    private fun handleClientSocket(
        clientSocket: Socket,
        httpClient: OkHttpClient,
        onTransactionCaptured: (HttpTransactionEntity) -> Unit,
        onInterceptCaptured: (InterceptedRequestEntity, onAction: (InterceptAction) -> Unit) -> Unit,
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

        val (headersMap, contentLength) = readHeaderBlock(inputStream)

        // Handle HTTPS CONNECT tunnel
        if (method == "CONNECT") {
            handleConnectTunnel(rawUrl, clientSocket, outputStream, httpClient, onTransactionCaptured, onInterceptCaptured, onStatsUpdated)
            return
        }

        val requestBodyString = readBody(inputStream, contentLength)

        val fullUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            val hostHeader = headersMap["Host"] ?: "127.0.0.1"
            "http://$hostHeader$rawUrl"
        }

        processAndForward(method, fullUrl, headersMap, requestBodyString, outputStream, httpClient, onTransactionCaptured, onInterceptCaptured, onStatsUpdated)
    }

    /**
     * Terminates the client's TLS connection with a per-host leaf certificate,
     * decrypts the real HTTP request inside it, and forwards it through the
     * same [processAndForward] pipeline used for plain HTTP — so the logged
     * method/URL/headers/body are the real ones, not just "CONNECT".
     */
    private fun handleConnectTunnel(
        hostPort: String,
        clientSocket: Socket,
        rawOutputStream: OutputStream,
        httpClient: OkHttpClient,
        onTransactionCaptured: (HttpTransactionEntity) -> Unit,
        onInterceptCaptured: (InterceptedRequestEntity, onAction: (InterceptAction) -> Unit) -> Unit,
        onStatsUpdated: (bytes: Long, activeConnIncrement: Int) -> Unit
    ) {
        val targetHost = hostPort.substringBefore(":")

        // Tell the client the tunnel is open — from here on we take over TLS ourselves.
        rawOutputStream.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
        rawOutputStream.flush()

        var clientTls: SSLSocket? = null
        try {
            val sslContext: SSLContext = CertificateManager.sslContextForHost(context, targetHost)
            clientTls = sslContext.socketFactory.createSocket(clientSocket, targetHost, clientSocket.port, true) as SSLSocket
            clientTls.useClientMode = false
            // Pin ALPN to HTTP/1.1 for the browser-facing leg — our text-based
            // header parser can't handle HTTP/2 framing. The upstream leg to the
            // real origin still goes through OkHttp below, which negotiates
            // HTTP/2 with the real server just fine on its own.
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val params = clientTls.sslParameters
                params.applicationProtocols = arrayOf("http/1.1")
                clientTls.sslParameters = params
            }
            clientTls.startHandshake()

            val tlsInput = clientTls.getInputStream()
            val tlsOutput = clientTls.getOutputStream()

            val requestLine = readHeaderLine(tlsInput) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val rawUrl = parts[1]
            val (headersMap, contentLength) = readHeaderBlock(tlsInput)
            val requestBodyString = readBody(tlsInput, contentLength)

            val fullUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                rawUrl
            } else {
                val hostHeader = headersMap["Host"] ?: targetHost
                "https://$hostHeader$rawUrl"
            }

            processAndForward(method, fullUrl, headersMap, requestBodyString, tlsOutput, httpClient, onTransactionCaptured, onInterceptCaptured, onStatsUpdated)
        } catch (e: Exception) {
            Log.e("ProxyEngine", "TLS termination failed for $hostPort: ${e.message}")
        } finally {
            try { clientTls?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Shared request/response pipeline: applies scope + intercept rules (request
     * side), forwards via OkHttp, applies intercept rules again (response side),
     * writes the raw HTTP response back to [outputStream], and logs the real
     * transaction. Used for both plain HTTP and decrypted HTTPS traffic.
     */
    private fun processAndForward(
        method: String,
        fullUrl: String,
        headersMap: Map<String, String>,
        requestBodyString: String,
        outputStream: OutputStream,
        httpClient: OkHttpClient,
        onTransactionCaptured: (HttpTransactionEntity) -> Unit,
        onInterceptCaptured: (InterceptedRequestEntity, onAction: (InterceptAction) -> Unit) -> Unit,
        onStatsUpdated: (bytes: Long, activeConnIncrement: Int) -> Unit
    ) {
        val headersJson = "{" + headersMap.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" } + "}"

        var execMethod = method
        var execUrl = fullUrl
        var execHeadersMap = headersMap.toMap()
        var execBody = requestBodyString

        val inScope = isUrlInScope(fullUrl, currentSettings, activeScopes)
        val effectiveInScope = if (currentSettings.enforceScopeOnly) inScope else true

        val shouldInterceptReq = effectiveInScope && currentSettings.isInterceptEnabled && currentSettings.interceptRequests && currentSettings.shouldInterceptMethod(method)

        if (shouldInterceptReq) {
            val latch = java.util.concurrent.CountDownLatch(1)
            var actionResult: InterceptAction = InterceptAction.Forward(method, fullUrl, headersMap, requestBodyString)

            val interceptEntity = InterceptedRequestEntity(
                method = method,
                url = fullUrl,
                headersJson = headersJson,
                body = requestBodyString
            )

            onInterceptCaptured(interceptEntity) { action ->
                actionResult = action
                latch.countDown()
            }

            try {
                latch.await(120, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.e("ProxyEngine", "Intercept wait timeout: ${e.message}")
            }

            when (val res = actionResult) {
                is InterceptAction.Drop -> {
                    val dropResp = "HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n[InterceptX] Request dropped by user in Intercept mode."
                    outputStream.write(dropResp.toByteArray(Charsets.UTF_8))
                    outputStream.flush()

                    val tx = HttpTransactionEntity(
                        url = fullUrl,
                        method = method,
                        statusCode = 502,
                        requestHeadersJson = headersJson,
                        requestBody = requestBodyString,
                        responseHeadersJson = "{\"Content-Type\":\"text/plain\"}",
                        responseBody = "[InterceptX] Request dropped by user in Intercept mode.",
                        responseTimeMs = 0L,
                        bytesTransferred = dropResp.length.toLong(),
                        isIntercepted = true
                    )
                    onTransactionCaptured(tx)
                    return
                }
                is InterceptAction.ForwardDirectResponse -> {
                    val statusText = getStatusMessage(res.statusCode)
                    val respBodyBytes = res.body.toByteArray(Charsets.UTF_8)
                    val finalHeaders = res.headersMap.toMutableMap()
                    finalHeaders["Content-Length"] = respBodyBytes.size.toString()
                    val headerLines = finalHeaders.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
                    val rawResponse = "HTTP/1.1 ${res.statusCode} $statusText\r\n$headerLines\r\n\r\n"
                    val headerBytes = rawResponse.toByteArray(Charsets.UTF_8)

                    outputStream.write(headerBytes)
                    if (respBodyBytes.isNotEmpty()) {
                        outputStream.write(respBodyBytes)
                    }
                    outputStream.flush()

                    val tx = HttpTransactionEntity(
                        url = fullUrl,
                        method = method,
                        statusCode = res.statusCode,
                        requestHeadersJson = headersJson,
                        requestBody = requestBodyString,
                        responseHeadersJson = "{" + res.headersMap.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" } + "}",
                        responseBody = res.body,
                        responseTimeMs = 12L,
                        bytesTransferred = (headerBytes.size + respBodyBytes.size).toLong(),
                        isIntercepted = true
                    )
                    onTransactionCaptured(tx)
                    return
                }
                is InterceptAction.Forward -> {
                    execMethod = res.method
                    execUrl = res.url
                    execHeadersMap = res.headersMap
                    execBody = res.body
                }
            }
        }

        val startTime = System.currentTimeMillis()
        var statusCode = 500
        var responseBodyString = ""
        var responseHeadersJson = "{}"
        var byteCount = 0L

        try {
            val reqBuilder = Request.Builder().url(execUrl)
            execHeadersMap.forEach { (k, v) ->
                if (!k.equals("Host", ignoreCase = true) && !k.equals("Proxy-Connection", ignoreCase = true)) {
                    reqBuilder.addHeader(k, v)
                }
            }

            if (execMethod in listOf("POST", "PUT", "PATCH")) {
                val mediaType = execHeadersMap["Content-Type"]?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
                reqBuilder.method(execMethod, execBody.toRequestBody(mediaType))
            } else if (execMethod == "DELETE") {
                if (execBody.isNotBlank()) {
                    val mediaType = execHeadersMap["Content-Type"]?.toMediaTypeOrNull()
                    reqBuilder.method("DELETE", execBody.toRequestBody(mediaType))
                } else {
                    reqBuilder.delete()
                }
            } else {
                reqBuilder.method(execMethod, null)
            }

            val okResponse = httpClient.newCall(reqBuilder.build()).execute()
            statusCode = okResponse.code
            responseBodyString = okResponse.body?.string() ?: ""

            val respHeaderMap = mutableMapOf<String, String>()
            respHeaderMap["X-Protocol"] = okResponse.protocol.toString()
            okResponse.headers.forEach { pair ->
                respHeaderMap[pair.first] = pair.second
            }
            responseHeadersJson = "{" + respHeaderMap.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" } + "}"

            var execStatusCode = statusCode
            var execRespHeaderMap = respHeaderMap.toMap()
            var execResponseBodyString = responseBodyString

            val effectiveInScope2 = if (currentSettings.enforceScopeOnly) inScope else true
            val shouldInterceptResp = effectiveInScope2 && currentSettings.isInterceptEnabled && currentSettings.interceptResponses && currentSettings.shouldInterceptMethod(execMethod)

            if (shouldInterceptResp) {
                val respLatch = java.util.concurrent.CountDownLatch(1)
                var respActionResult: InterceptAction = InterceptAction.Forward(statusCode.toString(), execUrl, respHeaderMap, responseBodyString)

                val responseInterceptEntity = InterceptedRequestEntity(
                    method = statusCode.toString(),
                    url = execUrl,
                    headersJson = responseHeadersJson,
                    body = responseBodyString,
                    isResponse = true,
                    statusCode = statusCode
                )

                onInterceptCaptured(responseInterceptEntity) { action ->
                    respActionResult = action
                    respLatch.countDown()
                }

                try {
                    respLatch.await(60, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    Log.e("ProxyEngine", "Response Intercept wait timeout: ${e.message}")
                }

                when (val res = respActionResult) {
                    is InterceptAction.Drop -> {
                        val dropResp = "HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n[InterceptX] Response dropped by user in Intercept mode."
                        outputStream.write(dropResp.toByteArray(Charsets.UTF_8))
                        outputStream.flush()

                        val execHeadersJson = "{" + execHeadersMap.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" } + "}"
                        val tx = HttpTransactionEntity(
                            url = execUrl,
                            method = execMethod,
                            statusCode = 502,
                            requestHeadersJson = execHeadersJson,
                            requestBody = execBody,
                            responseHeadersJson = "{\"Content-Type\":\"text/plain\"}",
                            responseBody = "[InterceptX] Response dropped by user in Intercept mode.",
                            responseTimeMs = (System.currentTimeMillis() - startTime),
                            bytesTransferred = dropResp.length.toLong(),
                            isIntercepted = true
                        )
                        onTransactionCaptured(tx)
                        return
                    }
                    is InterceptAction.ForwardDirectResponse -> {
                        execStatusCode = res.statusCode
                        execRespHeaderMap = res.headersMap
                        execResponseBodyString = res.body
                    }
                    is InterceptAction.Forward -> {
                        execStatusCode = res.method.toIntOrNull() ?: statusCode
                        execRespHeaderMap = res.headersMap
                        execResponseBodyString = res.body
                    }
                }
            }

            // Send HTTP response back to proxy client
            val statusText = getStatusMessage(execStatusCode)
            val respBodyBytes = execResponseBodyString.toByteArray(Charsets.UTF_8)

            val finalHeaders = execRespHeaderMap.toMutableMap()
            finalHeaders["Content-Length"] = respBodyBytes.size.toString()
            finalHeaders.keys.removeAll { it.equals("Transfer-Encoding", ignoreCase = true) }

            val headerLines = finalHeaders.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
            val rawResponse = "HTTP/1.1 $execStatusCode $statusText\r\n$headerLines\r\n\r\n"

            val headerBytes = rawResponse.toByteArray(Charsets.UTF_8)
            outputStream.write(headerBytes)
            if (respBodyBytes.isNotEmpty()) {
                outputStream.write(respBodyBytes)
            }
            outputStream.flush()

            byteCount = headerBytes.size.toLong() + respBodyBytes.size.toLong() + execBody.toByteArray().size

            val execHeadersJsonFinal = "{" + execHeadersMap.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" } + "}"
            if (inScope || !currentSettings.enforceScopeOnly) {
                val tx = HttpTransactionEntity(
                    url = execUrl,
                    method = execMethod,
                    statusCode = execStatusCode,
                    requestHeadersJson = execHeadersJsonFinal,
                    requestBody = execBody,
                    responseHeadersJson = "{" + execRespHeaderMap.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" } + "}",
                    responseBody = execResponseBodyString,
                    responseTimeMs = (System.currentTimeMillis() - startTime),
                    bytesTransferred = byteCount,
                    isIntercepted = currentSettings.isInterceptEnabled
                )
                onTransactionCaptured(tx)
            }
            onStatsUpdated(byteCount, 0)

        } catch (e: Exception) {
            Log.e("ProxyEngine", "Proxy execution error for $execUrl: ${e.message}")
            val errorResp = "HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain\r\n\r\nProxy Error: ${e.localizedMessage}"
            try {
                outputStream.write(errorResp.toByteArray())
                outputStream.flush()
            } catch (_: Exception) {}

            val execHeadersJson = "{" + execHeadersMap.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" } + "}"
            val tx = HttpTransactionEntity(
                url = execUrl,
                method = execMethod,
                statusCode = 502,
                requestHeadersJson = execHeadersJson,
                requestBody = execBody,
                responseHeadersJson = "{}",
                responseBody = "Proxy Error: ${e.localizedMessage}",
                responseTimeMs = (System.currentTimeMillis() - startTime),
                bytesTransferred = errorResp.length.toLong(),
                isIntercepted = currentSettings.isInterceptEnabled
            )
            onTransactionCaptured(tx)
        }
    }

    private fun getStatusMessage(code: Int): String {
        return when (code) {
            200 -> "OK"
            201 -> "Created"
            202 -> "Accepted"
            204 -> "No Content"
            301 -> "Moved Permanently"
            302 -> "Found"
            304 -> "Not Modified"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> "HTTP Response"
        }
    }
}
