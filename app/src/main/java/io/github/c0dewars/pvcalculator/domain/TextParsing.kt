package io.github.c0dewars.pvcalculator.domain

import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth

/**
 * Locale-agnostic helpers to find numbers, amounts of money, energy values and dates in free text
 * (PDF text or OCR output). Supports Portuguese, English, Spanish, French, Italian and German month names.
 */
object TextParsing {

    /** Number with optional thousands separators and decimals, e.g. 1.234,56 · 1,234.56 · 12 345,6 · 0,1671 · 503. */
    private const val NUMBER =
        """(?<![\d.,])(\d{1,3}(?:[ .]\d{3})+(?:,\d{1,4})?|\d{1,3}(?:,\d{3})+(?:\.\d{1,4})?|\d+(?:[.,]\d{1,4})?)(?![\d])"""

    private val moneySuffix = Regex("""$NUMBER\s?(?:€|EUR\b|eur\b|\$|£|USD\b|GBP\b|BRL\b|CHF\b|zł|PLN\b|kr\b)""")
    private val moneyPrefix = Regex("""(?:€|R\$|\$|£|EUR|USD|GBP|BRL|CHF)\s?$NUMBER(?!\s?%)""")
    private val energy = Regex("""$NUMBER\s?([kKmM])\s?[wW][hH](?![a-zA-Z/])""")

    /** Parses a number written with either decimal convention. */
    fun parseNumber(raw: String): Double? {
        var s = raw.trim().replace(' ', ' ').replace(' ', ' ').replace(" ", "")
        if (s.isEmpty()) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        s = when {
            lastDot >= 0 && lastComma >= 0 -> {
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            }
            lastComma >= 0 -> singleSeparator(s, ',')
            lastDot >= 0 -> singleSeparator(s, '.')
            else -> s
        }
        return s.toDoubleOrNull()
    }

    /** With a single kind of separator: "1.234" / "1,234" are thousands, "0,578" / "17,34" are decimals. */
    private fun singleSeparator(s: String, sep: Char): String {
        val parts = s.split(sep)
        val isThousands = parts.size > 2 ||
            (parts.size == 2 && parts[1].length == 3 && parts[0] != "0" && parts[0].isNotEmpty() && parts[0] != "-0")
        return if (isThousands) parts.joinToString("") else s.replace(sep, '.')
    }

    /** Parses user input in text fields (accepts "12,5" and "12.5"). */
    fun parseInput(raw: String): Double? {
        val s = raw.trim().replace(" ", "")
        if (s.isEmpty()) return null
        // In input fields a single separator is always a decimal separator.
        if (s.count { it == ',' } + s.count { it == '.' } <= 1) return s.replace(',', '.').toDoubleOrNull()
        return parseNumber(s)
    }

    fun normalize(text: String): String =
        text.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')

    fun moneyValues(line: String): List<Double> {
        val n = normalize(line)
        val found = mutableListOf<Pair<Int, Double>>()
        moneySuffix.findAll(n).forEach { m -> parseNumber(m.groupValues[1])?.let { found += m.range.first to it } }
        moneyPrefix.findAll(n).forEach { m ->
            parseNumber(m.groupValues[1])?.let { v -> if (found.none { kotlin.math.abs(it.first - m.range.first) < 3 }) found += m.range.first to v }
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    /** Energy values in kWh found in [line] (MWh converted to kWh). */
    fun energyValues(line: String): List<Double> =
        energy.findAll(normalize(line)).mapNotNull { m ->
            val v = parseNumber(m.groupValues[1]) ?: return@mapNotNull null
            if (m.groupValues[2].equals("m", ignoreCase = true)) v * 1000 else v
        }.toList()

    // ------------------------------------------------------------------ dates

    private val monthNames: Map<String, Int> = buildMap {
        fun add(month: Int, vararg names: String) = names.forEach { put(it, month) }
        add(1, "janeiro", "jan", "january", "enero", "ene", "janvier", "janv", "gennaio", "gen", "januar", "jän")
        add(2, "fevereiro", "fev", "february", "feb", "febrero", "fevrier", "fevr", "febbraio", "februar")
        add(3, "marco", "mar", "march", "marzo", "mars", "marz", "maerz")
        add(4, "abril", "abr", "april", "apr", "avril", "avr", "aprile")
        add(5, "maio", "mai", "may", "mayo", "maggio", "mag")
        add(6, "junho", "jun", "june", "junio", "juin", "giugno", "giu", "juni")
        add(7, "julho", "jul", "july", "julio", "juillet", "juil", "luglio", "lug", "juli")
        add(8, "agosto", "ago", "august", "aug", "aout", "agosto")
        add(9, "setembro", "set", "september", "sep", "sept", "septiembre", "septembre", "settembre")
        add(10, "outubro", "out", "october", "oct", "octubre", "octobre", "ottobre", "ott", "oktober", "okt")
        add(11, "novembro", "nov", "november", "noviembre", "novembre")
        add(12, "dezembro", "dez", "december", "dec", "diciembre", "dic", "decembre", "dicembre", "dezember")
    }

    private fun stripAccents(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun monthOf(word: String): Int? = monthNames[stripAccents(word.lowercase()).trimEnd('.')]

    private val isoDate = Regex("""(?<!\d)(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?!\d)""")
    private val numericDate = Regex("""(?<![\d.,])(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})(?![\d])""")
    private val dayMonthYear = Regex("""(?<!\d)(\d{1,2})(?:º|°|er)?\s*(?:de\s+|of\s+)?([\p{L}]{3,12})\.?(?:,?\s+(?:de\s+)?(\d{4}))?""")
    private val monthDayYear = Regex("""\b([\p{L}]{3,12})\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})""")

    /** A date found in text; [year] may be missing ("25 de setembro"). */
    data class FoundDate(val position: Int, val day: Int, val month: Int, val year: Int?) {
        fun toLocalDate(defaultYear: Int): LocalDate? =
            runCatching { LocalDate.of(year ?: defaultYear, month, day) }.getOrNull()
    }

    fun findDates(text: String): List<FoundDate> {
        val n = normalize(text)
        val found = mutableListOf<FoundDate>()
        val taken = mutableListOf<IntRange>()
        fun add(range: IntRange, d: FoundDate) {
            if (taken.any { it.first <= range.last && range.first <= it.last }) return
            if (d.month !in 1..12 || d.day !in 1..31) return
            taken += range
            found += d
        }
        isoDate.findAll(n).forEach { m ->
            add(m.range, FoundDate(m.range.first, m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()))
        }
        numericDate.findAll(n).forEach { m ->
            var day = m.groupValues[1].toInt()
            var month = m.groupValues[2].toInt()
            if (month > 12 && day <= 12) day = month.also { month = day } // mm/dd/yyyy
            val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            add(m.range, FoundDate(m.range.first, day, month, y))
        }
        monthDayYear.findAll(n).forEach { m ->
            val month = monthOf(m.groupValues[1]) ?: return@forEach
            add(m.range, FoundDate(m.range.first, m.groupValues[2].toInt(), month, m.groupValues[3].toInt()))
        }
        dayMonthYear.findAll(n).forEach { m ->
            val month = monthOf(m.groupValues[2]) ?: return@forEach
            add(m.range, FoundDate(m.range.first, m.groupValues[1].toInt(), month, m.groupValues[3].toIntOrNull()))
        }
        return found.sortedBy { it.position }
    }

    /** "setembro 2026", "Oct 2026", "10/2026", "2026-10" → YearMonth. */
    fun findMonthYear(text: String): YearMonth? {
        val n = normalize(text)
        Regex("""\b([\p{L}]{3,12})\.?\s+(?:de\s+)?(\d{4})\b""").findAll(n).forEach { m ->
            val month = monthOf(m.groupValues[1]) ?: return@forEach
            return YearMonth.of(m.groupValues[2].toInt(), month)
        }
        Regex("""(?<![\d/.-])(\d{1,2})[/.-](\d{4})(?![\d/.-])""").find(n)?.let { m ->
            val month = m.groupValues[1].toInt()
            if (month in 1..12) return YearMonth.of(m.groupValues[2].toInt(), month)
        }
        Regex("""(?<![\d/.-])(\d{4})-(\d{2})(?![\d/.-])""").find(n)?.let { m ->
            val month = m.groupValues[2].toInt()
            if (month in 1..12) return YearMonth.of(m.groupValues[1].toInt(), month)
        }
        return null
    }

    /** Lower-case, accent-free version of [s], handy for keyword matching. */
    fun fold(s: String): String = stripAccents(s.lowercase())
}
