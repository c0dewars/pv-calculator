@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.c0dewars.pvcalculator.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Attachment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.c0dewars.pvcalculator.R
import io.github.c0dewars.pvcalculator.ui.components.pvSegmentedColors
import io.github.c0dewars.pvcalculator.data.AppData
import io.github.c0dewars.pvcalculator.data.Attachment
import io.github.c0dewars.pvcalculator.data.DataRepository
import io.github.c0dewars.pvcalculator.data.ProductionEntry
import io.github.c0dewars.pvcalculator.domain.DeyeSync
import io.github.c0dewars.pvcalculator.domain.EnergyCandidate
import io.github.c0dewars.pvcalculator.domain.SavingsCalculator
import io.github.c0dewars.pvcalculator.domain.TextParsing
import io.github.c0dewars.pvcalculator.ui.AppViewModel
import io.github.c0dewars.pvcalculator.ui.ScreenScaffold
import io.github.c0dewars.pvcalculator.ui.components.AttachButton
import io.github.c0dewars.pvcalculator.ui.components.AttachmentCard
import io.github.c0dewars.pvcalculator.ui.components.DatePickerModal
import io.github.c0dewars.pvcalculator.ui.components.DecimalField
import io.github.c0dewars.pvcalculator.ui.components.EmptyState
import io.github.c0dewars.pvcalculator.ui.components.Formatter
import io.github.c0dewars.pvcalculator.ui.components.IconBadge
import io.github.c0dewars.pvcalculator.ui.components.MonthPickerDialog
import io.github.c0dewars.pvcalculator.ui.components.PickerField
import io.github.c0dewars.pvcalculator.ui.components.SectionCard
import io.github.c0dewars.pvcalculator.ui.theme.PvColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private sealed interface ProductionDialog {
    data class Edit(val entry: ProductionEntry?, val pickFile: Boolean = false) : ProductionDialog
}

@Composable
fun ProductionScreen(vm: AppViewModel, data: AppData, fmt: Formatter, snackbar: SnackbarHostState) {
    var dialog by remember { mutableStateOf<ProductionDialog?>(null) }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val deyeSyncing by vm.deyeSyncing.collectAsState()

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = vm.importCsv(uri)
            snackbar.showSnackbar(
                if (result == null || result.first == 0) context.getString(R.string.csv_import_failed)
                else context.resources.getQuantityString(R.plurals.csv_imported, result.first, result.first),
            )
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.production_title),
        snackbar = snackbar,
        actions = {
            if (data.deye != null) {
                if (deyeSyncing) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = {
                        scope.launch { runDeyeSync(vm, snackbar, context) { scope.launch { snackbar.showSnackbar(context.getString(R.string.deye_session_expired)) } } }
                    }) { Icon(Icons.Rounded.CloudSync, stringResource(R.string.deye_sync_now)) }
                }
            }
            IconButton(onClick = { dialog = ProductionDialog.Edit(null, pickFile = true) }) {
                Icon(Icons.Rounded.AddPhotoAlternate, stringResource(R.string.import_screenshot))
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_csv)) },
                    leadingIcon = { Icon(Icons.Rounded.UploadFile, null) },
                    onClick = {
                        menu = false
                        csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*"))
                    },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = ProductionDialog.Edit(null) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.add_production)) },
            )
        },
    ) { padding ->
        val entries = data.production.sortedByDescending { it.date }
        if (entries.isEmpty()) {
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
                EmptyState(
                    icon = Icons.Rounded.WbSunny,
                    title = stringResource(R.string.empty_production_title),
                    message = stringResource(R.string.empty_production_message),
                ) {
                    Button(onClick = { dialog = ProductionDialog.Edit(null, pickFile = true) }) {
                        Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.import_screenshot))
                    }
                    TextButton(onClick = {
                        csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*"))
                    }) { Text(stringResource(R.string.import_csv)) }
                }
            }
        } else {
            val grouped = entries.groupBy { SavingsCalculator.parseDate(it.date)?.let(YearMonth::from) ?: YearMonth.now() }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { ProductionSummary(data, fmt) }
                grouped.forEach { (month, list) ->
                    item(key = "h$month") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(fmt.month(month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(fmt.kwh(list.sumOf { it.kwh }), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    items(list, key = { it.id }) { e ->
                        ProductionRow(e, fmt) { dialog = ProductionDialog.Edit(e) }
                    }
                }
            }
        }
    }

    (dialog as? ProductionDialog.Edit)?.let { d ->
        ProductionEditDialog(
            vm = vm,
            initial = d.entry,
            pickFileOnOpen = d.pickFile,
            fmt = fmt,
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun ProductionSummary(data: AppData, fmt: Formatter) {
    val thisMonth = YearMonth.now()
    val monthTotal = data.production.filter { SavingsCalculator.parseDate(it.date)?.let(YearMonth::from) == thisMonth }.sumOf { it.kwh }
    val total = data.production.sumOf { it.kwh }
    SectionCard {
        Row {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.this_month), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt.kwh(monthTotal), style = MaterialTheme.typography.headlineSmall)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.all_time), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt.kwh(total), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun ProductionRow(e: ProductionEntry, fmt: Formatter, onClick: () -> Unit) {
    val date = SavingsCalculator.parseDate(e.date)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (e.wholeMonth) Icons.Rounded.CalendarMonth else Icons.Rounded.WbSunny, PvColors.solar, 36)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        e.wholeMonth && date != null -> stringResource(R.string.whole_month_of, fmt.month(YearMonth.from(date)))
                        date != null -> fmt.day(date)
                        else -> e.date
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (e.source == DeyeSync.SOURCE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Cloud, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            e.exportedKwh?.let { stringResource(R.string.deye_row, fmt.kwh(it)) } ?: "DeyeCloud",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (e.attachment != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Attachment, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.has_attachment), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(fmt.kwh(e.kwh), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProductionEditDialog(
    vm: AppViewModel,
    initial: ProductionEntry?,
    pickFileOnOpen: Boolean,
    fmt: Formatter,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val initialDate = initial?.date?.let(SavingsCalculator::parseDate)
    var wholeMonth by remember { mutableStateOf(initial?.wholeMonth ?: false) }
    var date by remember { mutableStateOf(initialDate ?: LocalDate.now().minusDays(1)) }
    var month by remember { mutableStateOf(initialDate?.let(YearMonth::from) ?: YearMonth.now()) }
    var kwhText by remember { mutableStateOf(Formatter.input(initial?.kwh)) }
    var exportedText by remember { mutableStateOf(Formatter.input(initial?.exportedKwh)) }
    var attachment by remember { mutableStateOf(initial?.attachment) }
    val newAttachments = remember { mutableStateListOf<Attachment>() }
    var analyzing by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<EnergyCandidate>>(emptyList()) }
    var detected by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            analyzing = true
            val att = vm.storeAttachment(uri)
            if (att != null) {
                newAttachments += att
                attachment = att
                val r = vm.analyzeProduction(att)
                candidates = r.candidates.filter {
                    it.role == EnergyCandidate.Role.PRODUCTION || it.role == EnergyCandidate.Role.PV || it.role == EnergyCandidate.Role.UNKNOWN
                }
                r.productionKwh?.let { kwhText = Formatter.input(it) }
                r.exportedKwh?.let { exportedText = Formatter.input(it) }
                when {
                    r.date != null -> {
                        wholeMonth = false
                        date = r.date
                    }
                    r.isWholeMonth && r.month != null -> {
                        wholeMonth = true
                        month = r.month
                    }
                }
                val found = listOfNotNull(
                    r.productionKwh?.let { context.getString(R.string.field_production, fmt.kwh(it)) },
                    r.exportedKwh?.let { context.getString(R.string.field_export, fmt.kwh(it)) },
                    r.date?.let { fmt.date(it) } ?: r.month?.takeIf { r.isWholeMonth }?.let { fmt.month(it) },
                )
                detected = if (r.productionKwh == null) context.getString(R.string.nothing_detected_production)
                else context.getString(R.string.detected_production, found.joinToString(" · "))
            }
            analyzing = false
        }
    }
    LaunchedEffect(Unit) { if (pickFileOnOpen) picker.launch(arrayOf("image/*", "application/pdf")) }

    fun close(saved: Attachment?) {
        scope.launch { newAttachments.filter { it != saved }.forEach { vm.discardAttachment(it) } }
        onDismiss()
    }

    val kwh = TextParsing.parseInput(kwhText)
    val exported = TextParsing.parseInput(exportedText)
    AlertDialog(
        onDismissRequest = { close(null) },
        title = { Text(stringResource(if (initial == null) R.string.add_production else R.string.edit_production)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !wholeMonth, onClick = { wholeMonth = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        colors = pvSegmentedColors(),
                    ) { Text(stringResource(R.string.one_day)) }
                    SegmentedButton(
                        selected = wholeMonth, onClick = { wholeMonth = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        colors = pvSegmentedColors(),
                    ) { Text(stringResource(R.string.whole_month)) }
                }
                if (wholeMonth) {
                    PickerField(stringResource(R.string.month), fmt.month(month), { showMonthPicker = true }, leading = Icons.Rounded.CalendarMonth)
                } else {
                    PickerField(stringResource(R.string.date), fmt.date(date), { showDatePicker = true }, leading = Icons.Rounded.Event)
                }
                DecimalField(
                    value = kwhText,
                    onValueChange = { kwhText = it },
                    label = stringResource(R.string.energy_produced),
                    suffix = "kWh",
                    isError = kwhText.isNotBlank() && kwh == null,
                )
                if (analyzing) {
                    Text(stringResource(R.string.reading_file), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                DecimalField(
                    value = exportedText,
                    onValueChange = { exportedText = it },
                    label = stringResource(R.string.energy_exported),
                    suffix = "kWh",
                    isError = exportedText.isNotBlank() && exported == null,
                    supportingText = stringResource(R.string.energy_exported_hint),
                )
                val det = detected
                if (det != null && !analyzing) {
                    DetectedBanner(det)
                    if (candidates.size > 1) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            candidates.take(8).forEach { c ->
                                FilterChip(
                                    selected = TextParsing.parseInput(kwhText) == c.kwh,
                                    onClick = { kwhText = Formatter.input(c.kwh) },
                                    label = {
                                        Text(
                                            if (c.label.isNotBlank()) "${fmt.kwh(c.kwh)} · ${c.label}" else fmt.kwh(c.kwh),
                                            maxLines = 1,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                val att = attachment
                if (att != null) {
                    AttachmentCard(att, vm.fileFor(att), onRemove = { attachment = null })
                } else {
                    AttachButton(
                        stringResource(R.string.attach_screenshot),
                        Icons.Rounded.AddPhotoAlternate,
                        { picker.launch(arrayOf("image/*", "application/pdf")) },
                    )
                    Text(
                        stringResource(R.string.attach_screenshot_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = kwh != null && kwh >= 0 && !analyzing && (exportedText.isBlank() || exported != null),
                onClick = {
                    val d = if (wholeMonth) month.atDay(1) else date
                    vm.saveProduction(
                        ProductionEntry(
                            id = initial?.id ?: DataRepository.newId(),
                            date = d.toString(),
                            kwh = kwh ?: 0.0,
                            wholeMonth = wholeMonth,
                            exportedKwh = exported?.coerceAtMost(kwh ?: 0.0),
                            attachment = attachment,
                        ),
                    )
                    close(attachment)
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { close(null) }) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (showDatePicker) DatePickerModal(date, { showDatePicker = false }) { date = it }
    if (showMonthPicker) MonthPickerDialog(month, fmt, { showMonthPicker = false }) { month = it }
    if (confirmDelete && initial != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_entry_title),
            message = stringResource(R.string.delete_entry_message),
            onConfirm = {
                vm.deleteProduction(initial.id)
                confirmDelete = false
                close(null)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun DetectedBanner(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmText: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText ?: stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
