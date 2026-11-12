package com.infinitecanvas.board

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.infinitecanvas.board.presentation.screen.board.BoardScreen
import com.infinitecanvas.board.presentation.theme.ui.InfCanvasTheme // Замените на имя вашей темы

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Включаем Edge-to-Edge для корректного отображения на Android 15/16
        enableEdgeToEdge()
        setContent {
            InfCanvasTheme {
                BoardScreen()
            }
        }
    }
}