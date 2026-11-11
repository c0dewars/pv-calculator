package io.github.c0dewars.pvcalculator.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/*
 * DeyeCloud OpenAPI models for reading inverter settings and controlling battery grid charging.
 * Only hybrid inverters with a battery support these endpoints.
 */

@Serializable
data class DeyeDevice(
    val deviceSn: String,
    val deviceType: String? = null,
    val productId: String? = null,
    val connectStatus: Int? = null,
)

@Serializable
data class DeyeDeviceListResponse(
    val code: JsonElement? = null,
    val msg: String? = null,
    val success: Boolean = false,
    val deviceListItems: List<DeyeDevice> = emptyList(),
)

/** Live values of the station (W and %). */
@Serializable
data class DeyeStationLatest(
    val code: JsonElement? = null,
    val msg: String? = null,
    val success: Boolean = false,
    val batterySOC: Double? = null,
    val batteryPower: Double? = null,
    val chargePower: Double? = null,
    val dischargePower: Double? = null,
    val generationPower: Double? = null,
    val consumptionPower: Double? = null,
    val gridPower: Double? = null,
    val purchasePower: Double? = null,
    val wirePower: Double? = null,
    val lastUpdateTime: JsonElement? = null,
) {
    val lastUpdateEpochSeconds: Long? get() = (lastUpdateTime as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()
}

@Serializable
data class DeyeBatteryConfig(
    val code: JsonElement? = null,
    val msg: String? = null,
    val success: Boolean = false,
    /** Battery capacity (Ah). */
    val battCapacity: Int? = null,
    /** Battery low level (%). */
    val battLowCapacity: Int? = null,
    /** Battery shutdown level (%). */
    val battShutDownCapacity: Int? = null,
    val maxChargeCurrent: Int? = null,
    val maxDischargeCurrent: Int? = null,
)

@Serializable
data class DeyeSystemConfig(
    val code: JsonElement? = null,
    val msg: String? = null,
    val success: Boolean = false,
    val systemWorkMode: String? = null,
    val energyPattern: String? = null,
    val maxSellPower: Int? = null,
    val maxSolarPower: Int? = null,
    val zeroExportPower: Int? = null,
)

/** One of the 6 time-of-use slots. A slot lasts from its [time] until the next slot's time. */
@Serializable
data class TouSlot(
    val time: String,
    val power: Int? = null,
    val soc: Int? = null,
    val enableGridCharge: Boolean = false,
    val enableGeneration: Boolean = true,
    val enableSell: Boolean? = null,
    val voltage: Int? = null,
) {
    fun localTime(): LocalTime? = runCatching { LocalTime.parse(time.padStart(5, '0')) }.getOrNull()
}

@Serializable
data class DeyeTouConfig(
    val code: JsonElement? = null,
    val msg: String? = null,
    val success: Boolean = false,
    val touAction: String? = null,
    val timeUseSettingItems: List<TouSlot> = emptyList(),
)

/** Full "dynamic control" state, read asynchronously through an order. */
@Serializable
data class DeyeDynamicConfig(
    val code: JsonElement? = null,
    val msg: String? = null,
    val success: Boolean = false,
    val gridChargeAction: String? = null,
    val gridChargeAmpere: Int? = null,
    val solarSellAction: String? = null,
    val touAction: String? = null,
    val touDays: List<String>? = null,
    val workMode: String? = null,
    val maxSellPower: Int? = null,
    val maxSolarPower: Int? = null,
    val zeroExportPower: Int? = null,
    val maxChargeCurrent: Int? = null,
    val maxDischargeCurrent: Int? = null,
    val timeUseSettingItems: List<TouSlot>? = null,
)

/** Answer to a control command: the command is executed asynchronously and tracked with [orderId]. */
@Serializable
data class DeyeOrderResponse(
    val code: JsonElement? = null,
    val msg: String? = null,
    val success: Boolean = false,
    val orderId: Long? = null,
    val connectionStatus: Int? = null,
)

@Serializable
data class DeyeOrderStatus(
    val code: JsonElement? = null,
    val msg: String? = null,
    val success: Boolean = false,
    /** 0 to be sent, 100 sending, 300 upgrading, 400 terminated, 500 failed, 666 succeeded. */
    val status: Int? = null,
    val error: String? = null,
) {
    val isDone: Boolean get() = status == SUCCEEDED || status == FAILED || status == TERMINATED
    val succeeded: Boolean get() = status == SUCCEEDED

    companion object {
        const val SUCCEEDED = 666
        const val FAILED = 500
        const val TERMINATED = 400
    }
}

/** Builds the 6-slot time-of-use schedule the inverter needs. */
object TouPlanner {

    const val SLOTS = 6
    private const val STEP_MINUTES = 5
    private const val DAY_MINUTES = 24 * 60

    private fun minutes(t: LocalTime) = t.hour * 60 + t.minute
    private fun format(m: Int) = LocalTime.of((m / 60) % 24, m % 60).toString()

    /** Rounds down to the 5-minute grid the inverter accepts. */
    fun align(t: LocalTime): LocalTime = t.truncatedTo(ChronoUnit.MINUTES).withMinute(t.minute / STEP_MINUTES * STEP_MINUTES)

    fun inWindow(slotStart: Int, start: Int, end: Int): Boolean =
        if (start < end) slotStart in start until end else slotStart >= start || slotStart < end

    /** The slot of [schedule] that is active at minute [m] of the day. */
    private fun activeAt(schedule: List<TouSlot>, m: Int): TouSlot? {
        val timed = schedule.mapNotNull { s -> s.localTime()?.let { minutes(it) to s } }.sortedBy { it.first }
        if (timed.isEmpty()) return null
        return timed.lastOrNull { it.first <= m }?.second ?: timed.last().second
    }

    /**
     * Schedule that charges the battery from the grid between [start] and [end] (may cross midnight)
     * up to [targetSoc], with [power] W. Outside that window the current schedule's battery level and
     * power are kept (or [defaultSoc] / [power] when unknown) and grid charging is off.
     */
    fun gridChargePlan(
        current: List<TouSlot>,
        start: LocalTime,
        end: LocalTime,
        targetSoc: Int,
        power: Int,
        defaultSoc: Int,
    ): List<TouSlot> {
        val s = minutes(align(start))
        val e = minutes(align(end))
        require(s != e) { "Start and end must differ" }

        val points = sortedSetOf(0, s, e)
        while (points.size < SLOTS) {
            // Split the largest gap (cyclic) at its middle, on the 5-minute grid.
            val list = points.toList()
            var bestStart = 0
            var bestLen = -1
            list.forEachIndexed { i, p ->
                val next = if (i == list.lastIndex) DAY_MINUTES else list[i + 1]
                if (next - p > bestLen) {
                    bestLen = next - p
                    bestStart = p
                }
            }
            val mid = bestStart + (bestLen / 2) / STEP_MINUTES * STEP_MINUTES
            if (mid == bestStart || mid in points) break
            points += mid
        }

        return points.toList().take(SLOTS).map { p ->
            val charging = inWindow(p, s, e)
            val existing = activeAt(current, p)
            TouSlot(
                time = format(p),
                power = if (charging) power else existing?.power ?: power,
                soc = if (charging) targetSoc else existing?.soc?.takeIf { !existing.enableGridCharge } ?: defaultSoc,
                enableGridCharge = charging,
                enableGeneration = existing?.enableGeneration ?: true,
                enableSell = existing?.enableSell ?: false,
            )
        }
    }

    /** True when any slot charges the battery from the grid. */
    fun isCharging(schedule: List<TouSlot>): Boolean = schedule.any { it.enableGridCharge }
}
