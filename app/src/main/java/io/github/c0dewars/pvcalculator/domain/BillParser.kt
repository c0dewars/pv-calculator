package io.github.c0dewars.pvcalculator.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

data class BillParseResult(
    val month: YearMonth? = null,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
    val amount: Double? = null,
    val importedKwh: Double? = null,
    val exportedKwh: Double? = null,
    val fixedCharges: Double? = null,
) {
    val foundAnything: Boolean
        get() = month != null || amount != null || importedKwh != null || exportedKwh != null || fixedCharges != null
}

/**
 * Best-effort extraction of the key values of an electricity bill from its text.
 * Works on text from PDFs or OCR and knows common wording in Portuguese, Spanish, English,
 * French, Italian and German. The user always reviews the values before saving.
 */
object BillParser {

    private val amountKeywords = listOf(
        "total a pagar", "valor a pagar", "montante a pagar", "quanto tenho a pagar", "importe a pagar",
        "amount due", "total due", "balance due", "amount to pay", "total to pay", "please pay",
        "montant a payer", "net a payer", "totale da pagare", "importo da pagare", "zu zahlen", "rechnungsbetrag",
        "a pagar", "total da fatura", "total fatura", "importe total", "total factura", "total amount", "valor total",
    )

    private val exportKeywords = listOf(
        "injec", "injet", "inyec", "excedent", "export", "feed-in", "feed in", "vendid", "energia vendida",
        "sold", "surplus", "einspeis", "immess", "entregue a rede", "fornecida a rede",
    )

    private val fixedKeywords = listOf(
        "potencia", "termo fixo", "termino fijo", "standing charge", "fixed charge", "quota fixa", "cuota fija",
        "abonnement", "daily charge", "supply charge", "grundpreis", "quota potenza", "termine fisso",
    )

    private val consumptionKeywords = listOf(
        "consumo", "consumption", "faturad", "facturad", "billed", "simples", "energia", "energy",
        "total", "consommation", "verbrauch", "used",
    )

    /** Lines with statistics rather than the bill's own consumption. */
    private val ignoreEnergyKeywords = listOf(
        "anual", "annual", "por ano", "per year", "/ano", "media", "average", "emiss", "co2", "inferior a",
        "ultimos 12", "last 12", "historico", "history",
    )

    private val periodKeywords = listOf(
        "periodo de fatura", "periodo de factura", "periodo de consumo", "periodo faturado", "periodo facturado",
        "billing period", "period", "periodo", "periode", "zeitraum", "faturacao", "facturacion",
    )

    private val timeOfUseKeywords = listOf(
        "vazio", "fora de vazio", "cheias", "ponta", "off-peak", "peak", "valle", "llano", "punta", "creuses", "pleines",
    )

    fun parse(text: String, today: LocalDate = LocalDate.now()): BillParseResult {
        val lines = TextParsing.normalize(text).lines().map { it.trim() }.filter { it.isNotEmpty() }
        val folded = lines.map(TextParsing::fold)

        val (start, end) = findPeriod(lines, folded, today) ?: (null to null)
        val month = if (start != null && end != null) {
            YearMonth.from(start.plusDays(ChronoUnit.DAYS.between(start, end) / 2))
        } else null

        val amount = findAmount(lines, folded)
        val exported = findExported(lines, folded)
        val imported = findImported(lines, folded, exported)
        val fixed = findFixed(lines, folded)?.takeIf { amount == null || it < amount }

        return BillParseResult(
            month = month,
            periodStart = start,
            periodEnd = end,
            amount = amount,
            importedKwh = imported,
            exportedKwh = exported,
            fixedCharges = fixed,
        )
    }

    private fun findAmount(lines: List<String>, folded: List<String>): Double? {
        for (keyword in amountKeywords) {
            for (i in lines.indices) {
                val idx = folded[i].indexOf(keyword)
                if (idx < 0) continue
                val sameLine = TextParsing.moneyValues(lines[i].substring(minOf(idx, lines[i].length)))
                    .firstOrNull { it > 0 }
                if (sameLine != null) return sameLine
                for (j in i + 1..minOf(i + 3, lines.lastIndex)) {
                    TextParsing.moneyValues(lines[j]).firstOrNull { it > 0 }?.let { return it }
                }
            }
        }
        return null
    }

    private fun findExported(lines: List<String>, folded: List<String>): Double? {
        val values = mutableListOf<Double>()
        for (i in lines.indices) {
            if (exportKeywords.none { folded[i].contains(it) }) continue
            if (ignoreEnergyKeywords.any { folded[i].contains(it) }) continue
            val same = TextParsing.energyValues(lines[i])
            values += same.ifEmpty { if (i < lines.lastIndex) TextParsing.energyValues(lines[i + 1]) else emptyList() }
        }
        return values.filter { it > 0 }.maxOrNull()
    }

    private fun findImported(lines: List<String>, folded: List<String>, exported: Double?): Double? {
        data class Candidate(val value: Double, var score: Double)

        val candidates = mutableMapOf<Long, Candidate>()
        val timeOfUse = mutableSetOf<Long>()
        for (i in lines.indices) {
            val f = folded[i]
            if (exportKeywords.any { f.contains(it) }) continue
            if (ignoreEnergyKeywords.any { f.contains(it) }) continue
            val values = TextParsing.energyValues(lines[i]).filter { it > 0 }
            val bonus = if (consumptionKeywords.any { f.contains(it) }) 0.5 else 0.0
            for (v in values) {
                val key = (v * 100).roundToLong()
                val c = candidates.getOrPut(key) { Candidate(v, 0.0) }
                c.score += 1.0 + bonus
                if (values.size == 1 && timeOfUseKeywords.any { f.contains(it) }) timeOfUse += key
            }
        }
        if (exported != null) candidates.remove((exported * 100).roundToLong())
        if (candidates.isEmpty()) return null

        // A value that equals the sum of other values is very likely the total consumption.
        val distinct = candidates.values.map { it.value }
        for (c in candidates.values) {
            val others = distinct.filter { it < c.value }
            if (isSumOfSubset(c.value, others)) c.score += 2.0
        }
        // Time-of-use bills (off-peak / peak) without a total line: offer the sum.
        if (timeOfUse.size >= 2) {
            val sum = timeOfUse.sumOf { candidates[it]?.value ?: 0.0 }
            val key = (sum * 100).roundToLong()
            candidates.getOrPut(key) { Candidate(sum, 0.0) }.score += 2.5
        }
        return candidates.values.maxWith(compareBy<Candidate> { it.score }.thenBy { it.value }).value
    }

    private fun isSumOfSubset(target: Double, values: List<Double>): Boolean {
        val v = values.take(12)
        for (a in v.indices) for (b in a + 1 until v.size) {
            if (abs(v[a] + v[b] - target) < 0.6) return true
            for (c in b + 1 until v.size) if (abs(v[a] + v[b] + v[c] - target) < 0.6) return true
        }
        return false
    }

    private fun findFixed(lines: List<String>, folded: List<String>): Double? {
        val values = mutableListOf<Double>()
        for (i in lines.indices) {
            if (fixedKeywords.none { folded[i].contains(it) }) continue
            val same = TextParsing.moneyValues(lines[i])
            if (same.isNotEmpty()) {
                values += same
            } else {
                for (j in i + 1..minOf(i + 1, lines.lastIndex)) values += TextParsing.moneyValues(lines[j])
            }
        }
        return values.filter { it >= 0.5 }.maxOrNull()
    }

    private fun findPeriod(lines: List<String>, folded: List<String>, today: LocalDate): Pair<LocalDate, LocalDate>? {
        // 1) A line introduced by "billing period" or similar.
        for (keyword in periodKeywords) {
            for (i in lines.indices) {
                if (!folded[i].contains(keyword)) continue
                val window = lines[i] + " " + lines.getOrElse(i + 1) { "" }
                resolvePeriod(TextParsing.findDates(window), today)?.let { return it }
            }
        }
        // 2) Any line with two dates that look like a billing period.
        for (line in lines) {
            resolvePeriod(TextParsing.findDates(line), today)?.let { return it }
        }
        return null
    }

    private fun resolvePeriod(dates: List<TextParsing.FoundDate>, today: LocalDate): Pair<LocalDate, LocalDate>? {
        for (k in 0 until dates.size - 1) {
            val a = dates[k]
            val b = dates[k + 1]
            val endYear = b.year ?: a.year ?: today.year
            val end = b.toLocalDate(endYear) ?: continue
            var start = a.toLocalDate(a.year ?: endYear) ?: continue
            if (a.year == null && start > end) start = start.minusYears(1)
            val days = ChronoUnit.DAYS.between(start, end)
            if (days in 7..100) return start to end
        }
        return null
    }
}
