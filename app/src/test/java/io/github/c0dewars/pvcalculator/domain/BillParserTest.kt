package io.github.c0dewars.pvcalculator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BillParserTest {

    /** Synthetic text with the structure of a Portuguese (EDP-like) bill. No real personal data. */
    private val portugueseBill = """
        JOANA EXEMPLO
        RUA DE TESTE, 1
        Documento emitido a: 27 de outubro 2026
        Período de faturação: 25 de setembro a 24 de outubro 2026
        Quanto tenho
        a pagar?
        108,10 €
        Eletricidade Gás Natural Serviços Taxas, Impostos e Débito na minha
        88,79 € Aderir Aderir 19,31 € D
        Sede social Avenida 24 de Julho, n.º 12 1249-300 Lisboa  Capital social: € 64 500 005
        Potência, Tarifa e Ciclo Horário
        6,9 kVA - Simples - Sem ciclo
        Fatura nº FT2026 X/1 De: 27 de outubro 2026 Valor: 105,08 €
        25 set a 24 out 2026 200 kWh 0,1671 € 33,42 € 15% (-5,01 €) 28,41 € 6%
        25 set a 24 out 2026 303 kWh 0,1671 € 50,63 € 15% (-7,59 €) 43,04 € 23%
        o seu consumo real entre estas datas foi 111 kWh (4652 - 4541) em vazio, 130 kWh (2250 - 2120) em ponta e 262 kWh (5061 - 4799) em
        cheias. o consumo medido é somado e faturado do seguinte modo: simples 503 kWh (111 + 130 + 262).
        Potência (6,9 kVA)
        25 set a 24 out 2026 30 dias 0,578 € 17,34 € 17,34 € 23%
        Potência 10,31 € (0,3436 € x 30dias)
        Energia Simples 30,53 € (0,0607 € x 503kWh)
        IEC 503 kWh 0,001 € 0,50 € 0,50 € 23%
        com consumo anual inferior a 400 kWh ou com atividades exclusivamente agrícolas.
        Em Portugal continental a taxa está fixa em 0,001 € por kWh.
        2331 kWh, o que equivale à emissão de cerca de 0,393 ton de CO2.
    """.trimIndent()

    @Test
    fun `reads a Portuguese bill`() {
        val r = BillParser.parse(portugueseBill, today = LocalDate.of(2026, 11, 8))
        assertEquals(YearMonth.of(2026, 10), r.month)
        assertEquals(LocalDate.of(2026, 9, 25), r.periodStart)
        assertEquals(LocalDate.of(2026, 10, 24), r.periodEnd)
        assertEquals(108.10, r.amount!!, 1e-9)
        assertEquals(503.0, r.importedKwh!!, 1e-9)
        assertEquals(17.34, r.fixedCharges!!, 1e-9)
        assertNull(r.exportedKwh)
    }

    @Test
    fun `reads an English bill with export`() {
        val text = """
            Billing period 01/03/2026 - 31/03/2026
            Electricity used 245.6 kWh
            Standing charge 31 days x £0.53 £16.43
            Exported to grid 120 kWh
            Amount due £68.20
        """.trimIndent()
        val r = BillParser.parse(text)
        assertEquals(YearMonth.of(2026, 3), r.month)
        assertEquals(68.20, r.amount!!, 1e-9)
        assertEquals(245.6, r.importedKwh!!, 1e-9)
        assertEquals(120.0, r.exportedKwh!!, 1e-9)
        assertEquals(16.43, r.fixedCharges!!, 1e-9)
    }

    @Test
    fun `sums time of use periods when there is no total`() {
        val text = """
            Período de faturação: 01/01/2026 a 31/01/2026
            Vazio 150 kWh 0,10 €
            Fora de vazio 250 kWh 0,20 €
            Total a pagar 80,00 €
        """.trimIndent()
        val r = BillParser.parse(text)
        assertEquals(400.0, r.importedKwh!!, 1e-9)
        assertEquals(80.0, r.amount!!, 1e-9)
        assertEquals(YearMonth.of(2026, 1), r.month)
    }
}
