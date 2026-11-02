package app.fluid.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTest {
    private val apps = listOf("Calculator", "Calendar", "Café Rio", "Chrome", "Gmail", "Google Maps", "WhatsApp", "YouTube Music")

    private fun search(q: String, launches: Map<String, Int> = emptyMap()) = rank(apps, q, { it }, { launches[it] ?: 0 })

    @Test fun scores() {
        assertEquals(0, matchScore("WhatsApp", "whats a"))
        assertEquals(1, matchScore("Google Maps", "map"))
        assertEquals(2, matchScore("YouTube Music", "ytm"))
        assertEquals(3, matchScore("Google Maps", "ogle"))
        assertEquals(0, matchScore("Café Rio", "cafe"))
        assertNull(matchScore("Chrome", "x"))
        assertNull(matchScore("Chrome", "  "))
    }

    @Test fun verbs() {
        assertEquals(false to "mom", Contacts.parse("call mom"))
        assertEquals(true to "sam lee", Contacts.parse("Text sam lee "))
        assertEquals(null to "mom", Contacts.parse("mom"))
        assertEquals(null to "call", Contacts.parse("call")) // just the verb is an ordinary search
    }

    @Test fun ranking() {
        assertEquals(listOf("Gmail", "Google Maps"), search("gm"))
        assertEquals(listOf("Calculator", "Calendar", "Café Rio"), search("ca"))
        assertEquals(listOf("Calendar", "Calculator", "Café Rio"), search("ca", mapOf("Calendar" to 3)))
        assertEquals(listOf("Google Maps"), search("maps"))
    }
}
