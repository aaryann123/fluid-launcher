package app.fluid.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoriesTest {
    @Test fun guesses() {
        assertEquals("Social", categorize("com.instagram.android", "Instagram", -1))
        assertEquals("Chat", categorize("com.whatsapp", "WhatsApp", -1))
        assertEquals("Money", categorize("com.infonow.bofa", "BofA", -1))
        assertEquals("Money", categorize("com.statefarm.pocketagent", "State Farm", -1))
        assertEquals("Shopping", categorize("com.amazon.mShop.android.shopping", "Amazon Shopping", -1))
        assertEquals("Games", categorize("com.some.puzzle", "Puzzle", 0)) // Android's CATEGORY_GAME
        assertEquals("Tools", categorize("com.sec.android.app.clockpackage", "Clock", -1))
        assertEquals("Money", categorize("com.phonevalley.progressive", "Progressive", -1))
        assertEquals("Entertainment", categorize("com.amazon.storm.lightning.client.aosp", "Fire TV", -1))
        assertEquals("Tools", categorize("com.sec.android.app.sbrowser", "Browser", -1))
        assertEquals("Other", categorize("com.unknown.thing", "Thing", -1))
    }
}
