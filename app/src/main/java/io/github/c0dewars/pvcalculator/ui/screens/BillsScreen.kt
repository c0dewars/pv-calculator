package io.github.c0dewars.pvcalculator.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.c0dewars.pvcalculator.data.AppData
import io.github.c0dewars.pvcalculator.data.Attachment
import io.github.c0dewars.pvcalculator.data.Bill
import io.github.c0dewars.pvcalculator.data.DataRepository
import io.github.c0dewars.pvcalculator.domain.SavingsCalculator
import io.github.c0dewars.pvcalculator.domain.TextParsing
import io.github.c0dewars.pvcalculator.ui.AppViewModel
import io.github.c0dewars.pvcalculator.ui.ScreenScaffold
import io.github.c0dewars.pvcalculator.ui.components.AttachButton
import io.github.c0dewars.pvcalculator.ui.components.AttachmentCard
import io.github.c0dewars.pvcalculator.ui.components.AttachmentThumbnail
import io.github.c0dewars.pvcalculator.ui.components.DecimalField
import io.github.c0dewars.pvcalculator.ui.components.EmptyState
import io.github.c0dewars.pvcalculator.ui.components.Formatter
import io.github.c0dewars.pvcalculator.ui.components.IconBadge
import io.github.c0dewars.pvcalculator.ui.components.MonthPickerDialog
import io.github.c0dewars.pvcalculator.ui.components.PickerField
import io.github.c0dewars.pvcalculator.ui.components.openAttachment
import kotlinx.coroutines.launch
import java.time.YearMonth

private data class BillDialogState(val bill: Bill?, val pickFile: Boolean)

private val billMimeTypes = arrayOf("application/pdf", "image/*")

@Composable
fun BillsScreen(vm: AppViewModel, data: AppData, fmt: Formatter, snackbar: SnackbarHostState) {
    var dialog by remember { mutableStateOf<BillDialogState?>(null) }

    ScreenScaffold(
        title = stringResource(R.string.bills_title),
        snackbar = snackbar,
        actions = {
            IconButton(onClick = { dialog = BillDialogState(null, pickFile = true) }) {
                Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.import_bill))
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = BillDialogState(null, pickFile = false) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.add_bill)) },
            )
        },
    ) { padding ->
        val bills = data.bills.sortedByDescending { it.month }
        if (bills.isEmpty()) {
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = stringResource(R.string.empty_bills_title),
                    message = stringResource(R.string.empty_bills_message),
                ) {
                    Button(onClick = { dialog = BillDialogState(null, pickFile = true) }) {
                        Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.import_bill))
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.bills_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                items(bills, key = { it.id }) { b -> BillRow(vm, b, fmt) { dialog = BillDialogState(b, false) } }
            }
        }
    }

    dialog?.let { d ->
        BillEditDialog(vm, d.bill, d.pickFile, fmt, snackbar) { dialog = null }
    }
}

@Composable
private fun BillRow(vm: AppViewModel, b: Bill, fmt: Formatter, onClick: () -> Unit) {
    val context = LocalContext.current
    val month = SavingsCalculator.parseMonth(b.month)
    val price = SavingsCalculator.effectivePrice(b)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val att = b.attachment
            if (att != null) {
                Surface(
                    onClick = { openAttachment(context, vm.fileFor(att), att.mimeType) },
                    shape = MaterialTheme.shapes.small,
                ) {
                    AttachmentThumbnail(vm.fileFor(att), att.mimeType, Modifier.size(width = 44.dp, height = 56.dp))
                }
            } else {
                IconBadge(Icons.AutoMirrored.Rounded.ReceiptLong, MaterialTheme.colorScheme.primary, 44)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(month?.let(fmt::month) ?: b.month, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append(stringResource(R.string.from_grid_value, fmt.kwh(b.importedKwh)))
                        if (price != null) append(" · ").append(fmt.pricePerKwh(price))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                b.exportedKwh?.let {
                    Text(
                        stringResource(R.string.exported_value, fmt.kwh(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(fmt.money(b.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BillEditDialog(
    vm: AppViewModel,
    initial: Bill?,
    pickFileOnOpen: Boolean,
    fmt: Formatter,
    snackbar: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var month by remember { mutableStateOf(initial?.month?.let(SavingsCalculator::parseMonth) ?: YearMonth.now().minusMonths(1)) }
    var importedText by remember { mutableStateOf(Formatter.input(initial?.importedKwh)) }
    var amountText by remember { mutableStateOf(Formatter.input(initial?.amount)) }
    var fixedText by remember { mutableStateOf(Formatter.input(initial?.fixedCharges)) }
    var exportedText by remember { mutableStateOf(Formatter.input(initial?.exportedKwh)) }
    var attachment by remember { mutableStateOf(initial?.attachment) }
    val newAttachments = remember { mutableStateListOf<Attachment>() }
    var analyzing by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf<String?>(null) }
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
                val r = vm.analyzeBill(att)
                r.month?.let { month = it }
                r.importedKwh?.let { importedText = Formatter.input(it) }
                r.amount?.let { amountText = Formatter.input(it) }
                r.fixedCharges?.let { fixedText = Formatter.input(it) }
                r.exportedKwh?.let { exportedText = Formatter.input(it) }
                val found = listOfNotNull(
                    r.month?.let { context.getString(R.string.field_month) },
                    r.amount?.let { context.getString(R.string.field_amount) },
                    r.importedKwh?.let { context.getString(R.string.field_consumption) },
                    r.fixedCharges?.let { context.getString(R.string.field_fixed) },
                    r.exportedKwh?.let { context.getString(R.string.field_exported) },
                )
                detected = if (found.isEmpty()) context.getString(R.string.nothing_detected_bill)
                else context.getString(R.string.detected_bill, found.joinToString(", "))
            } else {
                snackbar.showSnackbar(context.getString(R.string.file_open_failed))
            }
            analyzing = false
        }
    }
    LaunchedEffect(Unit) { if (pickFileOnOpen) picker.launch(billMimeTypes) }

    fun close(saved: Attachment?) {
        scope.launch { newAttachments.filter { it != saved }.forEach { vm.discardAttachment(it) } }
        onDismiss()
    }

    val imported = TextParsing.parseInput(importedText)
    val amount = TextParsing.parseInput(amountText)
    val fixed = TextParsing.parseInput(fixedText)
    val exported = TextParsing.parseInput(exportedText)
    val valid = imported != null && imported >= 0 && amount != null && amount >= 0 &&
        (fixedText.isBlank() || fixed != null) && (exportedText.isBlank() || exported != null)
    val preview = if (valid) {
        SavingsCalculator.effectivePrice(Bill("", month.toString(), imported ?: 0.0, amount ?: 0.0, fixed))
    } else null

    AlertDialog(
        onDismissRequest = { close(null) },
        title = { Text(stringResource(if (initial == null) R.string.add_bill else R.string.edit_bill)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val att = attachment
                if (att != null) {
                    AttachmentCard(att, vm.fileFor(att), onRemove = { attachment = null })
                } else {
                    AttachButton(stringResource(R.string.attach_bill), Icons.Rounded.PictureAsPdf, { picker.launch(billMimeTypes) })
                }
                if (analyzing) {
                    Text(stringResource(R.string.reading_file), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                detected?.takeIf { !analyzing }?.let { DetectedBanner(it) }

                PickerField(stringResource(R.string.bill_month), fmt.month(month), { showMonthPicker = true }, leading = Icons.Rounded.CalendarMonth)
                DecimalField(
                    amountText, { amountText = it }, stringResource(R.string.bill_amount),
                    suffix = fmt.currencySymbol, isError = amountText.isNotBlank() && amount == null,
                )
                DecimalField(
                    importedText, { importedText = it }, stringResource(R.string.bill_imported),
                    suffix = "kWh", isError = importedText.isNotBlank() && imported == null,
                    supportingText = stringResource(R.string.bill_imported_hint),
                )
                DecimalField(
                    fixedText, { fixedText = it }, stringResource(R.string.bill_fixed),
                    suffix = fmt.currencySymbol, isError = fixedText.isNotBlank() && fixed == null,
                    supportingText = stringResource(R.string.bill_fixed_hint),
                )
                DecimalField(
                    exportedText, { exportedText = it }, stringResource(R.string.bill_exported),
                    suffix = "kWh", isError = exportedText.isNotBlank() && exported == null,
                    supportingText = stringResource(R.string.bill_exported_hint),
                )
                if (preview != null) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                        Text(
                            stringResource(R.string.effective_price, fmt.pricePerKwh(preview)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !analyzing,
                onClick = {
                    vm.saveBill(
                        Bill(
                            id = initial?.id ?: DataRepository.newId(),
                            month = month.toString(),
                            importedKwh = imported ?: 0.0,
                            amount = amount ?: 0.0,
                            fixedCharges = fixed,
                            exportedKwh = exported,
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

    if (showMonthPicker) MonthPickerDialog(month, fmt, { showMonthPicker = false }) { month = it }
    if (confirmDelete && initial != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_bill_title),
            message = stringResource(R.string.delete_bill_message),
            onConfirm = {
                vm.deleteBill(initial.id)
                confirmDelete = false
                close(null)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
