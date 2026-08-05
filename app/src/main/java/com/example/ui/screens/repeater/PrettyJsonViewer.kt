package com.example.ui.screens.repeater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

sealed class JsonElementNode {
    data class ObjectNode(
        val key: String?,
        val children: List<JsonElementNode>,
        var isExpanded: MutableState<Boolean>
    ) : JsonElementNode()

    data class ArrayNode(
        val key: String?,
        val children: List<JsonElementNode>,
        var isExpanded: MutableState<Boolean>
    ) : JsonElementNode()

    data class ValueNode(
        val key: String?,
        val value: Any?
    ) : JsonElementNode()
}

@Composable
fun PrettyJsonViewer(
    jsonString: String,
    modifier: Modifier = Modifier
) {
    val parsedNode = remember(jsonString) {
        try {
            val trimmed = jsonString.trim()
            if (trimmed.startsWith("{")) {
                parseJsonObject(null, JSONObject(trimmed))
            } else if (trimmed.startsWith("[")) {
                parseJsonArray(null, JSONArray(trimmed))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    if (parsedNode == null) {
        // Fallback for non-JSON or malformed JSON
        Text(
            text = jsonString,
            color = OnCyberDark,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = modifier.padding(8.dp)
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(CyberDarkBg)
                .padding(8.dp)
        ) {
            JsonNodeItem(node = parsedNode, level = 0)
        }
    }
}

@Composable
private fun JsonNodeItem(node: JsonElementNode, level: Int) {
    val indentPadding = (level * 12).dp

    when (node) {
        is JsonElementNode.ObjectNode -> {
            var expanded by node.isExpanded
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = indentPadding, top = 2.dp, bottom = 2.dp)
                        .clickable { expanded = !expanded }
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = "Toggle",
                        tint = CyberCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = buildAnnotatedString {
                            if (node.key != null) {
                                withStyle(SpanStyle(color = CyberCyan, fontWeight = FontWeight.Bold)) {
                                    append("\"${node.key}\"")
                                }
                                withStyle(SpanStyle(color = OnCyberDark)) {
                                    append(": ")
                                }
                            }
                            withStyle(SpanStyle(color = NeonAmber, fontWeight = FontWeight.Bold)) {
                                append("{ ")
                            }
                            withStyle(SpanStyle(color = OnCyberSurfaceMuted, fontSize = 9.sp)) {
                                append("${node.children.size} items")
                            }
                            withStyle(SpanStyle(color = NeonAmber, fontWeight = FontWeight.Bold)) {
                                append(" }")
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                if (expanded) {
                    node.children.forEach { child ->
                        JsonNodeItem(node = child, level = level + 1)
                    }
                }
            }
        }

        is JsonElementNode.ArrayNode -> {
            var expanded by node.isExpanded
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = indentPadding, top = 2.dp, bottom = 2.dp)
                        .clickable { expanded = !expanded }
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = "Toggle",
                        tint = PurpleNeon,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = buildAnnotatedString {
                            if (node.key != null) {
                                withStyle(SpanStyle(color = CyberCyan, fontWeight = FontWeight.Bold)) {
                                    append("\"${node.key}\"")
                                }
                                withStyle(SpanStyle(color = OnCyberDark)) {
                                    append(": ")
                                }
                            }
                            withStyle(SpanStyle(color = PurpleNeon, fontWeight = FontWeight.Bold)) {
                                append("[ ")
                            }
                            withStyle(SpanStyle(color = OnCyberSurfaceMuted, fontSize = 9.sp)) {
                                append("${node.children.size} items")
                            }
                            withStyle(SpanStyle(color = PurpleNeon, fontWeight = FontWeight.Bold)) {
                                append(" ]")
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                if (expanded) {
                    node.children.forEach { child ->
                        JsonNodeItem(node = child, level = level + 1)
                    }
                }
            }
        }

        is JsonElementNode.ValueNode -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indentPadding + 16.dp, top = 1.dp, bottom = 1.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        if (node.key != null) {
                            withStyle(SpanStyle(color = CyberCyan)) {
                                append("\"${node.key}\"")
                            }
                            withStyle(SpanStyle(color = OnCyberDark)) {
                                append(": ")
                            }
                        }
                        appendJsonValueString(node.value)
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun AnnotatedString.Builder.appendJsonValueString(value: Any?) {
    when (value) {
        null -> withStyle(SpanStyle(color = WarningCrimson, fontWeight = FontWeight.Bold)) { append("null") }
        is String -> withStyle(SpanStyle(color = NeonGreen)) { append("\"$value\"") }
        is Number -> withStyle(SpanStyle(color = NeonAmber)) { append(value.toString()) }
        is Boolean -> withStyle(SpanStyle(color = PurpleNeon, fontWeight = FontWeight.Bold)) { append(value.toString()) }
        else -> withStyle(SpanStyle(color = OnCyberDark)) { append(value.toString()) }
    }
}

private fun parseJsonObject(key: String?, obj: JSONObject): JsonElementNode.ObjectNode {
    val children = mutableListOf<JsonElementNode>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        val v = obj.get(k)
        when (v) {
            is JSONObject -> children.add(parseJsonObject(k, v))
            is JSONArray -> children.add(parseJsonArray(k, v))
            else -> children.add(JsonElementNode.ValueNode(k, if (v == JSONObject.NULL) null else v))
        }
    }
    return JsonElementNode.ObjectNode(key, children, mutableStateOf(true))
}

private fun parseJsonArray(key: String?, array: JSONArray): JsonElementNode.ArrayNode {
    val children = mutableListOf<JsonElementNode>()
    for (i in 0 until array.length()) {
        val v = array.get(i)
        when (v) {
            is JSONObject -> children.add(parseJsonObject("[$i]", v))
            is JSONArray -> children.add(parseJsonArray("[$i]", v))
            else -> children.add(JsonElementNode.ValueNode("[$i]", if (v == JSONObject.NULL) null else v))
        }
    }
    return JsonElementNode.ArrayNode(key, children, mutableStateOf(true))
}
