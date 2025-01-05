package io.goji.llm4codebase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.RandomAccessReadBufferedFile
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.nio.charset.StandardCharsets


object FileUtils {
    private val IGNORED_DIRECTORIES = setOf(
        "node_modules", "venv", ".git", "__pycache__", ".idea", ".vscode"
    )

    private val IGNORED_FILES = setOf(
        ".DS_Store", "Thumbs.db", ".env", ".pyc",
        // Image files
        ".jpg", ".jpeg", ".png", ".gif", ".mp4",
        // Other binary files...
        ".exe", ".dll", ".bin"
    )

    private val LIKELY_TEXT_FILES = setOf(
        ".txt", ".md", ".json", ".js", ".ts",
        ".css", ".html", ".xml", ".yaml", ".yml",
        ".kt", ".kts", ".java", ".py", ".rb"
    )

    suspend fun processDirectory(dir: File): FileNode = withContext(Dispatchers.IO) {
        buildFileTree(dir)
    }

    private fun buildFileTree(file: File): FileNode {
        return when {
            file.isDirectory -> {
                val children = file.listFiles()
                    ?.filter { !shouldIgnore(it) }
                    ?.map { buildFileTree(it) }
                    ?.sortedWith(compareBy({ !it.isDir }, { it.name }))
                    ?: emptyList()

                FileNode(
                    name = file.name,
                    path = file.absolutePath,
                    isDir = true,
                    children = children
                )
            }
            else -> FileNode(
                name = file.name,
                path = file.absolutePath,
                isDir = false,
                size = file.length(),
                isTextFile = isLikelyTextFile(file)
            )
        }
    }

    private fun shouldIgnore(file: File): Boolean {
        return when {
            file.isDirectory -> file.name in IGNORED_DIRECTORIES
            else -> IGNORED_FILES.any { file.name.lowercase().endsWith(it) }
        }
    }

    private fun isLikelyTextFile(file: File): Boolean {
        return when {
            LIKELY_TEXT_FILES.any { file.name.lowercase().endsWith(it) } -> true
            file.name.lowercase().endsWith(".xlsx") ||
            file.name.lowercase().endsWith(".pdf") -> true
            else -> runCatching {
                val bytes = file.readBytes().take(4096)
                val text = String(bytes.toByteArray(), StandardCharsets.UTF_8)
                val printableChars = text.count { it in ' '..'}' || it == '\n' || it == '\r' || it == '\t' }
                printableChars.toDouble() / bytes.size > 0.7
            }.getOrDefault(false)
        }
    }

    suspend fun readFileContent(file: File): String = withContext(Dispatchers.IO) {
        when {
            file.name.lowercase().endsWith(".pdf") -> readPDFContent(file)
            file.name.lowercase().endsWith(".xlsx") -> readExcelContent(file)
            else -> file.readText()
        }
    }

    private fun readPDFContent(file: File): String {
        return Loader.loadPDF(RandomAccessReadBufferedFile(file)).use { document ->
            val stripper = PDFTextStripper()
            stripper.getText(document)
        }
    }

    private fun readExcelContent(file: File): String {
        return WorkbookFactory.create(file).use { workbook ->
            buildString {
                workbook.forEach { sheet ->
                    appendLine("Sheet: ${sheet.sheetName}")
                    sheet.forEach { row ->
                        row.forEach { cell ->
                            append(cell.toString())
                            append("\t")
                        }
                        appendLine()
                    }
                    appendLine()
                }
            }
        }
    }
}
