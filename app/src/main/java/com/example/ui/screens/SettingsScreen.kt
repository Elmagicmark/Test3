package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProxySettings
import com.example.ui.components.CyberCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    proxySettings: ProxySettings,
    onSaveSettings: (ProxySettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var host by remember(proxySettings) { mutableStateOf(proxySettings.host) }
    var portText by remember(proxySettings) { mutableStateOf(proxySettings.port.toString()) }
    var upstreamEnabled by remember(proxySettings) { mutableStateOf(proxySettings.upstreamProxyEnabled) }
    var upstreamHost by remember(proxySettings) { mutableStateOf(proxySettings.upstreamProxyHost) }
    var upstreamPortText by remember(proxySettings) { mutableStateOf(proxySettings.upstreamProxyPort.toString()) }
    var sslBypassEnabled by remember(proxySettings) { mutableStateOf(proxySettings.sslBypassEnabled) }
    var http2Enabled by remember(proxySettings) { mutableStateOf(proxySettings.http2Enabled) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CyberCard(borderColor = CyberCyan) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = CyberCyan)
                    Text(
                        text = "LOCAL PROXY LISTENER CONFIGURATION",
                        color = OnCyberDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        modifier = Modifier.weight(2f),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = OnCyberDark),
                        label = { Text("Proxy Bind Host", fontSize = 10.sp) }
                    )

                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = OnCyberDark),
                        label = { Text("Port", fontSize = 10.sp) }
                    )
                }
            }
        }

        CyberCard(borderColor = NeonAmber) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UPSTREAM PROXY CHAINING",
                        color = NeonAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Switch(
                        checked = upstreamEnabled,
                        onCheckedChange = { upstreamEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonAmber)
                    )
                }

                if (upstreamEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = upstreamHost,
                            onValueChange = { upstreamHost = it },
                            modifier = Modifier.weight(2f),
                            placeholder = { Text("e.g. 192.168.1.100", fontSize = 11.sp) },
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = OnCyberDark),
                            label = { Text("Upstream Proxy Host", fontSize = 10.sp) }
                        )

                        OutlinedTextField(
                            value = upstreamPortText,
                            onValueChange = { upstreamPortText = it },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = OnCyberDark),
                            label = { Text("Upstream Port", fontSize = 10.sp) }
                        )
                    }
                }
            }
        }

        CyberCard(borderColor = CyberCyan) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HTTP/2 PROTOCOL SUPPORT (دعم HTTP/2)",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (http2Enabled) "Enabled: ALPN multiplexing HTTP/2 with HTTP/1.1 fallback" else "Disabled: Force HTTP/1.1 legacy transport",
                            color = OnCyberSurfaceMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Switch(
                        checked = http2Enabled,
                        onCheckedChange = { http2Enabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan),
                        modifier = Modifier.testTag("http2_enabled_switch")
                    )
                }
            }
        }

        CyberCard(borderColor = WarningCrimson) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SSL / TLS PINNING & BYPASS RULES",
                            color = WarningCrimson,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Auto-bypass certificate errors on unrecognized SSL hosts",
                            color = OnCyberSurfaceMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Switch(
                        checked = sslBypassEnabled,
                        onCheckedChange = { sslBypassEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = WarningCrimson)
                    )
                }
            }
        }

        CyberCard(borderColor = PurpleNeon) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "FoxyProxy", tint = PurpleNeon)
                    Text(
                        text = "FOXYPROXY & EXTERNAL BROWSER GUIDE",
                        color = OnCyberDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "To capture traffic from Firefox (with FoxyProxy), Chrome, or external Wi-Fi clients:",
                    color = OnCyberSurfaceMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberDarkBg)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("1. FoxyProxy Protocol: HTTP / HTTPS Proxy", color = NeonGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("2. Host: 127.0.0.1 (or device Wi-Fi IP for remote devices)", color = CyberCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("3. Port: ${proxySettings.port}", color = NeonAmber, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("4. Import InterceptX_Root_CA.crt into Firefox/Android Trust Store", color = OnCyberDark, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Button(
            onClick = {
                val p = portText.toIntOrNull() ?: 8080
                val upP = upstreamPortText.toIntOrNull() ?: 8080
                val updated = proxySettings.copy(
                    host = host,
                    port = p,
                    upstreamProxyEnabled = upstreamEnabled,
                    upstreamProxyHost = upstreamHost,
                    upstreamProxyPort = upP,
                    sslBypassEnabled = sslBypassEnabled,
                    http2Enabled = http2Enabled
                )
                onSaveSettings(updated)
                Toast.makeText(context, "Proxy Settings Applied Successfully", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_proxy_settings_button")
        ) {
            Icon(Icons.Default.Save, contentDescription = "Save")
            Spacer(modifier = Modifier.width(6.dp))
            Text("SAVE & APPLY PROXY SETTINGS", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}
