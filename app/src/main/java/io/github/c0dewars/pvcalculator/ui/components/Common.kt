@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.c0dewars.pvcalculator.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.c0dewars.pvcalculator.R
import io.github.c0dewars.pvcalculator.data.Attachment
import io.github.c0dewars.pvcalculator.domain.TextParsing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        IconBadge(icon, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
            }
            content()
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Int = 36) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun StatTile(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier = Modifier, caption: String? = null) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconBadge(icon, tint, 32)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The caption line is always reserved so tiles side by side keep the same size.
            Text(
                caption.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, 72)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/** Label / value pair used in breakdowns. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified, bold: Boolean = false) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = valueColor,
        )
    }
}

// ---------------------------------------------------------------------------- inputs

@Composable
fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }) },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        suffix = suffix?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        shape = MaterialTheme.shapes.medium,
    )
}

/** A number field that saves as soon as the user types a valid value. */
@Composable
fun NumberSettingField(
    label: String,
    value: Double?,
    onValue: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    supportingText: String? = null,
    allowEmpty: Boolean = true,
) {
    var text by rememberSaveable { mutableStateOf(Formatter.input(value)) }
    // Keep the field in sync when data changes elsewhere (e.g. a restored backup).
    LaunchedEffect(value) {
        if (TextParsing.parseInput(text) != value) text = Formatter.input(value)
    }
    val parsed = TextParsing.parseInput(text)
    val invalid = text.isNotBlank() && parsed == null
    DecimalField(
        value = text,
        onValueChange = {
            text = it
            val p = TextParsing.parseInput(it)
            if (p != null) onValue(p) else if (it.isBlank() && allowEmpty) onValue(null)
        },
        label = label,
        modifier = modifier,
        suffix = suffix,
        supportingText = supportingText,
        isError = invalid,
    )
}

/** Read-only field that opens a picker when tapped. */
@Composable
fun PickerField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = leading?.let { { Icon(it, null) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 8.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
        )
    }
}

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDateUtc() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun DatePickerModal(initial: LocalDate?, onDismiss: () -> Unit, onDate: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: LocalDate.now()).toUtcMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onDate(it.toLocalDateUtc()) }
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun MonthPickerDialog(initial: YearMonth, formatter: Formatter, onDismiss: () -> Unit, onMonth: (YearMonth) -> Unit) {
    var year by remember { mutableStateOf(initial.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous)) }
                Text(year.toString(), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next)) }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..12).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m ->
                            val ym = YearMonth.of(year, m)
                            val selected = ym == initial
                            Surface(
                                onClick = {
                                    onMonth(ym)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            ) {
                                Text(
                                    formatter.monthShort(ym),
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------- attachments

fun openAttachment(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
    }
}

private fun loadThumbnail(file: File, mimeType: String, size: Int): Bitmap? = runCatching {
    if (mimeType == "application/pdf") {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(0).use { page ->
                    val scale = size.toFloat() / page.width
                    val bmp = Bitmap.createBitmap(size, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}.getOrNull()

@Composable
fun AttachmentThumbnail(file: File, mimeType: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { loadThumbnail(file, mimeType, 240)?.asImageBitmap() }
    }
    Box(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(
                if (mimeType == "application/pdf") Icons.Rounded.PictureAsPdf else Icons.Rounded.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shows an attached file with buttons to open or remove it. */
@Composable
fun AttachmentCard(attachment: Attachment, file: File, onRemove: (() -> Unit)?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = { openAttachment(context, file, attachment.mimeType) },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AttachmentThumbnail(file, attachment.mimeType, Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    attachment.originalName ?: stringResource(if (attachment.isPdf) R.string.pdf_document else R.string.image),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.tap_to_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, stringResource(R.string.remove_attachment)) }
            }
        }
    }
}

@Composable
fun AttachButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

/** Segmented buttons use the app's orange accent (instead of the default green) when selected. */
@Composable
fun pvSegmentedColors() = androidx.compose.material3.SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
