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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Currency
import java.util.Locale

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DataRepository.get(app)
    private val reader = DocumentReader(app)

    val data: StateFlow<AppData> = repo.data

    val summary: StateFlow<Summary> = repo.data
        .map { SavingsCalculator.summarize(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SavingsCalculator.summarize(repo.data.value))

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

    fun saveBill(bill: Bill) = update { d ->
        val others = d.bills.filter { it.id != bill.id && it.month != bill.month }
        d.copy(bills = (others + bill).sortedBy { it.month })
    }

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

    companion object {
        fun defaultCurrency(): String =
            runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "EUR"
    }
}
