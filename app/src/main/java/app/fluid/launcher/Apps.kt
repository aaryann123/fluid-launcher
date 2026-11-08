package app.fluid.launcher

import android.app.ActivityOptions
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.LauncherActivityInfo
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.window.SplashScreen
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.roundToInt

const val ICON_DP = 32

/** Anything drawer search can show and open. */
sealed interface Hit {
    val key: String
    val label: String
}

@Immutable
data class AppEntry(
    override val key: String, override val label: String, val component: ComponentName, val user: UserHandle, val isWork: Boolean,
) : Hit

/**
 * A search result that isn't an app: one of an app's shortcuts ([intent] null, opened via LauncherApps), or a
 * Settings page. [words] are extra terms it also answers to; [app] supplies the icon and the "· Chrome" detail.
 */
@Immutable
data class Extra(override val key: String, override val label: String, val words: String, val app: AppEntry?, val intent: Intent?) : Hit

/** Settings pages search can jump to: title, everyday words for it, intent action. Ones the phone lacks are dropped. */
private val SETTINGS_PAGES = listOf(
    Triple("Wi-Fi", "wifi wlan internet network", Settings.ACTION_WIFI_SETTINGS),
    Triple("Bluetooth", "bluetooth headphones earbuds pair", Settings.ACTION_BLUETOOTH_SETTINGS),
    Triple("Battery", "battery power saver charging", Intent.ACTION_POWER_USAGE_SUMMARY),
    Triple("Display", "display brightness screen dark mode", Settings.ACTION_DISPLAY_SETTINGS),
    Triple("Sounds and vibration", "sound volume ringtone vibration", Settings.ACTION_SOUND_SETTINGS),
    Triple("Notifications", "notifications alerts", "android.settings.ALL_APPS_NOTIFICATION_SETTINGS"),
    Triple("Do not disturb", "dnd silence quiet focus", Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),
    Triple("Apps", "applications uninstall permissions", Settings.ACTION_APPLICATION_SETTINGS),
    Triple("Default apps", "browser launcher home sms", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
    Triple("Mobile networks", "mobile cellular sim data roaming", Settings.ACTION_DATA_ROAMING_SETTINGS),
    Triple("Data usage", "data usage limit", Settings.ACTION_DATA_USAGE_SETTINGS),
    Triple("Hotspot", "hotspot tethering share internet", "android.settings.TETHER_SETTINGS"),
    Triple("Airplane mode", "airplane flight", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
    Triple("NFC", "nfc tap pay contactless", Settings.ACTION_NFC_SETTINGS),
    Triple("Location", "location gps", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    Triple("Storage", "storage space memory", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    Triple("Security", "lock screen fingerprint face password", Settings.ACTION_SECURITY_SETTINGS),
    Triple("Privacy", "privacy permissions", Settings.ACTION_PRIVACY_SETTINGS),
    Triple("Accessibility", "accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
    Triple("Night light", "eye comfort blue light night", Settings.ACTION_NIGHT_DISPLAY_SETTINGS),
    Triple("Cast", "cast smart view mirror screen", Settings.ACTION_CAST_SETTINGS),
    Triple("VPN", "vpn", Settings.ACTION_VPN_SETTINGS),
    Triple("Accounts", "accounts sync google", Settings.ACTION_SYNC_SETTINGS),
    Triple("Date and time", "date time timezone", Settings.ACTION_DATE_SETTINGS),
    Triple("Language", "language region", Settings.ACTION_LOCALE_SETTINGS),
    Triple("Keyboard", "keyboard input", Settings.ACTION_INPUT_METHOD_SETTINGS),
    Triple("Home screen", "launcher default home", Settings.ACTION_HOME_SETTINGS),
    Triple("Developer options", "developer usb debugging", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    Triple("About phone", "about phone model software version", Settings.ACTION_DEVICE_INFO_SETTINGS),
)

/** The installed-app list (all profiles) plus everything that launches things. */
object Apps {
    private lateinit var app: Application
    lateinit var launcherApps: LauncherApps; private set
    private lateinit var users: UserManager

    var all by mutableStateOf(emptyList<AppEntry>()); private set
    var loaded by mutableStateOf(false); private set

    /** App shortcuts and Settings pages for search. Rebuilt after each list load and when apps change shortcuts. */
    var extras by mutableStateOf(emptyList<Extra>()); private set
    private var extrasJob: Job? = null

    /** Drawer icons, keyed like [AppEntry.key]. Filled in the background after each list load. */
    val icons = mutableStateMapOf<String, ImageBitmap>()

    private val scope = MainScope()
    private var job: Job? = null
    private var iconJob: Job? = null

    fun init(application: Application) {
        app = application
        launcherApps = app.getSystemService(LauncherApps::class.java)
        users = app.getSystemService(UserManager::class.java)
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(p: String, u: UserHandle) = reload()
            override fun onPackageAdded(p: String, u: UserHandle) = reload()
            override fun onPackageChanged(p: String, u: UserHandle) = reload()
            override fun onPackagesAvailable(p: Array<out String>, u: UserHandle, r: Boolean) = reload()
            override fun onPackagesUnavailable(p: Array<out String>, u: UserHandle, r: Boolean) = reload()
            override fun onShortcutsChanged(p: String, s: MutableList<ShortcutInfo>, u: UserHandle) = loadExtras()
        }, Handler(Looper.getMainLooper()))
        // Work profile turned on/off/added, or labels changed language.
        val filter = IntentFilter().apply {
            listOf(Intent.ACTION_MANAGED_PROFILE_ADDED, Intent.ACTION_MANAGED_PROFILE_REMOVED,
                Intent.ACTION_MANAGED_PROFILE_AVAILABLE, Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
                Intent.ACTION_PROFILE_AVAILABLE, Intent.ACTION_PROFILE_UNAVAILABLE,
                Intent.ACTION_LOCALE_CHANGED).forEach(::addAction)
        }
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = reload()
        }, filter, ContextCompat.RECEIVER_EXPORTED) // protected system broadcasts only
        reload()
        // Halo icons switched in Settings: redraw every icon (the dock refetches when the list reloads).
        scope.launch { snapshotFlow { Store.haloIcons.value }.drop(1).collect { reload() } }
    }

    fun reload() {
        job?.cancel()
        job = scope.launch {
            val renames = Store.renames.value
            val (entries, infos) = withContext(Dispatchers.Default) { load(renames) }
            all = entries
            loaded = true
            refreshFavoriteLabels()
            seedFavorites()
            seedFar()
            loadIcons(entries.mapNotNull { e -> infos[e.key]?.let { e.key to it } })
            loadExtras()
        }
    }

    private fun load(renames: Map<String, String>): Pair<List<AppEntry>, Map<String, LauncherActivityInfo>> {
        val collator = Collator.getInstance()
        val infos = HashMap<String, LauncherActivityInfo>()
        val entries = launcherApps.profiles.flatMap { user ->
            val serial = users.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user).map { info ->
                val key = "a|$serial|${info.componentName.flattenToString()}"
                infos[key] = info
                AppEntry(key, renames[key] ?: info.label.toString(), info.componentName, user, user != Process.myUserHandle())
            }
        }.sortedWith(compareBy(collator) { it.label })
        return entries to infos
    }

    private fun loadExtras() {
        extrasJob?.cancel()
        extrasJob = scope.launch {
            delay(300) // apps often push several shortcut updates in a row
            val apps = all
            extras = withContext(Dispatchers.Default) {
                val owners = apps.associateBy { it.component.packageName to it.user }
                val shortcuts = if (!launcherApps.hasShortcutHostPermission()) emptyList() else launcherApps.profiles.flatMap { user ->
                    val q = LauncherApps.ShortcutQuery()
                        .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
                    runCatching { launcherApps.getShortcuts(q, user).orEmpty() }.getOrDefault(emptyList()).mapNotNull { s ->
                        val owner = owners[s.`package` to user]
                        val title = s.shortLabel ?: s.longLabel
                        if (!s.isEnabled || owner == null || title == null) null
                        else Extra(shortcutKey(s), title.toString(), s.longLabel?.toString().orEmpty(), owner, null)
                    }
                }
                val settingsApp = apps.firstOrNull { it.component.packageName == "com.android.settings" && !it.isWork }
                val pages = SETTINGS_PAGES.mapNotNull { (title, words, action) ->
                    val intent = Intent(action)
                    if (app.packageManager.resolveActivity(intent, 0) == null) null
                    else Extra("p|$action", title, words, settingsApp, intent)
                }
                // Settings ships its own "Wi-Fi", "Battery"… shortcuts; one row per name and app is enough.
                // Compared on letters and digits only: Settings writes "Wi‑Fi" with a non-breaking hyphen.
                (pages + shortcuts).distinctBy { it.label.lowercase().filter(Char::isLetterOrDigit) to it.app?.key }
            }
        }
    }

    /**
     * Decoded once to drawer size and kept in memory (~6 MB for 150 apps), so scrolling never waits on one.
     * Alphabetical batches: the rows you see first fill in first. Badged, so work apps keep their work badge.
     */
    private fun loadIcons(todo: List<Pair<String, LauncherActivityInfo>>) {
        iconJob?.cancel()
        iconJob = scope.launch {
            val size = (ICON_DP * app.resources.displayMetrics.density).roundToInt()
            val halo = Store.haloIcons.value
            todo.chunked(24).forEach { chunk ->
                icons.putAll(withContext(Dispatchers.Default) {
                    chunk.mapNotNull { (key, info) ->
                        runCatching { key to icon(info, size, halo).asImageBitmap() }.getOrNull()
                    }
                })
            }
        }
    }

    // ponytail: Halo icons drop the work-profile badge; work apps still say "· work" in search.
    private fun icon(info: LauncherActivityInfo, px: Int, halo: Boolean) =
        if (halo) Halo.icon(app, info.componentName.packageName, info.label.toString(), info.getIcon(0), px)
        else info.getBadgedIcon(0).toBitmap(px, px)

    /** One home app or pinned shortcut's icon at [px], sharper than [icons] so the magnifying dock stays crisp. Badged too. */
    suspend fun bigIcon(key: String, px: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val parts = key.split("|", limit = 4)
            val user = user(parts[1]) ?: return@runCatching null
            val halo = Store.haloIcons.value
            val icon = when (parts[0]) {
                "a" -> launcherApps.resolveActivity(Intent().setComponent(ComponentName.unflattenFromString(parts[2])), user)
                    ?.let { icon(it, px, halo) }
                "s" -> {
                    val q = LauncherApps.ShortcutQuery().setPackage(parts[2]).setShortcutIds(listOf(parts[3]))
                        .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
                    launcherApps.getShortcuts(q, user)?.firstOrNull()?.let { s ->
                        val d = launcherApps.getShortcutBadgedIconDrawable(s, app.resources.displayMetrics.densityDpi)
                        // A pinned shortcut wears its app's glyph, unless its name has one (a "Daily Brief" web app isn't the browser).
                        val label = (s.shortLabel ?: "").toString()
                        if (halo) Halo.icon(app, if (guessGlyph(label) != null) "" else parts[2], label, d.takeIf { guessGlyph(label) == null }, px)
                        else d?.toBitmap(px, px)
                    }
                }
                else -> null
            }
            icon?.asImageBitmap()
        }.getOrNull()
    }

    /** Favorites cache their labels for instant cold starts; keep them in sync with renames/updates. */
    private fun refreshFavoriteLabels() {
        val byKey = all.associateBy { it.key }
        val favs = Store.favorites.value
        val fresh = favs.map { f -> byKey[f.key]?.let { f.copy(label = it.label) } ?: f }
        if (fresh != favs) Store.favorites.value = fresh
    }

    fun find(key: String) = all.firstOrNull { it.key == key }

    private fun user(serial: String): UserHandle? = users.getUserForSerialNumber(serial.toLongOrNull() ?: return null)

    /** Launch an app or pinned shortcut key, animating out of [bounds] (in [view]'s coordinates). */
    /** A home-screen app pair: "p|<first key>||<second key>" (app keys never contain "||"). */
    fun pairKey(first: String, second: String) = "p|$first||$second"

    /** Opens two apps side by side, via [SplitActivity]. */
    fun openPair(view: View, first: String, second: String) {
        val ctx = view.context
        try {
            ctx.startActivity(
                Intent(ctx, SplitActivity::class.java)
                    .putExtra(SplitActivity.FIRST, first)
                    .putExtra(SplitActivity.SECOND, second)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            toast("Can't open those side by side")
        }
    }

    /** A plain launch intent for a personal-profile app key; null for shortcuts and work apps (no cross-profile starts). */
    fun mainIntent(key: String): Intent? {
        val parts = key.split("|", limit = 3)
        if (parts.size < 3 || parts[0] != "a" || user(parts[1]) != Process.myUserHandle()) return null
        return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName.unflattenFromString(parts[2]))
    }

    fun launch(view: View, key: String, bounds: Rect?) {
        if (key.startsWith("p|")) {
            val (first, second) = key.removePrefix("p|").split("||", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            return openPair(view, first, second)
        }
        val opts = launchOptions(view, bounds).toBundle()
        val screen = bounds?.let { b -> IntArray(2).also(view::getLocationOnScreen).let { Rect(b).apply { offset(it[0], it[1]) } } }
        val parts = key.split("|", limit = 4)
        try {
            val user = user(parts[1]) ?: return toast("App not found")
            if (users.isQuietModeEnabled(user)) {
                users.requestQuietModeEnabled(false, user) // system asks to turn work apps back on
                return
            }
            when (parts[0]) {
                "a" -> launcherApps.startMainActivity(ComponentName.unflattenFromString(parts[2]), user, screen, opts)
                "s" -> launcherApps.startShortcut(parts[2], parts[3], screen, opts, user)
            }
            Store.launches.value = Store.launches.value.let { it + (key to (it[key] ?: 0) + 1) }
        } catch (e: Exception) { // ActivityNotFound, Security (disabled/uninstalled), IllegalState (user locked)
            toast("Can't open that")
        }
    }

    fun shortcuts(app: AppEntry): List<ShortcutInfo> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.component.packageName)
            .setActivity(app.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        launcherApps.getShortcuts(q, app.user).orEmpty().filter { it.isEnabled }.sortedBy { it.rank }.take(4)
    }.getOrDefault(emptyList())

    fun shortcutKey(s: ShortcutInfo) = "s|${users.getSerialNumberForUser(s.userHandle)}|${s.`package`}|${s.id}"

    fun appInfo(key: String) {
        val parts = key.split("|", limit = 4)
        val user = user(parts[1]) ?: return
        val component = if (parts[0] == "a") ComponentName.unflattenFromString(parts[2])
        else launcherApps.getActivityList(parts[2], user).firstOrNull()?.componentName
        runCatching { launcherApps.startAppDetailsActivity(component, user, null, null) }
    }

    fun uninstall(context: Context, app: AppEntry) {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null)))
    }

    /** First run: put the phone/messages/camera/browser on the home screen, Olauncher-style. */
    fun seedFavorites() {
        if (Store.seeded.value || !loaded) return
        Store.seeded.value = true
        if (Store.favorites.value.isNotEmpty()) return
        val camera = default(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        val picks = listOfNotNull(
            default(Intent(Intent.ACTION_DIAL)),
            default(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            camera,
            default(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
        ).distinct()
        Store.favorites.value = picks.map { Favorite(it.key, it.label) }
        if (camera != null && Store.swipeLeftApp.value.isEmpty()) Store.swipeLeftApp.value = camera.key
    }

    /** Pull-farther defaults to the phone app; added after launch, so it seeds on its own for existing installs. */
    private fun seedFar() {
        if (Store.seededFar.value) return
        Store.seededFar.value = true
        if (Store.swipeFarApp.value.isEmpty()) default(Intent(Intent.ACTION_DIAL))?.let { Store.swipeFarApp.value = it.key }
    }

    /** The personal-profile app that handles [i] by default, if it's in the app list. */
    private fun default(i: Intent) = app.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName
        ?.let { pkg -> all.firstOrNull { !it.isWork && it.component.packageName == pkg } }

    fun toast(msg: String) = Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
}

/**
 * Clip-reveal out of the tapped label (what Launcher3 uses without system animation access), an icon
 * splash, and on Android 14+ our foreground status lent to PendingIntent-based starts (shortcuts,
 * widget config screens), which are otherwise blocked as background launches.
 */
fun launchOptions(view: View? = null, bounds: Rect? = null): ActivityOptions {
    val opts = if (view != null && bounds != null) ActivityOptions.makeClipRevealAnimation(view, bounds.left, bounds.top, bounds.width(), bounds.height())
    else ActivityOptions.makeBasic()
    if (Build.VERSION.SDK_INT >= 33) opts.splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_ICON
    when {
        Build.VERSION.SDK_INT >= 36 -> opts.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
        Build.VERSION.SDK_INT >= 34 -> @Suppress("DEPRECATION")
            opts.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
    }
    return opts
}
