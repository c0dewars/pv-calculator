package io.github.c0dewars.pvcalculator.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Locale-aware formatting of money, energy and dates. */
class Formatter(currencyCode: String, private val locale: Locale = Locale.getDefault()) {

    private val currency: Currency =
        runCatching { Currency.getInstance(currencyCode) }.getOrElse { Currency.getInstance("EUR") }

    private fun currencyFormat(min: Int, max: Int) = NumberFormat.getCurrencyInstance(locale).apply {
        currency = this@Formatter.currency
        minimumFractionDigits = min
        maximumFractionDigits = max
    }

    private val money = currencyFormat(2, 2)
    private val moneyRound = currencyFormat(0, 0)
    private val price = currencyFormat(2, 4)
    private val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    private val integer = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 0 }
    private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    private val monthShortFormat = DateTimeFormatter.ofPattern("LLL", locale)
    private val monthYearShortFormat = DateTimeFormatter.ofPattern("LLL yy", locale)
    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val dayFormat = DateTimeFormatter.ofPattern("EEE, d", locale)

    val currencySymbol: String = currency.getSymbol(locale)

    fun money(v: Double): String = money.format(v)
    fun moneyRound(v: Double): String = moneyRound.format(v)
    /** Money with decimals only when the amount is small. */
    fun moneySmart(v: Double): String = if (kotlin.math.abs(v) >= 1000) moneyRound.format(v) else money.format(v)
    fun pricePerKwh(v: Double): String = price.format(v) + "/kWh"
    fun kwh(v: Double): String = (if (v >= 100) integer.format(v) else number.format(v)) + " kWh"
    fun number(v: Double): String = number.format(v)
    fun percent(v: Double): String = integer.format(v) + "%"
    fun years(v: Double): String = number.format(v)
    fun month(m: YearMonth): String = m.format(monthFormat).replaceFirstChar { it.titlecase(locale) }
    fun monthShort(m: YearMonth): String = m.format(monthShortFormat).trimEnd('.').replaceFirstChar { it.titlecase(locale) }
    fun monthYearShort(m: YearMonth): String = m.format(monthYearShortFormat).replaceFirstChar { it.titlecase(locale) }
    fun date(d: LocalDate): String = d.format(dateFormat)
    fun day(d: LocalDate): String = d.format(dayFormat).replaceFirstChar { it.titlecase(locale) }

    companion object {
        /** Value shown in an editable text field, using the locale's decimal separator. */
        fun input(v: Double?): String {
            if (v == null) return ""
            val plain = BigDecimal(v.toString()).stripTrailingZeros().toPlainString()
            val sep = DecimalFormatSymbols.getInstance().decimalSeparator
            return if (sep == '.') plain else plain.replace('.', sep)
        }
    }
}

@Composable
fun rememberFormatter(currencyCode: String): Formatter = remember(currencyCode) { Formatter(currencyCode) }
