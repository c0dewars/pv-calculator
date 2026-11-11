package io.github.c0dewars.pvcalculator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TouPlannerTest {

    private val current = listOf(
        TouSlot("00:00", power = 5000, soc = 30), TouSlot("04:00", power = 5000, soc = 30),
        TouSlot("08:00", power = 5000, soc = 15), TouSlot("12:00", power = 5000, soc = 15),
        TouSlot("16:00", power = 5000, soc = 20), TouSlot("20:00", power = 5000, soc = 25),
    )

    private fun t(s: String) = LocalTime.parse(s)

    @Test
    fun `night window inside the day`() {
        val plan = TouPlanner.gridChargePlan(current, t("02:00"), t("06:00"), targetSoc = 90, power = 3000, defaultSoc = 20)
        assertEquals(6, plan.size)
        assertEquals(plan.map { it.time }.sorted(), plan.map { it.time })
        assertEquals(listOf("00:00", "02:00", "06:00"), plan.take(3).map { it.time })
        val charging = plan.filter { it.enableGridCharge }
        assertEquals(listOf("02:00"), charging.map { it.time })
        assertEquals(90, charging.single().soc)
        assertEquals(3000, charging.single().power)
        // Outside the window the existing battery levels are kept.
        assertEquals(30, plan.first { it.time == "00:00" }.soc)
        assertEquals(30, plan.first { it.time == "06:00" }.soc)
    }

    @Test
    fun `window crossing midnight`() {
        val plan = TouPlanner.gridChargePlan(current, t("22:00"), t("07:00"), targetSoc = 100, power = 4000, defaultSoc = 20)
        assertEquals(6, plan.size)
        val charging = plan.filter { it.enableGridCharge }.map { it.time }
        assertTrue("00:00" in charging)
        assertTrue("22:00" in charging)
        assertFalse(plan.first { it.time == "07:00" }.enableGridCharge)
        // Every slot that starts inside 22:00-07:00 charges, every other slot doesn't.
        plan.forEach { s ->
            val m = s.localTime()!!.let { it.hour * 60 + it.minute }
            assertEquals(s.time, m >= 22 * 60 || m < 7 * 60, s.enableGridCharge)
        }
    }

    @Test
    fun `times are aligned to 5 minutes and unique`() {
        val plan = TouPlanner.gridChargePlan(emptyList(), t("01:03"), t("05:58"), targetSoc = 80, power = 2000, defaultSoc = 20)
        assertEquals(6, plan.toSet().map { it.time }.size)
        assertTrue(plan.all { it.localTime()!!.minute % 5 == 0 })
        assertTrue(plan.any { it.time == "01:00" && it.enableGridCharge })
        assertTrue(plan.any { it.time == "05:55" && !it.enableGridCharge })
        // Without a current schedule the default level is used outside the window.
        assertTrue(plan.filter { !it.enableGridCharge }.all { it.soc == 20 })
    }

    @Test
    fun `window starting at midnight`() {
        val plan = TouPlanner.gridChargePlan(current, t("00:00"), t("05:00"), targetSoc = 70, power = 3000, defaultSoc = 20)
        assertEquals(6, plan.size)
        assertTrue(plan.first().enableGridCharge)
        // A window can be split in several slots: exactly the slots starting before 05:00 charge.
        plan.forEach { s -> assertEquals(s.time, s.localTime()!!.isBefore(t("05:00")), s.enableGridCharge) }
    }

    @Test
    fun `slots sent to the inverter include every flag`() {
        val json = DeyeSync.controlJson.encodeToString(TouSlot.serializer(), TouSlot("02:00", power = 3000, soc = 90, enableGridCharge = false, enableSell = false))
        assertEquals(
            "{\"time\":\"02:00\",\"power\":3000,\"soc\":90,\"enableGridCharge\":false,\"enableGeneration\":true,\"enableSell\":false}",
            json,
        )
    }

    @Test
    fun `parses inverter config responses`() {
        val tou = DeyeSync.json.decodeFromString(
            DeyeTouConfig.serializer(),
            """{"code":"1000000","success":true,"touAction":"on","timeUseSettingItems":[{"time":"00:00","power":5000,"soc":30,"enableGridCharge":true,"enableGeneration":false}]}""",
        )
        assertTrue(TouPlanner.isCharging(tou.timeUseSettingItems))
        val order = DeyeSync.json.decodeFromString(DeyeOrderStatus.serializer(), """{"success":true,"status":666}""")
        assertTrue(order.isDone && order.succeeded)
        val latest = DeyeSync.json.decodeFromString(DeyeStationLatest.serializer(), """{"success":true,"batterySOC":63.0,"batteryPower":-1200.5,"lastUpdateTime":1762700000.0}""")
        assertEquals(1762700000L, latest.lastUpdateEpochSeconds)
    }
}
