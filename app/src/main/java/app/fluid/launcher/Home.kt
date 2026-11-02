package app.fluid.launcher

import android.app.WallpaperColors
import android.app.AlarmManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.BatteryManager
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import java.util.Calendar
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.RowScope
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

// ---------- Look ----------

@Immutable
data class Palette(
    val bg: Color, val text: Color, val dim: Color, val accent: Color, val sheet: Color,
    val lightBars: Boolean, val wallpaper: Boolean, val shadow: Shadow? = null,
    /** Drawer tint over a blurred wallpaper: much lighter than [sheet], the blur does the rest. */
    val frost: Color = sheet,
) {
    fun style(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = text) =
        TextStyle(color = color, fontSize = size, fontWeight = weight, shadow = shadow)
}

private val Black = Palette(Color.Black, Color(0xFFF2F2F2), Color(0xFF8C8C8C), Color(0xFF8AB4FF), Color.Black, false, false)
private val Light = Palette(Color(0xFFFAFAF7), Color(0xFF141414), Color(0xFF6E6E6E), Color(0xFF2E5BD8), Color(0xFFFAFAF7), true, false)
private val OnDarkWall = Palette(Color.Transparent, Color.White, Color(0xD9FFFFFF), Color(0xFFA8C7FF), Color(0xE6000000), false, true,
    Shadow(Color(0x99000000), Offset(0f, 2f), 8f), frost = Color(0x66000000))
private val OnLightWall = Palette(Color.Transparent, Color(0xFF111111), Color(0xD9111111), Color(0xFF2E5BD8), Color(0xEBFFFFFF), true, true,
    Shadow(Color(0x66FFFFFF), Offset(0f, 1f), 6f), frost = Color(0x8CFFFFFF))

val LocalPalette = staticCompositionLocalOf { Black }

/** Fill and edge for Fluid's glass (dock, apps grid, hold bar, widget cards): neutral, from the palette's text colour. */
@Immutable
data class Glass(val fill: Color, val rim: Color, val solid: Color)

val LocalGlass = staticCompositionLocalOf { Glass(Color(0x1AFFFFFF), Color(0x29FFFFFF), Color(0xD1000000)) }

// ponytail: a wallpaper-tinted version was tried and rejected (2026-10-28); keep it neutral
private fun glassFor(p: Palette) = Glass(p.text.copy(alpha = 0.10f), p.text.copy(alpha = 0.16f), p.sheet.copy(alpha = 0.82f))

@Composable
private fun rememberPalette(): Palette {
    val theme = Store.theme.value
    val darkText = wallpaperWantsDarkText(theme == "wallpaper" && Store.wallText.value == "auto")
    return when (theme) {
        "dark" -> Black
        "light" -> Light
        "wallpaper" -> when (Store.wallText.value) {
            "dark" -> OnLightWall
            "light" -> OnDarkWall
            else -> if (darkText) OnLightWall else OnDarkWall
        }
        else -> if (isSystemInDarkTheme()) Black else Light
    }
}

/**
 * Light wallpapers ask for dark text via WallpaperColors hints. Samsung's live wallpapers report no colors at
 * all, so "auto" then means white text over a scrim (see [wallpaperScrim]), and settings offer a manual pick.
 */
@Composable
private fun wallpaperWantsDarkText(enabled: Boolean): Boolean {
    val ctx = LocalContext.current
    var dark by remember { mutableStateOf(false) }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val wm = WallpaperManager.getInstance(ctx)
        fun read() {
            dark = (wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.colorHints ?: 0) and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
        }
        read()
        val listener = WallpaperManager.OnColorsChangedListener { _, _ -> read() }
        wm.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { wm.removeOnColorsChangedListener(listener) }
    }
    return dark
}

/** Blurs the wallpaper as far as the drawer or widgets page is open, following the finger. Wallpaper theme only. */
@Composable
private fun FrostedGlass(ui: HomeState, p: Palette) {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    val on = p.wallpaper && Store.frosted.value && remember(activity) { Blur.supported(activity) }
    val maxRadius = with(LocalDensity.current) { 32.dp.roundToPx() }
    LaunchedEffect(on) {
        ui.frosted = on
        if (!on) {
            Blur.set(activity, view, 0, 0)
            return@LaunchedEffect
        }
        snapshotFlow { (maxOf(ui.drawer.value, ui.page.value).coerceIn(0f, 1f) * maxRadius).roundToInt() }
            .collect { Blur.set(activity, view, it, 0) }
    }
}

@Composable
private fun SystemBars(p: Palette) {
    val window = LocalActivity.current?.window ?: return
    val showStatus = Store.showStatusBar.value
    LaunchedEffect(p, showStatus) {
        if (p.wallpaper) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        WindowCompat.getInsetsController(window, window.decorView).run {
            isAppearanceLightStatusBars = p.lightBars
            isAppearanceLightNavigationBars = p.lightBars
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showStatus) show(WindowInsetsCompat.Type.statusBars()) else hide(WindowInsetsCompat.Type.statusBars())
        }
    }
}

/** White text on an unknown wallpaper: darken just the top (clock) and bottom (apps) so it always reads. */
private fun Modifier.wallpaperScrim(p: Palette) = if (p != OnDarkWall) this else drawBehind {
    drawRect(Brush.verticalGradient(0f to Color(0x66000000), 0.32f to Color.Transparent))
    drawRect(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0x8C000000)))
}

fun alignment(): Alignment.Horizontal = when (Store.align.value) {
    "center" -> Alignment.CenterHorizontally
    "end" -> Alignment.End
    else -> Alignment.Start
}

fun LayoutCoordinates?.toRect(): Rect? = this?.takeIf { it.isAttached }?.boundsInRoot()?.let {
    Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
}

fun start(view: View, intent: Intent, bounds: Rect? = null): Boolean = try {
    view.context.startActivity(intent, launchOptions(view, bounds).toBundle())
    true
} catch (e: Exception) {
    false
}

// ---------- Root ----------

@Composable
fun Home(ui: HomeState, widgets: Widgets) {
    val palette = rememberPalette()
    SystemBars(palette)
    val ctx = LocalContext.current
    val scheme = if (palette.lightBars) dynamicLightColorScheme(ctx) else dynamicDarkColorScheme(ctx)

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPalette provides palette, LocalGlass provides glassFor(palette)) {
            BoxWithConstraints(Modifier.fillMaxSize().background(palette.bg).wallpaperScrim(palette)) {
                val widthPx = constraints.maxWidth.toFloat()
                val travel = min(constraints.maxHeight * 0.4f, with(LocalDensity.current) { 360.dp.toPx() })
                LaunchedEffect(maxWidth) { // folded or unfolded: back to the home page
                    ui.page.snap(0f)
                    ui.editing = null
                }
                val onWidgetsPage by remember { derivedStateOf { ui.page.value > 0.5f } }
                BackHandler(onWidgetsPage) { ui.page.animateTo(0f) }

                // With frosted glass the widgets page gets a faint wash too, so see-through widgets read as glass panels.
                if (ui.frosted) Box(Modifier.fillMaxSize().graphicsLayer { alpha = ui.page.value }.background(palette.frost.copy(alpha = palette.frost.alpha * 0.4f)))
                // Both pages always exist in the same order, so fold/unfold only resizes them and widget views are
                // never torn down. On both screens home fills the screen and widgets wait one swipe to the right
                // (a split view on the inner screen looked cluttered). Widgets are only ever translated, never
                // scaled: each moved frame re-positions their views.
                Box(Modifier.fillMaxSize().homeGestures(ui, widthPx, travel, LocalView.current, LocalHapticFeedback.current)) {
                    WidgetsPane(ui, widgets, Modifier.fillMaxSize().graphicsLayer {
                        alpha = homeAlpha(ui)
                        translationX = -widthPx * (1f - ui.page.value)
                    })
                    HomePane(ui, Modifier.fillMaxSize().graphicsLayer {
                        val d = ui.drawer.value
                        alpha = homeAlpha(ui)
                        val s = (1f - 0.04f * d) * (0.94f + 0.06f * ui.enter.value)
                        scaleX = s
                        scaleY = s
                        translationY = -d * travel * 0.12f
                        translationX = widthPx * ui.page.value
                    })
                }
                FrostedGlass(ui, palette)
                Drawer(ui, travel)
                AnimatedVisibility(ui.picker, enter = fadeIn() + slideInVertically { it / 10 }, exit = fadeOut() + slideOutVertically { it / 10 }) {
                    WidgetPicker(ui, widgets)
                }
                ui.rename?.let { RenameDialog(ui, it) }
            }
        }
    }
}

/** The home fades out as the drawer opens, and fades in when coming back from an app. */
private fun homeAlpha(ui: HomeState) = (1f - ui.drawer.value * 1.25f).coerceIn(0f, 1f) * (0.5f + 0.5f * ui.enter.value)

// ---------- Gestures ----------

private enum class Mode { Drawer, Shade, Page, Pull }

private const val PULL_ARM_DP = 110 // first stop: the swipe-left app
private const val PULL_FAR_DP = 230 // keep pulling: the pull-farther app

/** Where a swipe-left pull of [pullPx] (negative) has got to: 0 nothing yet, 1 swipe-left app, 2 pull-farther app. */
fun pullStage(pullPx: Float, density: Float): Int = when {
    -pullPx >= PULL_FAR_DP * density && Store.swipeFarApp.value.isNotEmpty() -> 2
    -pullPx >= PULL_ARM_DP * density -> 1
    else -> 0
}

fun pullTarget(stage: Int) = when (stage) {
    2 -> Store.swipeFarApp.value
    1 -> Store.swipeLeftApp.value
    else -> ""
}.takeIf { it.isNotEmpty() }

/**
 * Locks to an axis after touch slop and decides in the Initial pass (before children), consuming from
 * then on so labels and widget views get cancelled. Gives up if the finger rests past the long-press
 * timeout first, so long-presses always win.
 */
private fun Modifier.homeGestures(ui: HomeState, widthPx: Float, travel: Float, view: View, haptic: HapticFeedback) =
    pointerInput(widthPx, travel) {
        val slop = viewConfiguration.touchSlop
        val longPress = viewConfiguration.longPressTimeoutMillis
        val fling = 600.dp.toPx()
        val shadeAt = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (ui.drawerOpen || ui.picker || ui.editing != null || ui.menu != null || ui.rename != null) return@awaitEachGesture
            val onHome = ui.page.value < 0.5f
            val onDock = ui.dock?.contains(down.position) == true
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var total = Offset.Zero
            var mode: Mode? = null
            var shaded = false
            var stage = 0

            fun move(delta: Offset) {
                when (mode) {
                    Mode.Drawer -> ui.drawer.snap((ui.drawer.value - delta.y / travel).coerceIn(0f, 1f))
                    Mode.Page -> ui.page.snap((ui.page.value + delta.x / widthPx).coerceIn(0f, 1f))
                    Mode.Pull -> {
                        ui.pull.snap(min(0f, ui.pull.value + delta.x))
                        val now = pullStage(ui.pull.value, density)
                        if (now > stage) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) // a tick per stop
                        stage = now
                    }
                    Mode.Shade -> {
                        total += delta
                        if (!shaded && total.y > shadeAt) {
                            shaded = true
                            LockService.openShade(view.context)
                        }
                    }
                    null -> Unit
                }
            }

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                tracker.addPosition(change.uptimeMillis, change.position)
                val delta = change.positionChange()
                if (mode == null) {
                    if (change.uptimeMillis - down.uptimeMillis > longPress) return@awaitEachGesture
                    total += delta
                    val dist = total.getDistance()
                    if (dist < slop) continue
                    val horizontal = abs(total.x) > abs(total.y)
                    mode = when {
                        !horizontal && onDock -> return@awaitEachGesture // the dock magnifies along the finger
                        !horizontal && onHome && total.y < 0 -> Mode.Drawer
                        !horizontal && onHome && Store.swipeDownShade.value -> Mode.Shade
                        horizontal && onHome && total.x > 0 -> Mode.Page
                        horizontal && !onHome && total.x < 0 -> Mode.Page
                        horizontal && onHome && total.x < 0 -> Mode.Pull
                        else -> return@awaitEachGesture
                    }
                    change.consume()
                    val past = total * (1f - slop / dist) // only what moved beyond the slop, so nothing jumps
                    total = Offset.Zero
                    move(past)
                } else {
                    change.consume()
                    move(delta)
                }
            }

            val v = tracker.calculateVelocity()
            when (mode) {
                Mode.Drawer -> {
                    val open = v.y < -fling || (v.y < fling && ui.drawer.value > 0.35f)
                    if (open) ui.openDrawer(-v.y / travel) else ui.closeDrawer(-v.y / travel)
                }
                Mode.Page -> {
                    val toWidgets = v.x > fling || (v.x > -fling && ui.page.value > 0.5f)
                    ui.page.animateTo(if (toWidgets) 1f else 0f, v.x / widthPx)
                }
                Mode.Pull -> {
                    pullTarget(stage)?.let { Apps.launch(view, it, null) }
                    ui.pull.animateTo(0f, spec = spring(0.75f, 500f))
                }
                Mode.Shade, null -> Unit
            }
        }
    }

// ---------- Home pane ----------

@Composable
private fun HomePane(ui: HomeState, modifier: Modifier) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current

    Box(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { if (Store.doubleTapLock.value) lockOrExplain(ctx) },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        ui.menu = Menu.Home(it.x, it.y)
                    },
                )
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("All apps") { ui.openDrawer(); true },
                    CustomAccessibilityAction("Notifications") { LockService.openShade(ctx); true },
                    CustomAccessibilityAction("Add widget") { openPicker(ui); true },
                    CustomAccessibilityAction("Fluid settings") { ctx.startActivity(Intent(ctx, SettingsActivity::class.java)); true },
                )
            }
    ) {
        val align = alignment()
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 36.dp)
                .graphicsLayer { translationX = rubberBand(ui) },
            horizontalAlignment = align,
        ) {
            Clock(align)
            if (Store.favorites.value.isEmpty()) {
                Spacer(Modifier.weight(1f))
                Text("Swipe up for apps.\nLong-press one to put it here.", style = p.style(17.sp, color = p.dim))
            }
        }
        if (Store.favorites.value.isNotEmpty()) Dock(ui, Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 20.dp, bottom = 36.dp)
            .graphicsLayer {
                translationX = rubberBand(ui)
                alpha = 1f - (-ui.pull.value / (PULL_ARM_DP * density)).coerceIn(0f, 1f) // the pull hint takes its place
            })
        PullHint(ui, Modifier.align(Alignment.CenterEnd).padding(end = 20.dp))
        HoldBar(ui, Modifier.align(Alignment.BottomCenter))
    }
}

/** Rubber band: follows a swipe-left pull at first, then stiffens, so a long pull never shoves the home off screen. */
private fun GraphicsLayerScope.rubberBand(ui: HomeState) = -48.dp.toPx() * (1f - exp(ui.pull.value / 150.dp.toPx()))

/** Long-press on empty home: a glass bar slides up with Widgets, Wallpaper and Settings. Tap outside to close. */
@Composable
private fun HoldBar(ui: HomeState, modifier: Modifier) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val open = ui.menu is Menu.Home
    val rim = LocalGlass.current.rim
    BackHandler(open) { ui.menu = null }
    if (open) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { ui.menu = null } })
    AnimatedVisibility(
        open, modifier,
        enter = fadeIn(tween(160)) + slideInVertically(spring(0.8f, 500f)) { it },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it },
    ) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(LocalGlass.current.solid)
                .drawBehind { drawRoundRect(rim, cornerRadius = CornerRadius(28.dp.toPx()), style = Stroke(1.dp.toPx())) }
                .padding(vertical = 10.dp),
        ) {
            fun go(action: () -> Unit) { ui.menu = null; action() }
            HoldButton(R.drawable.ic_widgets, "Widgets") { go { openPicker(ui) } }
            HoldButton(R.drawable.ic_wallpaper, "Wallpaper") {
                go {
                    Store.theme.value = "wallpaper" // picking a wallpaper means wanting to see it
                    runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) }
                }
            }
            HoldButton(R.drawable.ic_settings, "Settings") { go { ctx.startActivity(Intent(ctx, SettingsActivity::class.java)) } }
        }
    }
}

@Composable
private fun RowScope.HoldButton(icon: Int, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).background(LocalGlass.current.fill, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = p.text, modifier = Modifier.size(26.dp))
        }
        Text(label, style = p.style(13.sp).copy(shadow = null), modifier = Modifier.padding(top = 8.dp))
    }
}

fun openPicker(ui: HomeState) {
    ui.page.animateTo(1f)
    ui.picker = true
}

private fun lockOrExplain(ctx: Context) {
    if (LockService.lock()) return
    Apps.toast("Turn on Fluid in Accessibility to lock with a double-tap")
    ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
}

@Composable
private fun PullHint(ui: HomeState, modifier: Modifier) {
    val near = Apps.find(Store.swipeLeftApp.value)?.label
    val far = Apps.find(Store.swipeFarApp.value)?.label
    if (near == null && far == null) return
    val p = LocalPalette.current
    val density = LocalDensity.current.density
    val shift = 32 * density
    // Only recomposes when a stop is crossed; the fade-in itself is draw-only.
    val stage by remember(density) { derivedStateOf { pullStage(ui.pull.value, density) } }
    val label = if (stage == 2 || near == null) far else near
    Column(horizontalAlignment = Alignment.End, modifier = modifier.graphicsLayer {
        val k = (-ui.pull.value / (PULL_ARM_DP * density)).coerceIn(0f, 1f)
        alpha = k
        translationX = (1f - k) * shift
    }) {
        Crossfade(label, animationSpec = tween(140), label = "pull target") {
            Text(it.orEmpty(), style = p.style(18.sp, color = if (pullTarget(stage) != null) p.text else p.dim))
        }
        if (stage == 1 && far != null) Text("keep pulling: $far", style = p.style(13.sp, color = p.dim))
    }
}

/** Next Clock-app alarm ("7:30 AM", or "Sat 7:30 AM" when it's over a day away), battery ("82%") and whether it's charging. */
private fun glance(ctx: Context, now: Long): Triple<String?, String, Boolean> {
    val locale = Locale.getDefault()
    val hm = if (DateFormat.is24HourFormat(ctx)) "Hm" else "hm"
    // Only the Clock app's own alarms: Samsung Calendar registers event reminders as "alarm clocks" too.
    val clock = ctx.packageManager.resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)?.activityInfo?.packageName
    val next = ctx.getSystemService(AlarmManager::class.java).nextAlarmClock?.takeIf { it.showIntent?.creatorPackage == clock }
    val alarm = next?.triggerTime?.let { t ->
        DateFormat.format(DateFormat.getBestDateTimePattern(locale, if (t - now < 86_400_000L) hm else "EEE$hm"), t).toString()
    }
    val power = ctx.getSystemService(BatteryManager::class.java)
    val level = power.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    return Triple(alarm, "$level%", power.isCharging)
}

/** The Clock app itself, not just its alarm tab: whichever app handles alarms, opened from its front door. */
private fun openClock(view: View) {
    val pm = view.context.packageManager
    val clock = pm.resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)?.activityInfo?.packageName
    start(view, clock?.let(pm::getLaunchIntentForPackage) ?: Intent(AlarmClock.ACTION_SHOW_ALARMS))
}

private val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_heavy, FontWeight.ExtraBold),
)

private val HOURS = listOf("TWELVE", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN")

/** "FOUR", "09", "PM": the same words as Lumen's lock screen clock, so unlocking lands on the same design. */
internal fun clockParts(now: Calendar) = listOf(
    HOURS[now.get(Calendar.HOUR)],
    "%02d".format(now.get(Calendar.MINUTE)),
    if (now.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM",
)

/**
 * Desaturate Pro's "TWELVE 56 PM / Have a beautiful Monday", matching the lock screen: the hour as a heavy word, then
 * a hairline and the date, next alarm and battery in spaced small caps.
 */
@Composable
private fun Clock(align: Alignment.Horizontal) {
    if (!Store.showClock.value && !Store.showDate.value) return
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val scale = Store.textScale.value
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000 - now % 60_000)
            }
        }
    }
    fun font(size: TextUnit, weight: FontWeight, color: Color = p.text, spacing: TextUnit = TextUnit.Unspecified) =
        p.style(size, weight, color).copy(fontFamily = Montserrat, letterSpacing = spacing)
    fun openDay() {
        val day = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(now.toString()).build()
        start(view, Intent(Intent.ACTION_VIEW, day)) ||
            start(view, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
    }
    Column(horizontalAlignment = align) {
        if (Store.showClock.value) {
            val (hour, minutes, half) = remember(now) { clockParts(Calendar.getInstance().apply { timeInMillis = now }) }
            val time = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(hour) }
                append(" $minutes")
                // A small PM up by the top of the digits, sized with the time so it follows the auto-fit.
                // The shift is a fraction of the time's own height, not the PM's: 0.48 lifts it like the lock screen's.
                withStyle(SpanStyle(fontWeight = FontWeight.Medium, fontSize = 0.24.em, letterSpacing = 0.2.em, baselineShift = BaselineShift(0.48f))) {
                    append(" $half") // a PM-sized space: a full-size one left a wide gap
                }
            }
            // ELEVEN 59 is the widest; it shrinks to fit rather than wrap.
            BasicText(time, style = font(46.sp * scale, FontWeight.Normal), maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 46.sp * scale),
                modifier = Modifier.clickable(null, null) { openClock(view) })
        }
        if (Store.showDate.value) Column(Modifier.clickable(null, null) { openDay() }, horizontalAlignment = align) {
            Text("Have a beautiful", style = font(20.sp * scale, FontWeight.Normal))
            Text(DateFormat.format("EEEE", now).toString(), style = font(20.sp * scale, FontWeight.ExtraBold))
        }
        Box(Modifier.padding(top = 12.dp).width(180.dp * scale).height(1.dp).background(p.text.copy(alpha = 0.8f)))
        // 12dp of padding gives 48dp touch targets; nudged so the first glyph lines up with the edge it's aligned to.
        val nudge = when (align) { Alignment.Start -> (-12).dp; Alignment.End -> 12.dp; else -> 0.dp }
        val (alarm, battery, charging) = remember(now) { glance(ctx, now) } // refreshed with the minute tick
        val info = font(11.sp * scale, FontWeight.Medium, p.dim, 0.2.em)
        Row(Modifier.offset(x = nudge), verticalAlignment = Alignment.CenterVertically) {
            if (Store.showDate.value) Text(DateFormat.format("d MMMM", now).toString().uppercase(), style = info,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { openDay() }.padding(12.dp))
            if (Store.showClock.value) Row(
                Modifier.clip(RoundedCornerShape(50)).clickable { openClock(view) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_alarm), contentDescription = "Clock", tint = p.dim, modifier = Modifier.size(15.dp * scale))
                if (Store.showGlance.value && alarm != null) Text(alarm.uppercase(), style = info, modifier = Modifier.padding(start = 6.dp))
            }
            // A bolt instead of "· charging", so the row stays one line and clear of the dock.
            if (Store.showClock.value && Store.showGlance.value) Row(
                Modifier.clip(RoundedCornerShape(50)).clickable { start(view, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (charging) Icon(painterResource(R.drawable.ic_bolt), contentDescription = "Charging", tint = p.dim,
                    modifier = Modifier.padding(end = 2.dp).size(14.dp * scale))
                Text(battery, style = info)
            }
        }
    }
}

// ---------- Dock ----------

private const val MAGNIFY = 1.8f // the icon right under the finger, like the Mac dock's magnification
private const val DOCK_ICON_DP = 52 // resting icon size; shrinks to fit many apps
private const val DOCK_PAD_DP = 9 // glass around the icons
private const val DOCK_EDGE_DP = 8 // glass to screen edge
private const val DOCK_LIGHT_DP = 5 // extra glass on the edge side, room for the notification light

/** Mac-dock magnification for [n] icons of [base] px in slots of [slot] px. y is the finger, px down from the first slot's top. */
class DockMath(val n: Int, val slot: Float, val base: Float) {
    /** Icon [i]'s scale: [MAGNIFY] under the finger, falling off over about two neighbours each side. */
    fun scale(i: Int, y: Float, amount: Float): Float {
        val d = (y - slot * (i + 0.5f)) / slot
        return 1f + (MAGNIFY - 1f) * amount * exp(-d * d / 2f)
    }

    /** Extra length icons [0, upTo) take up while magnified. */
    fun grown(upTo: Int, y: Float, amount: Float): Float {
        var sum = 0f
        for (j in 0 until upTo) sum += base * (scale(j, y, amount) - 1f)
        return sum
    }

    /** How far icon [i] moves: bigger icons push their neighbours apart, and the dock grows at both ends. */
    fun shift(i: Int, y: Float, amount: Float) = grown(i, y, amount) + base * (scale(i, y, amount) - 1f) / 2 - grown(n, y, amount) / 2

    /** The icon under [y] as the dock looks now (magnified and shifted), or null past its ends. */
    fun hit(y: Float, amount: Float): Int? {
        var best: Int? = null
        var bestD = slot
        for (i in 0 until n) {
            val d = abs(y - slot * (i + 0.5f) - shift(i, y, amount))
            if (d < bestD) {
                best = i
                bestD = d
            }
        }
        return best
    }
}

enum class Touch { Up, Cancel, Moved }

/** Feeds [down]'s pointer to [onMove] until it lifts, the home takes it as a sideways swipe, or it moves past [slop]. */
suspend fun AwaitPointerEventScope.follow(down: PointerInputChange, slop: Float, onMove: (Offset) -> Unit): Touch {
    while (true) {
        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return Touch.Cancel
        if (c.isConsumed) return Touch.Cancel
        c.consume()
        if (!c.pressed) return Touch.Up
        onMove(c.position)
        if ((c.position - down.position).getDistance() > slop) return Touch.Moved
    }
}

/**
 * The home apps as icons in a glass dock on the right edge, magnifying around the finger like the Mac dock.
 * Tap an icon, or slide along the dock and let go on one, to open it. Hold still to drag it to a new spot,
 * or let go for its menu. Magnifying is draw-only: icons scale and shift in graphicsLayer, slots never move.
 */
@Composable
private fun Dock(ui: HomeState, modifier: Modifier) {
    val p = LocalPalette.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val favs = Store.favorites.value
    val magnify = remember { Animatable(0f) }
    var touchY by remember { mutableFloatStateOf(0f) }
    var hovered by remember { mutableStateOf<Int?>(null) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val coords = remember { HashMap<String, LayoutCoordinates>() }
    DisposableEffect(Unit) { onDispose { ui.dock = null } }

    BoxWithConstraints(modifier) {
        val n = favs.size
        // Shrink to fit instead of scrolling (a scrolling home would fight swipe-up), leaving room to magnify at the ends.
        val base = minOf(DOCK_ICON_DP.dp, maxHeight / (n * 1.22f + 1.6f))
        val slot = base * 1.22f
        val pad = DOCK_PAD_DP.dp
        val edge = DOCK_EDGE_DP.dp
        val light = DOCK_LIGHT_DP.dp
        val density = LocalDensity.current
        val math = remember(n, slot, base, density) { with(density) { DockMath(n, slot.toPx(), base.toPx()) } }
        val iconPx = with(density) { (DOCK_ICON_DP.dp.toPx() * MAGNIFY).roundToInt() }
        val glass = LocalGlass.current.fill
        val rim = LocalGlass.current.rim

        Box(
            Modifier.align(Alignment.CenterEnd)
                .padding(end = edge)
                .size(base + pad * 2 + light, slot * n + pad * 2)
                .onGloballyPositioned { ui.dock = it.boundsInRoot() }
                .drawBehind {
                    val grow = math.grown(n, touchY, magnify.value)
                    val r = CornerRadius(size.width * 0.36f)
                    val at = Offset(0f, -grow / 2)
                    val box = Size(size.width, size.height + grow)
                    drawRoundRect(glass, at, box, r)
                    drawRoundRect(rim, at, box, r, style = Stroke(1.dp.toPx()))
                }
        )
        LazyColumn(
            Modifier.fillMaxSize().pointerInput(math) {
                val slop = viewConfiguration.touchSlop
                val longPress = viewConfiguration.longPressTimeoutMillis
                val padPx = pad.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val top = (size.height - n * math.slot) / 2
                    val left = size.width - edge.toPx() - math.base - padPx * 2 - light.toPx()
                    if (down.position.x < left || abs(down.position.y - size.height / 2f) > n * math.slot / 2 + padPx) return@awaitEachGesture
                    down.consume() // keeps the pane's own long-press / double-tap out of it
                    fun track(at: Offset) {
                        touchY = at.y - top
                        val now = if (at.x < left - math.slot) null else math.hit(touchY, magnify.value) // slid well off the dock: none
                        if (now != null && hovered != null && now != hovered) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        hovered = now
                    }
                    track(down.position)
                    scope.launch { magnify.animateTo(1f, spring(0.8f, 600f)) }
                    val first = withTimeoutOrNull(longPress) { follow(down, slop, ::track) }
                    val end = if (first == Touch.Moved) follow(down, Float.MAX_VALUE, ::track) else first
                    val fav = hovered?.let { Store.favorites.value.getOrNull(it) }
                    hovered = null
                    scope.launch { magnify.animateTo(0f, spring(1f, 400f)) }
                    if (fav == null) return@awaitEachGesture
                    if (end == Touch.Up) Apps.launch(view, fav.key, coords[fav.key].toRect())
                    if (first != null) return@awaitEachGesture
                    // Held still: drag it along the dock, or let go for its menu.
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    draggingKey = fav.key
                    dragY = 0f
                    var moved = 0f
                    drag(down.id) { c ->
                        val dy = c.positionChange().y
                        c.consume()
                        moved += abs(dy)
                        dragY += dy
                        val list = Store.favorites.value
                        val from = list.indexOfFirst { it.key == fav.key }
                        val to = (from + (dragY / math.slot).roundToInt()).coerceIn(0, list.lastIndex)
                        if (from >= 0 && to != from) {
                            // Reorder live; LazyColumn animates the neighbours, the dragged icon stays under the finger.
                            Store.favorites.value = list.toMutableList().apply { add(to, removeAt(from)) }
                            dragY -= (to - from) * math.slot
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    }
                    draggingKey = null
                    dragY = 0f
                    if (moved <= slop) ui.menu = Menu.Favorite(fav)
                }
            },
            userScrollEnabled = false,
            verticalArrangement = Arrangement.Center,
        ) {
            itemsIndexed(favs, key = { _, f -> f.key }) { i, fav ->
                val dragging = draggingKey == fav.key
                Row(
                    Modifier.fillMaxWidth().height(slot)
                        .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (dragging) null else spring(0.85f, 700f))
                        .graphicsLayer { translationY = math.shift(i, touchY, magnify.value) + if (dragging) dragY else 0f }
                        .padding(end = edge + pad + light)
                        .semantics {
                            contentDescription = fav.label
                            role = Role.Button
                            onClick { Apps.launch(view, fav.key, coords[fav.key].toRect()); true }
                            onLongClick { ui.menu = Menu.Favorite(fav); true }
                        },
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hovered == i) Text(
                        fav.label, maxLines = 1, style = p.style(14.sp).copy(shadow = null),
                        modifier = Modifier
                            .graphicsLayer { translationX = -math.base * (math.scale(i, touchY, magnify.value) - 1f) }
                            .background(p.sheet.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    Box(Modifier.padding(start = 14.dp).size(base)) {
                        DockIcon(fav, iconPx, Modifier.fillMaxSize()
                            .graphicsLayer {
                                val s = math.scale(i, touchY, magnify.value)
                                scaleX = if (dragging) maxOf(s, 1.15f) else s
                                scaleY = scaleX
                                transformOrigin = TransformOrigin(1f, 0.5f) // grows away from the screen edge
                            }
                            .onGloballyPositioned { coords[fav.key] = it })
                        // Like the Mac dock's running light, on the screen-edge side: this app has notifications.
                        if (Dots.has(Apps.find(fav.key))) Dot(Modifier.align(Alignment.CenterEnd).offset(x = (pad + light) / 2 + 3.5.dp))
                        FavoriteMenu(ui, fav)
                    }
                }
            }
        }
    }
}

/** A home app's or shortcut's icon, loaded big enough to stay sharp magnified. An app pair shows both, corner to corner. */
@Composable
fun DockIcon(fav: Favorite, px: Int, modifier: Modifier) {
    if (fav.key.startsWith("p|")) {
        val (a, b) = fav.key.removePrefix("p|").split("||", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        Box(modifier) {
            DockIcon(Favorite(a, fav.label), px, Modifier.fillMaxSize(0.66f).align(Alignment.TopStart))
            DockIcon(Favorite(b, fav.label), px, Modifier.fillMaxSize(0.66f).align(Alignment.BottomEnd))
        }
        return
    }
    val p = LocalPalette.current
    val icon by produceState(Apps.icons[fav.key], fav.key, Apps.all) { Apps.bigIcon(fav.key, px)?.let { value = it } }
    icon?.let { Image(it, contentDescription = null, modifier = modifier) }
        ?: Box(modifier.background(p.text.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
            Text(fav.label.take(1).uppercase(), style = p.style(20.sp))
        }
}

@Composable
private fun FavoriteMenu(ui: HomeState, fav: Favorite) {
    GlassMenu(ui.menu == Menu.Favorite(fav), { ui.menu = null }) {
        MenuTitle(fav.label)
        GlassItem("Rename") { ui.menu = null; ui.rename = fav }
        GlassItem("Remove from home", danger = true) {
            ui.menu = null
            Store.favorites.value = Store.favorites.value.filterNot { it.key == fav.key }
        }
        if (!fav.key.startsWith("p|")) GlassItem("App info") { ui.menu = null; Apps.appInfo(fav.key) }
    }
}

@Composable
private fun RenameDialog(ui: HomeState, target: Favorite) {
    var text by remember(target) { mutableStateOf(target.label) }
    AlertDialog(
        onDismissRequest = { ui.rename = null },
        title = { Text("Rename") },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("Leave empty to reset") }) },
        confirmButton = {
            TextButton({
                ui.rename = null
                val name = text.trim()
                Store.favorites.value = Store.favorites.value.map { if (it.key == target.key && name.isNotEmpty()) it.copy(label = name) else it }
                if (target.key.startsWith("a|")) {
                    Store.renames.value = if (name.isEmpty()) Store.renames.value - target.key else Store.renames.value + (target.key to name)
                    Apps.reload()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton({ ui.rename = null }) { Text("Cancel") } },
    )
}
