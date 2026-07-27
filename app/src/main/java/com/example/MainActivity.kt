package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.HttpTransactionEntity
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            InterceptXTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: NavRoutes.DASHBOARD

                val proxySettings by mainViewModel.proxySettings.collectAsStateWithLifecycle()
                val proxyStats by mainViewModel.proxyStats.collectAsStateWithLifecycle()
                val transactions by mainViewModel.transactions.collectAsStateWithLifecycle()
                val repeaterTabs by mainViewModel.repeaterTabs.collectAsStateWithLifecycle()
                val interceptedRequests by mainViewModel.interceptedRequests.collectAsStateWithLifecycle()
                val targetScopes by mainViewModel.targetScopes.collectAsStateWithLifecycle()
                val securityProjects by mainViewModel.securityProjects.collectAsStateWithLifecycle()

                var selectedDetailTransaction by remember { mutableStateOf<HttpTransactionEntity?>(null) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = CyberDarkBg,
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = "SYSTEM PROTOCOL",
                                        color = CyberCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "INTERCEPT",
                                            color = OnCyberDark,
                                            fontWeight = FontWeight.Black,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            fontSize = 20.sp,
                                            letterSpacing = (-1).sp
                                        )
                                        Text(
                                            text = "X",
                                            color = CyberCyan,
                                            fontWeight = FontWeight.Black,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            fontSize = 20.sp
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = CyberDarkBg,
                                titleContentColor = OnCyberDark
                            ),
                            actions = {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(CyberCyan.copy(alpha = 0.08f))
                                        .border(1.dp, CyberCyan.copy(alpha = 0.3f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (proxySettings.isProxyRunning) NeonGreen else WarningCrimson)
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xFF0A0B10),
                            tonalElevation = 0.dp,
                            modifier = Modifier.border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
                        ) {
                            val items = listOf(
                                NavigationItem("Dash", NavRoutes.DASHBOARD, Icons.Default.Dashboard),
                                NavigationItem("Intercept", NavRoutes.INTERCEPT, Icons.Default.PauseCircle),
                                NavigationItem("History", NavRoutes.HISTORY, Icons.Default.History),
                                NavigationItem("Repeater", NavRoutes.REPEATER, Icons.Default.Repeat),
                                NavigationItem("Composer", NavRoutes.COMPOSER, Icons.Default.Terminal),
                                NavigationItem("Certs", NavRoutes.CERTS, Icons.Default.VpnKey),
                                NavigationItem("Config", NavRoutes.SETTINGS, Icons.Default.Settings)
                            )

                            items.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationBarItem(
                                    icon = {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (selected) CyberCyan.copy(alpha = 0.15f) else Color.Transparent)
                                                .padding(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.title,
                                                tint = if (selected) CyberCyan else OnCyberSurfaceMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = item.title.uppercase(),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) CyberCyan else OnCyberSurfaceMuted
                                        )
                                    },
                                    selected = selected,
                                    onClick = {
                                        if (currentRoute != item.route) {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.testTag("nav_${item.route}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = NavRoutes.DASHBOARD,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(NavRoutes.DASHBOARD) {
                            DashboardScreen(
                                proxySettings = proxySettings,
                                proxyStats = proxyStats,
                                recentTransactions = transactions,
                                onToggleProxy = { mainViewModel.toggleProxyServer(it) },
                                onNavigate = { target -> navController.navigate(target) },
                                onSelectTransaction = { selectedDetailTransaction = it }
                            )
                        }

                        composable(NavRoutes.INTERCEPT) {
                            InterceptScreen(
                                isInterceptEnabled = proxySettings.isInterceptEnabled,
                                interceptedList = interceptedRequests,
                                onToggleIntercept = { mainViewModel.toggleIntercept(it) },
                                onForward = { id, method, url, headers, body ->
                                    mainViewModel.forwardInterceptedRequest(id, method, url, headers, body)
                                },
                                onDrop = { mainViewModel.dropInterceptedRequest(it) },
                                onForwardAll = { mainViewModel.forwardAllIntercepted() },
                                onSendToRepeater = { method, url, headers, body ->
                                    mainViewModel.sendToRepeater(method, url, headers, body)
                                    navController.navigate(NavRoutes.REPEATER)
                                }
                            )
                        }

                        composable(NavRoutes.HISTORY) {
                            HistoryScreen(
                                transactions = transactions,
                                onDelete = { mainViewModel.deleteTransaction(it) },
                                onDeleteBatch = { mainViewModel.deleteTransactions(it) },
                                onClearAll = { mainViewModel.clearHistory() },
                                onSendToRepeater = { method, url, headers, body ->
                                    mainViewModel.sendToRepeater(method, url, headers, body)
                                    navController.navigate(NavRoutes.REPEATER)
                                },
                                onSelectDetail = { selectedDetailTransaction = it }
                            )
                        }

                        composable(NavRoutes.REPEATER) {
                            RepeaterScreen(
                                tabs = repeaterTabs,
                                onUpdateTab = { mainViewModel.updateRepeaterTab(it) },
                                onDeleteTab = { mainViewModel.deleteRepeaterTab(it) },
                                onCreateTab = {
                                    mainViewModel.sendToRepeater(
                                        method = "GET",
                                        url = "https://api.target-app.internal/v1/health",
                                        headersJson = "{\"User-Agent\":\"InterceptX/1.0\"}",
                                        body = "",
                                        name = "Tab ${repeaterTabs.size + 1}"
                                    )
                                },
                                onExecuteRequest = { tab, onResult ->
                                    mainViewModel.executeRepeaterRequest(tab, onResult)
                                }
                            )
                        }

                        composable(NavRoutes.COMPOSER) {
                            ComposerScreen(
                                onExecuteRaw = { method, url, headers, body, onResult ->
                                    mainViewModel.executeRawComposerRequest(method, url, headers, body, onResult)
                                }
                            )
                        }

                        composable(NavRoutes.CERTS) {
                            CertificatesScreen(
                                certificateInfo = com.example.util.CertificateManager.getCertificateDetails(androidx.compose.ui.platform.LocalContext.current)
                            )
                        }

                        composable(NavRoutes.PROJECTS_SCOPE) {
                            ProjectsScopeScreen(
                                projects = securityProjects,
                                scopes = targetScopes,
                                onAddProject = { name, desc -> mainViewModel.addSecurityProject(name, desc) },
                                onDeleteProject = { mainViewModel.deleteProject(it) },
                                onAddScope = { pattern, isInScope -> mainViewModel.addTargetScope(pattern, isInScope) },
                                onDeleteScope = { mainViewModel.deleteTargetScope(it) }
                            )
                        }

                        composable(NavRoutes.SETTINGS) {
                            SettingsScreen(
                                proxySettings = proxySettings,
                                onSaveSettings = { mainViewModel.updateProxySettings(it) }
                            )
                        }
                    }

                    selectedDetailTransaction?.let { tx ->
                        TransactionDetailDialog(
                            transaction = tx,
                            onDismiss = { selectedDetailTransaction = null },
                            onSendToRepeater = { method, url, headers, body ->
                                mainViewModel.sendToRepeater(method, url, headers, body)
                                navController.navigate(NavRoutes.REPEATER)
                            }
                        )
                    }
                }
            }
        }
    }
}

object NavRoutes {
    const val DASHBOARD = "dashboard"
    const val INTERCEPT = "intercept"
    const val HISTORY = "history"
    const val REPEATER = "repeater"
    const val COMPOSER = "composer"
    const val CERTS = "certs"
    const val PROJECTS_SCOPE = "projects_scope"
    const val SETTINGS = "settings"
}

data class NavigationItem(
    val title: String,
    val route: String,
    val icon: ImageVector
)
