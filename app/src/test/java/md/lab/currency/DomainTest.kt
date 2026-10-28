package md.lab.currency

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

fun fixture(day: LocalDate = LocalDate.of(2026,10,28), euro: String = "20"): String =
    """<ValCurs Date="${day.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu"))}"><Valute><CharCode>EUR</CharCode><Nominal>1</Nominal><Value>$euro</Value></Valute><Valute><CharCode>JPY</CharCode><Nominal>100</Nominal><Value>12</Value></Valute></ValCurs>"""

class DomainTest {
    @Test fun parsesRealSourceShapeAndNominal() {
        val quote = Domain.parse(fixture())
        assertEquals(LocalDate.of(2026,10,28), quote.day)
        assertEquals(0, quote.mdlPerUnit["JPY"]!!.compareTo(BigDecimal("0.12")))
        assertEquals(BigDecimal.ONE, quote.mdlPerUnit["MDL"])
    }

    @Test fun rejectsMalformedEmptyAndNonpositiveSources() {
        for(xml in listOf("bad", "<html/>", fixture(euro="0"), fixture(euro="-1"),
            fixture(euro="NaN"), "<ValCurs Date=\"31.02.2026\"/>",
            "<ValCurs Date=\"28.10.2026\"/>")) {
            assertTrue(xml, runCatching { Domain.parse(xml) }.isFailure)
        }
    }

    @Test fun rejectsXmlEntityDefinitions() {
        assertTrue(runCatching { Domain.parse("<!DOCTYPE x [<!ENTITY e 'x'>]><ValCurs/>") }.isFailure)
    }

    @Test fun validatesAmountsIncludingDecimalComma() {
        assertEquals(BigDecimal("10.5"), Domain.amount(" 10,5 "))
        for(value in listOf("", " ", "-1", "0", "text", "NaN", "Infinity", "1e16")) {
            assertTrue(value, runCatching { Domain.amount(value) }.isFailure)
        }
    }

    @Test fun convertsBothDirectionsAndSameCurrency() {
        val quote = Domain.parse(fixture())
        assertEquals(BigDecimal("200.00"), Domain.convert("10", "EUR", "MDL", quote))
        assertEquals(BigDecimal("10.00"), Domain.convert("200", "MDL", "EUR", quote))
        assertEquals(BigDecimal("10.00"), Domain.convert("10", "EUR", "EUR", quote))
        assertEquals(BigDecimal("12.00"), Domain.convert("100", "JPY", "MDL", quote))
    }

    @Test fun roundsHalfUpAndRejectsUnknownCurrency() {
        assertEquals(BigDecimal("1.01"), Domain.convert("1.005", "EUR", "EUR", Domain.parse(fixture())))
        assertTrue(runCatching { Domain.pair(Domain.parse(fixture()), "BAD", "MDL") }.isFailure)
    }

    @Test fun computesStatisticsInChronologicalOrder() {
        val day = LocalDate.of(2026,10,28)
        val points = listOf(Point(day, BigDecimal("110")), Point(day.minusDays(2),BigDecimal("100")),
            Point(day.minusDays(1),BigDecimal("90")))
        val summary = Domain.statistics(points)
        assertEquals(BigDecimal("90"), summary.minimum)
        assertEquals(BigDecimal("110"), summary.maximum)
        assertEquals(0, summary.average.compareTo(BigDecimal("100")))
        assertEquals(0, summary.change.compareTo(BigDecimal("10")))
        assertEquals(0, summary.percent.compareTo(BigDecimal("10")))
        assertEquals("Рост", summary.trend)
    }

    @Test fun handlesFallingFlatSingleAndEmptySeries() {
        val day = LocalDate.of(2026,10,28)
        val falling = Domain.statistics(listOf(Point(day.minusDays(1),BigDecimal("20")),Point(day,BigDecimal("18"))))
        assertEquals("Падение", falling.trend)
        assertEquals(0, falling.percent.compareTo(BigDecimal("-10")))
        val single = Domain.statistics(listOf(Point(day,BigDecimal("1"))))
        assertEquals("Без изменений", single.trend)
        assertEquals(0, single.percent.signum())
        assertTrue(runCatching { Domain.statistics(emptyList()) }.isFailure)
    }
}
