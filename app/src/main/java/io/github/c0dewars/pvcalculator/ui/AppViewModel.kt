package io.github.c0dewars.pvcalculator.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.c0dewars.pvcalculator.data.AppData
import io.github.c0dewars.pvcalculator.data.Attachment
import io.github.c0dewars.pvcalculator.data.Bill
import io.github.c0dewars.pvcalculator.data.DataRepository
import io.github.c0dewars.pvcalculator.data.DeyeCloudClient
import io.github.c0dewars.pvcalculator.data.DeyeCredentials
import io.github.c0dewars.pvcalculator.data.DeyeLink
import io.github.c0dewars.pvcalculator.domain.DeyeApiException
import io.github.c0dewars.pvcalculator.domain.DeyeDataCenter
import io.github.c0dewars.pvcalculator.domain.DeyeStation
import io.github.c0dewars.pvcalculator.domain.DeyeSync
import io.github.c0dewars.pvcalculator.data.DocumentReader
import io.github.c0dewars.pvcalculator.data.ExtraInvestment
import io.github.c0dewars.pvcalculator.data.ProductionEntry
import io.github.c0dewars.pvcalculator.data.SolarSystem
import io.github.c0dewars.pvcalculator.data.Tariffs
import io.github.c0dewars.pvcalculator.domain.BillParseResult
import io.github.c0dewars.pvcalculator.domain.BillParser
import io.github.c0dewars.pvcalculator.domain.CsvImporter
import io.github.c0dewars.pvcalculator.domain.ProductionParseResult
import io.github.c0dewars.pvcalculator.domain.ProductionParser
import io.github.c0dewars.pvcalculator.domain.SavingsCalculator
import io.github.c0dewars.pvcalculator.domain.Summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Currency
import java.util.Locale

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DataRepository.get(app)
    private val reader = DocumentReader(app)
    private val deyeCredentials = DeyeCredentials(app)

    /** State of the DeyeCloud sync shown in the UI. */
    private val _deyeSyncing = MutableStateFlow(false)
    val deyeSyncing: StateFlow<Boolean> = _deyeSyncing

    val data: StateFlow<AppData> = repo.data

    val summary: StateFlow<Summary> = repo.data
        .map { SavingsCalculator.summarize(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SavingsCalculator.summarize(repo.data.value))

    init {
        // Keep production up to date automatically (at most every 6 hours, only if connected).
        viewModelScope.launch {
            val link = repo.data.value.deye ?: return@launch
            val last = link.lastSync?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            if (deyeCredentials.hasValidToken && (last == null || last.isBefore(LocalDateTime.now().minusHours(6)))) {
                runCatching { syncDeye() }
            }
        }
    }

    val currencyCode: String
        get() = data.value.currencyCode ?: defaultCurrency()

    fun fileFor(attachment: Attachment): File = repo.fileFor(attachment)

    // ------------------------------------------------------------------ edits

    private fun update(transform: (AppData) -> AppData) {
        viewModelScope.launch { repo.update(transform) }
    }

    fun saveProduction(entry: ProductionEntry) = update { d ->
        // One value per day (or per month for whole-month entries).
        val others = d.production.filter {
            it.id != entry.id && !(it.date == entry.date && it.wholeMonth == entry.wholeMonth)
        }
        d.copy(production = (others + entry).sortedBy { it.date })
    }

    fun deleteProduction(id: String) = update { d -> d.copy(production = d.production.filter { it.id != id }) }

    /**
     * Saves a bill. When it is the most recent bill, its price per kWh also becomes the default
     * electricity price in Settings, so months without a bill use the latest known price.
     * Returns the new default price, or null if it was not changed.
     */
    fun saveBill(bill: Bill): Double? {
        val newPrice = latestBillPrice(data.value, bill)
        update { d ->
            val others = d.bills.filter { it.id != bill.id && it.month != bill.month }
            val tariffs = latestBillPrice(d, bill)?.let { d.tariffs.copy(gridPricePerKwh = it) } ?: d.tariffs
            d.copy(bills = (others + bill).sortedBy { it.month }, tariffs = tariffs)
        }
        return newPrice
    }

    private fun latestBillPrice(d: AppData, bill: Bill): Double? = SavingsCalculator.priceFromLatestBill(d.bills, bill)

    fun deleteBill(id: String) = update { d -> d.copy(bills = d.bills.filter { it.id != id }) }

    fun updateSystem(transform: (SolarSystem) -> SolarSystem) = update { it.copy(system = transform(it.system)) }

    fun updateTariffs(transform: (Tariffs) -> Tariffs) = update { it.copy(tariffs = transform(it.tariffs)) }

    fun saveExtraInvestment(item: ExtraInvestment) = updateSystem { s ->
        s.copy(extraInvestments = (s.extraInvestments.filter { it.id != item.id } + item).sortedBy { it.date ?: "" })
    }

    fun deleteExtraInvestment(id: String) = updateSystem { s ->
        s.copy(extraInvestments = s.extraInvestments.filter { it.id != id })
    }

    fun setCurrency(code: String) = update { it.copy(currencyCode = code) }

    fun clearAll() {
        viewModelScope.launch { repo.clearAll() }
    }

    // ------------------------------------------------------------------ attachments

    suspend fun storeAttachment(uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        runCatching {
            val mime = DocumentReader.mimeTypeOf(ctx, uri)
            val input = ctx.contentResolver.openInputStream(uri) ?: return@runCatching null
            repo.storeAttachment(input, mime, displayName(uri))
        }.getOrNull()
    }

    suspend fun discardAttachment(attachment: Attachment) = repo.discardAttachment(attachment)

    suspend fun analyzeBill(attachment: Attachment): BillParseResult =
        withContext(Dispatchers.Default) {
            BillParser.parse(reader.readText(repo.fileFor(attachment), attachment.mimeType))
        }

    suspend fun analyzeProduction(attachment: Attachment): ProductionParseResult =
        withContext(Dispatchers.Default) {
            ProductionParser.parseRows(reader.readRows(repo.fileFor(attachment), attachment.mimeType))
        }

    // ------------------------------------------------------------------ import / export

    /** Returns (imported, skipped) or null on failure. */
    suspend fun importCsv(uri: Uri): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        runCatching {
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@runCatching null
            val result = CsvImporter.parse(text, DataRepository::newId)
            if (result.entries.isNotEmpty()) {
                repo.update { d ->
                    val newDates = result.entries.map { it.date }.toSet()
                    val kept = d.production.filter { it.wholeMonth || it.date !in newDates }
                    d.copy(production = (kept + result.entries).sortedBy { it.date })
                }
            }
            result.entries.size to result.skippedLines
        }.getOrNull()
    }

    suspend fun exportBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { repo.exportBackup(it) } ?: error("no stream")
            true
        }.getOrDefault(false)
    }

    suspend fun importBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        runCatching {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("no stream")
            repo.importBackup(bytes)
            true
        }.getOrDefault(false)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    // ------------------------------------------------------------------ DeyeCloud

    val deyeLogin: String? get() = deyeCredentials.login
    val deyeAppId: String? get() = deyeCredentials.appId
    val deyeTokenValid: Boolean get() = deyeCredentials.hasValidToken

    /** Logs in and returns the stations of the account. Credentials are saved only if it works. */
    suspend fun connectDeye(
        dataCenter: DeyeDataCenter,
        appId: String,
        appSecret: String,
        login: String,
        password: String,
    ): List<DeyeStation> {
        val client = DeyeCloudClient(dataCenter)
        val token = client.obtainToken(appId, appSecret, login, DeyeSync.sha256(password))
        val stations = client.stations(token.token!!)
        deyeCredentials.appId = appId.trim()
        deyeCredentials.appSecret = appSecret.trim()
        deyeCredentials.login = login.trim()
        deyeCredentials.token = token.token
        deyeCredentials.tokenExpiresAt = System.currentTimeMillis() + token.expiresInSeconds * 1000
        pendingDataCenter = dataCenter
        return stations
    }

    private var pendingDataCenter: DeyeDataCenter? = null

    /** Links the chosen station and imports its history. Returns the number of days imported. */
    suspend fun selectDeyeStation(station: DeyeStation): Int {
        val dc = pendingDataCenter ?: data.value.deye?.let { DeyeDataCenter.valueOf(it.dataCenter) } ?: DeyeDataCenter.EUROPE
        repo.update { d ->
            d.copy(
                deye = DeyeLink(dataCenter = dc.name, stationId = station.id, stationName = station.name),
                system = d.system.copy(
                    installDate = d.system.installDate ?: station.startDate()?.toString(),
                    capacityKwp = d.system.capacityKwp ?: station.installedCapacity?.takeIf { it > 0 },
                ),
            )
        }
        return syncDeye()
    }

    /** Downloads daily production since the last sync (or since installation). Returns days imported. */
    suspend fun syncDeye(): Int {
        val link = data.value.deye ?: throw DeyeApiException("Not connected")
        val token = deyeCredentials.token?.takeIf { deyeCredentials.hasValidToken } ?: throw DeyeTokenExpired()
        if (_deyeSyncing.value) return 0
        _deyeSyncing.value = true
        try {
            val client = DeyeCloudClient(DeyeDataCenter.valueOf(link.dataCenter))
            val today = LocalDate.now()
            val from = link.lastSyncedDay?.let(SavingsCalculator::parseDate)?.minusDays(2)
                ?: data.value.system.installDate?.let(SavingsCalculator::parseDate)
                ?: today.minusMonths(12).withDayOfMonth(1)
            val items = mutableListOf<io.github.c0dewars.pvcalculator.domain.DeyeDataItem>()
            DeyeSync.chunks(from, today.plusDays(1)).forEachIndexed { i, (start, end) ->
                if (i > 0) delay(300) // be gentle with the API rate limits
                items += client.dailyHistory(token, link.stationId, start, end)
            }
            val entries = DeyeSync.toEntries(items)
            repo.update { d ->
                d.copy(
                    production = DeyeSync.merge(d.production, entries),
                    deye = d.deye?.copy(lastSync = LocalDateTime.now().withNano(0).toString(), lastSyncedDay = today.toString()),
                )
            }
            return entries.size
        } catch (e: DeyeApiException) {
            if (e.isAuthError) {
                deyeCredentials.token = null
                throw DeyeTokenExpired()
            }
            throw e
        } finally {
            _deyeSyncing.value = false
        }
    }

    /** Removes the link and credentials. Synced production entries are kept. */
    fun disconnectDeye() {
        deyeCredentials.clear()
        update { it.copy(deye = null) }
    }

    class DeyeTokenExpired : Exception("DeyeCloud session expired")

    companion object {
        fun defaultCurrency(): String =
            runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "EUR"
    }
}
