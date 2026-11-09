package io.github.c0dewars.pvcalculator.domain

import io.github.c0dewars.pvcalculator.data.AppData
import io.github.c0dewars.pvcalculator.data.Bill
import io.github.c0dewars.pvcalculator.data.ExtraInvestment
import io.github.c0dewars.pvcalculator.data.ProductionEntry
import io.github.c0dewars.pvcalculator.data.SolarSystem
import io.github.c0dewars.pvcalculator.data.Tariffs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SavingsCalculatorTest {

    private val tariffs = Tariffs(
        gridPricePerKwh = 0.20,
        feedInPricePerKwh = 0.05,
        selfConsumptionPercent = 50.0,
        annualPriceIncreasePercent = 0.0,
        panelDegradationPercent = 0.0,
    )

    private fun entry(date: String, kwh: Double, exported: Double? = null) =
        ProductionEntry(id = date, date = date, kwh = kwh, exportedKwh = exported)

    @Test
    fun `month without bill uses default price and self consumption share`() {
        val data = AppData(tariffs = tariffs, production = listOf(entry("2026-01-01", 10.0), entry("2026-01-02", 10.0)))
        val m = SavingsCalculator.monthResults(data).single()
        assertEquals(20.0, m.productionKwh, 1e-9)
        assertEquals(10.0, m.selfConsumedKwh, 1e-9)
        assertEquals(10.0, m.exportedKwh, 1e-9)
        assertTrue(m.exportEstimated)
        // 10 kWh * 0.20 + 10 kWh * 0.05
        assertEquals(2.5, m.totalSavings, 1e-9)
    }

    @Test
    fun `bill price excludes fixed charges and bill export wins`() {
        val bill = Bill("b", "2026-01", importedKwh = 100.0, amount = 40.0, fixedCharges = 10.0, exportedKwh = 5.0)
        val data = AppData(tariffs = tariffs, production = listOf(entry("2026-01-10", 20.0)), bills = listOf(bill))
        val m = SavingsCalculator.monthResults(data).single()
        assertEquals(0.30, m.gridPrice, 1e-9)
        assertEquals(5.0, m.exportedKwh, 1e-9)
        assertEquals(15.0 * 0.30 + 5.0 * 0.05, m.totalSavings, 1e-9)
        assertEquals(40.0 + 15.0 * 0.30, m.costWithoutSolar!!, 1e-9)
    }

    @Test
    fun `export reported by the inverter replaces the estimate (battery case)`() {
        val data = AppData(tariffs = tariffs, production = listOf(entry("2026-11-07", 7.6, exported = 0.0)))
        val m = SavingsCalculator.monthResults(data).single()
        assertEquals(7.6, m.selfConsumedKwh, 1e-9)
        assertEquals(false, m.exportEstimated)
    }

    @Test
    fun `payback with constant savings`() {
        // 12 months, 100 kWh each, all self consumed at 0.20 => 20/month => 240/year
        val production = (1..12).map { entry(YearMonth.of(2025, it).atDay(1).toString(), 100.0, exported = 0.0) }
        val data = AppData(
            tariffs = tariffs,
            system = SolarSystem(investment = 1200.0, installDate = "2025-01-01"),
            production = production,
        )
        val s = SavingsCalculator.summarize(data, LocalDate.of(2026, 1, 1))
        assertEquals(EstimateBasis.LAST_12_MONTHS, s.estimateBasis)
        assertEquals(240.0, s.estimatedAnnualSavings, 1e-9)
        assertEquals(240.0, s.savedSoFar, 1e-9)
        assertEquals(5.0, s.paybackYears!!, 0.01)
        assertEquals(LocalDate.of(2030, 1, 1), s.paybackDate!!.withDayOfMonth(1))
    }

    @Test
    fun `extra investments such as a battery are added to the investment`() {
        val system = SolarSystem(investment = 5000.0, extraInvestments = listOf(ExtraInvestment("x", "Battery", 3000.0, "2026-11-09")))
        val s = SavingsCalculator.summarize(AppData(system = system, tariffs = tariffs))
        assertEquals(8000.0, s.investment, 1e-9)
    }

    @Test
    fun `price increase shortens payback`() {
        val production = (1..12).map { entry(YearMonth.of(2025, it).atDay(1).toString(), 100.0, exported = 0.0) }
        val base = AppData(tariffs = tariffs, system = SolarSystem(investment = 3000.0), production = production)
        val flat = SavingsCalculator.summarize(base).paybackYears!!
        val growing = SavingsCalculator.summarize(base.copy(tariffs = tariffs.copy(annualPriceIncreasePercent = 5.0))).paybackYears!!
        assertTrue(growing < flat)
    }

    @Test
    fun `untracked time since installation is estimated`() {
        val production = (1..12).map { entry(YearMonth.of(2025, it).atDay(1).toString(), 100.0, exported = 0.0) }
        val data = AppData(tariffs = tariffs, system = SolarSystem(investment = 10000.0, installDate = "2024-01-01"), production = production)
        val s = SavingsCalculator.summarize(data)
        assertEquals(240.0, s.estimatedUntrackedSavings, 1.0)
    }

    @Test
    fun `latest bill price becomes the default price`() {
        val sept = Bill("s", "2026-09", importedKwh = 400.0, amount = 90.0, fixedCharges = 17.34)
        val oct = Bill("o", "2026-10", importedKwh = 503.0, amount = 108.10, fixedCharges = 17.34)
        // (108.10 - 17.34) / 503 = 0.18044...
        assertEquals(0.1804, SavingsCalculator.priceFromLatestBill(listOf(sept), oct)!!, 1e-9)
        // Saving an older bill doesn't change the default price.
        assertNull(SavingsCalculator.priceFromLatestBill(listOf(oct), sept))
        // Editing the latest bill updates it.
        assertEquals(0.1804, SavingsCalculator.priceFromLatestBill(listOf(sept, oct), oct.copy(amount = 108.10))!!, 1e-9)
        // No consumption: price can't be derived.
        assertNull(SavingsCalculator.priceFromLatestBill(emptyList(), oct.copy(importedKwh = 0.0)))
    }

    @Test
    fun `no data and no investment`() {
        val s = SavingsCalculator.summarize(AppData())
        assertNull(s.paybackYears)
        assertEquals(EstimateBasis.NONE, s.estimateBasis)
    }

    @Test
    fun `expected production gives an estimate before any data`() {
        val data = AppData(tariffs = tariffs, system = SolarSystem(investment = 1000.0, expectedAnnualProductionKwh = 2000.0))
        val s = SavingsCalculator.summarize(data)
        assertEquals(EstimateBasis.EXPECTED_PRODUCTION, s.estimateBasis)
        // 2000 kWh * (0.5 * 0.20 + 0.5 * 0.05)
        assertEquals(250.0, s.estimatedAnnualSavings, 1e-9)
        assertNotNull(s.paybackYears)
        assertEquals(4.0, s.paybackYears!!, 0.01)
    }
}
