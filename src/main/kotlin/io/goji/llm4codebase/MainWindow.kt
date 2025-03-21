package io.goji.llm4codebase

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import io.goji.llm4codebase.Utils.calculateTokens
import io.goji.llm4codebase.Utils.generateAsciiTree
import io.goji.llm4codebase.Utils.selectDirectory
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection




@Composable
fun FileViewerApp(
    state: FileViewerState,
    dispatch: suspend (FileViewerState.() -> FileViewerState) -> Unit
) {
    // 创建协程作用域
    val coroutineScope  = rememberCoroutineScope()

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Toolbar(
                onSelectDirectory = {
                    selectDirectory()?.let { dir ->
                        coroutineScope.launch {
                            // 在协程内部调用 processDirectory
                            val processedRoot = FileUtils.processDirectory(dir)
                            dispatch {
                                copy(root = processedRoot)
                            }
                        }
                    }
                },
                onExpandAll = {
                    coroutineScope.launch {
                        dispatch { copy(expandedNodes = getAllPaths(root)) }
                    }
                },
                onCollapseAll = {
                    coroutineScope.launch {
                        dispatch { copy(expandedNodes = emptySet()) }
                    }
                },
                onSelectAll = {
                    coroutineScope.launch {
                        dispatch { copy(selectedPaths = getAllSelectablePaths(root)) }
                    }
                },
                onDeselectAll = {
                    coroutineScope.launch {
                        dispatch { copy(selectedPaths = emptySet()) }
                    }
                },
                onClear = {
                    coroutineScope.launch {
                        dispatch { FileViewerState() }
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxSize().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // File Tree Panel
                FileTreePanel(
                    modifier = Modifier.weight(1f),
                    root = state.root,
                    selectedPaths = state.selectedPaths,
                    expandedNodes = state.expandedNodes,
                    onToggleSelect = { path ->
                        coroutineScope.launch {
                            dispatch { toggleSelection(path) }

                        }
                    },
                    onToggleExpand = { path ->
                        coroutineScope.launch {
                            dispatch { copy(expandedNodes = expandedNodes.toggle(path)) }
                        }
                    }
                )

                // Selected Files Panel
                SelectedFilesPanel(
                    modifier = Modifier.weight(1f),
                    state = state,
                    onCopyToClipboard = {
                        copyToClipboard(generateSelectedContent(state))
                    }
                )
            }

            StatsBar(
                selectedCount = state.stats.selectedCount,
                totalTokens = state.stats.totalTokens
            )
        }
    }
}



@Composable
fun Toolbar(
    onSelectDirectory: () -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选择目录按钮
            FilledTonalButton(
                onClick = onSelectDirectory,
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = "Select Directory",
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Select Directory")
                }
            }

            // 展开/折叠按钮组
            ButtonGroup(
                modifier = Modifier.height(36.dp)
            ) {
                IconButton(
                    onClick = onExpandAll,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "Expand All",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onCollapseAll,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Collapse All",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 全选/取消全选按钮组
            ButtonGroup(
                modifier = Modifier.height(36.dp)
            ) {
                ButtonGroupItem(
                    onClick = onSelectAll,
                    icon = Icons.Default.SelectAll,
                    text = "Select All",
                    isFirst = true
                )
                ButtonGroupItem(
                    onClick = onDeselectAll,
                    icon = Icons.Default.IndeterminateCheckBox ,
                    text = "Deselect All",
                    isFirst = false
                )
            }
        }

        // 清除按钮
        IconButton(
            onClick = onClear,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Clear, "Clear")
        }
    }
}

@Composable
private fun ButtonGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row {
            content()
        }
    }
}

@Composable
private fun ButtonGroupItem(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    isFirst: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier = Modifier.height(36.dp)) {
        if (!isFirst) {
            VerticalDivider(
                modifier = Modifier.height(36.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            )
        }

        TextButton(
            onClick = onClick,
            modifier = modifier.height(36.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = text,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun VerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline
) {
    Box(
        modifier = modifier
            .width(1.dp)
            .background(color)
    )
}

@Composable
fun FileTreePanel(
    modifier: Modifier = Modifier,
    root: FileNode?,
    selectedPaths: Set<String>,
    expandedNodes: Set<String>,
    onToggleSelect: (String) -> Unit,
    onToggleExpand: (String) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        if (root == null) {
            EmptyStateMessage()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp)
            ) {
                item {
                    FileTreeNode(
                        node = root,
                        level = 0,
                        selectedPaths = selectedPaths,
                        expandedNodes = expandedNodes,
                        onToggleSelect = onToggleSelect,
                        onToggleExpand = onToggleExpand
                    )
                }
            }
        }
    }
}





@Composable
private fun EmptyStateMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Select a directory to view its contents",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}



@Composable
fun FileTreeNode(
    node: FileNode,
    level: Int,
    selectedPaths: Set<String>,
    expandedNodes: Set<String>,
    onToggleSelect: (String) -> Unit,
    onToggleExpand: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 16).dp)
            .clickable {
                if (node.isDir) onToggleExpand(node.path)
                else if (node.isTextFile) onToggleSelect(node.path)
            }
    ) {
        if (node.isTextFile) {
            Checkbox(
                checked = selectedPaths.contains(node.path),
                onCheckedChange = { onToggleSelect(node.path) }
            )
        }

        Icon(
            imageVector = when {
//                node.isDir && expandedNodes.contains(node.path) -> Icons.Default.FolderOpen
//                node.isDir -> Icons.Default.Folder
//                node.isTextFile -> Icons.Default.Description
//                else -> Icons.Default.Block

                node.isDir && expandedNodes.contains(node.path) -> Icons.Default.FolderOpen
                node.isDir -> Icons.Default.Folder
                node.isTextFile -> Icons.Default.Description
                else -> Icons.Default.Block
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = buildString {
                append(node.name)
                if (!node.isDir) {
                    append(" (${formatFileSize(node.size)})")
                }
            }
        )
    }

    if (node.isDir && expandedNodes.contains(node.path)) {
        node.children.forEach { child ->
            FileTreeNode(
                node = child,
                level = level + 1,
                selectedPaths = selectedPaths,
                expandedNodes = expandedNodes,
                onToggleSelect = onToggleSelect,
                onToggleExpand = onToggleExpand
            )
        }
    }
}


@Composable
fun SelectedFilesPanel(
    modifier: Modifier = Modifier,
    state: FileViewerState,
    onCopyToClipboard: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Selected Files",
                    style = MaterialTheme.typography.titleMedium
                )

                Button(
                    onClick = onCopyToClipboard,
                    enabled = state.selectedPaths.isNotEmpty()
                ) {
                    Text("Copy to Clipboard")
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                SelectionContainer {
                    Text(
                        text = generateSelectedContent(state),
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}



@Composable
fun StatsBar(
    selectedCount: Int,
    totalTokens: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Selected Files: $selectedCount")
            Text("Estimated Tokens: $totalTokens")
        }
    }
}

private fun generateSelectedContent(state: FileViewerState): String {
    return buildString {
        appendLine("<folder-structure>")
        appendLine(generateAsciiTree(state.root, state.selectedPaths))
        appendLine("</folder-structure>")
        appendLine()

        state.selectedPaths.forEach { path ->
            state.fileContents[path]?.let { content ->
                appendLine("""<document path="$path">""")
                appendLine(content)
                appendLine("</document>")
                appendLine()
            }
        }
    }
}



private fun formatFileSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return "%.1f %s".format(size, units[unitIndex])
}

private fun copyToClipboard(text: String) {
    val selection = StringSelection(text)
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(selection, selection)
}

// Helper functions for the file tree
private fun Set<String>.toggle(item: String): Set<String> =
    if (contains(item)) minus(item) else plus(item)

private fun getAllPaths(root: FileNode?): Set<String> {
    val paths = mutableSetOf<String>()
    fun collect(node: FileNode) {
        if (node.isDir) {
            paths.add(node.path)
            node.children.forEach { collect(it) }
        }
    }
    root?.let { collect(it) }
    return paths
}

private fun getAllSelectablePaths(root: FileNode?): Set<String> {
    val paths = mutableSetOf<String>()
    fun collect(node: FileNode) {
        if (!node.isDir && node.isTextFile) {
            paths.add(node.path)
        }
        node.children.forEach { collect(it) }
    }
    root?.let { collect(it) }
    return paths
}


//FileViewerState的扩展函数
private fun FileViewerState.toggleSelection(path: String): FileViewerState {
    val newSelectedPaths = selectedPaths.toggle(path)
    return copy(
        selectedPaths = newSelectedPaths,
        stats = stats.copy(
            selectedCount = newSelectedPaths.size,
            totalTokens = calculateTotalTokens(newSelectedPaths)
        )
    )
}

private fun FileViewerState.calculateTotalTokens(selectedPaths: Set<String>): Int
{
    return selectedPaths.sumOf<String> { path ->
        fileContents[path]?.let { calculateTokens(it) } ?: 0
    }
}
