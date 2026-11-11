@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.c0dewars.pvcalculator.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.c0dewars.pvcalculator.R
import io.github.c0dewars.pvcalculator.ui.components.rememberFormatter
import io.github.c0dewars.pvcalculator.ui.screens.BatteryScreen
import io.github.c0dewars.pvcalculator.ui.screens.BillsScreen
import io.github.c0dewars.pvcalculator.ui.screens.OverviewScreen
import io.github.c0dewars.pvcalculator.ui.screens.ProductionScreen
import io.github.c0dewars.pvcalculator.ui.screens.SettingsScreen

enum class Tab(val title: Int, val icon: ImageVector) {
    Overview(R.string.tab_overview, Icons.Rounded.Insights),
    Production(R.string.tab_production, Icons.Rounded.WbSunny),
    Bills(R.string.tab_bills, Icons.AutoMirrored.Rounded.ReceiptLong),
    Battery(R.string.tab_battery, Icons.Rounded.BatteryChargingFull),
    Settings(R.string.tab_settings, Icons.Rounded.Settings),
}

@Composable
fun PvApp(vm: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.Overview) }
    val data by vm.data.collectAsState()
    val summary by vm.summary.collectAsState()
    val fmt = rememberFormatter(data.currencyCode ?: AppViewModel.defaultCurrency())
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.title)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { current ->
                when (current) {
                    Tab.Overview -> OverviewScreen(summary, data, fmt, snackbar, onOpenSettings = { tab = Tab.Settings }, onOpenProduction = { tab = Tab.Production })
                    Tab.Production -> ProductionScreen(vm, data, fmt, snackbar)
                    Tab.Bills -> BillsScreen(vm, data, fmt, snackbar)
                    Tab.Battery -> BatteryScreen(vm, data, snackbar, onOpenSettings = { tab = Tab.Settings })
                    Tab.Settings -> SettingsScreen(vm, data, fmt, snackbar)
                }
            }
        }
    }
}

/** Common screen frame: collapsing title, optional actions and floating button. */
@Composable
fun ScreenScaffold(
    title: String,
    snackbar: SnackbarHostState,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                actions = actions,
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}
