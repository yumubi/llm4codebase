package io.goji.llm4codebase

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import io.goji.llm4codebase.Utils.calculateTokens
import io.goji.llm4codebase.Utils.generateAsciiTree
import io.goji.llm4codebase.Utils.selectDirectory
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection


fun main() = application {
    val store = remember { FileViewerStore() }
    val state by store.state.collectAsState()

    Window(
        title = "File Viewer for LLM Prompts",
        onCloseRequest = ::exitApplication
    ) {
        MaterialTheme {
          //  FileViewerApp(state, store::dispatch)
            val scope = rememberCoroutineScope()
            FileViewerApp(
                state = state,
                dispatch = { action ->
                    scope.launch {
                        store.dispatch(action)
                    }
                }
            )
        }
    }
}


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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSelectDirectory) {
                Text("Select Directory")
            }
//
//            ButtonGroup {
//                IconButton(onClick = onExpandAll) {
//                    Icon(Icons.Default.ExpandMore, "Expand All")
//                }
//                IconButton(onClick = onCollapseAll) {
//                    Icon(Icons.Default.ChevronRight, "Collapse All")
//                }
//            }
//
//            ButtonGroup {
//                IconButton(onClick = onSelectAll) {
//                    Icon(Icons.Default.CheckBox, "Select All")
//                }
//                IconButton(onClick = onDeselectAll) {
//                    Icon(Icons.Default.CheckBoxOutlineBlank, "Deselect All")
//                }
//            }


            // 替换 ButtonGroup 为 Row，添加边框样式
            Surface(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(4.dp)
            ) {
                Row {
                    IconButton(onClick = onExpandAll) {
                        Icon(Icons.Default.ExpandMore, contentDescription = "Expand All")
                    }
                    Divider(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.outline
                    )
                    IconButton(onClick = onCollapseAll) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Collapse All")
                    }
                }
            }

            // 第二组按钮
            Surface(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(4.dp)
            ) {
                Row {
                    IconButton(onClick = onSelectAll) {
                        Icon(Icons.Default.CheckBox, contentDescription = "Select All")
                    }
                    Divider(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.outline
                    )
                    IconButton(onClick = onDeselectAll) {
                        Icon(Icons.Default.CheckBoxOutlineBlank, contentDescription = "Deselect All")
                    }
                }
            }

        }

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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Select a directory to view its contents",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
