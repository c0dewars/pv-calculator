package io.github.c0dewars.pvcalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.c0dewars.pvcalculator.ui.PvApp
import io.github.c0dewars.pvcalculator.ui.theme.PvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PvTheme { PvApp() }
        }
    }
}
