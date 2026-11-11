@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.c0dewars.pvcalculator.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.SolarPower
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.c0dewars.pvcalculator.R
import io.github.c0dewars.pvcalculator.ui.components.pvSegmentedColors
import io.github.c0dewars.pvcalculator.data.AppData
import io.github.c0dewars.pvcalculator.domain.DeyeDataCenter
import io.github.c0dewars.pvcalculator.domain.DeyeStation
import io.github.c0dewars.pvcalculator.ui.AppViewModel
import io.github.c0dewars.pvcalculator.ui.components.Formatter
import io.github.c0dewars.pvcalculator.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Runs a DeyeCloud sync and reports the result in a snackbar. Shared by Settings and Production. */
suspend fun runDeyeSync(vm: AppViewModel, snackbar: SnackbarHostState, context: android.content.Context, onExpired: () -> Unit) {
    try {
        val days = vm.syncDeye()
        snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.deye_synced, days, days))
    } catch (e: AppViewModel.DeyeTokenExpired) {
        onExpired()
    } catch (e: Exception) {
        snackbar.showSnackbar(context.getString(R.string.deye_error, e.message ?: e.javaClass.simpleName))
    }
}

@Composable
fun DeyeCloudSection(vm: AppViewModel, data: AppData, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncing by vm.deyeSyncing.collectAsState()
    var showConnect by remember { mutableStateOf(false) }
    var stations by remember { mutableStateOf<List<DeyeStation>>(emptyList()) }
    var importing by remember { mutableStateOf(false) }
    val link = data.deye

    SectionCard(title = stringResource(R.string.deye_title), icon = Icons.Rounded.Cloud) {
        if (link == null) {
            Text(
                stringResource(R.string.deye_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { showConnect = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Rounded.Cloud, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.deye_connect))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SolarPower, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(link.stationName ?: "#${link.stationId}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        link.lastSync?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
                            ?.let { stringResource(R.string.deye_last_sync, it.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))) }
                            ?: stringResource(R.string.deye_never_synced),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (syncing || importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { scope.launch { runDeyeSync(vm, snackbar, context) { showConnect = true } } },
                    enabled = !syncing,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Rounded.CloudSync, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.deye_sync_now))
                }
                OutlinedButton(onClick = { vm.disconnectDeye() }, shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Rounded.LinkOff, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.deye_disconnect))
                }
            }
            Text(
                stringResource(R.string.deye_sync_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showConnect) {
        DeyeConnectDialog(
            vm = vm,
            initialDataCenter = link?.dataCenter?.let { runCatching { DeyeDataCenter.valueOf(it) }.getOrNull() },
            onDismiss = { showConnect = false },
            onConnected = { list ->
                showConnect = false
                if (link != null) {
                    // Reconnecting after the session expired: keep the station and sync.
                    scope.launch { runDeyeSync(vm, snackbar, context) {} }
                } else if (list.size == 1) {
                    importing = true
                    scope.launch {
                        runCatching { vm.selectDeyeStation(list.first()) }
                            .onSuccess { snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.deye_synced, it, it)) }
                            .onFailure { snackbar.showSnackbar(context.getString(R.string.deye_error, it.message ?: "")) }
                        importing = false
                    }
                } else if (list.isEmpty()) {
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.deye_no_stations)) }
                } else {
                    stations = list
                }
            },
        )
    }

    if (stations.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { stations = emptyList() },
            title = { Text(stringResource(R.string.deye_choose_station)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stations.forEach { st ->
                        Surface(
                            onClick = {
                                stations = emptyList()
                                importing = true
                                scope.launch {
                                    runCatching { vm.selectDeyeStation(st) }
                                        .onSuccess { snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.deye_synced, it, it)) }
                                        .onFailure { snackbar.showSnackbar(context.getString(R.string.deye_error, it.message ?: "")) }
                                    importing = false
                                }
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(st.name ?: "#${st.id}", style = MaterialTheme.typography.bodyLarge)
                                st.installedCapacity?.let {
                                    Text("${Formatter.input(it)} kWp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { stations = emptyList() }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun DeyeConnectDialog(
    vm: AppViewModel,
    initialDataCenter: DeyeDataCenter?,
    onDismiss: () -> Unit,
    onConnected: (List<DeyeStation>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dataCenter by remember { mutableStateOf(initialDataCenter ?: DeyeDataCenter.EUROPE) }
    var appId by remember { mutableStateOf(vm.deyeAppId.orEmpty()) }
    var appSecret by remember { mutableStateOf("") }
    var login by remember { mutableStateOf(vm.deyeLogin.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showSecrets by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.deye_connect)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.deye_how_to), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.deyecloud.com/start")))
                }) {
                    Text(stringResource(R.string.deye_open_portal))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                }
                Text(stringResource(R.string.deye_data_center), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf(
                        DeyeDataCenter.EUROPE to R.string.deye_dc_europe,
                        DeyeDataCenter.AMERICAS to R.string.deye_dc_americas,
                        DeyeDataCenter.INDIA to R.string.deye_dc_india,
                    )
                    options.forEachIndexed { i, (dc, label) ->
                        SegmentedButton(
                            selected = dataCenter == dc,
                            onClick = { dataCenter = dc },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        colors = pvSegmentedColors(),
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
                val visual = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation()
                val eye: @Composable () -> Unit = {
                    IconButton(onClick = { showSecrets = !showSecrets }) {
                        Icon(if (showSecrets) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                    }
                }
                OutlinedTextField(appId, { appId = it.trim() }, label = { Text("App ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                OutlinedTextField(
                    appSecret, { appSecret = it.trim() }, label = { Text("App Secret") }, singleLine = true,
                    visualTransformation = visual, trailingIcon = eye, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    login, { login = it }, label = { Text(stringResource(R.string.deye_login)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    password, { password = it }, label = { Text(stringResource(R.string.deye_password)) }, singleLine = true,
                    visualTransformation = visual, trailingIcon = eye,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                )
                Text(
                    stringResource(R.string.deye_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && appId.isNotBlank() && appSecret.isNotBlank() && login.isNotBlank() && password.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        runCatching { vm.connectDeye(dataCenter, appId, appSecret, login, password) }
                            .onSuccess { onConnected(it) }
                            .onFailure { error = context.getString(R.string.deye_error, it.message ?: it.javaClass.simpleName) }
                        busy = false
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.deye_connect_button))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}
