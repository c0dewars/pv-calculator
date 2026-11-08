package io.github.c0dewars.pvcalculator.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Stores all user data in a JSON file in the app's private storage, plus attachments
 * (bill PDFs, screenshots) in a sibling folder. Nothing ever leaves the device unless
 * the user exports a backup.
 */
class DataRepository private constructor(private val baseDir: File) {

    private val dataFile = File(baseDir, "pvcalculator.json")
    val attachmentsDir: File = File(baseDir, "attachments").apply { mkdirs() }

    private val mutex = Mutex()
    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    init {
        removeOrphanAttachments(_data.value)
    }

    suspend fun update(transform: (AppData) -> AppData) {
        mutex.withLock {
            val old = _data.value
            val new = transform(old)
            if (new == old) return
            _data.value = new
            withContext(Dispatchers.IO) {
                save(new)
                deleteRemovedAttachments(old, new)
            }
        }
    }

    // ---------------------------------------------------------------- attachments

    fun fileFor(attachment: Attachment): File = File(attachmentsDir, attachment.fileName)

    /** Copies [input] into private storage and returns the new attachment (not yet linked to anything). */
    suspend fun storeAttachment(input: InputStream, mimeType: String, originalName: String?): Attachment =
        withContext(Dispatchers.IO) {
            val ext = when {
                mimeType == "application/pdf" -> "pdf"
                mimeType == "image/png" -> "png"
                mimeType == "image/webp" -> "webp"
                mimeType.startsWith("image/") -> "jpg"
                else -> "bin"
            }
            val name = "${UUID.randomUUID()}.$ext"
            input.use { src -> File(attachmentsDir, name).outputStream().use { src.copyTo(it) } }
            Attachment(fileName = name, mimeType = mimeType, originalName = originalName)
        }

    /** Deletes an attachment that was stored but never saved with a bill/entry. */
    suspend fun discardAttachment(attachment: Attachment) = withContext(Dispatchers.IO) {
        if (!isReferenced(_data.value, attachment.fileName)) fileFor(attachment).delete()
    }

    // ---------------------------------------------------------------- backup

    /** Writes a .zip with the data and every attachment. */
    suspend fun exportBackup(out: OutputStream) = withContext(Dispatchers.IO) {
        val snapshot = _data.value
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BACKUP_DATA))
            zip.write(json.encodeToString(AppData.serializer(), snapshot).toByteArray())
            zip.closeEntry()
            referencedAttachments(snapshot).forEach { att ->
                val f = fileFor(att)
                if (f.exists()) {
                    zip.putNextEntry(ZipEntry("attachments/${att.fileName}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Restores a backup created by [exportBackup] (a .zip) or a plain JSON data file. Replaces all data. */
    suspend fun importBackup(bytes: ByteArray) {
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val restored: AppData
        val files = mutableMapOf<String, ByteArray>()
        if (isZip) {
            var dataJson: String? = null
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        when {
                            entry.name == BACKUP_DATA -> dataJson = zip.readBytes().decodeToString()
                            entry.name.startsWith("attachments/") -> {
                                // Only keep the bare file name to avoid writing outside our folder.
                                val safeName = File(entry.name).name
                                if (safeName.isNotBlank()) files[safeName] = zip.readBytes()
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            restored = json.decodeFromString(AppData.serializer(), dataJson ?: error("No data in backup"))
        } else {
            restored = json.decodeFromString(AppData.serializer(), bytes.decodeToString())
        }
        mutex.withLock {
            withContext(Dispatchers.IO) {
                files.forEach { (name, content) -> File(attachmentsDir, name).writeBytes(content) }
                _data.value = restored
                save(restored)
                removeOrphanAttachments(restored)
            }
        }
    }

    suspend fun clearAll() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                _data.value = AppData()
                save(_data.value)
                attachmentsDir.listFiles()?.forEach { it.delete() }
            }
        }
    }

    // ---------------------------------------------------------------- internals

    private fun load(): AppData = try {
        if (dataFile.exists()) json.decodeFromString(AppData.serializer(), dataFile.readText()) else AppData()
    } catch (e: Exception) {
        // Keep a copy of the unreadable file instead of silently losing it.
        dataFile.copyTo(File(baseDir, "pvcalculator-corrupt-${System.currentTimeMillis()}.json"), overwrite = true)
        AppData()
    }

    private fun save(data: AppData) {
        val tmp = File(baseDir, "pvcalculator.json.tmp")
        tmp.writeText(json.encodeToString(AppData.serializer(), data))
        if (!tmp.renameTo(dataFile)) {
            dataFile.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun deleteRemovedAttachments(old: AppData, new: AppData) {
        val still = referencedAttachments(new).map { it.fileName }.toSet()
        referencedAttachments(old).filter { it.fileName !in still }.forEach { fileFor(it).delete() }
    }

    private fun removeOrphanAttachments(data: AppData) {
        val referenced = referencedAttachments(data).map { it.fileName }.toSet()
        attachmentsDir.listFiles()?.filter { it.name !in referenced }?.forEach { it.delete() }
    }

    private fun isReferenced(data: AppData, fileName: String) =
        referencedAttachments(data).any { it.fileName == fileName }

    private fun referencedAttachments(data: AppData): List<Attachment> =
        data.bills.mapNotNull { it.attachment } + data.production.mapNotNull { it.attachment }

    companion object {
        private const val BACKUP_DATA = "pvcalculator.json"

        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

        @Volatile
        private var instance: DataRepository? = null

        fun get(context: Context): DataRepository =
            instance ?: synchronized(this) {
                instance ?: DataRepository(context.applicationContext.filesDir).also { instance = it }
            }

        fun newId(): String = UUID.randomUUID().toString()
    }
}
