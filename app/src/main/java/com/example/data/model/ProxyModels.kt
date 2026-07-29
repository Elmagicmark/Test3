package com.example.data.model

data class ProxySettings(
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    val isInterceptEnabled: Boolean = false,
    val isProxyRunning: Boolean = false,
    val upstreamProxyEnabled: Boolean = false,
    val upstreamProxyHost: String = "",
    val upstreamProxyPort: Int = 8080,
    val sslBypassEnabled: Boolean = true,
    val interceptMethods: Set<String> = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"),
    val interceptRequests: Boolean = true,
    val interceptResponses: Boolean = true,
    val enforceScopeOnly: Boolean = false,
    val includeSubdomains: Boolean = true
) {
    fun shouldInterceptMethod(method: String): Boolean {
        if (method.equals("CONNECT", ignoreCase = true)) return false
        if (interceptMethods.isEmpty()) return true
        return interceptMethods.any { it.equals(method, ignoreCase = true) }
    }
}

data class ProxyStats(
    val totalRequests: Int = 0,
    val interceptedRequests: Int = 0,
    val activeConnections: Int = 0,
    val bytesTransferred: Long = 0L
)

data class CertificateInfo(
    val commonName: String = "InterceptX Security CA",
    val organization: String = "InterceptX Cyber Labs",
    val serialNumber: String = "7A:93:B4:88:1C:E0:4F:29",
    val validFrom: String = "2025-01-01 00:00:00 UTC",
    val validTo: String = "2035-12-31 23:59:59 UTC",
    val sha256Fingerprint: String = "9B:5E:82:1D:C4:F0:3A:76:88:E2:BB:A4:91:C2:5E:70:A9:C1:DF:8B:33:EE:4D:12:8F:88:9C:2B:10:A8:DF:49",
    val md5Fingerprint: String = "E4:8A:2F:90:1C:5D:82:77:B1:3C:99:A0:F2:41:88:0B"
)

enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, CONNECT, TRACE
}

data class KeyValuePair(
    val key: String,
    val value: String,
    val isEnabled: Boolean = true
)
