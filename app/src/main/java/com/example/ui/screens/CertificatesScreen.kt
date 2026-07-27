package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.data.model.CertificateInfo
import com.example.ui.components.CyberCard
import com.example.ui.theme.*

@Composable
fun CertificatesScreen(
    certificateInfo: CertificateInfo,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Root CA Header Card
        CyberCard(borderColor = NeonAmber) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = "Cert", tint = NeonAmber)
                        Column {
                            Text(
                                text = "ROOT CA CERTIFICATE",
                                color = OnCyberDark,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Required for HTTPS/TLS MITM Decryption",
                                color = OnCyberSurfaceMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                val result = com.example.util.CertificateManager.exportCertificateToDownloads(context)
                                Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = Color.Black),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("export_ca_cert_button")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("EXPORT (.PEM)", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        OutlinedButton(
                            onClick = {
                                com.example.util.CertificateManager.shareOrInstallCertificate(context)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAmber),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("install_ca_cert_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Install/Share", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("INSTALL / SHARE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Divider(color = CyberBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                // Certificate Fingerprints
                CertMetaRow(label = "Common Name (CN)", value = certificateInfo.commonName)
                CertMetaRow(label = "Organization (O)", value = certificateInfo.organization)
                CertMetaRow(label = "Serial Number", value = certificateInfo.serialNumber)
                CertMetaRow(label = "Validity Period", value = "${certificateInfo.validFrom} to ${certificateInfo.validTo}")

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SHA-256 Fingerprint:",
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberDarkBg)
                        .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                        .padding(6.dp)
                ) {
                    Text(
                        text = certificateInfo.sha256Fingerprint,
                        color = NeonGreen,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Step-by-Step Installation Setup Guide for Android
        CyberCard(borderColor = CyberCyan) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ANDROID CA INSTALLATION GUIDE",
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                SetupStepItem(
                    stepNumber = "01",
                    title = "Export Root CA Certificate",
                    description = "Click 'EXPORT CA (.PEM)' above to save the root certificate file to your device Download folder."
                )

                SetupStepItem(
                    stepNumber = "02",
                    title = "Open Android Security Settings",
                    description = "Navigate to Android Settings -> Security & Privacy -> More Security Settings -> Encryption & Credentials -> Install a Certificate."
                )

                SetupStepItem(
                    stepNumber = "03",
                    title = "Install as CA / Wi-Fi Certificate",
                    description = "Select 'CA Certificate' (or VPN & App user certificate), locate InterceptX_Root_CA.pem and confirm installation."
                )

                SetupStepItem(
                    stepNumber = "04",
                    title = "Configure Network Proxy",
                    description = "Set your Wi-Fi or APN HTTP Proxy to Manual: Host 127.0.0.1, Port 8080. Start InterceptX Proxy Engine!"
                )
            }
        }
    }
}

@Composable
fun CertMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = OnCyberSurfaceMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = OnCyberDark, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SetupStepItem(stepNumber: String, title: String, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(CyberSurfaceVariant)
                .border(1.dp, CyberCyan, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = stepNumber, color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }

        Column {
            Text(text = title, color = OnCyberDark, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(text = description, color = OnCyberSurfaceMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
