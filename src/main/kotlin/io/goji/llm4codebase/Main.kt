package io.goji.llm4codebase

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch

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
