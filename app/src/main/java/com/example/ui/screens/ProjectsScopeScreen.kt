package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onAddProject: (String, String) -> Unit,
    onDeleteProject: (Long) -> Unit,
    onAddScope: (String, Boolean) -> Unit,
    onDeleteScope: (Long) -> Unit,
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Target Scope Configuration Section
        CyberCard(borderColor = CyberCyan) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TARGET SCOPE PATTERN RULES",
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newScopePattern,
                        onValueChange = { newScopePattern = it },
                        placeholder = { Text("Regex/URL e.g. .*\\.example\\.com/.*", fontSize = 11.sp, color = OnCyberSurfaceMuted) },
                        modifier = Modifier.weight(1f).testTag("scope_pattern_input"),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = OnCyberDark)
                    )

                    Button(
                        onClick = {
                            if (newScopePattern.isNotBlank()) {
                                onAddScope(newScopePattern, newScopeIsInScope)
                                newScopePattern = ""
                                Toast.makeText(context, "Target Scope Rule Added", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.testTag("add_scope_rule_button")
                    ) {
                        Text("ADD RULE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Type:", color = OnCyberSurfaceMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    RadioButton(
                        selected = newScopeIsInScope,
                        onClick = { newScopeIsInScope = true },
                        colors = RadioButtonDefaults.colors(selectedColor = NeonGreen)
                    )
                    Text("In-Scope", color = NeonGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

                    RadioButton(
                        selected = !newScopeIsInScope,
                        onClick = { newScopeIsInScope = false },
                        colors = RadioButtonDefaults.colors(selectedColor = WarningCrimson)
                    )
                    Text("Out-of-Scope", color = WarningCrimson, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }

                // Scope Rules List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
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
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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

                            IconButton(onClick = { onDeleteScope(rule.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = WarningCrimson, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Security Projects / Workspaces Section
        CyberCard(borderColor = NeonGreen, modifier = Modifier.weight(1f)) {
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
                        placeholder = { Text("Project Name", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = OnCyberDark)
                    )
                    OutlinedTextField(
                        value = newProjectDesc,
                        onValueChange = { newProjectDesc = it },
                        placeholder = { Text("Description", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = OnCyberDark)
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
                        modifier = Modifier.testTag("create_project_button")
                    ) {
                        Text("CREATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(proj.name, color = OnCyberDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text(proj.description, color = OnCyberSurfaceMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            IconButton(onClick = { onDeleteProject(proj.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = WarningCrimson)
                            }
                        }
                    }
                }
            }
        }
    }
}
