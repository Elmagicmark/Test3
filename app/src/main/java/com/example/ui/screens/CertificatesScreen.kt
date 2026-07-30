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
                                val result = com.example.util.CertificateManager.installCertificateInSystem(context)
                                Toast.makeText(context, result.second, Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = Color.Black),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("install_ca_cert_button")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = "Install System", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("INSTALL IN SYSTEM", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        OutlinedButton(
                            onClick = {
                                val result = com.example.util.CertificateManager.exportCertificateToDownloads(context)
                                Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAmber),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("export_ca_cert_button")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("EXPORT (.CRT & .PEM)", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        IconButton(
                            onClick = {
                                com.example.util.CertificateManager.shareOrInstallCertificate(context)
                            },
                            modifier = Modifier.testTag("share_ca_cert_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = NeonAmber)
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
                    text = "دليل تثبيت شهادة CA على نظام أندرويد (INSTALL GUIDE)",
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                SetupStepItem(
                    stepNumber = "01",
                    title = "تصدير شهادة Root CA",
                    description = "اضغط 'EXPORT (.CRT & .PEM)' لحفظ ملف الشهادة في مجلد Downloads بهاتفك."
                )

                SetupStepItem(
                    stepNumber = "02",
                    title = "افتح إعدادات الأمان في الهاتف",
                    description = "الذهاب إلى: الإعدادات -> الأمان والخصوصية -> المزيد من إعدادات الأمان -> التشفير وأدوات الاعتماد -> تثبيت شهادة."
                )

                SetupStepItem(
                    stepNumber = "03",
                    title = "اختر شهادة CA (CA Certificate)",
                    description = "اختر 'شهادة CA' (وليس شهادة المستخدم / VPN لمنع طلب المفتاح الخاص)، ثم اختر 'التثبيت على أي حال' وحدد ملف InterceptX_Root_CA.crt من مجلد Downloads."
                )

                SetupStepItem(
                    stepNumber = "04",
                    title = "ضبط البروكسي في Firefox / FoxyProxy / Wi-Fi",
                    description = "في FoxyProxy أو ضبط شبكة Wi-Fi: حدد البروكسي اليدوي Host: 127.0.0.1 والمنفذ Port: 8080 لتجريب الفحص."
                )
            }
        }

        // Chrome & Android 7+ Restriction Warning & Solutions Card (Reqable style)
        CyberCard(borderColor = NeonAmber) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Warning", tint = NeonAmber)
                    Text(
                        text = "سبب ظهور خطأ NET::ERR_CERT_AUTHORITY_INVALID في متصفح Chrome",
                        color = NeonAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "بدايةً من نظام Android 7.0 (API 24)، يتجاهل متصفح Chrome وتطبيقات النظام الشهادات المثبتة من المستخدِم (User CAs) بشكل افتراضي لأسباب أمنية، ويعتمد فقط على شهادات النظام (System CAs).",
                    color = OnCyberDark,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Divider(color = CyberBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "الحلول المتاحة للتطبيقات والمتصفحات (مثل Reqable / Burp Suite):",
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                SetupStepItem(
                    stepNumber = "A",
                    title = "استخدام متصفح Firefox Mobile (الأسهل بدون روت)",
                    description = "متصفح Firefox يسمح بالشهادات المحلية: افتح إعدادات Firefox -> حول Firefox -> اضغط على اللوجو 5 مرات لفتح Secret Settings -> فعّل 'Use third-party CA certificates'."
                )

                SetupStepItem(
                    stepNumber = "B",
                    title = "فحص حركة مرور الكمبيوتر (PC Browser)",
                    description = "قم بتصدير شهادة InterceptX وتثبيتها في Trusted Root Certification Authorities على جهاز الكمبيوتر، ثم اضبط البروكسي في الكمبيوتر على IP الهاتف والمنفذ 8080."
                )

                SetupStepItem(
                    stepNumber = "C",
                    title = "تعديل Network Security Config لتطبيقك (للمطورين)",
                    description = "أضف <certificates src=\"user\" /> داخل ملف network_security_config.xml في التطبيق المراد فحصه لكي يثق بشهادة المستخدِم."
                )

                SetupStepItem(
                    stepNumber = "D",
                    title = "نقل الشهادة لمجلد النظام (للهواتف ذات الروت / Magisk)",
                    description = "على الهواتف ذات الروت أو المحاكيات، قم بنقل ملف الشهادة إلى /system/etc/security/cacerts/ ليعتمدها متصفح Chrome وجميع تطبيقات النظام تلقائيًا."
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
