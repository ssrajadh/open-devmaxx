package io.github.ssrajadh.prep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A deliberately tiny Markdown renderer covering what card explanations use:
 * paragraphs, `-`/`*` bullets, numbered lists, fenced code, **bold**, *italic*, `code`.
 * Avoids pulling in a Markdown library for a handful of features.
 */
@Composable
fun Markdown(text: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks(text)) {
            when (block) {
                is Block.Code -> Text(
                    block.text,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(8.dp),
                )
                is Block.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { i, item ->
                        Row {
                            Text(if (block.ordered) "${i + 1}." else "•", modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium)
                            Text(inline(item, MaterialTheme.colorScheme.surfaceVariant), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is Block.Para -> Text(inline(block.text, MaterialTheme.colorScheme.surfaceVariant), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private sealed interface Block {
    data class Para(val text: String) : Block
    data class ListBlock(val ordered: Boolean, val items: List<String>) : Block
    data class Code(val text: String) : Block
}

private val bullet = Regex("^\\s*[-*]\\s+")
private val numbered = Regex("^\\s*\\d+\\.\\s+")

private fun blocks(src: String): List<Block> {
    val out = mutableListOf<Block>()
    val lines = src.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                val buf = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) buf += lines[i++]
                i++
                out += Block.Code(buf.joinToString("\n"))
            }
            line.isBlank() -> i++
            bullet.containsMatchIn(line) || numbered.containsMatchIn(line) -> {
                val ordered = numbered.containsMatchIn(line)
                val re = if (ordered) numbered else bullet
                val items = mutableListOf<String>()
                while (i < lines.size && re.containsMatchIn(lines[i])) items += lines[i++].replace(re, "")
                out += Block.ListBlock(ordered, items)
            }
            else -> {
                val buf = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank() && !bullet.containsMatchIn(lines[i]) &&
                    !numbered.containsMatchIn(lines[i]) && !lines[i].trimStart().startsWith("```")
                ) buf += lines[i++].trim()
                out += Block.Para(buf.joinToString(" "))
            }
        }
    }
    return out
}

private val inlineToken = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\*(.+?)\\*")

fun inline(s: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in inlineToken.findAll(s)) {
        append(s.substring(last, m.range.first))
        val (bold, code, italic) = m.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(code) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
        }
        last = m.range.last + 1
    }
    append(s.substring(last))
}
