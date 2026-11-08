package io.github.c0dewars.pvcalculator.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TextParsingTest {
    @Test
    fun numbers() {
        assertEquals(1234.56, TextParsing.parseNumber("1.234,56")!!, 1e-9)
        assertEquals(1234.56, TextParsing.parseNumber("1,234.56")!!, 1e-9)
        assertEquals(0.578, TextParsing.parseNumber("0,578")!!, 1e-9)
        assertEquals(17.34, TextParsing.parseNumber("17,34")!!, 1e-9)
        assertEquals(1234.0, TextParsing.parseNumber("1.234")!!, 1e-9)
        assertEquals(12.5, TextParsing.parseInput("12,5")!!, 1e-9)
    }

    @Test
    fun money() {
        assertEquals(listOf(0.578, 17.34, 17.34), TextParsing.moneyValues("30 dias 0,578 € 17,34 € 17,34 € 23%"))
        assertEquals(listOf(68.2), TextParsing.moneyValues("Amount due £68.20"))
    }

    @Test
    fun energy() {
        assertEquals(listOf(503.0), TextParsing.energyValues("Energia Simples 30,53 € (0,0607 € x 503kWh)"))
        assertEquals(listOf(12000.0), TextParsing.energyValues("Total 12 MWh"))
    }
}
