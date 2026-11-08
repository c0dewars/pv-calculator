package io.github.c0dewars.pvcalculator.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SolarPower
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.c0dewars.pvcalculator.R
import io.github.c0dewars.pvcalculator.data.AppData
import io.github.c0dewars.pvcalculator.domain.EstimateBasis
import io.github.c0dewars.pvcalculator.domain.MonthResult
import io.github.c0dewars.pvcalculator.domain.Summary
import io.github.c0dewars.pvcalculator.ui.ScreenScaffold
import io.github.c0dewars.pvcalculator.ui.components.Bar
import io.github.c0dewars.pvcalculator.ui.components.BarChart
import io.github.c0dewars.pvcalculator.ui.components.EmptyState
import io.github.c0dewars.pvcalculator.ui.components.Formatter
import io.github.c0dewars.pvcalculator.ui.components.InfoRow
import io.github.c0dewars.pvcalculator.ui.components.PaybackChart
import io.github.c0dewars.pvcalculator.ui.components.SectionCard
import io.github.c0dewars.pvcalculator.ui.components.SplitBar
import io.github.c0dewars.pvcalculator.ui.components.StatTile
import io.github.c0dewars.pvcalculator.ui.theme.PvColors
import io.github.c0dewars.pvcalculator.ui.theme.SunGradient

@Composable
fun OverviewScreen(
    summary: Summary,
    data: AppData,
    fmt: Formatter,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onOpenProduction: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.overview_title), snackbar = snackbar) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (data.system.investment <= 0) {
                item { SetupCard(onOpenSettings) }
            }
            item { HeroCard(summary, fmt) }
            if (summary.hasData || summary.estimatedAnnualSavings > 0) {
                item { StatsGrid(summary, fmt) }
            }
            if (summary.investment > 0 && summary.estimatedAnnualSavings > 0) {
                item { PaybackCard(summary, fmt) }
            }
            if (summary.hasData) {
                item { MonthlyChartCard(summary.months, fmt) }
                item {
                    Text(
                        stringResource(R.string.month_by_month),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                items(summary.months.reversed(), key = { it.month.toString() }) { MonthCard(it, fmt) }
            } else {
                item {
                    EmptyState(
                        icon = Icons.Rounded.SolarPower,
                        title = stringResource(R.string.empty_overview_title),
                        message = stringResource(R.string.empty_overview_message),
                    ) {
                        Button(onClick = onOpenProduction) { Text(stringResource(R.string.add_production)) }
                    }
                }
            }
            item { HowItWorksCard() }
        }
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
            Step(1, stringResource(R.string.setup_step_1))
            Step(2, stringResource(R.string.setup_step_2))
            Step(3, stringResource(R.string.setup_step_3))
            Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.setup_button)) }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Text(n.toString(), style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HeroCard(summary: Summary, fmt: Formatter) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(SunGradient)
            .padding(24.dp),
    ) {
        Icon(
            Icons.Rounded.WbSunny,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.16f),
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.TopEnd)
                .padding(start = 40.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.total_saved), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleSmall)
            Text(fmt.money(summary.savedSoFar), color = Color.White, style = MaterialTheme.typography.displaySmall)
            if (summary.estimatedUntrackedSavings > 0.5) {
                Text(
                    stringResource(R.string.includes_estimated, fmt.moneySmart(summary.estimatedUntrackedSavings)),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (summary.investment > 0) {
                LinearProgressIndicator(
                    progress = { summary.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.28f),
                    drawStopIndicator = {},
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        if (summary.isPaidOff) stringResource(R.string.paid_off)
                        else stringResource(R.string.recovered_percent, fmt.percent(summary.progress * 100.0)),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.of_investment, fmt.moneySmart(summary.investment)),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            } else {
                Text(stringResource(R.string.set_investment_hint), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatsGrid(summary: Summary, fmt: Formatter) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                Icons.Rounded.Savings, stringResource(R.string.avg_per_month), fmt.money(summary.averageMonthlySavings),
                PvColors.savings, Modifier.weight(1f),
            )
            StatTile(
                Icons.AutoMirrored.Rounded.TrendingUp, stringResource(R.string.per_year), fmt.moneySmart(summary.estimatedAnnualSavings),
                PvColors.export, Modifier.weight(1f), caption = stringResource(R.string.estimated),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                Icons.Rounded.Timelapse, stringResource(R.string.payback_period),
                summary.paybackYears?.let { stringResource(R.string.years_value, fmt.years(it)) } ?: "—",
                PvColors.solar, Modifier.weight(1f),
            )
            StatTile(
                Icons.Rounded.WbSunny, stringResource(R.string.produced), fmt.kwh(summary.totalProductionKwh),
                PvColors.solar, Modifier.weight(1f),
                caption = if (summary.hasData) stringResource(R.string.per_kwh_saved, fmt.pricePerKwh(summary.savingsPerKwh)) else null,
            )
        }
    }
}

@Composable
private fun PaybackCard(summary: Summary, fmt: Formatter) {
    SectionCard(title = stringResource(R.string.payback_title), icon = Icons.Rounded.CalendarMonth) {
        val years = summary.paybackYears
        val date = summary.paybackDate
        val text = when {
            summary.isPaidOff -> stringResource(R.string.payback_done)
            years != null && date != null -> stringResource(
                R.string.payback_sentence, fmt.years(years), fmt.month(java.time.YearMonth.from(date)),
            )
            years != null -> stringResource(R.string.payback_sentence_no_date, fmt.years(years))
            else -> stringResource(R.string.payback_not_reached)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (!summary.isPaidOff && summary.remaining > 0) {
            Text(
                stringResource(R.string.remaining_to_recover, fmt.moneySmart(summary.remaining)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val yearAxis = stringResource(R.string.year_axis)
        PaybackChart(
            points = summary.projection.map { it.year to it.cumulativeSavings },
            investment = summary.investment,
            paybackYear = summary.paybackYears,
            lineColor = PvColors.savings,
            investmentColor = MaterialTheme.colorScheme.primary,
            yearLabel = { y -> if (y == 0) "0" else yearAxis.format(y) },
            moneyLabel = { fmt.moneyRound(it) },
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(PvColors.savings, stringResource(R.string.legend_savings))
            Legend(MaterialTheme.colorScheme.primary, stringResource(R.string.legend_investment))
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(
            stringResource(R.string.in_n_years, summary.lifetimeYears),
            style = MaterialTheme.typography.titleSmall,
        )
        InfoRow(stringResource(R.string.lifetime_savings), fmt.moneySmart(summary.lifetimeSavings), bold = true)
        InfoRow(
            stringResource(R.string.net_profit), fmt.moneySmart(summary.netProfit), bold = true,
            valueColor = if (summary.netProfit >= 0) PvColors.savings else MaterialTheme.colorScheme.error,
        )
        summary.roiPercent?.let { InfoRow(stringResource(R.string.roi), fmt.percent(it), bold = true) }
        Text(basisText(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun basisText(summary: Summary): String = when (summary.estimateBasis) {
    EstimateBasis.LAST_12_MONTHS -> stringResource(R.string.basis_12_months)
    EstimateBasis.EXPECTED_PRODUCTION -> stringResource(R.string.basis_expected)
    EstimateBasis.EXTRAPOLATED -> pluralStringResource(R.plurals.basis_extrapolated, summary.months.size, summary.months.size)
    EstimateBasis.NONE -> ""
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthlyChartCard(months: List<MonthResult>, fmt: Formatter) {
    SectionCard(title = stringResource(R.string.monthly_savings), icon = Icons.Rounded.Savings) {
        val last = months.takeLast(12)
        BarChart(
            bars = last.map { Bar(fmt.monthShort(it.month), it.totalSavings) },
            color = PvColors.savings,
            valueLabel = { fmt.moneyRound(it) },
        )
        Text(
            stringResource(R.string.last_months_total, fmt.money(last.sumOf { it.totalSavings })),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthCard(m: MonthResult, fmt: Formatter) {
    var expanded by rememberSaveable(m.month.toString()) { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(fmt.month(m.month), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.produced_value, fmt.kwh(m.productionKwh)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    fmt.money(m.totalSavings),
                    style = MaterialTheme.typography.titleLarge,
                    color = PvColors.savings,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            SplitBar(m.selfConsumedKwh, m.exportedKwh, PvColors.solar, PvColors.export)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(PvColors.solar, stringResource(R.string.self_consumed_value, fmt.kwh(m.selfConsumedKwh)))
                Legend(
                    PvColors.export,
                    stringResource(if (m.exportEstimated) R.string.exported_est_value else R.string.exported_value, fmt.kwh(m.exportedKwh)),
                )
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                InfoRow(stringResource(R.string.savings_self_consumption), fmt.money(m.selfConsumptionSavings))
                InfoRow(stringResource(R.string.export_income), fmt.money(m.exportIncome))
                InfoRow(
                    stringResource(if (m.priceFromBill) R.string.price_from_bill else R.string.price_default),
                    fmt.pricePerKwh(m.gridPrice),
                )
                val bill = m.bill
                if (bill != null) {
                    InfoRow(stringResource(R.string.bill_paid), fmt.money(bill.amount))
                    m.costWithoutSolar?.let {
                        InfoRow(stringResource(R.string.without_solar), fmt.money(it), bold = true)
                    }
                } else {
                    Text(
                        stringResource(R.string.no_bill_for_month),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(
        onClick = { open = !open },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.how_it_works), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            if (open) {
                Text(stringResource(R.string.how_it_works_text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
