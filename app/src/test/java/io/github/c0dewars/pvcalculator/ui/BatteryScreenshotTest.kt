package io.github.c0dewars.pvcalculator.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.github.c0dewars.pvcalculator.domain.DeyeBatteryConfig
import io.github.c0dewars.pvcalculator.domain.DeyeDynamicConfig
import io.github.c0dewars.pvcalculator.domain.DeyeStationLatest
import io.github.c0dewars.pvcalculator.domain.DeyeSystemConfig
import io.github.c0dewars.pvcalculator.domain.TouPlanner
import io.github.c0dewars.pvcalculator.domain.TouSlot
import io.github.c0dewars.pvcalculator.ui.screens.GridChargeCard
import io.github.c0dewars.pvcalculator.ui.screens.InverterSettingsCard
import io.github.c0dewars.pvcalculator.ui.screens.LiveCard
import io.github.c0dewars.pvcalculator.ui.theme.PvTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime

/** Renders the battery cards with sample inverter data to docs/screenshots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp-xhdpi")
class BatteryScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun batteryCards() = render(dark = false, name = "7-battery")

    @Test
    fun batteryCardsDark() = render(dark = true, name = "dark-5-battery")

    private fun render(dark: Boolean, name: String) {
        val base = listOf(
            TouSlot("00:00", 5000, 20), TouSlot("04:00", 5000, 20), TouSlot("08:00", 5000, 15),
            TouSlot("12:00", 5000, 15), TouSlot("16:00", 5000, 20), TouSlot("20:00", 5000, 20),
        )
        val plan = TouPlanner.gridChargePlan(base, LocalTime.of(2, 0), LocalTime.of(6, 0), 90, 3000, 20)
        val state = AppViewModel.InverterState(
            deviceSn = "2212228299",
            latest = DeyeStationLatest(success = true, batterySOC = 63.0, batteryPower = -1850.0, generationPower = 3420.0, consumptionPower = 980.0, gridPower = 0.0),
            battery = DeyeBatteryConfig(success = true, battCapacity = 314, battLowCapacity = 20, battShutDownCapacity = 10, maxChargeCurrent = 100, maxDischargeCurrent = 100),
            system = DeyeSystemConfig(success = true, systemWorkMode = "ZERO_EXPORT_TO_CT", energyPattern = "LOAD_FIRST", maxSellPower = 6000, maxSolarPower = 8000, zeroExportPower = 20),
            dynamic = DeyeDynamicConfig(success = true, gridChargeAction = "on", gridChargeAmpere = 40, solarSellAction = "off", touAction = "on", timeUseSettingItems = plan),
            updatedAt = LocalDateTime.of(2026, 11, 11, 14, 32),
        )
        compose.setContent {
            PvTheme(darkTheme = dark) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LiveCard(state)
                    GridChargeCard(state, onCharge = {}, onStop = {})
                    InverterSettingsCard(state)
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
