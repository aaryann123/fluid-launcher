package app.fluid.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HaloTest {
    @Test fun everyNamedGlyphIsInAssets() {
        val have = File("src/main/assets/glyphs.txt").readLines().filterNot { it.startsWith("#") }.map { it.substringBefore('\t') }.toSet()
        val missing = (GLYPHS.values + GLYPH_WORDS.map { it.second }).toSet() - have
        assertTrue("run Tools/glyphs.py; missing $missing", missing.isEmpty())
    }

    @Test fun guessesFromTheStartOfAWord() {
        assertEquals("bank", guessGlyph("My Bank"))
        assertEquals("cloud-sun", guessGlyph("Weather Pro"))
        assertEquals("chat-circle", guessGlyph("Messages+"))
        assertNull(guessGlyph("Chase")) // no false hit on letters inside a word
        assertNull(guessGlyph("Muse"))
    }
}
