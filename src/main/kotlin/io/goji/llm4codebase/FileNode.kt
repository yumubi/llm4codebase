package io.goji.llm4codebase

data class FileNode(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long = 0L,
    val isTextFile: Boolean = false,
    val children: List<FileNode> = emptyList()
){
    // 添加计算所有子节点数量的辅助函数
    fun countTextFiles(): Int = if (isDir) {
        children.sumOf { it.countTextFiles() }
    } else if (isTextFile) {
        1
    } else {
        0
    }

    // 添加获取所有文本文件路径的辅助函数
    fun getAllTextFilePaths(): Set<String> {
        val paths = mutableSetOf<String>()
        if (isTextFile) {
            paths.add(path)
        }
        children.forEach {
            paths.addAll(it.getAllTextFilePaths())
        }
        return paths
    }
}

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


