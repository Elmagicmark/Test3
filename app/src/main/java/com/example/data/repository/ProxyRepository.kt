package com.example.data.repository

import com.example.data.local.*
import com.example.data.model.InterceptAction
import com.example.data.model.ProxySettings
import com.example.data.model.ProxyStats
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private val proxyEngine = ProxyEngine()

    private val _proxySettings = MutableStateFlow(ProxySettings())
    val proxySettings: StateFlow<ProxySettings> = _proxySettings.asStateFlow()

    private val _proxyStats = MutableStateFlow(ProxyStats())
    val proxyStats: StateFlow<ProxyStats> = _proxyStats.asStateFlow()

    val allTransactions: Flow<List<HttpTransactionEntity>> = transactionDao.getAllTransactions()
    val allRepeaterTabs: Flow<List<RepeaterTabEntity>> = repeaterDao.getAllTabs()
    val allInterceptedRequests: Flow<List<InterceptedRequestEntity>> = interceptDao.getAllIntercepted()
    val targetScopes: Flow<List<TargetScopeEntity>> = scopeDao.getAllScopes()
    val securityProjects: Flow<List<SecurityProjectEntity>> = projectDao.getAllProjects()

    private val _pendingInterceptActions = MutableSharedFlow<InterceptAction>()
    val pendingInterceptActions: SharedFlow<InterceptAction> = _pendingInterceptActions.asSharedFlow()

    init {
        scope.launch(Dispatchers.IO) {
            targetScopes.collect { scopes ->
                proxyEngine.updateActiveScopes(scopes)
            }
        }

        scope.launch(Dispatchers.IO) {
            _proxySettings.collect { settings ->
                proxyEngine.updateProxySettings(settings)
            }
        }
    }

    fun toggleProxyServer(running: Boolean) {
        _proxySettings.value = _proxySettings.value.copy(isProxyRunning = running)
        if (running) {
            proxyEngine.startProxy(
                onTransactionCaptured = { tx ->
                    scope.launch(Dispatchers.IO) {
                        transactionDao.insert(tx)
                        _proxyStats.value = _proxyStats.value.copy(
                            totalRequests = _proxyStats.value.totalRequests + 1,
                            lastRequestTimestamp = System.currentTimeMillis()
                        )
                    }
                },
                onInterceptCaptured = { req, onAction ->
                    scope.launch(Dispatchers.IO) {
                        val id = interceptDao.insert(req)
                        val action = _pendingInterceptActions.first()
                        onAction(action)
                    }
                },
                onStatsUpdated = { bytes, _ ->
                    _proxyStats.value = _proxyStats.value.copy(
                        totalBytesTransferred = _proxyStats.value.totalBytesTransferred + bytes
                    )
                }
            )
        } else {
            proxyEngine.stopProxy()
        }
    }

    fun toggleIntercept(enabled: Boolean) {
        _proxySettings.value = _proxySettings.value.copy(isInterceptEnabled = enabled)
    }

    fun updateProxySettings(settings: ProxySettings) {
        _proxySettings.value = settings
    }

    fun toggleInterceptMethod(method: String) {
        val current = _proxySettings.value.interceptMethods.toMutableSet()
        if (current.contains(method)) {
            current.remove(method)
        } else {
            current.add(method)
        }
        _proxySettings.value = _proxySettings.value.copy(interceptMethods = current)
    }

    fun toggleEnforceScopeOnly(enforce: Boolean) {
        _proxySettings.value = _proxySettings.value.copy(enforceScopeOnly = enforce)
    }

    fun toggleFilterHistoryByScope(filter: Boolean) {
        _proxySettings.value = _proxySettings.value.copy(filterHistoryByScope = filter)
    }

    fun toggleIncludeSubdomains(include: Boolean) {
        _proxySettings.value = _proxySettings.value.copy(includeSubdomains = include)
    }

    fun toggleHttp2(enabled: Boolean) {
        _proxySettings.value = _proxySettings.value.copy(http2Enabled = enabled)
    }

    fun saveTransaction(tx: HttpTransactionEntity) {
        scope.launch(Dispatchers.IO) {
            transactionDao.insert(tx)
        }
    }

    fun saveTransactions(txs: List<HttpTransactionEntity>) {
        scope.launch(Dispatchers.IO) {
            txs.forEach { transactionDao.insert(it) }
        }
    }

    fun deleteTransaction(id: Long) {
        scope.launch(Dispatchers.IO) {
            transactionDao.deleteById(id)
        }
    }

    fun deleteTransactions(ids: List<Long>) {
        scope.launch(Dispatchers.IO) {
            ids.forEach { transactionDao.deleteById(it) }
        }
    }

    fun clearHistory() {
        scope.launch(Dispatchers.IO) {
            transactionDao.clearAll()
        }
    }

    fun createRepeaterTab(tabName: String, method: String, url: String, headersJson: String, body: String) {
        scope.launch(Dispatchers.IO) {
            repeaterDao.insert(
                RepeaterTabEntity(
                    tabName = tabName,
                    method = method,
                    url = url,
                    headersJson = headersJson,
                    body = body
                )
            )
        }
    }

    fun updateRepeaterTab(tab: RepeaterTabEntity) {
        scope.launch(Dispatchers.IO) {
            repeaterDao.update(tab)
        }
    }

    fun deleteRepeaterTab(id: Long) {
        scope.launch(Dispatchers.IO) {
            repeaterDao.deleteById(id)
        }
    }

    fun addInterceptedRequest(req: InterceptedRequestEntity) {
        scope.launch(Dispatchers.IO) {
            interceptDao.insert(req)
        }
    }

    fun forwardInterceptedRequest(id: Long, method: String, url: String, headersJson: String, body: String) {
        scope.launch(Dispatchers.IO) {
            _pendingInterceptActions.emit(InterceptAction.Forward(method, url, headersJson, body))
            interceptDao.deleteById(id)
        }
    }

    fun forwardInterceptedResponse(id: Long, statusCode: Int, headersJson: String, body: String) {
        scope.launch(Dispatchers.IO) {
            _pendingInterceptActions.emit(InterceptAction.ForwardResponse(statusCode, headersJson, body))
            interceptDao.deleteById(id)
        }
    }

    fun dropInterceptedRequest(id: Long) {
        scope.launch(Dispatchers.IO) {
            _pendingInterceptActions.emit(InterceptAction.Drop)
            interceptDao.deleteById(id)
        }
    }

    fun forwardAllIntercepted() {
        scope.launch(Dispatchers.IO) {
            val all = interceptDao.getAllIntercepted().first()
            all.forEach { req ->
                _pendingInterceptActions.emit(InterceptAction.Forward(req.method, req.url, req.headersJson, req.body))
                interceptDao.deleteById(req.id)
            }
        }
    }

    fun addTargetScope(pattern: String, isInScope: Boolean) {
        scope.launch(Dispatchers.IO) {
            scopeDao.insert(TargetScopeEntity(pattern = pattern, isInScope = isInScope))
        }
    }

    fun deleteTargetScope(id: Long) {
        scope.launch(Dispatchers.IO) {
            scopeDao.deleteById(id)
        }
    }

    fun addSecurityProject(name: String, description: String) {
        scope.launch(Dispatchers.IO) {
            projectDao.insert(SecurityProjectEntity(name = name, description = description))
        }
    }

    fun deleteProject(id: Long) {
        scope.launch(Dispatchers.IO) {
            projectDao.deleteById(id)
        }
    }

    fun simulateTestInterceptRequest() {
        scope.launch(Dispatchers.IO) {
            addInterceptedRequest(
                InterceptedRequestEntity(
                    method = "POST",
                    url = "https://example.com/api/test",
                    headersJson = "{\"Content-Type\":\"application/json\"}",
                    body = "{\"test\":\"data\"}"
                )
            )
        }
    }

    fun fetchAndInspectResponse(id: Long, method: String, url: String, headersJson: String, body: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val requestBuilder = Request.Builder().url(url).method(method, if (body.isNotEmpty()) okhttp3.RequestBody.create(null, body.toByteArray()) else null)
                val response = client.newCall(requestBuilder.build()).execute()
                val responseBody = response.body?.string() ?: ""

                forwardInterceptedResponse(id, response.code, response.headers.toString(), responseBody)
            } catch (e: Exception) {
                forwardInterceptedResponse(id, 0, "{}", "Error: ${e.message}")
            }
        }
    }

    fun executeRawHttpRequest(method: String, url: String, headersMap: Map<String, String>, bodyString: String): Pair<Int, String> {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val requestBuilder = Request.Builder().url(url).method(method, if (bodyString.isNotEmpty()) okhttp3.RequestBody.create(null, bodyString.toByteArray()) else null)
            headersMap.forEach { (k, v) -> requestBuilder.header(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: ""
            Pair(response.code, body)
        } catch (e: Exception) {
            Pair(0, "Error: ${e.message}")
        }
    }
}
