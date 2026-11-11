package io.github.c0dewars.pvcalculator.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.c0dewars.pvcalculator.data.AppData
import io.github.c0dewars.pvcalculator.data.Bill
import io.github.c0dewars.pvcalculator.data.DataRepository
import io.github.c0dewars.pvcalculator.data.ExtraInvestment
import io.github.c0dewars.pvcalculator.data.ProductionEntry
import io.github.c0dewars.pvcalculator.data.SolarSystem
import io.github.c0dewars.pvcalculator.data.Tariffs
import io.github.c0dewars.pvcalculator.ui.theme.PvTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.PI
import kotlin.math.sin

/**
 * Renders the main screens with sample data to docs/screenshots (used in the README).
 * Run with: ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun sampleData(): AppData {
        val production = mutableListOf<ProductionEntry>()
        var d = LocalDate.of(2025, 11, 1)
        val end = LocalDate.of(2026, 10, 31)
        while (!d.isAfter(end)) {
            val season = 0.55 + 0.45 * sin((d.dayOfYear - 80) / 365.0 * 2 * PI)
            val weather = 0.75 + 0.25 * sin(d.dayOfMonth * 1.7)
            production += ProductionEntry(d.toString(), d.toString(), Math.round(30 * season * weather * 10) / 10.0)
            d = d.plusDays(1)
        }
        val bills = (0 until 12).map { i ->
            val m = YearMonth.of(2025, 11).plusMonths(i.toLong())
            val kwh = 260.0 + 140 * sin((i + 1) / 12.0 * 2 * PI + PI / 2)
            Bill(m.toString(), m.toString(), Math.round(kwh).toDouble(), Math.round((kwh * 0.21 + 17.3) * 100) / 100.0, 17.34)
        }
        return AppData(
            system = SolarSystem(
                investment = 7500.0,
                installDate = "2025-11-01",
                capacityKwp = 6.0,
                extraInvestments = listOf(ExtraInvestment("b", "Battery 16 kWh", 0.0, "2026-11-09")).filter { it.amount > 0 },
            ),
            tariffs = Tariffs(gridPricePerKwh = 0.21, feedInPricePerKwh = 0.05, selfConsumptionPercent = 65.0),
            currencyCode = "EUR",
            production = production,
            bills = bills,
        )
    }

    private fun save(name: String) {
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun screens() {
        val repo = DataRepository.get(ApplicationProvider.getApplicationContext())
        kotlinx.coroutines.runBlocking { repo.update { sampleData() } }

        compose.setContent { PvTheme { PvApp() } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        save("1-overview")

        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Cumulative savings"))
        compose.mainClock.advanceTimeBy(1500)
        save("1b-payback")

        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Month by month"))
        compose.mainClock.advanceTimeBy(1500)
        save("1c-monthly")

        compose.onNodeWithText("Production").performClick()
        compose.mainClock.advanceTimeBy(1000)
        save("2-production")

        compose.onNodeWithText("Bills").performClick()
        compose.mainClock.advanceTimeBy(1000)
        save("3-bills")

        compose.onNodeWithText("Settings").performClick()
        compose.mainClock.advanceTimeBy(1000)
        save("4-settings")

        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Connect DeyeCloud"))
        compose.mainClock.advanceTimeBy(1000)
        save("5-deye-section")
    }

    @Test
    fun darkScreens() {
        val repo = DataRepository.get(ApplicationProvider.getApplicationContext())
        kotlinx.coroutines.runBlocking { repo.update { sampleData().copy(themeMode = "DARK") } }

        compose.setContent { PvTheme(darkTheme = true) { PvApp() } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        save("dark-1-overview")

        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Cumulative savings"))
        compose.mainClock.advanceTimeBy(1500)
        save("dark-2-payback")

        compose.onNodeWithText("Production").performClick()
        compose.mainClock.advanceTimeBy(1000)
        save("dark-3-production")

        compose.onNodeWithText("Settings").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Appearance"))
        compose.mainClock.advanceTimeBy(1000)
        save("dark-4-settings")
    }
}
