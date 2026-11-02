package app.fluid.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DockTest {
    private val dock = DockMath(n = 5, slot = 100f, base = 80f)
    private fun centre(i: Int) = 100f * i + 50f

    @Test fun biggestUnderTheFinger() {
        assertEquals(1.8f, dock.scale(2, centre(2), 1f), 0.001f)
        assertTrue(dock.scale(1, centre(2), 1f) > dock.scale(0, centre(2), 1f))
        assertEquals(1f, dock.scale(2, centre(2), 0f), 0.001f) // not touched: no magnifying
    }

    @Test fun growsEvenlyAtBothEnds() {
        assertEquals(0f, dock.shift(2, centre(2), 1f), 0.001f) // middle stays put
        assertEquals(-dock.shift(0, centre(2), 1f), dock.shift(4, centre(2), 1f), 0.001f)
        assertTrue(dock.shift(1, centre(2), 1f) < 0 && dock.shift(3, centre(2), 1f) > 0) // neighbours pushed apart
    }

    @Test fun hitsTheIconUnderTheFinger() {
        for (i in 0 until 5) {
            assertEquals(i, dock.hit(centre(i), 0f))
            assertEquals(i, dock.hit(centre(i), 1f))
        }
        assertNull(dock.hit(-120f, 1f))
        assertNull(dock.hit(620f, 1f))
    }
}

