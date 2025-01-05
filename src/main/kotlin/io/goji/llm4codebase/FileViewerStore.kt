package io.goji.llm4codebase

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File


class FileViewerStore {


    private val _state = MutableStateFlow(FileViewerState())
    val state: StateFlow<FileViewerState> = _state.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val saveDebounceTime = 500L // 添加防抖时间
    private var saveJob: Job? = null

    fun dispatch(action: suspend FileViewerState.() -> FileViewerState) {
        scope.launch {
            _state.update { state ->
                state.action().also { newState ->
                    // 更新统计信息
                    updateStats(newState)
                }
            }
            debounceSaveState()
        }
    }


    private fun debounceSaveState() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(saveDebounceTime)
            saveState()
        }
    }

    private fun updateStats(state: FileViewerState) {
        val selectedCount = state.selectedPaths.size
        val totalTokens = state.selectedPaths.sumOf { path ->
            state.fileContents[path]?.let { Utils.calculateTokens(it) } ?: 0
        }
        _state.update {
            it.copy(stats = Stats(selectedCount, totalTokens))
        }
    }

    private fun saveState() {
        scope.launch(Dispatchers.IO) {
            val serializedState = state.value.let { state ->
                // Convert state to serializable format
//                mapOf(
//                    "selectedPaths" to state.selectedPaths.toList(),
//                    "expandedNodes" to state.expandedNodes.toList()
//                )
                SavedFileViewerState(
                    selectedPaths = state.selectedPaths.toList(),
                    expandedNodes = state.expandedNodes.toList()
                )
            }

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

           // val json = moshi.adapter<Map<String, Any>>().toJson(serializedState)
            val json = moshi.adapter(SavedFileViewerState::class.java).toJson(serializedState)
            File("fileviewer.json").writeText(json)
        }
    }

    private fun loadState(): SavedFileViewerState  {
        return runCatching {
            val json = File("fileviewer.json").readText()
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()


            moshi.adapter(SavedFileViewerState::class.java)
                .fromJson(json) ?: SavedFileViewerState()
        }.getOrDefault(SavedFileViewerState ())

    }
}


data class SavedFileViewerState(
    val selectedPaths: List<String> = emptyList(),
    val expandedNodes: List<String> = emptyList()
)
