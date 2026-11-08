package app.fluid.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaTest {
    private data class S(val playing: Boolean, val loaded: Boolean)

    @Test fun playingBeatsLoaded() {
        val paused = S(playing = false, loaded = true)
        val playing = S(playing = true, loaded = true)
        val empty = S(playing = false, loaded = false)
        assertEquals(playing, choose(listOf(paused, playing, empty), S::playing, S::loaded))
        assertEquals(paused, choose(listOf(empty, paused), S::playing, S::loaded))
        assertNull(choose(listOf(empty), S::playing, S::loaded))
        assertNull(choose(emptyList<S>(), S::playing, S::loaded))
    }
}
