@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.c0dewars.pvcalculator.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.SolarPower
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.c0dewars.pvcalculator.BuildConfig
import io.github.c0dewars.pvcalculator.R
import io.github.c0dewars.pvcalculator.data.AppData
import io.github.c0dewars.pvcalculator.data.DataRepository
import io.github.c0dewars.pvcalculator.data.ExtraInvestment
import io.github.c0dewars.pvcalculator.domain.TextParsing
import io.github.c0dewars.pvcalculator.ui.components.DecimalField
import io.github.c0dewars.pvcalculator.domain.SavingsCalculator
import io.github.c0dewars.pvcalculator.ui.AppViewModel
import io.github.c0dewars.pvcalculator.ui.ScreenScaffold
import io.github.c0dewars.pvcalculator.ui.components.DatePickerModal
import io.github.c0dewars.pvcalculator.ui.components.Formatter
import io.github.c0dewars.pvcalculator.ui.components.NumberSettingField
import io.github.c0dewars.pvcalculator.ui.components.PickerField
import io.github.c0dewars.pvcalculator.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

private val commonCurrencies = listOf(
    "EUR", "USD", "GBP", "BRL", "CHF", "AUD", "CAD", "NZD", "ZAR", "INR", "JPY", "CNY",
    "MXN", "ARS", "CLP", "COP", "PLN", "CZK", "HUF", "RON", "SEK", "NOK", "DKK", "TRY", "AOA", "MZN", "CVE",
)

@Composable
fun SettingsScreen(vm: AppViewModel, data: AppData, fmt: Formatter, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showInstallPicker by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            snackbar.showSnackbar(context.getString(if (vm.exportBackup(uri)) R.string.backup_saved else R.string.backup_failed))
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    ScreenScaffold(title = stringResource(R.string.settings_title), snackbar = snackbar) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --------------------------------------------------------- system
            SectionCard(title = stringResource(R.string.section_system), icon = Icons.Rounded.SolarPower) {
                NumberSettingField(
                    label = stringResource(R.string.investment),
                    value = data.system.investment.takeIf { it > 0 },
                    onValue = { v -> vm.updateSystem { it.copy(investment = v ?: 0.0) } },
                    suffix = fmt.currencySymbol,
                    supportingText = stringResource(R.string.investment_hint),
                )
                ExtraInvestments(vm, data, fmt)
                val install = data.system.installDate?.let(SavingsCalculator::parseDate)
                PickerField(
                    label = stringResource(R.string.install_date),
                    value = install?.let(fmt::date) ?: stringResource(R.string.not_set),
                    onClick = { showInstallPicker = true },
                    leading = Icons.Rounded.Event,
                )
                if (install != null) {
                    TextButton(onClick = { vm.updateSystem { it.copy(installDate = null) } }) {
                        Text(stringResource(R.string.clear_date))
                    }
                }
                NumberSettingField(
                    label = stringResource(R.string.capacity),
                    value = data.system.capacityKwp,
                    onValue = { v -> vm.updateSystem { it.copy(capacityKwp = v) } },
                    suffix = "kWp",
                )
                NumberSettingField(
                    label = stringResource(R.string.expected_production),
                    value = data.system.expectedAnnualProductionKwh,
                    onValue = { v -> vm.updateSystem { it.copy(expectedAnnualProductionKwh = v) } },
                    suffix = stringResource(R.string.kwh_per_year),
                    supportingText = stringResource(R.string.expected_production_hint),
                )
            }

            // --------------------------------------------------------- prices
            SectionCard(title = stringResource(R.string.section_prices), icon = Icons.Rounded.Bolt) {
                NumberSettingField(
                    label = stringResource(R.string.grid_price),
                    value = data.tariffs.gridPricePerKwh,
                    onValue = { v -> if (v != null) vm.updateTariffs { it.copy(gridPricePerKwh = v) } },
                    suffix = "${fmt.currencySymbol}/kWh",
                    supportingText = stringResource(R.string.grid_price_hint),
                    allowEmpty = false,
                )
                NumberSettingField(
                    label = stringResource(R.string.feed_in_price),
                    value = data.tariffs.feedInPricePerKwh,
                    onValue = { v -> vm.updateTariffs { it.copy(feedInPricePerKwh = v ?: 0.0) } },
                    suffix = "${fmt.currencySymbol}/kWh",
                    supportingText = stringResource(R.string.feed_in_hint),
                )
                SelfConsumptionSlider(data.tariffs.selfConsumptionPercent) { v ->
                    vm.updateTariffs { it.copy(selfConsumptionPercent = v) }
                }
            }

            // --------------------------------------------------------- projection
            SectionCard(title = stringResource(R.string.section_projection), icon = Icons.AutoMirrored.Rounded.TrendingUp) {
                NumberSettingField(
                    label = stringResource(R.string.price_increase),
                    value = data.tariffs.annualPriceIncreasePercent,
                    onValue = { v -> vm.updateTariffs { it.copy(annualPriceIncreasePercent = v ?: 0.0) } },
                    suffix = stringResource(R.string.percent_per_year),
                    supportingText = stringResource(R.string.price_increase_hint),
                )
                NumberSettingField(
                    label = stringResource(R.string.degradation),
                    value = data.tariffs.panelDegradationPercent,
                    onValue = { v -> vm.updateTariffs { it.copy(panelDegradationPercent = v ?: 0.0) } },
                    suffix = stringResource(R.string.percent_per_year),
                    supportingText = stringResource(R.string.degradation_hint),
                )
            }

            // --------------------------------------------------------- DeyeCloud
            DeyeCloudSection(vm, data, snackbar)

            // --------------------------------------------------------- general
            SectionCard(title = stringResource(R.string.section_general), icon = Icons.Rounded.Language) {
                CurrencyPicker(data.currencyCode ?: AppViewModel.defaultCurrency()) { vm.setCurrency(it) }
            }

            // --------------------------------------------------------- data
            SectionCard(title = stringResource(R.string.section_data), icon = Icons.Rounded.Storage) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ActionButton(Icons.Rounded.FileDownload, stringResource(R.string.export_backup)) {
                    exporter.launch("pvcalculator-backup-${LocalDate.now()}.zip")
                }
                ActionButton(Icons.Rounded.FileUpload, stringResource(R.string.restore_backup)) {
                    importer.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*"))
                }
                ActionButton(Icons.Rounded.DeleteForever, stringResource(R.string.clear_all), destructive = true) {
                    confirmClear = true
                }
            }

            // --------------------------------------------------------- about
            SectionCard(title = stringResource(R.string.section_about), icon = Icons.Rounded.Info) {
                Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showInstallPicker) {
        DatePickerModal(
            data.system.installDate?.let(SavingsCalculator::parseDate),
            { showInstallPicker = false },
        ) { d -> vm.updateSystem { it.copy(installDate = d.toString()) } }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_all_title),
            message = stringResource(R.string.clear_all_message),
            onConfirm = {
                vm.clearAll()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
    confirmRestore?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.restore_title),
            message = stringResource(R.string.restore_message),
            confirmText = stringResource(R.string.restore),
            onConfirm = {
                confirmRestore = null
                scope.launch {
                    snackbar.showSnackbar(context.getString(if (vm.importBackup(uri)) R.string.restore_done else R.string.restore_failed))
                }
            },
            onDismiss = { confirmRestore = null },
        )
    }
}

@Composable
private fun SelfConsumptionSlider(value: Double, onValue: (Double) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.self_consumption), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${local.roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onValue(local.roundToInt().toDouble()) },
            valueRange = 0f..100f,
            steps = 19,
        )
        Text(
            stringResource(R.string.self_consumption_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CurrencyPicker(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val options = (listOf(selected, AppViewModel.defaultCurrency()) + commonCurrencies).distinct()
    fun label(code: String) = runCatching {
        val c = Currency.getInstance(code)
        "${c.getSymbol(locale)} · ${c.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }} ($code)"
    }.getOrDefault(code)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.currency)) },
            leadingIcon = { Icon(Icons.Rounded.Payments, null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text(label(code)) },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, text: String, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ExtraInvestments(vm: AppViewModel, data: AppData, fmt: Formatter) {
    var editing by remember { mutableStateOf<ExtraInvestment?>(null) }
    var creating by remember { mutableStateOf(false) }
    val extras = data.system.extraInvestments
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        extras.forEach { e ->
            Surface(
                onClick = { editing = e },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.BatteryChargingFull, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.description, style = MaterialTheme.typography.bodyLarge)
                        e.date?.let(SavingsCalculator::parseDate)?.let {
                            Text(fmt.date(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(fmt.moneySmart(e.amount), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (extras.isNotEmpty()) {
            Text(
                stringResource(R.string.total_investment, fmt.moneySmart(data.system.totalInvestment)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TextButton(onClick = { creating = true }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.add_extra_investment))
        }
    }
    if (creating || editing != null) {
        ExtraInvestmentDialog(
            initial = editing,
            fmt = fmt,
            onSave = { vm.saveExtraInvestment(it) },
            onDelete = { vm.deleteExtraInvestment(it) },
            onDismiss = {
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun ExtraInvestmentDialog(
    initial: ExtraInvestment?,
    fmt: Formatter,
    onSave: (ExtraInvestment) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultName = stringResource(R.string.battery)
    var description by remember { mutableStateOf(initial?.description ?: defaultName) }
    var amountText by remember { mutableStateOf(Formatter.input(initial?.amount)) }
    var date by remember { mutableStateOf(initial?.date?.let(SavingsCalculator::parseDate) ?: LocalDate.now()) }
    var showDate by remember { mutableStateOf(false) }
    val amount = TextParsing.parseInput(amountText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.extra_investment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.extra_investment_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                )
                DecimalField(amountText, { amountText = it }, stringResource(R.string.cost), suffix = fmt.currencySymbol,
                    isError = amountText.isNotBlank() && amount == null)
                PickerField(stringResource(R.string.date), fmt.date(date), { showDate = true }, leading = Icons.Rounded.Event)
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount > 0 && description.isNotBlank(),
                onClick = {
                    onSave(
                        ExtraInvestment(
                            id = initial?.id ?: DataRepository.newId(),
                            description = description.trim(),
                            amount = amount ?: 0.0,
                            date = date.toString(),
                        ),
                    )
                    onDismiss()
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = {
                        onDelete(initial.id)
                        onDismiss()
                    }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
    if (showDate) DatePickerModal(date, { showDate = false }) { date = it }
}
