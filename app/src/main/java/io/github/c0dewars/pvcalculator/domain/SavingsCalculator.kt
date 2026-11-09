package io.github.c0dewars.pvcalculator.domain

import io.github.c0dewars.pvcalculator.data.AppData
import io.github.c0dewars.pvcalculator.data.Bill
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

/** Savings of a single month. */
data class MonthResult(
    val month: YearMonth,
    val productionKwh: Double,
    val selfConsumedKwh: Double,
    val exportedKwh: Double,
    /** True when the exported energy was estimated from the self-consumption percentage. */
    val exportEstimated: Boolean,
    /** Price per kWh used (from the bill when available). */
    val gridPrice: Double,
    val priceFromBill: Boolean,
    val selfConsumptionSavings: Double,
    val exportIncome: Double,
    val bill: Bill?,
) {
    val totalSavings: Double get() = selfConsumptionSavings + exportIncome

    /** What the bill would have been without panels (only when a bill exists). */
    val costWithoutSolar: Double? get() = bill?.let { it.amount + selfConsumptionSavings }
}

enum class EstimateBasis {
    /** Sum of the most recent 12 months with data. */
    LAST_12_MONTHS,
    /** Expected annual production (from the settings) times the savings per kWh. */
    EXPECTED_PRODUCTION,
    /** Average of the months available, times 12. */
    EXTRAPOLATED,
    NONE,
}

data class ProjectionPoint(val year: Double, val cumulativeSavings: Double)

data class Summary(
    val months: List<MonthResult>,
    val totalProductionKwh: Double,
    /** Savings computed from the recorded months. */
    val recordedSavings: Double,
    /** Estimated savings between the install date and the first recorded month. */
    val estimatedUntrackedSavings: Double,
    val averageMonthlySavings: Double,
    val estimatedAnnualSavings: Double,
    val estimateBasis: EstimateBasis,
    /** Average money saved per kWh produced. */
    val savingsPerKwh: Double,
    val investment: Double,
    val startDate: LocalDate?,
    val paybackYears: Double?,
    val paybackDate: LocalDate?,
    val projection: List<ProjectionPoint>,
    val lifetimeYears: Int,
    val lifetimeSavings: Double,
) {
    val savedSoFar: Double get() = recordedSavings + estimatedUntrackedSavings
    val remaining: Double get() = max(0.0, investment - savedSoFar)
    val progress: Float get() = if (investment > 0) (savedSoFar / investment).coerceIn(0.0, 1.0).toFloat() else 0f
    val isPaidOff: Boolean get() = investment > 0 && savedSoFar >= investment
    val netProfit: Double get() = lifetimeSavings - investment
    val roiPercent: Double? get() = if (investment > 0) netProfit / investment * 100 else null
    val hasData: Boolean get() = months.isNotEmpty()
}

object SavingsCalculator {

    const val LIFETIME_YEARS = 25
    private const val DAYS_PER_YEAR = 365.25

    /** Price per kWh paid on a bill, ignoring its fixed charges. Null if it cannot be derived. */
    fun effectivePrice(bill: Bill): Double? {
        if (bill.importedKwh <= 0) return null
        val variable = bill.amount - (bill.fixedCharges ?: 0.0)
        return (variable / bill.importedKwh).takeIf { it > 0 }
    }

    /**
     * The price per kWh of [saved] (rounded to 4 decimals) when it is the most recent bill among
     * [existing], or null when an older bill was saved or its price can't be derived.
     */
    fun priceFromLatestBill(existing: List<Bill>, saved: Bill): Double? {
        val others = existing.filter { it.id != saved.id && it.month != saved.month }
        if (others.any { it.month > saved.month }) return null
        return effectivePrice(saved)?.let { Math.round(it * 10000) / 10000.0 }
    }

    fun monthResults(data: AppData): List<MonthResult> {
        val t = data.tariffs
        val selfShare = (t.selfConsumptionPercent / 100).coerceIn(0.0, 1.0)
        val entriesByMonth = data.production
            .mapNotNull { e -> parseDate(e.date)?.let { YearMonth.from(it) to e } }
            .groupBy({ it.first }, { it.second })
        val billsByMonth = data.bills.mapNotNull { b -> parseMonth(b.month)?.let { it to b } }.toMap()

        return entriesByMonth.keys.sorted().map { month ->
            val entries = entriesByMonth.getValue(month)
            val produced = entries.sumOf { it.kwh }
            val bill = billsByMonth[month]
            val billPrice = bill?.let(::effectivePrice)
            val price = billPrice ?: t.gridPricePerKwh
            // Export: from the bill, else from the monitoring app, else estimated from the self-consumption %.
            // Energy stored in a battery and used later counts as self-consumed.
            val exportFromBill = bill?.exportedKwh
            val exportEstimated = exportFromBill == null && entries.any { it.exportedKwh == null }
            val exported = (exportFromBill ?: entries.sumOf { it.exportedKwh ?: (it.kwh * (1 - selfShare)) })
                .coerceIn(0.0, produced)
            val self = produced - exported
            MonthResult(
                month = month,
                productionKwh = produced,
                selfConsumedKwh = self,
                exportedKwh = exported,
                exportEstimated = exportEstimated,
                gridPrice = price,
                priceFromBill = billPrice != null,
                selfConsumptionSavings = self * price,
                exportIncome = exported * t.feedInPricePerKwh,
                bill = bill,
            )
        }
    }

    fun summarize(data: AppData, today: LocalDate = LocalDate.now()): Summary {
        val t = data.tariffs
        val months = monthResults(data)
        val recorded = months.sumOf { it.totalSavings }
        val production = months.sumOf { it.productionKwh }

        val selfShare = (t.selfConsumptionPercent / 100).coerceIn(0.0, 1.0)
        val defaultRate = selfShare * t.gridPricePerKwh + (1 - selfShare) * t.feedInPricePerKwh
        val rate = if (production > 0) recorded / production else defaultRate
        val expected = data.system.expectedAnnualProductionKwh?.takeIf { it > 0 }

        val (annual, basis) = when {
            months.size >= 12 -> months.takeLast(12).sumOf { it.totalSavings } to EstimateBasis.LAST_12_MONTHS
            expected != null -> rate * expected to EstimateBasis.EXPECTED_PRODUCTION
            months.isNotEmpty() -> recorded / months.size * 12 to EstimateBasis.EXTRAPOLATED
            else -> 0.0 to EstimateBasis.NONE
        }

        val installDate = data.system.installDate?.let(::parseDate)
        val firstMonthStart = months.firstOrNull()?.month?.atDay(1)
        val startDate = installDate ?: firstMonthStart

        // Time between installation and the first recorded month is estimated from the annual savings.
        val untrackedYears = if (installDate != null && firstMonthStart != null && installDate < firstMonthStart) {
            ChronoUnit.DAYS.between(installDate, firstMonthStart) / DAYS_PER_YEAR
        } else 0.0
        val untracked = annual * untrackedYears

        // "Now" is the end of the last recorded month (so the numbers match the data), or today without data.
        val reference = months.lastOrNull()?.month?.atEndOfMonth()?.plusDays(1) ?: today
        val elapsedYears = startDate?.let { max(0.0, ChronoUnit.DAYS.between(it, reference) / DAYS_PER_YEAR) } ?: 0.0

        val growth = (1 + t.annualPriceIncreasePercent / 100) * (1 - t.panelDegradationPercent / 100)
        val investment = data.system.totalInvestment
        val saved = recorded + untracked

        val paybackYears: Double? = when {
            investment <= 0 -> null
            saved >= investment -> elapsedYears // already paid off
            annual <= 0 -> null
            else -> yearsUntil(investment - saved, annual, growth, elapsedYears)?.let { elapsedYears + it }
        }
        val paybackDate = if (paybackYears != null && startDate != null) {
            startDate.plusDays((paybackYears * DAYS_PER_YEAR).toLong())
        } else null

        val lifetime = max(LIFETIME_YEARS, paybackYears?.let { kotlin.math.ceil(it).toInt() + 2 } ?: 0)
            .coerceAtMost(40)
        val projection = buildProjection(saved, elapsedYears, annual, growth, lifetime)
        val lifetimeSavings = projection.firstOrNull { it.year >= LIFETIME_YEARS }?.cumulativeSavings
            ?: projection.lastOrNull()?.cumulativeSavings ?: 0.0

        return Summary(
            months = months,
            totalProductionKwh = production,
            recordedSavings = recorded,
            estimatedUntrackedSavings = untracked,
            averageMonthlySavings = if (months.isNotEmpty()) recorded / months.size else annual / 12,
            estimatedAnnualSavings = annual,
            estimateBasis = basis,
            savingsPerKwh = rate,
            investment = investment,
            startDate = startDate,
            paybackYears = paybackYears,
            paybackDate = paybackDate,
            projection = projection,
            lifetimeYears = LIFETIME_YEARS,
            lifetimeSavings = lifetimeSavings,
        )
    }

    /** Savings in year [index] (0-based) since installation, given the first-year savings. */
    private fun savingsOfYear(firstYear: Double, growth: Double, index: Int) = firstYear * growth.pow(index)

    /**
     * Years needed, starting at [fromYear] (years since installation), to accumulate [amount]
     * when the first year saves [firstYear] and every following year changes by [growth].
     */
    internal fun yearsUntil(amount: Double, firstYear: Double, growth: Double, fromYear: Double): Double? {
        var remaining = amount
        var t = fromYear
        var years = 0.0
        repeat(100) {
            val index = floor(t).toInt()
            val perYear = savingsOfYear(firstYear, growth, index)
            if (perYear <= 0) return null
            val portion = (index + 1) - t
            val segment = perYear * portion
            if (segment >= remaining) return years + remaining / perYear
            remaining -= segment
            years += portion
            t = (index + 1).toDouble()
        }
        return null
    }

    private fun buildProjection(
        saved: Double,
        elapsedYears: Double,
        firstYear: Double,
        growth: Double,
        lifetime: Int,
    ): List<ProjectionPoint> {
        val points = mutableListOf(ProjectionPoint(0.0, 0.0))
        if (elapsedYears > 0) points += ProjectionPoint(elapsedYears, saved)
        var cumulative = saved
        var t = elapsedYears
        while (t < lifetime) {
            val index = floor(t).toInt()
            val next = (index + 1).toDouble()
            cumulative += savingsOfYear(firstYear, growth, index) * (next - t)
            t = next
            points += ProjectionPoint(t, cumulative)
        }
        return points
    }

    fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()
    fun parseMonth(s: String): YearMonth? = runCatching { YearMonth.parse(s) }.getOrNull()
}
