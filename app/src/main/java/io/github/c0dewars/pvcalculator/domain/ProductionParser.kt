package io.github.c0dewars.pvcalculator.domain

import io.github.c0dewars.pvcalculator.data.ProductionEntry
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** A piece of recognised text and its position on the screen (OCR). Text from PDFs has no position. */
data class OcrToken(
    val text: String,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    val centerY: Float get() = (top + bottom) / 2
    val height: Float get() = bottom - top
}

/** Groups OCR tokens into visual rows (same height on screen), ordered left to right. */
object RowBuilder {
    fun rows(tokens: List<OcrToken>): List<List<OcrToken>> {
        val rows = mutableListOf<MutableList<OcrToken>>()
        for (t in tokens.filter { it.text.isNotBlank() }.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            if (row != null) {
                val rowCenter = row.map { it.centerY }.average().toFloat()
                val rowHeight = row.map { it.height }.average().toFloat()
                if (abs(t.centerY - rowCenter) < 0.5f * minOf(t.height, rowHeight).coerceAtLeast(1f)) {
                    row += t
                    continue
                }
            }
            rows += mutableListOf(t)
        }
        return rows.map { r -> r.sortedBy { it.left } }
    }

    /** Plain text (one token per line), e.g. from a PDF. */
    fun fromText(text: String): List<List<OcrToken>> =
        TextParsing.normalize(text).lines().filter { it.isNotBlank() }.map { listOf(OcrToken(it.trim())) }

    fun toText(rows: List<List<OcrToken>>): String = rows.joinToString("\n") { r -> r.joinToString("  ") { it.text } }
}

/** One energy value found in a screenshot, with its label and what it most likely means. */
data class EnergyCandidate(val kwh: Double, val label: String, val role: Role) {
    enum class Role { PRODUCTION, PV, EXPORT, IMPORT, CONSUMPTION, CHARGE, DISCHARGE, OTHER, UNKNOWN }
}

data class ProductionParseResult(
    val productionKwh: Double? = null,
    val exportedKwh: Double? = null,
    val importedKwh: Double? = null,
    val consumptionKwh: Double? = null,
    val batteryChargedKwh: Double? = null,
    val batteryDischargedKwh: Double? = null,
    /** Day the values refer to, when the screen shows one. */
    val date: LocalDate? = null,
    /** Month shown on the screen. */
    val month: YearMonth? = null,
    val candidates: List<EnergyCandidate> = emptyList(),
) {
    /** Values look like a monthly total (a month is shown but no specific day). */
    val isWholeMonth: Boolean get() = date == null && month != null
    val foundAnything: Boolean get() = productionKwh != null || exportedKwh != null
}

/**
 * Reads energy values from screenshots of solar monitoring apps (Solarman, Deye, FusionSolar,
 * SolarEdge, Growatt, ...). Labels may be next to the value or right above it (column layouts).
 */
object ProductionParser {

    private val energyRegex = Regex(
        """(?<![\d.,])(\d{1,3}(?:[ .]\d{3})+(?:,\d{1,4})?|\d{1,3}(?:,\d{3})+(?:\.\d{1,4})?|\d+(?:[.,]\d{1,4})?)\s?([kKmM]?)\s?[wW][hH](?![a-zA-Z/])""",
    )
    private val monthDay = Regex("""(?<![\d/.-])(\d{1,2})[/-](\d{1,2})(?![\d/.-])""")

    private data class Found(val kwh: Double, val row: Int, val label: String, val role: EnergyCandidate.Role)

    fun parse(text: String): ProductionParseResult = parseRows(RowBuilder.fromText(text))

    fun parseRows(rows: List<List<OcrToken>>): ProductionParseResult {
        val allText = RowBuilder.toText(rows)
        val screenMonth = TextParsing.findMonthYear(allText)
        val found = mutableListOf<Found>()

        rows.forEachIndexed { r, row ->
            // Join tokens so values split by OCR ("7.60" + "kWh") are still recognised.
            val ranges = mutableListOf<Pair<IntRange, OcrToken>>()
            val sb = StringBuilder()
            row.forEach { t ->
                if (sb.isNotEmpty()) sb.append(" ")
                val start = sb.length
                sb.append(TextParsing.normalize(t.text))
                ranges += (start until sb.length) to t
            }
            val line = sb.toString()
            var prevEnd = 0
            energyRegex.findAll(line).forEach { m ->
                val number = TextParsing.parseNumber(m.groupValues[1]) ?: return@forEach
                val kwh = when (m.groupValues[2].lowercase()) {
                    "k" -> number
                    "m" -> number * 1000
                    else -> number / 1000 // Wh
                }
                val token = ranges.firstOrNull { m.range.first in it.first }?.second
                    ?: ranges.lastOrNull { it.first.first <= m.range.first }?.second
                var label = cleanLabel(line.substring(prevEnd, m.range.first))
                if (label.isBlank() && token != null) label = labelAbove(rows, r, token)
                found += Found(kwh, r, label, roleOf(label))
                prevEnd = m.range.last + 1
            }
        }

        fun best(vararg roles: EnergyCandidate.Role, positive: Boolean): Found? {
            for (role in roles) {
                found.firstOrNull { it.role == role && (!positive || it.kwh > 0) }?.let { return it }
            }
            return null
        }

        val production = best(EnergyCandidate.Role.PRODUCTION, EnergyCandidate.Role.PV, positive = true)
            ?: found.filter { it.role == EnergyCandidate.Role.UNKNOWN && it.kwh > 0 }.takeIf { it.size == 1 }?.first()
        val date = production?.let { dateNear(rows, it.row, screenMonth) }
        val month = date?.let(YearMonth::from) ?: screenMonth

        return ProductionParseResult(
            productionKwh = production?.kwh,
            exportedKwh = best(EnergyCandidate.Role.EXPORT, positive = false)?.kwh,
            importedKwh = best(EnergyCandidate.Role.IMPORT, positive = false)?.kwh,
            consumptionKwh = best(EnergyCandidate.Role.CONSUMPTION, positive = true)?.kwh,
            batteryChargedKwh = best(EnergyCandidate.Role.CHARGE, positive = false)?.kwh,
            batteryDischargedKwh = best(EnergyCandidate.Role.DISCHARGE, positive = false)?.kwh,
            date = date,
            month = month,
            candidates = found.filter { it.kwh > 0 }.distinctBy { it.kwh to it.role }
                .map { EnergyCandidate(it.kwh, it.label.take(30), it.role) },
        )
    }

    private fun cleanLabel(s: String): String =
        s.replace(Regex("""[\d.,%:()\[\]|•·\-]+"""), " ").replace(Regex("\\s+"), " ").trim()
            .takeIf { it.any(Char::isLetter) }.orEmpty()

    /** Label written above the value (same column) within the two previous rows. */
    private fun labelAbove(rows: List<List<OcrToken>>, r: Int, token: OcrToken): String {
        for (above in (r - 1) downTo maxOf(0, r - 2)) {
            val candidates = rows[above].filter { cleanLabel(it.text).isNotBlank() && !energyRegex.containsMatchIn(it.text) }
            if (candidates.isEmpty()) continue
            val hasPosition = token.right > 0
            val pick = if (hasPosition) {
                candidates.filter { it.left <= token.right }.minByOrNull { abs(it.left - token.left) }
            } else candidates.singleOrNull()
            if (pick != null) return cleanLabel(pick.text)
        }
        return ""
    }

    private fun roleOf(label: String): EnergyCandidate.Role {
        val f = TextParsing.fold(label)
        fun has(vararg k: String) = k.any { f.contains(it) }
        val words = f.split(Regex("[^a-z]+"))
        return when {
            f.isBlank() -> EnergyCandidate.Role.UNKNOWN
            has("generator", "gerador", "generador", "co2", "tree", "arvore", "income", "revenue", "receita") ||
                "gti" in words -> EnergyCandidate.Role.OTHER
            has("discharg", "descarga", "descarreg", "entlad") -> EnergyCandidate.Role.DISCHARGE
            has("charg", "carregad", "carregament", "cargad", "laden") -> EnergyCandidate.Role.CHARGE
            has("export", "feed", "injec", "injet", "inyec", "vendid", "sold", "to grid", "para a rede", "einspeis", "venda") ->
                EnergyCandidate.Role.EXPORT
            has("import", "purchas", "comprad", "bought", "from grid", "da rede", "bezug", "compra") -> EnergyCandidate.Role.IMPORT
            has("consum", "load", "carga", "verbrauch", "utiliz", "home", "casa") -> EnergyCandidate.Role.CONSUMPTION
            has("produc", "produz", "yield", "generat", "gerac", "generac", "rendimento", "ertrag", "erzeug") ->
                EnergyCandidate.Role.PRODUCTION
            "pv" in words || has("solar", "fotovolt", "photovolt") -> EnergyCandidate.Role.PV
            else -> EnergyCandidate.Role.UNKNOWN
        }
    }

    /** The date shown closest above (or on) the row of the value. */
    private fun dateNear(rows: List<List<OcrToken>>, row: Int, screenMonth: YearMonth?): LocalDate? {
        for (r in row downTo maxOf(0, row - 4)) {
            rowDate(rows[r].joinToString("  ") { it.text }, screenMonth)?.let { return it }
        }
        return null
    }

    private fun rowDate(text: String, screenMonth: YearMonth?): LocalDate? {
        val full = TextParsing.findDates(text).mapNotNull { d -> d.year?.let { d.toLocalDate(it) } }
        if (full.size == 1) return full.first()
        if (full.isNotEmpty() || screenMonth == null) return null

        // Short dates like "11/07" or "11-09" (month/day or day/month) on a screen showing a month.
        val days = monthDay.findAll(text).mapNotNull { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            when {
                a == screenMonth.monthValue && b in 1..31 -> b
                b == screenMonth.monthValue && a in 1..31 -> a
                else -> null
            }
        }.toList()
        val day = when {
            days.size == 1 -> days.first()
            days.size >= 3 -> oddOneOut(days)
            else -> null
        } ?: return null
        return runCatching { screenMonth.atDay(day) }.getOrNull()
    }

    /**
     * Chart axes show evenly spaced days (01, 05, 09, ...). A highlighted day (tooltip) drawn on the
     * same row breaks the pattern, so it is the one selected.
     */
    private fun oddOneOut(days: List<Int>): Int? {
        val sorted = days.distinct().sorted()
        if (sorted.size < 3) return null
        val step = sorted.zipWithNext { a, b -> b - a }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return null
        if (step <= 1) return null
        val base = sorted.groupingBy { it % step }.eachCount().maxByOrNull { it.value }?.key ?: return null
        return sorted.filter { it % step != base }.singleOrNull()
    }
}

/** Imports production from CSV files exported by monitoring portals: `date;kWh` per line. */
object CsvImporter {

    data class Result(val entries: List<ProductionEntry>, val skippedLines: Int)

    fun parse(text: String, newId: () -> String): Result {
        val entries = mutableListOf<ProductionEntry>()
        var skipped = 0
        text.lineSequence().map { it.trim().trim('﻿') }.filter { it.isNotEmpty() }.forEach { line ->
            val parsed = parseLine(line)
            if (parsed == null) {
                skipped++
            } else {
                entries += ProductionEntry(newId(), parsed.date.toString(), parsed.kwh, exportedKwh = parsed.exported)
            }
        }
        // One value per day: the last one wins.
        val unique = entries.associateBy { it.date }.values.sortedBy { it.date }
        return Result(unique, skipped)
    }

    private data class Line(val date: LocalDate, val kwh: Double, val exported: Double?)

    private fun parseLine(line: String): Line? {
        val cells = when {
            ';' in line -> line.split(';')
            '\t' in line -> line.split('\t')
            else -> {
                val parts = line.split(',')
                // "2026-01-05,12,5" → date, "12,5"
                if (parts.size == 3 && parts[2].trim().length <= 3 && parts[1].trim().all(Char::isDigit)) {
                    listOf(parts[0], parts[1] + "," + parts[2])
                } else parts
            }
        }.map { it.trim().trim('"') }
        if (cells.size < 2) return null
        val d = TextParsing.findDates(cells[0]).firstOrNull() ?: return null
        val date = d.year?.let { d.toLocalDate(it) } ?: return null
        fun num(cell: String?) = cell?.let { TextParsing.parseInput(it.replace(Regex("(?i)\\s*kwh"), "")) }
        val value = num(cells[1]) ?: return null
        if (value < 0) return null
        return Line(date, value, num(cells.getOrNull(2))?.takeIf { it >= 0 })
    }
}
