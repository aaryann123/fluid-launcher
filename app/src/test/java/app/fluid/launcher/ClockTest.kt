package app.fluid.launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ClockTest {
    private fun at(h: Int, m: Int) = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 28, h, m) }

    @Test fun wordsLikeTheLockScreen() {
        assertEquals(listOf("TWELVE", "56", "PM"), clockParts(at(12, 56)))
        assertEquals(listOf("TWELVE", "05", "AM"), clockParts(at(0, 5)))
        assertEquals(listOf("FOUR", "09", "PM"), clockParts(at(16, 9)))
    }
}
