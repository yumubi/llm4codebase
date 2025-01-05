package io.goji.llm4codebase// Utils.kt
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import kotlin.math.ceil

object Utils {
    fun selectDirectory(): File? {
        return when {
            // Try native file dialog first on macOS
            System.getProperty("os.name").lowercase().contains("mac") -> {
                val dialog = FileDialog(Frame()).apply {
                    mode = FileDialog.LOAD
                    title = "Select Directory"
                    isMultipleMode = false
                    // Set filter for directories only
                    setFilenameFilter { _, _ -> true }
                }
                dialog.isVisible = true
                dialog.files.firstOrNull()
            }
            // Fall back to Swing JFileChooser
            else -> {
                JFileChooser().apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Select Directory"
                    approveButtonText = "Select"
                }.let { chooser ->
                    when (chooser.showOpenDialog(null)) {
                        JFileChooser.APPROVE_OPTION -> chooser.selectedFile
                        else -> null
                    }
                }
            }
        }
    }

    fun generateAsciiTree(
        node: FileNode?,
        selectedPaths: Set<String>,
        prefix: String = "",
        isLast: Boolean = true
    ): String {
        if (node == null) return ""

        val hasSelectedDescendant = hasSelectedDescendant(node, selectedPaths)
        if (!selectedPaths.contains(node.path) && !hasSelectedDescendant) {
            return ""
        }

        return buildString {
            val connector = if (isLast) "└── " else "├── "
            append(prefix)
            append(connector)
            append(node.name)
            appendLine()

            if (node.children.isNotEmpty()) {
                val newPrefix = prefix + if (isLast) "    " else "│   "
                node.children
                    .filter { child ->
                        selectedPaths.contains(child.path) ||
                        hasSelectedDescendant(child, selectedPaths)
                    }
                    .forEachIndexed { index, child ->
                        append(
                            generateAsciiTree(
                                node = child,
                                selectedPaths = selectedPaths,
                                prefix = newPrefix,
                                isLast = index == node.children.size - 1
                            )
                        )
                    }
            }
        }
    }

    private fun hasSelectedDescendant(
        node: FileNode,
        selectedPaths: Set<String>
    ): Boolean {
        if (!node.isDir) return false
        return node.children.any { child ->
            selectedPaths.contains(child.path) ||
            hasSelectedDescendant(child, selectedPaths)
        }
    }

    fun calculateTokens(text: String): Int {
        // Rough estimation: 1 token per 4 characters
//        return (text.length / 4.0).ceil().toInt()
        return ceil(text.length / 4.0).toInt()
    }
}
