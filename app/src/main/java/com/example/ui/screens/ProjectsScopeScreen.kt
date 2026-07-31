package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.local.SecurityProjectEntity
import com.example.data.local.TargetScopeEntity
import com.example.ui.components.CyberCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScopeScreen(
    projects: List<SecurityProjectEntity>,
    scopes: List<TargetScopeEntity>,
    proxySettings: com.example.data.model.ProxySettings = com.example.data.model.ProxySettings(),
    onAddProject: (String, String) -> Unit,
    onDeleteProject: (Long) -> Unit,
    onAddScope: (String, Boolean) -> Unit,
    onDeleteScope: (Long) -> Unit,
    onToggleEnforceScope: (Boolean) -> Unit = {},
    onToggleIncludeSubdomains: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var newProjectName by remember { mutableStateOf("") }
    var newProjectDesc by remember { mutableStateOf("") }
    var newScopePattern by remember { mutableStateOf("") }
    var newScopeIsInScope by remember { mutableStateOf(true) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Target Scope Controls & Settings Card
        CyberCard(borderColor = NeonAmber) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ENFORCE TARGET SCOPE ONLY",
                            color = NeonAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (proxySettings.enforceScopeOnly) "Active: Proxy only logs & intercepts in-scope URLs" else "Disabled: Proxy logs all HTTP traffic",
                            color = OnCyberSurfaceMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Switch(
                        checked = proxySettings.enforceScopeOnly,
                        onCheckedChange = onToggleEnforceScope,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = NeonAmber
                        ),
                        modifier = Modifier.testTag("enforce_scope_switch")
                    )
                }

                HorizontalDivider(color = CyberBorder, thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "INCLUDE SUBDOMAINS (تضمين النطاقات الفرعية)",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (proxySettings.includeSubdomains) "Enabled: sub.example.com matches example.com" else "Strict: Exact host match required",
                            color = OnCyberSurfaceMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Switch(
                        checked = proxySettings.includeSubdomains,
                        onCheckedChange = onToggleIncludeSubdomains,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = CyberCyan
                        ),
                        modifier = Modifier.testTag("include_subdomains_switch")
                    )
                }
            }
        }

        // Target Scope Configuration Section
        CyberCard(borderColor = CyberCyan) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TARGET DOMAIN SCOPE RULES (بدون Regex)",
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "ضع اسم الدومين فقط (مثل: example.com) وسيقوم ملتقط الترافك بتصفية هذا الدومين وكل النطاقات الفرعية تلقائياً ويمرر أي شىء آخر.",
                    color = OnCyberSurfaceMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newScopePattern,
                        onValueChange = { newScopePattern = it },
                        placeholder = { Text("Domain e.g. example.com or target.com", fontSize = 10.5.sp, color = OnCyberSurfaceMuted) },
                        modifier = Modifier.weight(1f).testTag("scope_pattern_input"),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = OnCyberDark),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            val clean = newScopePattern.trim().lowercase()
                                .removePrefix("http://").removePrefix("https://")
                                .removePrefix("*.").removePrefix(".")
                                .replace(".*", "").replace("\\.", ".")
                                .substringBefore("/").substringBefore(":")
                            if (clean.isNotBlank()) {
                                onAddScope(clean, newScopeIsInScope)
                                newScopePattern = ""
                                Toast.makeText(context, "Scope Added: $clean", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("add_scope_rule_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(11.dp))
                            Text("ADD RULE", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    Text("Type:", color = OnCyberSurfaceMuted, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { newScopeIsInScope = true }
                    ) {
                        RadioButton(
                            selected = newScopeIsInScope,
                            onClick = { newScopeIsInScope = true },
                            colors = RadioButtonDefaults.colors(selectedColor = NeonGreen),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("In-Scope", color = NeonGreen, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { newScopeIsInScope = false }
                    ) {
                        RadioButton(
                            selected = !newScopeIsInScope,
                            onClick = { newScopeIsInScope = false },
                            colors = RadioButtonDefaults.colors(selectedColor = WarningCrimson),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Out-of-Scope", color = WarningCrimson, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                // Scope Rules List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(scopes) { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSurfaceVariant)
                                .border(0.5.dp, if (rule.isInScope) NeonGreen else WarningCrimson, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(if (rule.isInScope) Color(0xFF003814) else Color(0xFF4A0E17))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (rule.isInScope) "IN-SCOPE" else "EXCLUDED",
                                        color = if (rule.isInScope) NeonGreen else WarningCrimson,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(rule.pattern, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = OnCyberDark)
                            }

                            IconButton(
                                onClick = { onDeleteScope(rule.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = WarningCrimson, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // Security Projects / Workspaces Section
        CyberCard(borderColor = NeonGreen) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SECURITY WORKSPACE PROJECTS",
                    color = NeonGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        placeholder = { Text("Project Name", fontSize = 10.5.sp) },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = OnCyberDark),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newProjectDesc,
                        onValueChange = { newProjectDesc = it },
                        placeholder = { Text("Description", fontSize = 10.5.sp) },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = OnCyberDark),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (newProjectName.isNotBlank()) {
                                onAddProject(newProjectName, newProjectDesc)
                                newProjectName = ""
                                newProjectDesc = ""
                                Toast.makeText(context, "Workspace Created", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("create_project_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Create", modifier = Modifier.size(11.dp))
                            Text("CREATE", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(projects) { proj ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSurface)
                                .border(1.dp, CyberBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(proj.name, color = OnCyberDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text(proj.description, color = OnCyberSurfaceMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            IconButton(
                                onClick = { onDeleteProject(proj.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = WarningCrimson, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
