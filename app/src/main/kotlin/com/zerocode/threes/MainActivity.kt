package com.zerocode.threes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zerocode.threes.ui.ThreesApp
import com.zerocode.threes.ui.theme.ThreesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThreesTheme {
                val viewModel: ThreesViewModel = viewModel()
                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                ThreesApp(
                    state = state,
                    onNewGame = viewModel::newGame,
                    onSelectAi = viewModel::selectAi,
                    onMove = viewModel::move,
                    onToggleAuto = viewModel::toggleAuto,
                    onUndo = viewModel::undo,
                )
            }
        }
    }
}
