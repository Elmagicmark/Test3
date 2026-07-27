package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "http_transactions")
data class HttpTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val method: String,
    val url: String,
    val statusCode: Int,
    val responseTimeMs: Long,
    val requestHeadersJson: String,
    val requestBody: String,
    val responseHeadersJson: String,
    val responseBody: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIntercepted: Boolean = false,
    val bytesTransferred: Long = 0
)

@Entity(tableName = "repeater_tabs")
data class RepeaterTabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tabName: String,
    val method: String,
    val url: String,
    val headersJson: String,
    val body: String,
    val lastResponseStatus: Int? = null,
    val lastResponseHeadersJson: String? = null,
    val lastResponseBody: String? = null,
    val lastResponseTimeMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "intercepted_requests")
data class InterceptedRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val method: String,
    val url: String,
    val headersJson: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "target_scopes")
data class TargetScopeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val isInScope: Boolean, // true = In Scope, false = Out of Scope
    val isEnabled: Boolean = true
)

@Entity(tableName = "security_projects")
data class SecurityProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)
