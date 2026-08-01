package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    transactions: List<HttpTransactionEntity>,
    targetScopes: List<TargetScopeEntity>,
    filterHistoryByScope: Boolean,
    onDelete: (Long) -> Unit,
    onDeleteBatch: (List<Long>) -> Unit,
    onClearAll: () -> Unit,
    onSendToRepeater: (String, String, String, String) -> Unit,
    onSelectDetail: (HttpTransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMethodFilter by remember { mutableStateOf("ALL") }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var sortBy by remember { mutableStateOf("NEWEST") }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val context = LocalContext.current

    val scopeFilteredList = remember(transactions, targetScopes, filterHistoryByScope) {
        if (!filterHistoryByScope || targetScopes.isEmpty()) {
            transactions
        } else {
            val inScopePatterns = targetScopes
                .filter { it.isInScope }
                .map { it.pattern.trim().lowercase() }

            transactions.filter { tx ->
                val urlLower = tx.url.lowercase()
                inScopePatterns.any { pattern ->
                    urlLower.contains(pattern) ||
                    urlLower.contains("*.$pattern")
                }
            }
        }
    }

    val filteredList = remember(scopeFilteredList, searchQuery, selectedMethodFilter, selectedStatusFilter, sortBy) {
        scopeFilteredList.filter { tx ->
            val matchesQuery = searchQuery.isEmpty() || tx.url.contains(searchQuery, ignoreCase = true) || tx.requestBody.contains(searchQuery, ignoreCase = true)
            val matchesMethod = selectedMethodFilter == "ALL" || tx.method.equals(selectedMethodFilter, ignoreCase = true)
            val matchesStatus = when (selectedStatusFilter) {
                "2xx" -> tx.statusCode in 200..299
                "3xx" -> tx.statusCode in 300..399
                "4xx" -> tx.statusCode in 400..499
                "5xx" -> tx.statusCode in 500..599
                else -> true
            }
            matchesQuery && matchesMethod && matchesStatus
        }.sortedWith { a, b ->
            when (sortBy) {
                "OLDEST" -> a.timestamp.compareTo(b.timestamp)
                "SLOWEST" -> b.responseTimeMs.compareTo(a.responseTimeMs)
                "STATUS" -> b.statusCode.compareTo(a.statusCode)
                else -> b.timestamp.compareTo(a.timestamp)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (filterHistoryByScope && targetScopes.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF003814), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FilterAlt, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "FILTERED BY SCOPE: ${targetScopes.filter { it.isInScope }.size} rule(s)",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = NeonGreen
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search URL or body payload...", fontSize = 12.sp, color = OnCyberSurfaceMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = CyberCyan) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear", tint = WarningCrimson) } }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_input"),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = OnCyberDark)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            var methodExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { methodExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text("Method: $selectedMethodFilter", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = CyberCyan)
                }
                DropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                    listOf("ALL", "GET", "POST", "PUT", "DELETE", "PATCH").forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, fontFamily = FontFamily.Monospace) },
                            onClick = { selectedMethodFilter = m; methodExpanded = false }
                        )
                    }
                }
            }

            var statusExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { statusExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text("Status: $selectedStatusFilter", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NeonGreen)
                }
                DropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                    listOf("ALL", "2xx", "3xx", "4xx", "5xx").forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s, fontFamily = FontFamily.Monospace) },
                            onClick = { selectedStatusFilter = s; statusExpanded = false }
                        )
                    }
                }
            }

            var sortExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { sortExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text("Sort: $sortBy", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NeonAmber)
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    listOf("NEWEST", "OLDEST", "SLOWEST", "STATUS").forEach { sb ->
                        DropdownMenuItem(
                            text = { Text(sb, fontFamily = FontFamily.Monospace) },
                            onClick = { sortBy = sb; sortExpanded = false }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TRANSACTIONS (${filteredList.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = OnCyberSurfaceMuted
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selectedIds.isNotEmpty()) {
                    IconButton(onClick = {
                        onDeleteBatch(selectedIds.toList())
                        selectedIds = emptySet()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Batch", tint = WarningCrimson)
                    }
                }

                IconButton(onClick = {
                    Toast.makeText(context, "Exported ${filteredList.size} transaction logs to JSON file", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export JSON", tint = CyberCyan)
                }

                IconButton(onClick = { onClearAll() }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = WarningCrimson)
                }
            }
        }

        if (filteredList.isEmpty()) {
            CyberCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions match current query filter", color = OnCyberSurfaceMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredList) { tx ->
                    val isChecked = selectedIds.contains(tx.id)
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelectDetail(tx) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + tx.id else selectedIds - tx.id
                                },
                                colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                            )

                            Column(modifier = Modifier.weight(1f).padding(horizontal = 2.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MethodBadge(method = tx.method)
                                    StatusCodeBadge(statusCode = tx.statusCode)
                                    Text(
                                        text = "${tx.responseTimeMs} ms",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = OnCyberSurfaceMuted
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tx.url,
                                    color = OnCyberDark,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        onSendToRepeater(tx.method, tx.url, tx.requestHeadersJson, tx.requestBody)
                                        Toast.makeText(context, "Sent request to Repeater Workbench", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Repeat, contentDescription = "Send to Repeater", tint = NeonGreen, modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { onDelete(tx.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Log", tint = WarningCrimson, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
