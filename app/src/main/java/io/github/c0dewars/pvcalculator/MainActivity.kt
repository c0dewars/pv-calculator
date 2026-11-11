package io.github.c0dewars.pvcalculator

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.c0dewars.pvcalculator.ui.AppViewModel
import io.github.c0dewars.pvcalculator.ui.PvApp
import io.github.c0dewars.pvcalculator.ui.theme.PvTheme
import io.github.c0dewars.pvcalculator.ui.theme.isDark
import io.github.c0dewars.pvcalculator.ui.theme.themeModeOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val data by vm.data.collectAsState()
            val dark = themeModeOf(data.themeMode).isDark()
            // Status/navigation bar icons follow the app theme, not only the phone's.
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PvTheme(darkTheme = dark) { PvApp(vm) }
        }
    }
}
