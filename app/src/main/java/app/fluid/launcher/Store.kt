package app.fluid.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/** A home-screen entry: an app ("a|serial|pkg/cls") or a pinned shortcut ("s|serial|pkg|id"). */
data class Favorite(val key: String, val label: String)

data class PlacedWidget(val id: Int, val heightDp: Int)

/** One SharedPreferences value mirrored into Compose state; reads are lazy so Store.init can run first. */
class Pref<T>(private val key: String, private val default: T, private val decode: (String) -> T, private val encode: (T) -> String) {
    private val state by lazy {
        mutableStateOf(Store.sp.getString(key, null)?.let { runCatching { decode(it) }.getOrNull() } ?: default)
    }
    var value: T
        get() = state.value
        set(v) {
            state.value = v
            Store.sp.edit().putString(key, encode(v)).apply()
        }
}

object Store {
    internal lateinit var sp: SharedPreferences
    fun init(context: Context) {
        sp = context.getSharedPreferences("fluid", Context.MODE_PRIVATE)
    }

    // wallpaper | system | dark | light. Key renamed when the default became "wallpaper" so existing installs move to it.
    val theme = str("theme2", "wallpaper")
    val frosted = bool("frosted", true) // blur the wallpaper behind the drawer and widgets (wallpaper theme)
    val wallText = str("wall_text", "auto") // auto | light | dark: text colour on the wallpaper theme
    val textScale = Pref("text_scale", 1f, String::toFloat, Float::toString)
    val align = str("align", "start") // start | center | end
    val showClock = bool("show_clock", true)
    val showDate = bool("show_date", true)
    val showGlance = bool("show_glance", true) // next alarm + battery under the date
    val showStatusBar = bool("status_bar", true)

    val doubleTapLock = bool("double_tap_lock", true)
    val swipeDownShade = bool("swipe_down_shade", true)
    val swipeLeftApp = str("swipe_left_app", "") // Favorite key, "" = off
    val swipeFarApp = str("swipe_far_app", "") // second stop of the same pull; "" = off
    val seededFar = bool("seeded_far", false)
    val autoOpenMatch = bool("auto_open", true)
    val drawerIcons = bool("drawer_icons", true)
    val haloIcons = bool("halo_icons", true) // Fluid's own white glyphs (Halo.kt) instead of the apps' icons
    val topApps = bool("top_apps", true) // most-used apps above the drawer's search bar
    val seeded = bool("seeded", false)
    val askedDefault = bool("asked_default", false)

    val favorites = Pref("favorites", emptyList(),
        { s -> JSONArray(s).objects().map { Favorite(it.getString("k"), it.getString("l")) } },
        { l -> JSONArray(l.map { JSONObject().put("k", it.key).put("l", it.label) }).toString() })
    val widgets = Pref("widgets", emptyList(),
        { s -> JSONArray(s).objects().map { PlacedWidget(it.getInt("id"), it.getInt("h")) } },
        { l -> JSONArray(l.map { JSONObject().put("id", it.id).put("h", it.heightDp) }).toString() })
    val renames = Pref("renames", emptyMap(),
        { s -> JSONObject(s).let { o -> o.keys().asSequence().associateWith(o::getString) } },
        { m -> JSONObject(m).toString() })
    val hidden = Pref("hidden", emptySet(),
        { s -> JSONArray(s).let { a -> List(a.length(), a::getString).toSet() } },
        { s -> JSONArray(s.toList()).toString() })
    val widgetWidths = Pref("widget_widths", emptyList(),
        { s -> JSONArray(s).let { a -> List(a.length(), a::getInt) } },
        { l -> JSONArray(l).toString() })
    val launches = Pref("launches", emptyMap(),
        { s -> JSONObject(s).let { o -> o.keys().asSequence().associateWith(o::getInt) } },
        { m -> JSONObject(m).toString() })

    private fun str(key: String, default: String) = Pref(key, default, { it }, { it })
    private fun bool(key: String, default: Boolean) = Pref(key, default, String::toBoolean, Boolean::toString)
    private fun JSONArray.objects() = List(length(), ::getJSONObject)
}
