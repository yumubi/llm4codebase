package io.goji.llm4codebase

data class FileNode(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long = 0L,
    val isTextFile: Boolean = false,
    val children: List<FileNode> = emptyList()
)

data class FileViewerState(
    val root: FileNode? = null,
    val selectedPaths: Set<String> = emptySet(),
    val expandedNodes: Set<String> = emptySet(),
    val fileContents: Map<String, String> = emptyMap(),
    val stats: Stats = Stats()
)

data class Stats(
    val selectedCount: Int = 0,
    val totalTokens: Int = 0
)


