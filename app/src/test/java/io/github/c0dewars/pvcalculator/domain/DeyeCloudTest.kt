package io.github.c0dewars.pvcalculator.domain

import io.github.c0dewars.pvcalculator.data.ProductionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DeyeCloudTest {

    @Test
    fun `password hash is lower-case sha256`() {
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", DeyeSync.sha256("test"))
    }

    @Test
    fun `parses token response with Bearer prefix`() {
        val r = DeyeSync.json.decodeFromString(
            DeyeTokenResponse.serializer(),
            """{"code":"1000000","msg":"success","success":true,"accessToken":"Bearer abc.def","expiresIn":"5183999","tokenType":"bearer","uid":3}""",
        )
        assertTrue(r.success)
        assertEquals("abc.def", r.token)
        assertEquals(5183999L, r.expiresInSeconds)
    }

    @Test
    fun `error responses become auth errors`() {
        val r = DeyeSync.json.decodeFromString(
            DeyeStationListResponse.serializer(),
            """{"msg":"auth invalid token","code":"2101019","success":false,"requestId":"0453d52efafb04b1"}""",
        )
        val e = runCatching { DeyeSync.check(r.success, r.code, r.msg) }.exceptionOrNull() as DeyeApiException
        assertTrue(e.isAuthError)
        val bad = DeyeApiException("auth invalid appId", "2101021")
        assertFalse(bad.isAuthError)
    }

    @Test
    fun `parses station list with numeric times`() {
        val r = DeyeSync.json.decodeFromString(
            DeyeStationListResponse.serializer(),
            """{"code":"1000000","msg":"success","success":true,"requestId":null,"total":1,"stationList":[{"id":10,"name":"Casa",
               "locationLat":50.09,"regionTimezone":"Europe/Lisbon","installedCapacity":6.0,"startOperatingTime":1761955200,
               "createdDate":1705304227.0,"batterySOC":0.0,"connectionStatus":"NORMAL","ownerName":null}]}""",
        )
        val st = r.stationList.single()
        assertEquals(10L, st.id)
        assertEquals(LocalDate.of(2025, 11, 1), st.startDate())
    }

    @Test
    fun `daily history becomes production entries with export`() {
        val r = DeyeSync.json.decodeFromString(
            DeyeHistoryResponse.serializer(),
            """{"code":"1000000","msg":"success","success":true,"stationDataItems":[
               {"generationValue":7.6,"gridValue":0.0,"purchaseValue":23.3,"consumptionValue":30.1,"chargeValue":0.8,"dischargeValue":0.0,"year":2026,"month":11,"day":7},
               {"generationValue":4.5,"gridValue":1.2,"purchaseValue":9.5,"year":2026,"month":11,"day":8},
               {"generationValue":null,"year":2026,"month":11,"day":9}]}""",
        )
        val entries = DeyeSync.toEntries(r.stationDataItems)
        assertEquals(2, entries.size)
        assertEquals("2026-11-07", entries[0].date)
        assertEquals(7.6, entries[0].kwh, 1e-9)
        assertEquals(0.0, entries[0].exportedKwh!!, 1e-9)
        assertEquals(1.2, entries[1].exportedKwh!!, 1e-9)
        assertEquals(DeyeSync.SOURCE, entries[1].source)
    }

    @Test
    fun `requests are split in chunks of at most 31 days`() {
        val c = DeyeSync.chunks(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 15))
        assertEquals(LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 2, 1), c[0])
        assertEquals(LocalDate.of(2026, 3, 15), c.last().second)
        assertTrue(c.all { java.time.temporal.ChronoUnit.DAYS.between(it.first, it.second) <= 31 })
    }

    @Test
    fun `synced days replace manual days and whole-month entries`() {
        val manual = listOf(
            ProductionEntry("a", "2026-10-01", 300.0, wholeMonth = true),
            ProductionEntry("b", "2026-11-07", 5.0),
            ProductionEntry("c", "2026-11-06", 4.0),
        )
        val synced = listOf(ProductionEntry("deye-2026-11-07", "2026-11-07", 7.6, source = "deye"))
        val merged = DeyeSync.merge(manual, synced)
        assertEquals(listOf("a", "c", "deye-2026-11-07"), merged.map { it.id })

        val octSynced = listOf(ProductionEntry("deye-2026-10-02", "2026-10-02", 6.0, source = "deye"))
        assertFalse(DeyeSync.merge(manual, octSynced).any { it.id == "a" })
    }
}
