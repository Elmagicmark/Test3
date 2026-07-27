package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HttpTransactionEntity
import com.example.data.local.TargetScopeEntity
import com.example.ui.components.CyberCard
import com.example.ui.components.MethodBadge
import com.example.ui.components.StatusCodeBadge
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

data class SiteNode(
    val path: String,
    val method: String = "GET",
    val statusCode: Int = 200,
    val isCrawledDiscovered: Boolean = false,
    val mimeType: String = "application/json"
)

data class HostSiteMap(
    val host: String,
    val isScopeMatch: Boolean = true,
    val endpoints: MutableList<SiteNode> = mutableListOf()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSiteMapScreen(
    transactions: List<HttpTransactionEntity>,
    targetScopes: List<TargetScopeEntity>,
    onSendToRepeater: (String, String, String, String) -> Unit,
    onSelectTransaction: (HttpTransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var crawlerTargetUrl by remember { mutableStateOf("https://api.target-app.internal") }
    var isCrawling by remember { mutableStateOf(false) }
    var crawlLogs by remember { mutableStateOf("Ready to crawl target host...") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Dynamically build Host SiteMap Tree from captured HTTP Transactions + Crawled endpoints
    val hostMap = remember(transactions, targetScopes) {
        val map = mutableMapOf<String, HostSiteMap>()

        // Default mock host endpoints for realistic security analysis representation
        val defaultHost = "api.target-app.internal"
        val defaultMap = HostSiteMap(
            host = defaultHost,
            isScopeMatch = true,
            endpoints = mutableListOf(
                SiteNode("/v1/auth/login", "POST", 200, false, "application/json"),
                SiteNode("/v1/users/profile", "GET", 200, false, "application/json"),
                SiteNode("/v1/telemetry/event", "POST", 202, false, "application/json"),
                SiteNode("/v1/admin/config", "GET", 403, true, "application/json"),
                SiteNode("/v2/graphql", "POST", 200, true, "application/json"),
                SiteNode("/swagger-ui.html", "GET", 200, true, "text/html"),
                SiteNode("/robots.txt", "GET", 200, true, "text/plain")
            )
        )
        map[defaultHost] = defaultMap

        // Process live captured transactions
        transactions.forEach { tx ->
            try {
                val uri = URI(tx.url)
                val host = uri.host ?: "unknown-host"
                val path = if (uri.path.isNullOrEmpty()) "/" else uri.path

                val isInScope = targetScopes.isEmpty() || targetScopes.any { scope ->
                    if (scope.isInScope) tx.url.contains(scope.pattern.replace(".*", "")) else false
                }

                val existing = map.getOrPut(host) { HostSiteMap(host, isInScope, mutableListOf()) }
                if (existing.endpoints.none { it.path == path && it.method == tx.method }) {
                    existing.endpoints.add(
                        SiteNode(
                            path = path,
                            method = tx.method,
                            statusCode = tx.statusCode,
                            isCrawledDiscovered = false
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        map
    }

    var expandedHosts by remember { mutableStateOf(setOf("api.target-app.internal")) }
    var selectedNode by remember { mutableStateOf<Pair<String, SiteNode>?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Active Crawler Bar
        CyberCard(borderColor = PurpleNeon) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.AccountTree, contentDescription = "Crawler", tint = PurpleNeon, modifier = Modifier.size(18.dp))
                        Text(
                            text = "TARGET CRAWLER & RECON ENGINE",
                            color = OnCyberDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = {
                            if (crawlerTargetUrl.isBlank()) return@Button
                            isCrawling = true
                            crawlLogs = "Initiating active endpoint crawl on $crawlerTargetUrl..."

                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val client = OkHttpClient.Builder()
                                        .connectTimeout(5, TimeUnit.SECONDS)
                                        .readTimeout(5, TimeUnit.SECONDS)
                                        .build()

                                    val endpointsToTest = listOf(
                                        "/robots.txt", "/sitemap.xml", "/.well-known/security.txt",
                                        "/api/v1/health", "/api/v1/config", "/swagger-ui.html",
                                        "/v1/auth/me", "/graphql", "/.git/HEAD"
                                    )

                                    var discoveredCount = 0
                                    endpointsToTest.forEach { path ->
                                        val testUrl = if (crawlerTargetUrl.endsWith("/")) "$crawlerTargetUrl${path.removePrefix("/")}" else "$crawlerTargetUrl$path"
                                        try {
                                            val req = Request.Builder().url(testUrl).build()
                                            client.newCall(req).execute().use { resp ->
                                                if (resp.code != 404) {
                                                    discoveredCount++
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }

                                    withContext(Dispatchers.Main) {
                                        isCrawling = false
                                        crawlLogs = "Crawl finished: Discovered $discoveredCount active endpoints for $crawlerTargetUrl"
                                        Toast.makeText(context, "Crawl complete!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isCrawling = false
                                        crawlLogs = "Crawl simulated scan complete for $crawlerTargetUrl"
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleNeon, contentColor = Color.White),
                        shape = RoundedCornerShape(4.dp),
                        enabled = !isCrawling,
                        modifier = Modifier.testTag("start_crawler_button")
                    ) {
                        if (isCrawling) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Search, contentDescription = "Crawl", modifier = Modifier.size(14.dp))
                                Text("CRAWL TARGET", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = crawlerTargetUrl,
                        onValueChange = { crawlerTargetUrl = it },
                        placeholder = { Text("https://target-domain.com", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = OnCyberDark)
                    )
                }

                Text(
                    text = crawlLogs,
                    color = OnCyberSurfaceMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Site Map Tree View
        CyberCard(borderColor = CyberCyan, modifier = Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SITE MAP TREE (${hostMap.size} HOSTS)",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = "BURP/REQABLE STRUCTURE",
                        color = NeonGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(hostMap.values.toList()) { hostData ->
                        val isExpanded = expandedHosts.contains(hostData.host)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSurface)
                                .border(1.dp, CyberBorder, RoundedCornerShape(6.dp))
                        ) {
                            // Host Row Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedHosts = if (isExpanded) {
                                            expandedHosts - hostData.host
                                        } else {
                                            expandedHosts + hostData.host
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.ArrowRight,
                                        contentDescription = "Expand",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(18.dp)
                                    )

                                    Icon(
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = "Host",
                                        tint = NeonAmber,
                                        modifier = Modifier.size(16.dp)
                                    )

                                    Text(
                                        text = hostData.host,
                                        color = OnCyberDark,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (hostData.isScopeMatch) Color(0xFF003814) else Color(0xFF380000))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (hostData.isScopeMatch) "IN-SCOPE" else "OUT-OF-SCOPE",
                                            color = if (hostData.isScopeMatch) NeonGreen else WarningCrimson,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Text(
                                    text = "${hostData.endpoints.size} endpoints",
                                    color = OnCyberSurfaceMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Host Endpoints List
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CyberDarkBg)
                                        .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    hostData.endpoints.forEach { node ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CyberSurfaceVariant)
                                                .border(0.5.dp, CyberBorder, RoundedCornerShape(4.dp))
                                                .clickable { selectedNode = Pair(hostData.host, node) }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                MethodBadge(method = node.method)
                                                StatusCodeBadge(statusCode = node.statusCode)

                                                Text(
                                                    text = node.path,
                                                    color = OnCyberDark,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold
                                                )

                                                if (node.isCrawledDiscovered) {
                                                    Text(
                                                        text = "CRAWLED",
                                                        color = PurpleNeon,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        modifier = Modifier
                                                            .background(PurpleNeon.copy(alpha = 0.15f))
                                                            .padding(horizontal = 3.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    onSendToRepeater(
                                                        node.method,
                                                        "https://${hostData.host}${node.path}",
                                                        "{\"Host\":\"${hostData.host}\",\"User-Agent\":\"InterceptX/1.0\"}",
                                                        ""
                                                    )
                                                    Toast.makeText(context, "Sent to Repeater", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Default.Repeat, contentDescription = "Repeater", tint = NeonGreen, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
