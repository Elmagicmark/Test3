package com.example.data.repository

import com.example.data.local.*
import com.example.data.model.ProxySettings
import com.example.data.model.ProxyStats
import com.example.proxy.ProxyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class ProxyRepository(
    private val db: InterceptXDatabase,
    private val scope: CoroutineScope
) {
    private val transactionDao = db.httpTransactionDao()
    private val repeaterDao = db.repeaterDao()
    private val interceptDao = db.interceptedRequestDao()
    private val scopeDao = db.targetScopeDao()
    private val projectDao = db.securityProjectDao()

    private val _proxySettings = MutableStateFlow(ProxySettings())
    val proxySettings: StateFlow<ProxySettings> = _proxySettings.asStateFlow()

    private val _proxyStats = MutableStateFlow(ProxyStats(totalRequests = 142, interceptedRequests = 8, activeConnections = 0, bytesTransferred = 10485760L))
    val proxyStats: StateFlow<ProxyStats> = _proxyStats.asStateFlow()

    val allTransactions: Flow<List<HttpTransactionEntity>> = transactionDao.getAllTransactions()
    val allRepeaterTabs: Flow<List<RepeaterTabEntity>> = repeaterDao.getAllTabs()
    val allInterceptedRequests: Flow<List<InterceptedRequestEntity>> = interceptDao.getAllIntercepted()
    val targetScopes: Flow<List<TargetScopeEntity>> = scopeDao.getAllScopes()
    val securityProjects: Flow<List<SecurityProjectEntity>> = projectDao.getAllProjects()

    private val proxyEngine = ProxyEngine()
    private val pendingIntercepts = java.util.concurrent.ConcurrentHashMap<Long, (com.example.proxy.InterceptAction) -> Unit>()

    init {
        scope.launch(Dispatchers.IO) {
            seedInitialDataIfEmpty()
        }
    }

    private suspend fun seedInitialDataIfEmpty() {
        val currentProjects = db.securityProjectDao()
    }

    fun toggleProxyServer(running: Boolean) {
        val newSettings = _proxySettings.value.copy(isProxyRunning = running)
        _proxySettings.value = newSettings

        if (running) {
            startProxyEngine(newSettings)
        } else {
            releaseAllPendingIntercepts()
            proxyEngine.stopProxy()
            _proxyStats.value = _proxyStats.value.copy(activeConnections = 0)
        }
    }

    fun toggleIntercept(intercept: Boolean) {
        val newSettings = _proxySettings.value.copy(isInterceptEnabled = intercept)
        _proxySettings.value = newSettings
        if (!intercept) {
            releaseAllPendingIntercepts()
        }
        if (newSettings.isProxyRunning) {
            startProxyEngine(newSettings)
        }
    }

    private fun releaseAllPendingIntercepts() {
        pendingIntercepts.forEach { (_, callback) ->
            callback.invoke(com.example.proxy.InterceptAction.Drop)
        }
        pendingIntercepts.clear()
        scope.launch(Dispatchers.IO) {
            interceptDao.clearAll()
        }
    }

    fun updateProxySettings(newSettings: ProxySettings) {
        _proxySettings.value = newSettings
        if (newSettings.isProxyRunning) {
            startProxyEngine(newSettings)
        } else {
            releaseAllPendingIntercepts()
            proxyEngine.stopProxy()
            _proxyStats.value = _proxyStats.value.copy(activeConnections = 0)
        }
    }

    private fun startProxyEngine(settings: ProxySettings) {
        proxyEngine.startProxy(
            settings = settings,
            scope = scope,
            onTransactionCaptured = { tx ->
                scope.launch(Dispatchers.IO) {
                    saveTransaction(tx)
                }
            },
            onInterceptCaptured = { req, onAction ->
                scope.launch(Dispatchers.IO) {
                    val id = addInterceptedRequest(req)
                    pendingIntercepts[id] = onAction
                }
            },
            onStatsUpdated = { bytes, connDelta ->
                val curr = _proxyStats.value
                val newActive = (curr.activeConnections + connDelta).coerceAtLeast(0)
                _proxyStats.value = curr.copy(
                    bytesTransferred = curr.bytesTransferred + bytes,
                    activeConnections = newActive
                )
            }
        )
    }

    suspend fun saveTransaction(transaction: HttpTransactionEntity): Long {
        val id = transactionDao.insertTransaction(transaction)
        val isIntercepted = transaction.isIntercepted
        _proxyStats.value = _proxyStats.value.copy(
            totalRequests = _proxyStats.value.totalRequests + 1,
            interceptedRequests = if (isIntercepted) _proxyStats.value.interceptedRequests + 1 else _proxyStats.value.interceptedRequests,
            bytesTransferred = _proxyStats.value.bytesTransferred + transaction.bytesTransferred
        )
        return id
    }

    suspend fun saveTransactions(transactions: List<HttpTransactionEntity>) {
        transactionDao.insertTransactions(transactions)
    }

    suspend fun deleteTransaction(id: Long) {
        transactionDao.deleteTransactionById(id)
    }

    suspend fun deleteTransactions(ids: List<Long>) {
        transactionDao.deleteTransactionsByIds(ids)
    }

    suspend fun clearHistory() {
        transactionDao.clearAll()
    }

    // Repeater Tab methods
    suspend fun createRepeaterTab(tabName: String, method: String, url: String, headersJson: String, body: String): Long {
        val entity = RepeaterTabEntity(
            tabName = tabName,
            method = method,
            url = url,
            headersJson = headersJson,
            body = body
        )
        return repeaterDao.insertTab(entity)
    }

    suspend fun updateRepeaterTab(tab: RepeaterTabEntity) {
        repeaterDao.updateTab(tab)
    }

    suspend fun deleteRepeaterTab(id: Long) {
        repeaterDao.deleteTabById(id)
    }

    // Intercepted Request Actions
    suspend fun addInterceptedRequest(req: InterceptedRequestEntity): Long {
        _proxyStats.value = _proxyStats.value.copy(
            interceptedRequests = _proxyStats.value.interceptedRequests + 1
        )
        return interceptDao.insertIntercepted(req)
    }

    private fun parseHeadersJson(jsonStr: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (jsonStr.isBlank()) return map
        try {
            val json = org.json.JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = json.optString(key)
            }
        } catch (_: Exception) {}
        return map
    }

    suspend fun dropInterceptedRequest(id: Long) {
        val callback = pendingIntercepts.remove(id)
        callback?.invoke(com.example.proxy.InterceptAction.Drop)
        interceptDao.deleteIntercepted(id)
    }

    suspend fun forwardInterceptedRequest(id: Long, method: String, url: String, headersJson: String, body: String) {
        val callback = pendingIntercepts.remove(id)
        val headersMap = parseHeadersJson(headersJson)
        callback?.invoke(com.example.proxy.InterceptAction.Forward(method, url, headersMap, body))
        interceptDao.deleteIntercepted(id)
    }

    suspend fun forwardInterceptedResponse(id: Long, statusCode: Int, headersJson: String, body: String) {
        val callback = pendingIntercepts.remove(id)
        val headersMap = parseHeadersJson(headersJson)
        callback?.invoke(com.example.proxy.InterceptAction.ForwardDirectResponse(statusCode, headersMap, body))
        interceptDao.deleteIntercepted(id)
    }

    suspend fun fetchAndInspectResponse(id: Long, method: String, url: String, headersJson: String, body: String): Unit = withContext(Dispatchers.IO) {
        val headersMap = parseHeadersJson(headersJson)
        val (code, respBody) = executeRawHttpRequest(method, url, headersMap, body)
        val defaultRespHeaders = "{\"Content-Type\":\"application/json; charset=utf-8\",\"Server\":\"InterceptX-Engine\"}"
        val updatedEntity = InterceptedRequestEntity(
            id = id,
            method = code.toString(),
            url = url,
            headersJson = defaultRespHeaders,
            body = respBody,
            isResponse = true,
            statusCode = code
        )
        interceptDao.updateIntercepted(updatedEntity)
    }

    fun toggleInterceptMethod(method: String) {
        val current = _proxySettings.value.interceptMethods.toMutableSet()
        val upper = method.uppercase()
        if (current.contains(upper)) {
            current.remove(upper)
        } else {
            current.add(upper)
        }
        val newSettings = _proxySettings.value.copy(interceptMethods = current)
        updateProxySettings(newSettings)
    }

    suspend fun forwardAllIntercepted() {
        pendingIntercepts.forEach { (id, callback) ->
            val entity = interceptDao.getInterceptedById(id)
            if (entity != null) {
                val headersMap = parseHeadersJson(entity.headersJson)
                callback.invoke(com.example.proxy.InterceptAction.Forward(entity.method, entity.url, headersMap, entity.body))
            } else {
                callback.invoke(com.example.proxy.InterceptAction.Forward("GET", "", emptyMap(), ""))
            }
        }
        pendingIntercepts.clear()
        interceptDao.clearAll()
    }

    // Target Scope
    suspend fun addTargetScope(pattern: String, isInScope: Boolean) {
        scopeDao.insertScope(TargetScopeEntity(pattern = pattern, isInScope = isInScope))
    }

    suspend fun deleteTargetScope(id: Long) {
        scopeDao.deleteScope(id)
    }

    // Security Projects
    suspend fun addSecurityProject(name: String, description: String) {
        projectDao.insertProject(SecurityProjectEntity(name = name, description = description))
    }

    suspend fun setActiveProject(id: Long) {
        projectDao.setActiveProject(id)
    }

    suspend fun deleteProject(id: Long) {
        projectDao.deleteProject(id)
    }

    // Real HTTP execution for Repeater & Raw Composer
    suspend fun executeRawHttpRequest(
        method: String,
        url: String,
        headersMap: Map<String, String>,
        bodyString: String
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val settings = _proxySettings.value
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)

            if (settings.upstreamProxyEnabled && settings.upstreamProxyHost.isNotBlank()) {
                val upstreamProxy = Proxy(
                    Proxy.Type.HTTP,
                    InetSocketAddress(settings.upstreamProxyHost, settings.upstreamProxyPort)
                )
                clientBuilder.proxy(upstreamProxy)
            }

            val client = clientBuilder.build()

            val reqBuilder = Request.Builder().url(url)
            headersMap.forEach { (k, v) ->
                if (k.isNotBlank()) reqBuilder.addHeader(k, v)
            }

            val reqBody = if (method in listOf("POST", "PUT", "PATCH", "DELETE") && bodyString.isNotBlank()) {
                bodyString.toRequestBody()
            } else if (method == "POST" || method == "PUT" || method == "PATCH") {
                "".toRequestBody()
            } else null

            reqBuilder.method(method, reqBody)
            val request = reqBuilder.build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val respBody = response.body?.string() ?: ""
                Pair(code, respBody)
            }
        } catch (e: Exception) {
            Pair(500, "Error executing request: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
