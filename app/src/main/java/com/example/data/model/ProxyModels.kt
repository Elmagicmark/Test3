package com.example.data.model

data class ProxySettings(
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    val isInterceptEnabled: Boolean = false,
    val isProxyRunning: Boolean = true,
    val upstreamProxyEnabled: Boolean = false,
    val upstreamProxyHost: String = "",
    val upstreamProxyPort: Int = 8080,
    val sslBypassEnabled: Boolean = true,
    val http2Enabled: Boolean = true,
    val interceptMethods: Set<String> = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "CONNECT"),
    val interceptRequests: Boolean = true,
    val interceptResponses: Boolean = true,
    val enforceScopeOnly: Boolean = false,
    val filterHistoryByScope: Boolean = false,  // ← جديد
    val includeSubdomains: Boolean = true,
    val bypassDomains: List<String> = emptyList()
) {
    fun shouldInterceptMethod(method: String): Boolean {
        if (interceptMethods.isEmpty()) return true
        return interceptMethods.any { it.equals(method, ignoreCase = true) }
    }
}

data class ProxyStats(
    val activeConnections: Int = 0,
    val totalRequests: Long = 0,
    val totalBytesTransferred: Long = 0,
    val lastRequestTimestamp: Long = 0
)

data class CertificateInfo(
    val commonName: String = "InterceptX Security Root CA",
    val organization: String = "InterceptX Cyber Labs Inc.",
    val serialNumber: String = "A1B2C3D4E5F6",
    val validFrom: String = "2026-01-01",
    val validTo: String = "2036-01-01",
    val sha256Fingerprint: String = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB",
    val md5Fingerprint: String = "AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90"
)
