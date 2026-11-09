package io.github.c0dewars.pvcalculator.domain

import io.github.c0dewars.pvcalculator.data.ProductionEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Models and pure logic for the DeyeCloud OpenAPI (https://developer.deyecloud.com).
 * Networking lives in data/DeyeCloudClient.kt.
 */
enum class DeyeDataCenter(val baseUrl: String) {
    EUROPE("https://eu1-developer.deyecloud.com"),
    AMERICAS("https://us1-developer.deyecloud.com"),
    INDIA("https://india-developer.deyecloud.com"),
}

/** Values may come as strings or numbers depending on the endpoint, so a few fields are kept as raw JSON. */
private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.content

@Serializable
data class DeyeTokenResponse(
    val code: JsonElement? = null,
    val msg: String? = null,
    val success: Boolean = false,
    val accessToken: String? = null,
    val expiresIn: JsonElement? = null,
) {
    /** Token without the "Bearer " prefix the API sometimes includes. */
    val token: String? get() = accessToken?.trim()?.removePrefix("Bearer ")?.removePrefix("bearer ")?.trim()
    val expiresInSeconds: Long get() = expiresIn.text()?.toDoubleOrNull()?.toLong() ?: (60L * 24 * 3600)
}

@Serializable
data class DeyeStation(
    val id: Long,
    val name: String? = null,
    val installedCapacity: Double? = null,
    val startOperatingTime: JsonElement? = null,
    val regionTimezone: String? = null,
) {
    /** Date the station started producing, if the API reports it (epoch seconds). */
    fun startDate(): LocalDate? = startOperatingTime.text()?.toDoubleOrNull()?.takeIf { it > 0 }?.let { secs ->
        val zone = runCatching { ZoneId.of(regionTimezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))
        Instant.ofEpochSecond(secs.toLong()).atZone(zone).toLocalDate()
    }
}

@Serializable
data class DeyeStationListResponse(
    val code: JsonElement? = null,
    val msg: String? = null,
    val success: Boolean = false,
    val stationList: List<DeyeStation> = emptyList(),
    val total: Int? = null,
)

@Serializable
data class DeyeDataItem(
    /** Energy produced (kWh). */
    val generationValue: Double? = null,
    /** Energy fed into the grid (kWh). */
    val gridValue: Double? = null,
    /** Energy bought from the grid (kWh). */
    val purchaseValue: Double? = null,
    val consumptionValue: Double? = null,
    val chargeValue: Double? = null,
    val dischargeValue: Double? = null,
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
) {
    fun date(): LocalDate? {
        val y = year ?: return null
        val m = month?.takeIf { it in 1..12 } ?: return null
        val d = day?.takeIf { it in 1..31 } ?: return null
        return runCatching { LocalDate.of(y, m, d) }.getOrNull()
    }
}

@Serializable
data class DeyeHistoryResponse(
    val code: JsonElement? = null,
    val msg: String? = null,
    val success: Boolean = false,
    val stationDataItems: List<DeyeDataItem> = emptyList(),
)

class DeyeApiException(message: String, val code: String? = null) : Exception(message) {
    /** The token is missing, invalid or expired: the user has to sign in again. */
    val isAuthError: Boolean
        get() = code == "2101019" || message?.contains("invalid token", ignoreCase = true) == true ||
            message?.startsWith("Unauthorized") == true
}

object DeyeSync {

    const val SOURCE = "deye"

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    /** DeyeCloud expects the password hashed with SHA-256, lower-case hex. */
    fun sha256(password: String): String =
        MessageDigest.getInstance("SHA-256").digest(password.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Splits [from, toExclusive) into ranges of at most 31 days, the maximum allowed per daily request. */
    fun chunks(from: LocalDate, toExclusive: LocalDate, maxDays: Long = 31): List<Pair<LocalDate, LocalDate>> {
        val out = mutableListOf<Pair<LocalDate, LocalDate>>()
        var start = from
        while (start < toExclusive) {
            val end = minOf(start.plusDays(maxDays), toExclusive)
            out += start to end
            start = end
        }
        return out
    }

    /** Converts daily items into production entries (one per day, stable ids so re-syncing updates them). */
    fun toEntries(items: List<DeyeDataItem>): List<ProductionEntry> =
        items.mapNotNull { item ->
            val date = item.date() ?: return@mapNotNull null
            val kwh = item.generationValue ?: return@mapNotNull null
            if (kwh < 0) return@mapNotNull null
            ProductionEntry(
                id = "$SOURCE-$date",
                date = date.toString(),
                kwh = kwh,
                exportedKwh = item.gridValue?.coerceIn(0.0, kwh),
                source = SOURCE,
            )
        }.associateBy { it.date }.values.sortedBy { it.date }

    /**
     * Adds synced days to the existing production. Synced data is measured, so it replaces manual
     * entries for the same days, and whole-month manual entries for the months it covers
     * (otherwise the month would be counted twice).
     */
    fun merge(current: List<ProductionEntry>, synced: List<ProductionEntry>): List<ProductionEntry> {
        if (synced.isEmpty()) return current
        val days = synced.map { it.date }.toSet()
        val months = synced.mapNotNull { SavingsCalculator.parseDate(it.date)?.let(YearMonth::from) }.toSet()
        val kept = current.filter { e ->
            val month = SavingsCalculator.parseDate(e.date)?.let(YearMonth::from)
            when {
                e.wholeMonth -> month !in months
                else -> e.date !in days
            }
        }
        return (kept + synced).sortedBy { it.date }
    }

    fun check(success: Boolean, code: JsonElement?, msg: String?) {
        if (!success) throw DeyeApiException(msg?.takeIf { it.isNotBlank() } ?: "DeyeCloud error", code?.text())
    }
}
