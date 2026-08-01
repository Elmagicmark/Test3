package com.example.util

import com.example.data.local.TargetScopeEntity

/**
 * Robust Burp Suite style Target Scope Matching Engine.
 * Supports:
 *  - Exact Host match (e.g. "example.com" matches "example.com")
 *  - Subdomain match (e.g. "example.com" or "*.example.com" matches "api.example.com", "test.api.example.com")
 *  - Strict boundaries: Does NOT match "myexample.com", "example.com.evil.com", or "example.org"
 *  - Priority order:
 *      1. Exclude from scope rules checked first (if matched -> out of scope)
 *      2. Include in scope rules checked next (if any exist, host must match at least one)
 */
object ScopeMatcher {

    /**
     * Extracts clean, lowercased hostname from a full URL, scheme + host, or raw domain string.
     */
    fun extractHost(urlOrHost: String): String {
        if (urlOrHost.isBlank()) return ""
        var clean = urlOrHost.trim().lowercase()
        clean = clean.removePrefix("http://").removePrefix("https://")
        val slashIdx = clean.indexOf('/')
        if (slashIdx != -1) clean = clean.substring(0, slashIdx)
        val portIdx = clean.indexOf(':')
        if (portIdx != -1) clean = clean.substring(0, portIdx)
        return clean.trim().removePrefix("www.").trim('.', ' ')
    }

    /**
     * Cleans a user-input scope pattern (e.g. "*.example.com", "https://api.example.com", "example.com/api").
     */
    fun cleanDomainPattern(pattern: String): String {
        var s = pattern.trim().lowercase()
        s = s.removePrefix("http://").removePrefix("https://")
        s = s.removePrefix("*.").removePrefix(".")
        s = s.replace(".*", "").replace("\\.", ".")
        val slashIdx = s.indexOf('/')
        if (slashIdx != -1) s = s.substring(0, slashIdx)
        val portIdx = s.indexOf(':')
        if (portIdx != -1) s = s.substring(0, portIdx)
        return s.trim().trim('.', ' ')
    }

    /**
     * Checks if a specific target host matches a single scope pattern rule.
     * Burp Suite style:
     * - "example.com" matches "example.com"
     * - "example.com" matches "api.example.com" and "www.example.com" (if includeSubdomains=true)
     * - "example.com" DOES NOT match "myexample.com", "example.com.evil.com", "example.org"
     */
    fun matchSingleScope(urlOrHost: String, pattern: String, includeSubdomains: Boolean = true): Boolean {
        val host = extractHost(urlOrHost)
        val cleanScope = cleanDomainPattern(pattern)
        if (host.isEmpty() || cleanScope.isEmpty()) return false

        // 1. Exact match
        if (host == cleanScope) return true

        // 2. Subdomain match (must be preceded by dot, e.g. .example.com)
        if (includeSubdomains && host.endsWith(".$cleanScope")) return true

        return false
    }

    /**
     * Full Target Scope evaluation:
     * 1. Check Exclude rules -> if any match, return false.
     * 2. Check Include rules -> if none present, return true; if present, host must match at least one.
     */
    fun isUrlInScope(
        urlOrHost: String,
        scopes: List<TargetScopeEntity>,
        includeSubdomains: Boolean = true
    ): Boolean {
        val enabledScopes = scopes.filter { it.isEnabled }
        if (enabledScopes.isEmpty()) return true

        // 1. Exclude from scope check (highest priority)
        val isExcluded = enabledScopes.any { scope ->
            !scope.isInScope && matchSingleScope(urlOrHost, scope.pattern, includeSubdomains)
        }
        if (isExcluded) return false

        // 2. Include in scope check
        val includeRules = enabledScopes.filter { it.isInScope }
        if (includeRules.isEmpty()) return true

        return includeRules.any { scope ->
            matchSingleScope(urlOrHost, scope.pattern, includeSubdomains)
        }
    }

    /**
     * Checks if there are any active Include rules configured.
     */
    fun hasActiveIncludeRules(scopes: List<TargetScopeEntity>): Boolean {
        return scopes.any { it.isEnabled && it.isInScope }
    }
}
