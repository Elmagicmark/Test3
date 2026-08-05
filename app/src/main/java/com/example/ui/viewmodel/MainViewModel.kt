package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.model.CertificateInfo
import com.example.data.model.ProxySettings
import com.example.data.model.ProxyStats
import com.example.data.repository.ProxyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = InterceptXDatabase.getDatabase(application)
    val repository = ProxyRepository(db, viewModelScope)

    val proxySettings: StateFlow<ProxySettings> = repository.proxySettings
    val proxyStats: StateFlow<ProxyStats> = repository.proxyStats

    val transactions: StateFlow<List<HttpTransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val repeaterTabs: StateFlow<List<RepeaterTabEntity>> = repository.allRepeaterTabs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val interceptedRequests: StateFlow<List<InterceptedRequestEntity>> = repository.allInterceptedRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val targetScopes: StateFlow<List<TargetScopeEntity>> = repository.targetScopes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val securityProjects: StateFlow<List<SecurityProjectEntity>> = repository.securityProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val certificateInfo = CertificateInfo()

    init {
        seedDefaultsIfEmpty()
    }

    private fun seedDefaultsIfEmpty() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = db.httpTransactionDao().getAllTransactions().first()
                if (list.isEmpty()) {
                    val sampleList = listOf(
                        HttpTransactionEntity(
                            method = "POST",
                            url = "https://api.target-app.internal/v1/auth/login",
                            statusCode = 200,
                            responseTimeMs = 142,
                            requestHeadersJson = "{\"Host\":\"api.target-app.internal\",\"Content-Type\":\"application/json\",\"User-Agent\":\"InterceptX-Client/1.0\"}",
                            requestBody = "{\"username\":\"admin_sec\",\"auth_token\":\"dG9rZW5fYmFzZTY0\"}",
                            responseHeadersJson = "{\"Content-Type\":\"application/json; charset=utf-8\",\"Set-Cookie\":\"session_id=sx_99214; Secure; HttpOnly\"}",
                            responseBody = "{\"status\":\"authenticated\",\"role\":\"administrator\",\"permissions\":[\"read\",\"write\",\"intercept\",\"exec\"]}",
                            bytesTransferred = 1240
                        ),
                        HttpTransactionEntity(
                            method = "GET",
                            url = "https://api.target-app.internal/v1/user/profile?id=1024",
                            statusCode = 200,
                            responseTimeMs = 88,
                            requestHeadersJson = "{\"Host\":\"api.target-app.internal\",\"Authorization\":\"Bearer sx_99214\"}",
                            requestBody = "",
                            responseHeadersJson = "{\"Content-Type\":\"application/json\"}",
                            responseBody = "{\"id\":1024,\"email\":\"secops@interceptx.net\",\"clearance\":\"Level-4\"}",
                            bytesTransferred = 850
                        ),
                        HttpTransactionEntity(
                            method = "PUT",
                            url = "https://api.target-app.internal/v1/config/settings",
                            statusCode = 403,
                            responseTimeMs = 210,
                            requestHeadersJson = "{\"Host\":\"api.target-app.internal\",\"Content-Type\":\"application/json\"}",
                            requestBody = "{\"debug_mode\":true,\"allow_cors_wildcard\":true}",
                            responseHeadersJson = "{\"Content-Type\":\"application/json\"}",
                            responseBody = "{\"error\":\"Access Denied\",\"code\":\"E_INSUFFICIENT_CLEARANCE\"}",
                            bytesTransferred = 420
                        ),
                        HttpTransactionEntity(
                            method = "DELETE",
                            url = "https://api.target-app.internal/v1/cache/purge",
                            statusCode = 500,
                            responseTimeMs = 450,
                            requestHeadersJson = "{\"Host\":\"api.target-app.internal\"}",
                            requestBody = "{\"target_cluster\":\"prod-us-east\"}",
                            responseHeadersJson = "{\"Content-Type\":\"application/json\"}",
                            responseBody = "{\"error\":\"Internal Service Exception\",\"stack_trace\":\"NullPointerAtCacheManager.java:82\"}",
                            bytesTransferred = 630
                        )
                    )
                    repository.saveTransactions(sampleList)
                }

                val tabs = db.repeaterDao().getAllTabs().first()
                if (tabs.isEmpty()) {
                    repository.createRepeaterTab(
                        tabName = "Tab 1 (Auth)",
                        method = "POST",
                        url = "https://api.target-app.internal/v1/auth/verify",
                        headersJson = "{\"Content-Type\":\"application/json\",\"X-Api-Key\":\"sec_live_9948\"}",
                        body = "{\"challenge_id\":\"ch_88192\",\"response\":\"0xFA829B\"}"
                    )
                    repository.createRepeaterTab(
                        tabName = "Tab 2 (GraphQL)",
                        method = "POST",
                        url = "https://api.target-app.internal/graphql",
                        headersJson = "{\"Content-Type\":\"application/json\"}",
                        body = "{\"query\":\"query { currentUser { id name roles permissions } }\"}"
                    )
                }

                val reqs = db.interceptedRequestDao().getAllIntercepted().first()
                if (reqs.isEmpty()) {
                    repository.addInterceptedRequest(
                        InterceptedRequestEntity(
                            method = "POST",
                            url = "https://checkout.shop.internal/api/v2/payment/process",
                            headersJson = "{\"Host\":\"checkout.shop.internal\",\"Content-Type\":\"application/json\",\"X-Requested-With\":\"XMLHttpRequest\"}",
                            body = "{\"cart_id\":\"cart_9921\",\"amount\":199.99,\"currency\":\"USD\",\"payment_method\":\"token_cc\"}"
                        )
                    )
                }

                val scopes = db.targetScopeDao().getAllScopes().first()
                if (scopes.isEmpty()) {
                    repository.addTargetScope("target-app.internal", true)
                    repository.addTargetScope("shop.internal", true)
                    repository.addTargetScope("analytics-tracker.net", false)
                }

                val projects = db.securityProjectDao().getAllProjects().first()
                if (projects.isEmpty()) {
                    repository.addSecurityProject("Default Workspace", "Primary assessment project workspace")
                    repository.addSecurityProject("Mobile App Audit", "Targeted security testing for Android API endpoints")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleProxyServer(running: Boolean) {
        repository.toggleProxyServer(running)
    }

    fun toggleIntercept(enabled: Boolean) {
        repository.toggleIntercept(enabled)
    }

    fun updateProxySettings(settings: ProxySettings) {
        repository.updateProxySettings(settings)
    }

    fun forwardInterceptedRequest(id: Long, method: String, url: String, headersJson: String, body: String) {
        viewModelScope.launch {
            repository.forwardInterceptedRequest(id, method, url, headersJson, body)
        }
    }

    fun forwardInterceptedResponse(id: Long, statusCode: Int, headersJson: String, body: String) {
        viewModelScope.launch {
            repository.forwardInterceptedResponse(id, statusCode, headersJson, body)
        }
    }

    fun fetchAndInspectResponse(id: Long, method: String, url: String, headersJson: String, body: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.fetchAndInspectResponse(id, method, url, headersJson, body)
            onComplete()
        }
    }

    fun toggleInterceptMethod(method: String) {
        repository.toggleInterceptMethod(method)
    }

    fun toggleEnforceScopeOnly(enforce: Boolean) {
        repository.toggleEnforceScopeOnly(enforce)
    }

    fun toggleIncludeSubdomains(include: Boolean) {
        repository.toggleIncludeSubdomains(include)
    }

    fun toggleHttp2(enabled: Boolean) {
        repository.toggleHttp2(enabled)
    }

    fun simulateTestInterceptRequest() {
        repository.simulateTestInterceptRequest()
    }

    fun dropInterceptedRequest(id: Long) {
        viewModelScope.launch {
            repository.dropInterceptedRequest(id)
        }
    }

    fun forwardAllIntercepted() {
        viewModelScope.launch {
            repository.forwardAllIntercepted()
        }
    }

    fun sendToRepeater(method: String, url: String, headersJson: String, body: String, name: String = "Repeater") {
        viewModelScope.launch {
            repository.createRepeaterTab(
                tabName = name,
                method = method,
                url = url,
                headersJson = headersJson,
                body = body
            )
        }
    }

    fun updateRepeaterTab(tab: RepeaterTabEntity) {
        viewModelScope.launch {
            repository.updateRepeaterTab(tab)
        }
    }

    fun deleteRepeaterTab(id: Long) {
        viewModelScope.launch {
            repository.deleteRepeaterTab(id)
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
    }

    fun deleteTransactions(ids: List<Long>) {
        viewModelScope.launch {
            repository.deleteTransactions(ids)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun addTargetScope(pattern: String, isInScope: Boolean) {
        viewModelScope.launch {
            repository.addTargetScope(pattern, isInScope)
        }
    }

    fun deleteTargetScope(id: Long) {
        viewModelScope.launch {
            repository.deleteTargetScope(id)
        }
    }

    fun addSecurityProject(name: String, description: String) {
        viewModelScope.launch {
            repository.addSecurityProject(name, description)
        }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch {
            repository.deleteProject(id)
        }
    }

    fun executeRepeaterRequest(tab: RepeaterTabEntity, onResult: (Int, String) -> Unit) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val rawResp = repository.executeRawHttpRequest(
                method = tab.method,
                url = tab.url,
                headersMap = parseJsonToMap(tab.headersJson),
                bodyString = tab.body
            )
            val status = rawResp.first
            val respHeadersMap = rawResp.second
            val responseBody = rawResp.third
            val elapsed = System.currentTimeMillis() - startTime
            val respHeadersJson = "{" + respHeadersMap.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" } + "}"
            val updatedTab = tab.copy(
                lastResponseStatus = status,
                lastResponseBody = responseBody,
                lastResponseHeadersJson = respHeadersJson,
                lastResponseTimeMs = elapsed
            )
            repository.updateRepeaterTab(updatedTab)
            onResult(status, responseBody)
        }
    }

    fun executeRawComposerRequest(
        method: String,
        url: String,
        headersJson: String,
        body: String,
        onResult: (Int, String, Long) -> Unit
    ) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val rawResp = repository.executeRawHttpRequest(
                method = method,
                url = url,
                headersMap = parseJsonToMap(headersJson),
                bodyString = body
            )
            val status = rawResp.first
            val respHeadersMap = rawResp.second
            val responseBody = rawResp.third
            val elapsed = System.currentTimeMillis() - startTime
            val respHeadersJson = "{" + respHeadersMap.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" } + "}"
            
            // Save transaction log
            repository.saveTransaction(
                HttpTransactionEntity(
                    method = method,
                    url = url,
                    statusCode = status,
                    responseTimeMs = elapsed,
                    requestHeadersJson = headersJson,
                    requestBody = body,
                    responseHeadersJson = respHeadersJson,
                    responseBody = responseBody,
                    bytesTransferred = (body.length + responseBody.length).toLong()
                )
            )
            onResult(status, responseBody, elapsed)
        }
    }

    private fun parseJsonToMap(json: String): Map<String, String> {
        return try {
            val map = mutableMapOf<String, String>()
            if (json.trim().startsWith("{") && json.trim().endsWith("}")) {
                val cleaned = json.trim().substring(1, json.trim().length - 1)
                val pairs = cleaned.split(",")
                for (pair in pairs) {
                    val kv = pair.split(":")
                    if (kv.size >= 2) {
                        val k = kv[0].replace("\"", "").trim()
                        val v = kv[1].replace("\"", "").trim()
                        if (k.isNotEmpty()) map[k] = v
                    }
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
