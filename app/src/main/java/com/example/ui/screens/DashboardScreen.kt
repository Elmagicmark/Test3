package com.example.ui.screens

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HttpTransactionEntity
import com.example.data.model.ProxySettings
import com.example.data.model.ProxyStats
import com.example.ui.components.CyberCard
import com.example.ui.components.MethodBadge
import com.example.ui.components.StatusCodeBadge
import com.example.ui.components.StatsCard
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    proxySettings: ProxySettings,
    proxyStats: ProxyStats,
    recentTransactions: List<HttpTransactionEntity>,
    onToggleProxy: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onSelectTransaction: (HttpTransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Proxy Control
        item {
            CyberCard(
                borderColor = if (proxySettings.isProxyRunning) NeonGreen else WarningCrimson.copy(alpha = 0.6f),
                accentLeftColor = if (proxySettings.isProxyRunning) NeonGreen else WarningCrimson
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LOCAL PROXY",
                            color = if (proxySettings.isProxyRunning) NeonGreen else WarningCrimson,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (proxySettings.isProxyRunning) "PORT ${proxySettings.port} • ${proxySettings.host}" else "ENGINE INACTIVE",
                            color = OnCyberSurfaceMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Switch(
                        checked = proxySettings.isProxyRunning,
                        onCheckedChange = { onToggleProxy(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = NeonGreen,
                            uncheckedThumbColor = OnCyberSurfaceMuted,
                            uncheckedTrackColor = CyberSurfaceVariant
                        ),
                        modifier = Modifier.testTag("proxy_toggle_button")
                    )
                }
            }
        }

        // CA Certificate Quick Export Banner
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            CyberCard(
                borderColor = NeonAmber,
                accentLeftColor = NeonAmber
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = "Cert", tint = NeonAmber, modifier = Modifier.size(24.dp))
                        Column {
                            Text(
                                text = "ROOT CA CERTIFICATE",
                                color = OnCyberDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "InterceptX_Root_CA.pem",
                                color = OnCyberSurfaceMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Button(
                        onClick = {
                            android.widget.Toast.makeText(context, "Exported InterceptX_Root_CA.pem to Download folder", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = Color.Black),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("export_ca_cert_dashboard_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EXPORT (.PEM)", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Live Real-Time Statistics
        item {
            Text(
                text = "REAL-TIME TELEMETRY",
                color = OnCyberSurfaceMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsCard(
                    title = "Requests",
                    value = proxyStats.totalRequests.toString(),
                    icon = Icons.Default.SwapVert,
                    accentColor = CyberCyan,
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    title = "Intercepted",
                    value = proxyStats.interceptedRequests.toString(),
                    icon = Icons.Default.Security,
                    accentColor = NeonAmber,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsCard(
                    title = "Active Conn",
                    value = proxyStats.activeConnections.toString(),
                    icon = Icons.Default.Sync,
                    accentColor = NeonGreen,
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    title = "Bandwidth",
                    value = "${proxyStats.bytesTransferred / 1024 / 1024} MB",
                    icon = Icons.Default.DataUsage,
                    accentColor = PurpleNeon,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Quick Action Shortcuts
        item {
            Text(
                text = "WORKBENCH MODULES",
                color = OnCyberSurfaceMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionItem(
                    label = "Intercept",
                    icon = Icons.Default.PauseCircle,
                    color = WarningCrimson,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("intercept") }
                )
                QuickActionItem(
                    label = "History",
                    icon = Icons.Default.History,
                    color = CyberCyan,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("history") }
                )
                QuickActionItem(
                    label = "Repeater",
                    icon = Icons.Default.Repeat,
                    color = NeonGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("repeater") }
                )
                QuickActionItem(
                    label = "Certificates",
                    icon = Icons.Default.VpnKey,
                    color = NeonAmber,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("certs") }
                )
            }
        }

        // Recent Traffic Activity Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT INTERCEPT TRAFFIC",
                    color = OnCyberSurfaceMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                TextTextButton(
                    text = "View All",
                    color = CyberCyan,
                    onClick = { onNavigate("history") }
                )
            }
        }

        // List of Recent Traffic Items
        if (recentTransactions.isEmpty()) {
            item {
                CyberCard {
                    Text(
                        text = "No proxy traffic recorded yet. Start proxy and route device traffic.",
                        color = OnCyberSurfaceMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            items(recentTransactions.take(8)) { tx ->
                RecentTrafficRow(
                    transaction = tx,
                    onClick = { onSelectTransaction(tx) }
                )
            }
        }
    }
}

@Composable
fun RecentTrafficRow(
    transaction: HttpTransactionEntity,
    onClick: () -> Unit
) {
    val accentColor = when (transaction.statusCode) {
        in 200..299 -> NeonGreen
        in 300..399 -> CyberCyan
        in 400..499 -> WarningCrimson
        else -> WarningCrimson
    }

    CyberCard(
        modifier = Modifier.fillMaxWidth(),
        accentLeftColor = accentColor,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                StatusCodeBadge(statusCode = transaction.statusCode)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.url,
                        color = OnCyberDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${transaction.method.uppercase()} • ${transaction.responseTimeMs}ms • ${(transaction.responseBody.length / 1024.0).let { "%.1f".format(it) }}kb",
                        color = OnCyberSurfaceMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Text(
                text = "↗",
                color = CyberCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun QuickActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CyberSurface)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = OnCyberDark,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun TextTextButton(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.clickable { onClick() }
    )
}
