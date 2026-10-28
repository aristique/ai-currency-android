package md.lab.currency

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class Loaded(val quote: Quote, val cached: Boolean, val notice: String)
data class History(val quotes: List<Quote>, val expected: Int, val cached: Boolean)
fun today(): LocalDate = LocalDate.now(ZoneId.of("Europe/Chisinau"))

fun fetchXml(day: LocalDate): String {
    val date = day.format(DateTimeFormatter.ofPattern("dd.MM.uuuu"))
    val connection = URL("https://www.bnm.md/en/official_exchange_rates?get_xml=1&date=$date")
        .openConnection() as HttpURLConnection
    connection.connectTimeout = 5000
    connection.readTimeout = 6000
    connection.setRequestProperty("User-Agent", "EducationalCurrencyMobile/1.0")
    try {
        if (connection.responseCode != 200) throw IOException("Источник временно недоступен.")
        connection.inputStream.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                if (output.size() > 2_000_000) throw IOException("Слишком большой ответ источника.")
            }
            return output.toByteArray().toString(Charsets.UTF_8)
        }
    } finally { connection.disconnect() }
}

class Repository(private val directory: File, private val fetcher: (LocalDate) -> String = ::fetchXml) {
    init { directory.mkdirs() }
    private fun file(day: LocalDate) = File(directory, "$day.xml")
    fun cached(day: LocalDate): Quote? = try { Domain.parse(file(day).readText()) } catch (_: Exception) { null }
    fun latestCached(): Quote? = directory.listFiles()?.filter { it.extension == "xml" }?.sortedByDescending { it.name }
        ?.firstNotNullOfOrNull { try { Domain.parse(it.readText()) } catch (_: Exception) { null } }

    private fun download(day: LocalDate): Quote {
        val payload = fetcher(day)
        val quote = Domain.parse(payload)
        require(!quote.day.isAfter(day)) { "Источник вернул неверную дату." }
        val temporary = File.createTempFile("quote-", ".tmp", directory)
        try {
            temporary.outputStream().use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            Files.move(temporary.toPath(), file(quote.day).toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
        return quote
    }

    fun current(day: LocalDate = today()): Loaded {
        try {
            for (offset in 0L..14L) {
                try {
                    val quote = download(day.minusDays(offset))
                    return Loaded(quote, false, if (quote.day == day) "Курс получен из BNM."
                        else "На выбранную дату курс не найден. Показан последний доступный.")
                } catch (_: EmptyQuote) { continue }
            }
            throw IOException("Доступного курса не найдено.")
        } catch (error: Exception) {
            val cached = latestCached()
            if (cached != null) return Loaded(cached, true, "Источник недоступен. Используется сохранённый курс.")
            throw IOException("Не удалось загрузить курс. Сохранённых данных пока нет.", error)
        }
    }

    fun history(days: Int, end: LocalDate = today()): History {
        require(days == 7 || days == 30)
        val quotes = mutableListOf<Quote>()
        var networkFailed = false
        var usedCache = false
        for (offset in (days-1) downTo 0) {
            val day = end.minusDays(offset.toLong())
            val saved = cached(day)
            if (saved != null) {
                quotes.add(saved)
                usedCache = true
                continue
            }
            if (networkFailed) continue
            try {
                val quote = download(day)
                if (quote.day == day) quotes.add(quote)
            } catch (_: EmptyQuote) {
                continue
            } catch (_: Exception) {
                networkFailed = true
            }
        }
        return History(quotes.distinctBy { it.day }.sortedBy { it.day }, days, usedCache)
    }
}
