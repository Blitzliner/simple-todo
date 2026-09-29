package com.example.minimaltodo.util

import com.example.minimaltodo.data.local.SortMode
import com.example.minimaltodo.data.local.TodoEntity
import com.example.minimaltodo.data.local.TodoType

/**
 * Menschenlesbare Ausgabe für Teilen (Share Sheet), TXT- und Markdown-Export.
 * Die Reihenfolge folgt der Sortierung, die auch in der App angezeigt wird.
 */
class TextExporter(
    items: List<TodoEntity>,
    private val defaultSort: SortMode = SortMode.MANUAL,
) {
    private val index = TreeIndex(items)

    private fun kids(parentId: String?): List<TodoEntity> = index.sortedChildren(parentId, defaultSort)

    // ---------------------------------------------------------------- Teilen

    /**
     * Text für das Teilen eines einzelnen Elements inklusive Unterbaum.
     * - einfache Liste: „Einkauf\n\n☐ Milch\n☐ Brot“
     * - verschachtelt: Hierarchie mit ├─ / └─
     */
    fun shareText(id: String): String {
        val item = index.byId[id] ?: return ""
        val children = kids(item.id)
        val sb = StringBuilder()
        appendHeader(sb, item)
        if (children.isEmpty()) return sb.toString().trimEnd()
        val nested = children.any { kids(it.id).isNotEmpty() }
        if (nested) {
            appendTree(sb, children, "  ", HashSet<String>().apply { add(item.id) })
        } else {
            sb.append("\n\n")
            children.forEach { appendIndented(sb, it, 0, HashSet()) }
        }
        return sb.toString().trimEnd()
    }

    private fun appendHeader(sb: StringBuilder, item: TodoEntity) {
        when (item.type) {
            TodoType.TODO -> {
                sb.append(checkbox(item)).append(' ').append(item.title)
                item.content?.takeIf { it.isNotBlank() }?.let { note ->
                    note.trimEnd().lines().forEach { sb.append("\n  ").append(it) }
                }
            }
            TodoType.GROUP -> sb.append(item.title)
            TodoType.TEXT -> appendTextBlock(sb, item, "")
        }
    }

    private fun appendTree(sb: StringBuilder, children: List<TodoEntity>, prefix: String, visited: MutableSet<String>) {
        children.forEachIndexed { i, child ->
            if (!visited.add(child.id)) return@forEachIndexed
            val last = i == children.lastIndex
            val lines = labelLines(child)
            sb.append('\n').append(prefix).append(if (last) "└─ " else "├─ ").append(lines.first())
            val childPrefix = prefix + if (last) "    " else "│   "
            lines.drop(1).forEach { sb.append('\n').append(childPrefix).append(it) }
            appendTree(sb, kids(child.id), childPrefix, visited)
        }
    }

    // ------------------------------------------------------------------ TXT

    /** Kompletter TXT-Export aller Elemente. */
    fun exportTxt(): String {
        val sb = StringBuilder()
        val roots = kids(null)
        var previousWasBlock = false
        roots.forEachIndexed { i, item ->
            val isBlock = item.type != TodoType.TODO || kids(item.id).isNotEmpty()
            if (i > 0 && (isBlock || previousWasBlock)) sb.append('\n')
            if (item.type == TodoType.GROUP) {
                sb.append(item.title).append('\n')
                val children = kids(item.id)
                if (children.isNotEmpty()) sb.append('\n')
                val visited = HashSet<String>().apply { add(item.id) }
                children.forEach { appendIndented(sb, it, 0, visited) }
            } else {
                appendIndented(sb, item, 0, HashSet())
            }
            previousWasBlock = isBlock
        }
        return sb.toString().trimEnd() + "\n"
    }

    private fun appendIndented(sb: StringBuilder, item: TodoEntity, depth: Int, visited: MutableSet<String>) {
        if (!visited.add(item.id)) return
        val indent = "  ".repeat(depth)
        labelLines(item).forEachIndexed { i, line ->
            sb.append(indent)
            if (i > 0) sb.append("  ")
            sb.append(line).append('\n')
        }
        kids(item.id).forEach { appendIndented(sb, it, depth + 1, visited) }
    }

    // ------------------------------------------------------------- Markdown

    /** Markdown-Export: Root-Gruppen werden Überschriften, Inhalte verschachtelte Listen. */
    fun exportMarkdown(): String {
        val sb = StringBuilder()
        var inLooseList = false
        for (item in kids(null)) {
            when (item.type) {
                TodoType.GROUP -> {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append("# ").append(escapeHeading(item.title)).append('\n')
                    val children = kids(item.id)
                    if (children.isNotEmpty()) sb.append('\n')
                    val visited = HashSet<String>().apply { add(item.id) }
                    children.forEach { appendMarkdownItem(sb, it, 0, visited) }
                    inLooseList = false
                }
                TodoType.TEXT -> {
                    if (sb.isNotEmpty()) sb.append('\n')
                    if (item.title.isNotBlank()) sb.append("## ").append(escapeHeading(item.title)).append("\n\n")
                    item.content?.takeIf { it.isNotBlank() }?.let { sb.append(it.trimEnd()).append('\n') }
                    val visited = HashSet<String>().apply { add(item.id) }
                    val children = kids(item.id)
                    if (children.isNotEmpty()) sb.append('\n')
                    children.forEach { appendMarkdownItem(sb, it, 0, visited) }
                    inLooseList = false
                }
                TodoType.TODO -> {
                    if (sb.isNotEmpty() && !inLooseList) sb.append('\n')
                    appendMarkdownItem(sb, item, 0, HashSet())
                    inLooseList = true
                }
            }
        }
        return sb.toString().trimEnd() + "\n"
    }

    private fun appendMarkdownItem(sb: StringBuilder, item: TodoEntity, depth: Int, visited: MutableSet<String>) {
        if (!visited.add(item.id)) return
        val indent = "  ".repeat(depth)
        val continuation = "$indent  "
        when (item.type) {
            TodoType.TODO -> {
                sb.append(indent).append(if (item.completed) "- [x] " else "- [ ] ").append(item.title).append('\n')
                item.content?.takeIf { it.isNotBlank() }?.trimEnd()?.lines()?.forEach {
                    sb.append(continuation).append(it).append('\n')
                }
            }
            TodoType.GROUP -> sb.append(indent).append("- **").append(item.title).append("**\n")
            TodoType.TEXT -> {
                val lines = labelLines(item)
                sb.append(indent).append("- ").append(lines.first()).append('\n')
                lines.drop(1).forEach { sb.append(continuation).append(it).append('\n') }
            }
        }
        kids(item.id).forEach { appendMarkdownItem(sb, it, depth + 1, visited) }
    }

    // --------------------------------------------------------------- Helfer

    private fun checkbox(item: TodoEntity) = if (item.completed) CHECKED else UNCHECKED

    /** Zeilen, mit denen ein Element in Listen dargestellt wird. */
    private fun labelLines(item: TodoEntity): List<String> = when (item.type) {
        TodoType.TODO -> listOf("${checkbox(item)} ${item.title}")
        TodoType.GROUP -> listOf(item.title)
        TodoType.TEXT -> {
            val content = item.content?.trimEnd()?.lines().orEmpty().filterIndexed { i, l -> i > 0 || l.isNotBlank() }
            when {
                item.title.isNotBlank() && content.isNotEmpty() -> listOf(item.title) + content
                item.title.isNotBlank() -> listOf(item.title)
                content.isNotEmpty() -> content
                else -> listOf("")
            }
        }
    }

    private fun appendTextBlock(sb: StringBuilder, item: TodoEntity, indent: String) {
        labelLines(item).forEachIndexed { i, line ->
            if (i > 0) sb.append('\n')
            sb.append(indent).append(line)
        }
    }

    private fun escapeHeading(text: String) = text.replace("\n", " ")

    companion object {
        const val UNCHECKED = "☐"
        const val CHECKED = "☑"
    }
}
