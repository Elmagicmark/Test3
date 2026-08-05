package com.example.ui.screens.repeater

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HttpTransactionEntity
import com.example.ui.components.MethodBadge
import com.example.ui.components.StatusCodeBadge
import com.example.ui.theme.*

@Composable
fun HistoryPanel(
    transactions: List<HttpTransactionEntity>,
    onSelectTransaction: (HttpTransactionEntity) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) {
            transactions
        } else {
            transactions.filter {
                it.url.contains(searchQuery, ignoreCase = true) ||
                        it.method.contains(searchQuery, ignoreCase = true) ||
                        it.statusCode.toString().contains(searchQuery)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkBg)
            .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.History, contentDescription = "History", tint = CyberCyan, modifier = Modifier.size(18.dp))
                Text("REPEATER HISTORY", color = CyberCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("(${filtered.size})", color = OnCyberSurfaceMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }

            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = WarningCrimson, modifier = Modifier.size(16.dp))
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search history...", fontSize = 10.sp, color = OnCyberSurfaceMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(14.dp)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = OnCyberDark)
        )

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No history transactions match",
                    color = OnCyberSurfaceMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered) { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSurface)
                            .border(0.5.dp, CyberBorder, RoundedCornerShape(6.dp))
                            .clickable { onSelectTransaction(tx) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MethodBadge(method = tx.method)
                        StatusCodeBadge(statusCode = tx.statusCode)

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.url,
                                color = OnCyberDark,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${tx.responseTimeMs} ms • ${tx.bytesTransferred} B",
                                color = OnCyberSurfaceMuted,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Load",
                            tint = NeonGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
