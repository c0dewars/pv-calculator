package io.github.c0dewars.pvcalculator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ProductionParserTest {

    /** Builds OCR tokens: each row is a list of (text, left x). */
    private fun screen(vararg rows: List<Pair<String, Int>>): List<List<OcrToken>> {
        val tokens = rows.flatMapIndexed { i, row ->
            row.map { (text, x) -> OcrToken(text, x.toFloat(), i * 60f, x + text.length * 18f, i * 60f + 40f) }
        }
        return RowBuilder.rows(tokens.shuffled())
    }

    @Test
    fun `daily values with labels next to them`() {
        val rows = screen(
            listOf("Day" to 120, "Month" to 330, "Year" to 560, "Lifetime" to 760),
            listOf("2026-11" to 400),
            listOf("Production" to 100),
            listOf("2026-11-08" to 30),
            listOf("Production" to 80, "4.5 kWh" to 260, "PV" to 520, "4.5 kWh" to 580),
            listOf("GTI" to 80, "0 kWh" to 150, "Generator" to 520, "0 kWh" to 680),
            listOf("(kWh)" to 60),
            listOf("8" to 60),
            listOf("Grid" to 100),
            listOf("2026-11-08" to 30),
            listOf("Import" to 80, "9.5 kWh" to 200, "Export" to 520, "0 kWh" to 640),
        )
        val r = ProductionParser.parseRows(rows)
        assertEquals(4.5, r.productionKwh!!, 1e-9)
        assertEquals(0.0, r.exportedKwh!!, 1e-9)
        assertEquals(9.5, r.importedKwh!!, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 8), r.date)
        assertFalse(r.isWholeMonth)
    }

    @Test
    fun `monthly summary with labels above values`() {
        val rows = screen(
            listOf("2026-11" to 440),
            listOf("Energy Profile" to 70),
            listOf("Time 11-09" to 100),
            listOf("Production" to 128, "0.00kWh" to 322, "Consumption" to 516, "0.00kWh" to 710),
            listOf("Export" to 128, "0.00kWh" to 322, "Import" to 516, "0.00kWh" to 710),
            listOf("kWh" to 100),
            listOf("40" to 110),
            listOf("30" to 110),
            listOf("20" to 110),
            listOf("1" to 170, "3" to 220, "5" to 270, "7" to 320),
            listOf("Self-consumption Ratio" to 80),
            listOf("Utilization" to 80, "117.10kWh" to 734),
            listOf("PV" to 105, "Discharge" to 438, "Import" to 732),
            listOf("22%" to 80, "26.20kWh" to 130, "0kWh" to 412, "78%" to 706, "90.89kWh" to 757),
            listOf("Production" to 80, "30.60kWh" to 752),
            listOf("Consumption" to 105, "Charge" to 468, "Export" to 813),
            listOf("86%" to 80, "26.20kWh" to 130, "14%" to 443, "4.39kWh" to 494, "0kWh" to 787),
        )
        val r = ProductionParser.parseRows(rows)
        assertEquals(30.60, r.productionKwh!!, 1e-9)
        assertEquals(0.0, r.exportedKwh!!, 1e-9)
        assertNull(r.date)
        assertEquals(YearMonth.of(2026, 11), r.month)
        assertTrue(r.isWholeMonth)
    }

    @Test
    fun `inverter history with Wh units and highlighted day on the axis`() {
        val rows = screen(
            listOf("Inverter" to 120),
            listOf("2026-11" to 452),
            listOf("Historical Data" to 102),
            listOf("11/01" to 86, "11/0" to 163, "11/07" to 218, "/09" to 300, "11/13" to 387, "11/17" to 498, "11/21" to 610, "11/25" to 722, "11/29" to 800),
            listOf("Production" to 118, "Grid Feed-in" to 532),
            listOf("7.60" to 118, "kWh" to 200, "0.00" to 532, "Wh" to 614),
            listOf("Consumption" to 118, "Electricity Purchasing" to 532),
            listOf("30.10 kWh" to 118, "23.30 kWh" to 532),
            listOf("Charging Capacity" to 118, "Discharging Capacity" to 532),
            listOf("800.00 Wh" to 118, "0.00 Wh" to 532),
            listOf("Generator Power" to 118),
            listOf("Generation" to 118),
            listOf("0.00 Wh" to 118),
        )
        val r = ProductionParser.parseRows(rows)
        assertEquals(7.60, r.productionKwh!!, 1e-9)
        assertEquals(0.0, r.exportedKwh!!, 1e-9)
        assertEquals(23.30, r.importedKwh!!, 1e-9)
        assertEquals(30.10, r.consumptionKwh!!, 1e-9)
        assertEquals(0.8, r.batteryChargedKwh!!, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 7), r.date)
    }

    @Test
    fun `plain screenshot with a single value`() {
        val r = ProductionParser.parse("Hoje\n23,4 kWh\n07/11/2026")
        assertEquals(23.4, r.productionKwh!!, 1e-9)
    }

    @Test
    fun `csv import with comma decimals and export column`() {
        var n = 0
        val r = CsvImporter.parse("date;production;export\n2026-01-01;12,5;3\n02/01/2026;10\nbad line\n", newId = { "id${n++}" })
        assertEquals(2, r.entries.size)
        assertEquals(2, r.skippedLines)
        assertEquals(12.5, r.entries[0].kwh, 1e-9)
        assertEquals(3.0, r.entries[0].exportedKwh!!, 1e-9)
        assertEquals("2026-01-02", r.entries[1].date)
    }
}
