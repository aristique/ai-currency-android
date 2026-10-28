package md.lab.currency

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.LocalDate

class RepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val day = LocalDate.of(2026,10,28)

    @Test fun cacheSurvivesNewRepositoryAndOfflineRefresh() {
        Repository(folder.root) { fixture(it) }.current(day)
        val restarted = Repository(folder.root) { throw IOException("offline") }
        val result = restarted.current(day)
        assertTrue(result.cached)
        assertEquals(day, result.quote.day)
        assertEquals(day, restarted.latestCached()!!.day)
    }

    @Test fun invalidResponseDoesNotOverwriteValidCache() {
        Repository(folder.root) { fixture(it) }.current(day)
        val file = File(folder.root, "$day.xml")
        val previous = file.readText()
        val result = Repository(folder.root) { "broken XML" }.current(day)
        assertTrue(result.cached)
        assertEquals(previous, file.readText())
    }

    @Test fun downloadsThirtyHistoricalDaysAndReusesThemAfterRestart() {
        var calls = 0
        val online = Repository(folder.root) { calls++; fixture(it, (20+it.dayOfMonth).toString()) }
        val history = online.history(30, day)
        assertEquals(30, calls)
        assertEquals(30, history.quotes.size)
        assertEquals(day.minusDays(29), history.quotes.first().day)
        val offline = Repository(folder.root) { throw AssertionError("Should use cache") }
        assertEquals(30, offline.history(30,day).quotes.size)
    }

    @Test fun emptyOfflineHistoryDoesNotRetryEveryDay() {
        var calls = 0
        val offline = Repository(folder.root) { calls++; throw IOException("offline") }
        assertTrue(offline.history(7,day).quotes.isEmpty())
        assertEquals(1, calls)
        assertTrue(runCatching { offline.current(day) }.isFailure)
    }

    @Test fun emptyDateFallsBackToPreviousAvailableDay() {
        val source = Repository(folder.root) {
            if(it == day) """<ValCurs Date="28.10.2026"/>""" else fixture(it)
        }
        val result = source.current(day)
        assertEquals(day.minusDays(1), result.quote.day)
        assertFalse(result.cached)
        assertTrue(result.notice.contains("последний"))
    }

    @Test fun ignoresCorruptCacheAndKeepsOlderValidData() {
        Repository(folder.root) { fixture(it) }.current(day.minusDays(1))
        File(folder.root,"$day.xml").writeText("corrupt")
        val offline = Repository(folder.root) { throw IOException("offline") }
        assertEquals(day.minusDays(1), offline.current(day).quote.day)
    }
}
