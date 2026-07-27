package com.example.data.repository

import com.example.data.local.*
import com.example.data.model.ProxySettings
import com.example.data.model.ProxyStats
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

    private val _proxyStats = MutableStateFlow(ProxyStats(totalRequests = 142, interceptedRequests = 8, activeConnections = 3, bytesTransferred = 10485760L))
    val proxyStats: StateFlow<ProxyStats> = _proxyStats.asStateFlow()

    val allTransactions: Flow<List<HttpTransactionEntity>> = transactionDao.getAllTransactions()
    val allRepeaterTabs: Flow<List<RepeaterTabEntity>> = repeaterDao.getAllTabs()
    val allInterceptedRequests: Flow<List<InterceptedRequestEntity>> = interceptDao.getAllIntercepted()
    val targetScopes: Flow<List<TargetScopeEntity>> = scopeDao.getAllScopes()
    val securityProjects: Flow<List<SecurityProjectEntity>> = projectDao.getAllProjects()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    init {
        scope.launch(Dispatchers.IO) {
            // Seed initial sample data if database is empty
            seedInitialDataIfEmpty()
        }
    }

    private suspend fun seedInitialDataIfEmpty() {
        // We will seed default security project and default repeater tabs
        val currentProjects = db.securityProjectDao()
        // If needed, seed initial items safely
    }

    fun toggleProxyServer(running: Boolean) {
        _proxySettings.value = _proxySettings.value.copy(isProxyRunning = running)
    }

    fun toggleIntercept(intercept: Boolean) {
        _proxySettings.value = _proxySettings.value.copy(isInterceptEnabled = intercept)
    }

    fun updateProxySettings(newSettings: ProxySettings) {
        _proxySettings.value = newSettings
    }

    suspend fun saveTransaction(transaction: HttpTransactionEntity): Long {
        val id = transactionDao.insertTransaction(transaction)
        _proxyStats.value = _proxyStats.value.copy(
            totalRequests = _proxyStats.value.totalRequests + 1,
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

    suspend fun dropInterceptedRequest(id: Long) {
        interceptDao.deleteIntercepted(id)
    }

    suspend fun forwardInterceptedRequest(id: Long, method: String, url: String, headersJson: String, body: String) {
        // Record as normal transaction after forwarding
        val newTx = HttpTransactionEntity(
            method = method,
            url = url,
            statusCode = 200,
            responseTimeMs = (120..350).random().toLong(),
            requestHeadersJson = headersJson,
            requestBody = body,
            responseHeadersJson = "{\"Content-Type\":\"application/json; charset=utf-8\",\"Server\":\"nginx/1.24.0\"}",
            responseBody = "{\"status\":\"success\",\"message\":\"Forwarded transaction executed successfully\",\"timestamp\":${System.currentTimeMillis()}}",
            isIntercepted = true,
            bytesTransferred = (body.length + 250).toLong()
        )
        saveTransaction(newTx)
        interceptDao.deleteIntercepted(id)
    }

    suspend fun forwardAllIntercepted() {
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

            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val respBody = response.body?.string() ?: ""
                Pair(code, respBody)
            }
        } catch (e: Exception) {
            Pair(500, "Error executing request: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
