@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.c0dewars.pvcalculator.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.c0dewars.pvcalculator.R
import io.github.c0dewars.pvcalculator.data.AppData
import io.github.c0dewars.pvcalculator.domain.DeyeOrderStatus
import io.github.c0dewars.pvcalculator.domain.TouPlanner
import io.github.c0dewars.pvcalculator.domain.TouSlot
import io.github.c0dewars.pvcalculator.domain.TextParsing
import io.github.c0dewars.pvcalculator.ui.AppViewModel
import io.github.c0dewars.pvcalculator.ui.ScreenScaffold
import io.github.c0dewars.pvcalculator.ui.components.DecimalField
import io.github.c0dewars.pvcalculator.ui.components.EmptyState
import io.github.c0dewars.pvcalculator.ui.components.InfoRow
import io.github.c0dewars.pvcalculator.ui.components.PickerField
import io.github.c0dewars.pvcalculator.ui.components.SectionCard
import io.github.c0dewars.pvcalculator.ui.theme.PvColors
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

private fun watts(w: Double?): String {
    if (w == null) return "—"
    val nf = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
    return if (abs(w) >= 1000) "${nf.format(w / 1000)} kW" else "${w.roundToInt()} W"
}

@Composable
private fun workModeLabel(code: String?): String = when (code?.uppercase()) {
    null -> "—"
    "SELLING_FIRST" -> stringResource(R.string.mode_selling_first)
    "ZERO_EXPORT_TO_LOAD" -> stringResource(R.string.mode_zero_export_load)
    "ZERO_EXPORT_TO_CT" -> stringResource(R.string.mode_zero_export_ct)
    "BATTERY_FIRST" -> stringResource(R.string.pattern_battery_first)
    "LOAD_FIRST" -> stringResource(R.string.pattern_load_first)
    else -> code
}

/** End time of each slot = start of the next one (the last one ends at the first one). */
private fun slotRanges(slots: List<TouSlot>): List<Pair<TouSlot, String>> {
    val sorted = slots.sortedBy { it.localTime() ?: LocalTime.MIDNIGHT }
    return sorted.mapIndexed { i, s -> s to sorted[(i + 1) % sorted.size].time }
}

@Composable
fun BatteryScreen(vm: AppViewModel, data: AppData, snackbar: SnackbarHostState, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.inverter.collectAsState()
    var showChargeDialog by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }

    fun reportError(e: Throwable) {
        scope.launch {
            snackbar.showSnackbar(
                if (e is AppViewModel.DeyeTokenExpired) context.getString(R.string.deye_session_expired)
                else context.getString(R.string.deye_error, e.message ?: e.javaClass.simpleName),
            )
        }
    }

    fun refresh() = scope.launch { runCatching { vm.refreshInverter() }.onFailure(::reportError) }

    fun reportOrder(status: DeyeOrderStatus?, okText: Int) = scope.launch {
        snackbar.showSnackbar(
            when {
                status?.succeeded == true -> context.getString(okText)
                status?.isDone == true -> context.getString(R.string.command_failed, status.error ?: status.status.toString())
                else -> context.getString(R.string.command_pending)
            },
        )
    }

    LaunchedEffect(data.deye?.stationId) {
        if (data.deye != null && state.updatedAt == null && !state.loading) refresh()
    }

    ScreenScaffold(
        title = stringResource(R.string.battery_title),
        snackbar = snackbar,
        actions = {
            if (data.deye != null) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { refresh() }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh)) }
                }
            }
        },
    ) { padding ->
        if (data.deye == null) {
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
                EmptyState(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = stringResource(R.string.battery_connect_title),
                    message = stringResource(R.string.battery_connect_message),
                ) {
                    Button(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Cloud, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.deye_connect))
                    }
                }
            }
            return@ScreenScaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { LiveCard(state) }
            item {
                GridChargeCard(
                    state = state,
                    onCharge = { showChargeDialog = true },
                    onStop = { confirmStop = true },
                )
            }
            item { InverterSettingsCard(state) }
            if (state.errors.isNotEmpty()) {
                item {
                    Text(
                        state.errors.joinToString("\n") { context.getString(R.string.deye_error, it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showChargeDialog) {
        GridChargeDialog(
            vm = vm,
            onDismiss = { showChargeDialog = false },
            onSend = { plan, ampere ->
                showChargeDialog = false
                scope.launch {
                    runCatching { vm.startGridCharge(plan, ampere) }
                        .onSuccess { reportOrder(it, R.string.grid_charge_started) }
                        .onFailure(::reportError)
                }
            },
        )
    }
    if (confirmStop) {
        ConfirmDialog(
            title = stringResource(R.string.stop_grid_charge_title),
            message = stringResource(R.string.stop_grid_charge_message),
            confirmText = stringResource(R.string.stop_grid_charge),
            onConfirm = {
                confirmStop = false
                scope.launch {
                    runCatching { vm.stopGridCharge() }
                        .onSuccess { reportOrder(it, R.string.grid_charge_stopped) }
                        .onFailure(::reportError)
                }
            },
            onDismiss = { confirmStop = false },
        )
    }
}

@Composable
internal fun LiveCard(state: AppViewModel.InverterState) {
    val latest = state.latest
    val soc = latest?.batterySOC
    SectionCard(title = stringResource(R.string.battery_now), icon = Icons.Rounded.BatteryChargingFull) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                soc?.let { "${it.roundToInt()}%" } ?: "—",
                style = MaterialTheme.typography.displaySmall,
                color = PvColors.savings,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                val bp = latest?.batteryPower
                Text(
                    when {
                        bp == null -> stringResource(R.string.battery_state_unknown)
                        bp < -20 -> stringResource(R.string.battery_charging, watts(-bp))
                        bp > 20 -> stringResource(R.string.battery_discharging, watts(bp))
                        else -> stringResource(R.string.battery_idle)
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                state.battery?.battCapacity?.let {
                    Text(stringResource(R.string.battery_capacity_ah, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        LinearProgressIndicator(
            progress = { ((soc ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape),
            color = PvColors.savings,
            drawStopIndicator = {},
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowTile(Icons.Rounded.WbSunny, stringResource(R.string.flow_solar), watts(latest?.generationPower), PvColors.solar, Modifier.weight(1f))
            FlowTile(Icons.Rounded.Home, stringResource(R.string.flow_home), watts(latest?.consumptionPower), PvColors.export, Modifier.weight(1f))
            FlowTile(
                Icons.Rounded.ElectricalServices, stringResource(R.string.flow_grid),
                watts(latest?.gridPower ?: latest?.purchasePower), PvColors.grid, Modifier.weight(1f),
            )
        }
        Text(
            state.updatedAt?.let { stringResource(R.string.updated_at, it.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) }
                ?: stringResource(R.string.loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FlowTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, tint: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
internal fun GridChargeCard(state: AppViewModel.InverterState, onCharge: () -> Unit, onStop: () -> Unit) {
    SectionCard(title = stringResource(R.string.grid_charge_title), icon = Icons.Rounded.Bolt) {
        val on = state.gridChargeOn
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (on) {
                            true -> PvColors.savings
                            false -> MaterialTheme.colorScheme.outline
                            null -> MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    state.dynamicLoading && on == null -> stringResource(R.string.grid_charge_reading)
                    on == true -> stringResource(R.string.grid_charge_on, state.dynamic?.gridChargeAmpere?.let { "$it A" } ?: "—")
                    on == false -> stringResource(R.string.grid_charge_off)
                    else -> stringResource(R.string.grid_charge_unknown)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val charging = slotRanges(state.schedule).filter { it.first.enableGridCharge }
        if (charging.isNotEmpty()) {
            Text(stringResource(R.string.grid_charge_windows), style = MaterialTheme.typography.labelLarge)
            charging.forEach { (slot, end) ->
                Text(
                    stringResource(R.string.grid_charge_window, slot.time, end, slot.soc ?: 0, watts(slot.power?.toDouble())),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (state.commandRunning) {
            Text(stringResource(R.string.command_sending), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCharge,
                enabled = !state.commandRunning && state.deviceSn != null,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            ) { Text(stringResource(R.string.charge_from_grid)) }
            OutlinedButton(
                onClick = onStop,
                enabled = !state.commandRunning && state.deviceSn != null && on != false,
                shape = MaterialTheme.shapes.medium,
            ) { Text(stringResource(R.string.stop_grid_charge)) }
        }
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.grid_charge_tariff_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun InverterSettingsCard(state: AppViewModel.InverterState) {
    SectionCard(title = stringResource(R.string.inverter_settings), icon = Icons.Rounded.Tune) {
        Text(stringResource(R.string.read_only_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.deviceSn?.let { InfoRow(stringResource(R.string.inverter_serial), it) }

        val sys = state.system
        val dyn = state.dynamic
        Text(stringResource(R.string.settings_system), style = MaterialTheme.typography.titleSmall)
        InfoRow(stringResource(R.string.work_mode), workModeLabel(sys?.systemWorkMode ?: dyn?.workMode))
        InfoRow(stringResource(R.string.energy_pattern), workModeLabel(sys?.energyPattern))
        InfoRow(stringResource(R.string.solar_sell), dyn?.solarSellAction?.let { if (it.equals("on", true)) stringResource(R.string.on) else stringResource(R.string.off) } ?: "—")
        InfoRow(stringResource(R.string.max_sell_power), watts((sys?.maxSellPower ?: dyn?.maxSellPower)?.toDouble()))
        InfoRow(stringResource(R.string.max_solar_power), watts((sys?.maxSolarPower ?: dyn?.maxSolarPower)?.toDouble()))
        InfoRow(stringResource(R.string.zero_export_power), watts((sys?.zeroExportPower ?: dyn?.zeroExportPower)?.toDouble()))

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        val bat = state.battery
        Text(stringResource(R.string.settings_battery), style = MaterialTheme.typography.titleSmall)
        InfoRow(stringResource(R.string.batt_capacity), bat?.battCapacity?.let { "$it Ah" } ?: "—")
        InfoRow(stringResource(R.string.batt_low), bat?.battLowCapacity?.let { "$it%" } ?: "—")
        InfoRow(stringResource(R.string.batt_shutdown), bat?.battShutDownCapacity?.let { "$it%" } ?: "—")
        InfoRow(stringResource(R.string.max_charge_current), (bat?.maxChargeCurrent ?: dyn?.maxChargeCurrent)?.let { "$it A" } ?: "—")
        InfoRow(stringResource(R.string.max_discharge_current), (bat?.maxDischargeCurrent ?: dyn?.maxDischargeCurrent)?.let { "$it A" } ?: "—")
        InfoRow(stringResource(R.string.grid_charge_current), dyn?.gridChargeAmpere?.let { "$it A" } ?: "—")

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.tou_schedule), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            val touOn = (dyn?.touAction ?: state.tou?.touAction)?.equals("on", true)
            Text(
                when (touOn) {
                    true -> stringResource(R.string.on)
                    false -> stringResource(R.string.off)
                    null -> "—"
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
        val slots = slotRanges(state.schedule)
        if (slots.isEmpty()) {
            Text("—", style = MaterialTheme.typography.bodyMedium)
        } else {
            ScheduleTable(slots)
        }
    }
}

@Composable
private fun ScheduleTable(slots: List<Pair<TouSlot, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            listOf(R.string.tou_time, R.string.tou_power, R.string.tou_soc, R.string.tou_grid).forEachIndexed { i, label ->
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(if (i == 0) 1.6f else 1f),
                )
            }
        }
        slots.forEach { (slot, end) ->
            Row {
                Text("${slot.time}–$end", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.6f))
                Text(watts(slot.power?.toDouble()), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(slot.soc?.let { "$it%" } ?: "—", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    if (slot.enableGridCharge) "✓" else "–",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (slot.enableGridCharge) FontWeight.Bold else FontWeight.Normal,
                    color = if (slot.enableGridCharge) PvColors.savings else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GridChargeDialog(vm: AppViewModel, onDismiss: () -> Unit, onSend: (List<TouSlot>, Int?) -> Unit) {
    val state by vm.inverter.collectAsState()
    var start by remember { mutableStateOf(LocalTime.of(2, 0)) }
    var end by remember { mutableStateOf(LocalTime.of(6, 0)) }
    var target by remember { mutableFloatStateOf(90f) }
    val existingPower = state.schedule.firstNotNullOfOrNull { it.power }
    var powerText by remember { mutableStateOf((existingPower ?: 3000).toString()) }
    var ampereText by remember { mutableStateOf(state.dynamic?.gridChargeAmpere?.toString().orEmpty()) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    val power = TextParsing.parseInput(powerText)?.roundToInt()?.takeIf { it in 100..30000 }
    val ampere = ampereText.takeIf { it.isNotBlank() }?.let { TextParsing.parseInput(it)?.roundToInt() }
    val ampereValid = ampereText.isBlank() || (ampere != null && ampere in 1..300)
    val plan = if (power != null && TouPlanner.align(start) != TouPlanner.align(end)) {
        TouPlanner.gridChargePlan(state.schedule, start, end, target.roundToInt(), power, vm.defaultBatterySoc())
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.charge_from_grid)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.charge_dialog_intro), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerField(stringResource(R.string.from_time), start.toString(), { pickStart = true }, Modifier.weight(1f))
                    PickerField(stringResource(R.string.to_time), end.toString(), { pickEnd = true }, Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.charge_up_to), modifier = Modifier.weight(1f))
                    Text("${target.roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(value = target, onValueChange = { target = it }, valueRange = 20f..100f, steps = 15)
                DecimalField(powerText, { powerText = it }, stringResource(R.string.charge_power), suffix = "W", isError = power == null)
                DecimalField(
                    ampereText, { ampereText = it }, stringResource(R.string.grid_charge_current_optional), suffix = "A",
                    isError = !ampereValid, supportingText = stringResource(R.string.grid_charge_current_hint),
                )
                if (plan != null) {
                    Text(stringResource(R.string.schedule_preview), style = MaterialTheme.typography.labelLarge)
                    ScheduleTable(slotRanges(plan))
                }
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        stringResource(R.string.charge_warning),
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = plan != null && ampereValid, onClick = { plan?.let { onSend(it, ampere) } }) {
                Text(stringResource(R.string.send_to_inverter))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (pickStart) TimePickerDialog(start, { pickStart = false }) { start = it }
    if (pickEnd) TimePickerDialog(end, { pickEnd = false }) { end = it }
}

@Composable
private fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onTime: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onTime(TouPlanner.align(LocalTime.of(state.hour, state.minute)))
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state = state) },
    )
}
