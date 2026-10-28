package md.lab.currency

import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import javax.xml.parsers.DocumentBuilderFactory

private val mc = MathContext.DECIMAL128
private val sourceDate = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
data class Quote(val day: LocalDate, val mdlPerUnit: Map<String, BigDecimal>)
data class Point(val day: LocalDate, val value: BigDecimal)
data class Summary(val minimum: BigDecimal, val maximum: BigDecimal, val average: BigDecimal,
                   val change: BigDecimal, val percent: BigDecimal, val trend: String)
class EmptyQuote : IllegalArgumentException("Источник не опубликовал курсы.")

object Domain {
    fun amount(text: String): BigDecimal {
        val number = text.trim().replace(',', '.').toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Введите сумму числом.")
        require(number > BigDecimal.ZERO && number <= BigDecimal("1e15")) {
            "Сумма должна быть больше нуля и не превышать 10¹⁵."
        }
        return number
    }

    fun parse(xml: String): Quote {
        require(xml.length <= 2_000_000 && !xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) {
            "Недопустимый XML источника."
        }
        val factory = DocumentBuilderFactory.newInstance()
        factory.isExpandEntityReferences = false
        val root = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))).documentElement
        require(root.tagName == "ValCurs") { "Неверный формат ответа BNM." }
        val day = LocalDate.parse(root.getAttribute("Date"), sourceDate)
        val nodes = root.getElementsByTagName("Valute")
        if (nodes.length == 0) throw EmptyQuote()
        val rates = linkedMapOf("MDL" to BigDecimal.ONE)
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) as org.w3c.dom.Element
            fun text(name: String): String = node.getElementsByTagName(name).item(0)?.textContent?.trim()
                ?: throw IllegalArgumentException("Неполный ответ BNM.")
            val code = text("CharCode")
            val nominal = text("Nominal").replace(',', '.').toBigDecimal()
            val value = text("Value").replace(',', '.').toBigDecimal()
            require(code.matches(Regex("[A-Z]{3}")) && !rates.containsKey(code)) { "Некорректная валюта BNM." }
            require(nominal.signum() > 0 && value.signum() > 0) { "Некорректный номинал или курс." }
            rates[code] = value.divide(nominal, mc)
        }
        return Quote(day, rates)
    }

    fun pair(quote: Quote, from: String, to: String): BigDecimal {
        val first = quote.mdlPerUnit[from] ?: throw IllegalArgumentException("Валюта $from недоступна.")
        val second = quote.mdlPerUnit[to] ?: throw IllegalArgumentException("Валюта $to недоступна.")
        return first.divide(second, mc)
    }

    fun convert(text: String, from: String, to: String, quote: Quote): BigDecimal =
        amount(text).multiply(pair(quote, from, to), mc).setScale(2, RoundingMode.HALF_UP)

    fun statistics(points: List<Point>): Summary {
        require(points.isNotEmpty()) { "За этот период данных пока нет." }
        val sorted = points.sortedBy { it.day }
        require(sorted.all { it.value.signum() > 0 }) { "Курс должен быть положительным." }
        val values = sorted.map { it.value }
        val difference = values.last().subtract(values.first(), mc)
        val percent = difference.divide(values.first(), mc).multiply(BigDecimal("100"), mc)
        return Summary(values.minOrNull()!!, values.maxOrNull()!!,
            values.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(values.size), mc),
            difference, percent, when(difference.signum()) { 1 -> "Рост"; -1 -> "Падение"; else -> "Без изменений" })
    }
}
