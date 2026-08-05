package com.example.ui.screens.repeater

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

sealed class XmlElementNode {
    data class TagNode(
        val tagName: String,
        val attributes: Map<String, String>,
        val children: List<XmlElementNode>,
        val textContent: String?,
        var isExpanded: MutableState<Boolean>
    ) : XmlElementNode()

    data class TextNode(
        val text: String
    ) : XmlElementNode()
}

@Composable
fun PrettyXmlViewer(
    xmlString: String,
    modifier: Modifier = Modifier
) {
    val parsedNode = remember(xmlString) {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val inputSource = org.xml.sax.InputSource(StringReader(xmlString.trim()))
            val doc = builder.parse(inputSource)
            doc.documentElement?.let { parseXmlElement(it) }
        } catch (e: Exception) {
            null
        }
    }

    if (parsedNode == null) {
        // Syntax highlighted XML fallback if strict DOM parsing fails
        XmlHighlightedText(xmlString = xmlString, modifier = modifier)
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(CyberDarkBg)
                .padding(8.dp)
        ) {
            XmlNodeItem(node = parsedNode, level = 0)
        }
    }
}

@Composable
private fun XmlNodeItem(node: XmlElementNode, level: Int) {
    val indentPadding = (level * 12).dp

    when (node) {
        is XmlElementNode.TagNode -> {
            var expanded by node.isExpanded
            val hasChildren = node.children.isNotEmpty()

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = indentPadding, top = 2.dp, bottom = 2.dp)
                        .then(if (hasChildren) Modifier.clickable { expanded = !expanded } else Modifier)
                ) {
                    if (hasChildren) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                            contentDescription = "Toggle XML Node",
                            tint = CyberCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    } else {
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = OnCyberSurfaceMuted, fontWeight = FontWeight.Bold)) {
                                append("<")
                            }
                            withStyle(SpanStyle(color = PurpleNeon, fontWeight = FontWeight.Bold)) {
                                append(node.tagName)
                            }
                            node.attributes.forEach { (attrName, attrVal) ->
                                withStyle(SpanStyle(color = CyberCyan)) {
                                    append(" $attrName")
                                }
                                withStyle(SpanStyle(color = OnCyberDark)) {
                                    append("=")
                                }
                                withStyle(SpanStyle(color = NeonGreen)) {
                                    append("\"$attrVal\"")
                                }
                            }
                            withStyle(SpanStyle(color = OnCyberSurfaceMuted, fontWeight = FontWeight.Bold)) {
                                append(">")
                            }

                            if (!hasChildren && !node.textContent.isNullOrBlank()) {
                                withStyle(SpanStyle(color = OnCyberDark)) {
                                    append(node.textContent)
                                }
                                withStyle(SpanStyle(color = OnCyberSurfaceMuted, fontWeight = FontWeight.Bold)) {
                                    append("</")
                                }
                                withStyle(SpanStyle(color = PurpleNeon, fontWeight = FontWeight.Bold)) {
                                    append(node.tagName)
                                }
                                withStyle(SpanStyle(color = OnCyberSurfaceMuted, fontWeight = FontWeight.Bold)) {
                                    append(">")
                                }
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                if (hasChildren && expanded) {
                    node.children.forEach { child ->
                        XmlNodeItem(node = child, level = level + 1)
                    }
                    // Closing Tag
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indentPadding + 16.dp, top = 1.dp, bottom = 1.dp)
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = OnCyberSurfaceMuted, fontWeight = FontWeight.Bold)) {
                                    append("</")
                                }
                                withStyle(SpanStyle(color = PurpleNeon, fontWeight = FontWeight.Bold)) {
                                    append(node.tagName)
                                }
                                withStyle(SpanStyle(color = OnCyberSurfaceMuted, fontWeight = FontWeight.Bold)) {
                                    append(">")
                                }
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        is XmlElementNode.TextNode -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indentPadding + 16.dp, top = 1.dp, bottom = 1.dp)
            ) {
                Text(
                    text = node.text,
                    color = OnCyberDark,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun XmlHighlightedText(xmlString: String, modifier: Modifier = Modifier) {
    val annotated = remember(xmlString) {
        buildAnnotatedString {
            val lines = xmlString.lines()
            lines.forEachIndexed { idx, line ->
                var i = 0
                while (i < line.length) {
                    when {
                        line.startsWith("<!--", i) -> {
                            val end = line.indexOf("-->", i)
                            val comment = if (end != -1) line.substring(i, end + 3) else line.substring(i)
                            withStyle(SpanStyle(color = OnCyberSurfaceMuted)) { append(comment) }
                            i += comment.length
                        }
                        line[i] == '<' -> {
                            val tagEnd = line.indexOf('>', i)
                            if (tagEnd != -1) {
                                val tagContent = line.substring(i, tagEnd + 1)
                                withStyle(SpanStyle(color = PurpleNeon, fontWeight = FontWeight.Bold)) {
                                    append(tagContent)
                                }
                                i += tagContent.length
                            } else {
                                withStyle(SpanStyle(color = PurpleNeon)) { append(line[i].toString()) }
                                i++
                            }
                        }
                        else -> {
                            val nextTag = line.indexOf('<', i)
                            val text = if (nextTag != -1) line.substring(i, nextTag) else line.substring(i)
                            withStyle(SpanStyle(color = OnCyberDark)) { append(text) }
                            i += text.length
                        }
                    }
                }
                if (idx < lines.size - 1) append("\n")
            }
        }
    }

    Text(
        text = annotated,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        modifier = modifier.padding(8.dp)
    )
}

private fun parseXmlElement(element: Element): XmlElementNode.TagNode {
    val attributesMap = mutableMapOf<String, String>()
    val attrs = element.attributes
    for (i in 0 until attrs.length) {
        val attr = attrs.item(i)
        attributesMap[attr.nodeName] = attr.nodeValue
    }

    val children = mutableListOf<XmlElementNode>()
    var textContent: String? = null

    val childNodes = element.childNodes
    for (i in 0 until childNodes.length) {
        val child = childNodes.item(i)
        when (child.nodeType) {
            Node.ELEMENT_NODE -> {
                children.add(parseXmlElement(child as Element))
            }
            Node.TEXT_NODE -> {
                val txt = child.nodeValue?.trim()
                if (!txt.isNullOrEmpty()) {
                    if (childNodes.length == 1) {
                        textContent = txt
                    } else {
                        children.add(XmlElementNode.TextNode(txt))
                    }
                }
            }
        }
    }

    return XmlElementNode.TagNode(
        tagName = element.tagName,
        attributes = attributesMap,
        children = children,
        textContent = textContent,
        isExpanded = mutableStateOf(true)
    )
}
