package app.fluid.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

class SettingsActivity : ComponentActivity() {
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Glass like the rest of Fluid: the wallpaper shows through, frosted where the phone can blur it.
        if (Blur.supported(this)) Blur.set(this, window.decorView, (40 * resources.displayMetrics.density).toInt(), 0)
        setContent {
            val ctx = LocalContext.current
            MaterialTheme(colorScheme = dynamicDarkColorScheme(ctx)) {
                Surface(Modifier.fillMaxSize(), color = Color(0xB8000000), contentColor = Color.White) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = 640.dp)) { SettingsScreen(resumes) }
                    }
                }
            }
        }
    }

    // Re-check default-home and accessibility state whenever the user comes back from system settings.
    override fun onResume() {
        super.onResume()
        resumes++
    }
}

private val wallTexts = listOf("auto" to "Auto", "light" to "White", "dark" to "Black")
private val themes = listOf("wallpaper" to "Wallpaper", "system" to "Follow system", "dark" to "Black", "light" to "Light")

@Composable
private fun SettingsScreen(resumes: Int) {
    val ctx = LocalContext.current
    val roles = remember { ctx.getSystemService(RoleManager::class.java) }
    val isHome = remember(resumes) { roles.isRoleHeld(RoleManager.ROLE_HOME) }
    val lockReady = remember(resumes) { LockService.instance != null }
    var dialog by remember { mutableStateOf("") }
    val askHome = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Samsung stops showing the role dialog after repeated "no"; fall back to the system picker.
        if (!roles.isRoleHeld(RoleManager.ROLE_HOME)) ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Settings", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(8.dp, 24.dp, 8.dp, 8.dp))
        if (!isHome) Glass {
            run {
                Column(Modifier.padding(20.dp)) {
                    Text("Fluid isn't your home screen yet", style = MaterialTheme.typography.titleMedium)
                    Button({ askHome.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }, Modifier.padding(top = 12.dp)) {
                        Text("Set as home screen")
                    }
                }
            }
        }

        Section("Look") {
            ValueRow("Theme", themes.first { it.first == Store.theme.value }.second) { dialog = "theme" }
            if (Store.theme.value == "wallpaper") Toggle("Frosted glass", Store.frosted, "Blur the wallpaper behind the apps menu and widgets")
            if (Store.theme.value == "wallpaper") {
                ValueRow("Text on wallpaper", wallTexts.first { it.first == Store.wallText.value }.second) { dialog = "walltext" }
            }
            run {
                ListItem(
                    headlineContent = { Text("Text size") },
                    supportingContent = {
                        Slider(Store.textScale.value, { Store.textScale.value = it }, valueRange = 0.8f..1.4f, steps = 5)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            run {
                ListItem(
                    headlineContent = { Text("Alignment") },
                    supportingContent = {
                        val options = listOf("start" to "Left", "center" to "Center", "end" to "Right")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            options.forEachIndexed { i, (value, label) ->
                                SegmentedButton(
                                    selected = Store.align.value == value,
                                    onClick = { Store.align.value = value },
                                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                                ) { Text(label) }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            Toggle("Halo icons", Store.haloIcons, "Every app as a white glyph with a soft shadow")
            Toggle("Clock", Store.showClock)
            Toggle("Date", Store.showDate)
            Toggle("Next alarm and battery", Store.showGlance)
            Toggle("Status bar", Store.showStatusBar)
        }

        Section("Gestures") {
            Toggle("Double-tap to lock", Store.doubleTapLock, if (lockReady) null else "Needs Fluid turned on in Accessibility")
            if (Store.doubleTapLock.value && !lockReady) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    Text(
                        "Settings → Accessibility → Installed apps → Fluid. If the switch is greyed out (normal for an " +
                            "installed APK): App info → ⋮ → Allow restricted settings, then try again.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row {
                        TextButton({ ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accessibility") }
                        TextButton({
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                        }) { Text("App info") }
                    }
                }
            }
            Toggle("Swipe down for notifications", Store.swipeDownShade)
            ValueRow("Swipe left opens", Apps.find(Store.swipeLeftApp.value)?.label ?: "Nothing") { dialog = "swipe" }
            ValueRow("Keep pulling left opens", Apps.find(Store.swipeFarApp.value)?.label ?: "Nothing") { dialog = "far" }
        }

        Section("Notification dots") {
            run {
                val on = remember(resumes) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }
                ValueRow("Dots for unread notifications", if (on) "On" else "Off: tap to allow notification access") {
                    ctx.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(ctx, DotService::class.java).flattenToString())
                    )
                }
            }
        }

        Section("App drawer") {
            Toggle("App icons", Store.drawerIcons)
            Toggle("Your apps on top", Store.topApps, "Most-used apps above the search bar")
            Toggle("Open app when only one matches", Store.autoOpenMatch)
        }

        Section("Hidden apps") {
            val hidden = Apps.all.filter { it.key in Store.hidden.value }
            if (hidden.isEmpty()) Hint("None. Long-press an app in the drawer to hide it.")
            hidden.forEach { app ->
                ListItem(
                    headlineContent = { Text(app.label) },
                    trailingContent = { TextButton({ Store.hidden.value -= app.key }) { Text("Unhide") } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        Section("How it works") {
            run {
                Hint(
                    "Swipe up: all apps · Swipe down: notifications · Swipe left: your swipe-left app, keep pulling for the second · " +
                        "Swipe right (cover screen): widgets · Double-tap: lock\n\n" +
                        "Long-press empty space: add a widget or open settings. Long-press a home app: drag to reorder, " +
                        "or let go for rename/remove. Long-press an app in the drawer: shortcuts, add to home, rename, hide."
                )
            }
        }
    }

    when (dialog) {
        "theme" -> Choose("Theme", themes, Store.theme.value) { Store.theme.value = it; dialog = "" }
        "walltext" -> Choose("Text on wallpaper", wallTexts, Store.wallText.value) { Store.wallText.value = it; dialog = "" }
        "swipe" -> Choose(
            "Swipe left opens",
            listOf("" to "Nothing") + Apps.all.filter { it.key !in Store.hidden.value }.map { it.key to it.label },
            Store.swipeLeftApp.value,
        ) { Store.swipeLeftApp.value = it; dialog = "" }
        "far" -> Choose(
            "Keep pulling left opens",
            listOf("" to "Nothing") + Apps.all.filter { it.key !in Store.hidden.value }.map { it.key to it.label },
            Store.swipeFarApp.value,
        ) { Store.swipeFarApp.value = it; dialog = "" }
    }
}

/** A titled glass card holding one group of settings. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp), color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 8.dp))
    Glass(content)
}

@Composable
private fun Glass(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.08f))
        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)).padding(vertical = 4.dp)
) { content() }

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) = ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(value) },
    modifier = Modifier.clickable(onClick = onClick),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
)

@Composable
private fun Toggle(title: String, pref: Pref<Boolean>, note: String? = null) = ListItem(
    headlineContent = { Text(title) },
    supportingContent = note?.let { { Text(it) } },
    trailingContent = { Switch(pref.value, null) },
    modifier = Modifier.clickable { pref.value = !pref.value },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
)

@Composable
private fun Choose(title: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) = AlertDialog(
    onDismissRequest = { onPick(selected) },
    confirmButton = {},
    title = { Text(title) },
    text = {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(options, key = { it.first }) { (value, label) ->
                Text(
                    label,
                    color = if (value == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(value) }.padding(vertical = 14.dp),
                )
            }
        }
    },
)
