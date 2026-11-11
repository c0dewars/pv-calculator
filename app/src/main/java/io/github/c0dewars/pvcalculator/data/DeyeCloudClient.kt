package io.github.c0dewars.pvcalculator.data

import android.content.Context
import io.github.c0dewars.pvcalculator.domain.DeyeApiException
import io.github.c0dewars.pvcalculator.domain.DeyeDataCenter
import io.github.c0dewars.pvcalculator.domain.DeyeDataItem
import io.github.c0dewars.pvcalculator.domain.DeyeHistoryResponse
import io.github.c0dewars.pvcalculator.domain.DeyeStation
import io.github.c0dewars.pvcalculator.domain.DeyeStationListResponse
import io.github.c0dewars.pvcalculator.domain.DeyeSync
import io.github.c0dewars.pvcalculator.domain.DeyeTokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.github.c0dewars.pvcalculator.domain.DeyeBatteryConfig
import io.github.c0dewars.pvcalculator.domain.DeyeDevice
import io.github.c0dewars.pvcalculator.domain.DeyeDeviceListResponse
import io.github.c0dewars.pvcalculator.domain.DeyeDynamicConfig
import io.github.c0dewars.pvcalculator.domain.DeyeOrderResponse
import io.github.c0dewars.pvcalculator.domain.DeyeOrderStatus
import io.github.c0dewars.pvcalculator.domain.DeyeStationLatest
import io.github.c0dewars.pvcalculator.domain.DeyeSystemConfig
import io.github.c0dewars.pvcalculator.domain.DeyeTouConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/** Minimal client for the DeyeCloud OpenAPI endpoints the app needs. */
class DeyeCloudClient(private val dataCenter: DeyeDataCenter) {

    /** [login] is an e-mail address or a username; [passwordSha256] is the hashed password. */
    suspend fun obtainToken(appId: String, appSecret: String, login: String, passwordSha256: String): DeyeTokenResponse {
        val body = buildJsonObject {
            put("appSecret", appSecret)
            put(if ('@' in login) "email" else "username", login.trim())
            put("password", passwordSha256)
        }
        val path = "/v1.0/account/token?appId=" + URLEncoder.encode(appId.trim(), "UTF-8")
        val r = DeyeSync.json.decodeFromString(DeyeTokenResponse.serializer(), post(path, body, token = null))
        DeyeSync.check(r.success, r.code, r.msg)
        if (r.token.isNullOrBlank()) throw DeyeApiException(r.msg ?: "No token received")
        return r
    }

    suspend fun stations(token: String): List<DeyeStation> {
        val all = mutableListOf<DeyeStation>()
        var page = 1
        while (page <= 10) {
            val body = buildJsonObject {
                put("page", page)
                put("size", 100)
            }
            val r = DeyeSync.json.decodeFromString(DeyeStationListResponse.serializer(), post("/v1.0/station/list", body, token))
            DeyeSync.check(r.success, r.code, r.msg)
            all += r.stationList
            if (r.stationList.size < 100 || all.size >= (r.total ?: 0)) break
            page++
        }
        return all
    }

    /** Daily values for [from, toExclusive), at most 31 days. */
    suspend fun dailyHistory(token: String, stationId: Long, from: LocalDate, toExclusive: LocalDate): List<DeyeDataItem> {
        val body = buildJsonObject {
            put("stationId", stationId)
            put("granularity", 2)
            put("startAt", from.toString())
            put("endAt", toExclusive.toString())
        }
        val r = DeyeSync.json.decodeFromString(DeyeHistoryResponse.serializer(), post("/v1.0/station/history", body, token))
        DeyeSync.check(r.success, r.code, r.msg)
        return r.stationDataItems
    }

    // ------------------------------------------------------------------ inverter (hybrid + battery)

    /** Devices of a station; the hybrid inverter is the one with deviceType INVERTER. */
    suspend fun devices(token: String, stationId: Long): List<DeyeDevice> {
        val body = buildJsonObject {
            put("page", 1)
            put("size", 50)
            put("stationIds", buildJsonArray { add(stationId) })
        }
        val r = DeyeSync.json.decodeFromString(DeyeDeviceListResponse.serializer(), post("/v1.0/station/device", body, token))
        DeyeSync.check(r.success, r.code, r.msg)
        return r.deviceListItems
    }

    suspend fun stationLatest(token: String, stationId: Long): DeyeStationLatest {
        val r = DeyeSync.json.decodeFromString(
            DeyeStationLatest.serializer(),
            post("/v1.0/station/latest", buildJsonObject { put("stationId", stationId) }, token),
        )
        DeyeSync.check(r.success, r.code, r.msg)
        return r
    }

    suspend fun batteryConfig(token: String, deviceSn: String): DeyeBatteryConfig {
        val r = DeyeSync.json.decodeFromString(DeyeBatteryConfig.serializer(), post("/v1.0/config/battery", sn(deviceSn), token))
        DeyeSync.check(r.success, r.code, r.msg)
        return r
    }

    suspend fun systemConfig(token: String, deviceSn: String): DeyeSystemConfig {
        val r = DeyeSync.json.decodeFromString(DeyeSystemConfig.serializer(), post("/v1.0/config/system", sn(deviceSn), token))
        DeyeSync.check(r.success, r.code, r.msg)
        return r
    }

    suspend fun touConfig(token: String, deviceSn: String): DeyeTouConfig {
        val r = DeyeSync.json.decodeFromString(DeyeTouConfig.serializer(), post("/v1.0/config/tou", sn(deviceSn), token))
        DeyeSync.check(r.success, r.code, r.msg)
        return r
    }

    /**
     * Reads the full dynamic-control state (grid charge on/off, current, TOU...). The inverter answers
     * asynchronously, so this sends a read order and polls its result for up to [timeoutMs].
     */
    suspend fun dynamicConfig(token: String, deviceSn: String, timeoutMs: Long = 45_000): DeyeDynamicConfig? {
        val order = DeyeSync.json.decodeFromString(
            DeyeOrderResponse.serializer(),
            post("/v1.0/strategy/dynamicControl/read", sn(deviceSn), token),
        )
        DeyeSync.check(order.success, order.code, order.msg)
        val orderId = order.orderId ?: return null
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(3000)
            val text = post("/v1.0/strategy/dynamicControl/readResult", buildJsonObject { put("orderId", orderId) }, token)
            val r = runCatching { DeyeSync.json.decodeFromString(DeyeDynamicConfig.serializer(), text) }.getOrNull() ?: continue
            if (r.success && (r.gridChargeAction != null || r.timeUseSettingItems != null || r.workMode != null)) return r
        }
        return null
    }

    /**
     * Sends a dynamic-control command. Only the given fields change on the inverter; everything else
     * keeps its value. Returns the order id to follow with [waitForOrder].
     */
    suspend fun dynamicControl(token: String, deviceSn: String, fields: JsonObject): Long {
        val body = JsonObject(mapOf("deviceSn" to JsonPrimitive(deviceSn)) + fields)
        val r = DeyeSync.json.decodeFromString(DeyeOrderResponse.serializer(), post("/v1.0/strategy/dynamicControl", body, token))
        DeyeSync.check(r.success, r.code, r.msg)
        return r.orderId ?: throw DeyeApiException(r.msg ?: "No order id")
    }

    /** Polls a command until the inverter reports success or failure (or [timeoutMs] passes). */
    suspend fun waitForOrder(token: String, orderId: Long, timeoutMs: Long = 90_000): DeyeOrderStatus? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: DeyeOrderStatus? = null
        while (System.currentTimeMillis() < deadline) {
            delay(3000)
            last = runCatching {
                DeyeSync.json.decodeFromString(DeyeOrderStatus.serializer(), request("GET", "/v1.0/order/$orderId", null, token))
            }.getOrNull() ?: last
            if (last?.isDone == true) return last
        }
        return last
    }

    private fun sn(deviceSn: String) = buildJsonObject { put("deviceSn", deviceSn) }

    private suspend fun post(path: String, body: JsonObject, token: String?): String = request("POST", path, body, token)

    private suspend fun request(method: String, path: String, body: JsonObject?, token: String?): String = withContext(Dispatchers.IO) {
        var attempt = 0
        while (true) {
            val conn = (URL(dataCenter.baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 30_000
                doOutput = body != null
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (token != null) setRequestProperty("Authorization", "bearer $token")
            }
            try {
                if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val status = conn.responseCode
                // Rate limited or temporary server error: back off and retry a couple of times.
                if ((status == 429 || status >= 500) && attempt < 2) {
                    attempt++
                    delay(2000L * attempt)
                    continue
                }
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status == 401 || status == 403) throw DeyeApiException("Unauthorized ($status)")
                if (status !in 200..299 && text.isBlank()) throw IOException("HTTP $status")
                return@withContext text
            } finally {
                conn.disconnect()
            }
        }
        @Suppress("UNREACHABLE_CODE")
        ""
    }
}

/**
 * DeyeCloud credentials, kept in private storage only. They are never included in backups
 * (see res/xml/backup_rules.xml), never exported, and the password itself is never stored.
 */
class DeyeCredentials(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var appId: String?
        get() = prefs.getString("appId", null)
        set(v) = prefs.edit().putString("appId", v).apply()
    var appSecret: String?
        get() = prefs.getString("appSecret", null)
        set(v) = prefs.edit().putString("appSecret", v).apply()
    var login: String?
        get() = prefs.getString("login", null)
        set(v) = prefs.edit().putString("login", v).apply()
    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()
    var tokenExpiresAt: Long
        get() = prefs.getLong("tokenExpiresAt", 0)
        set(v) = prefs.edit().putLong("tokenExpiresAt", v).apply()

    val hasValidToken: Boolean get() = !token.isNullOrBlank() && System.currentTimeMillis() < tokenExpiresAt - 3_600_000

    fun clear() = prefs.edit().clear().apply()

    companion object {
        const val FILE = "deye_credentials"
    }
}
