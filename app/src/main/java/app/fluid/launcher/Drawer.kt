package app.fluid.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.produceState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.os.Build
import android.app.SearchManager
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.drop
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/** Not placed (so no drawing, no touches) while closed, but kept composed so opening costs nothing. */
private fun Modifier.placeWhen(visible: () -> Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (visible()) placeable.place(0, 0) }
}

@Composable
fun Drawer(ui: HomeState, travel: Float) {
    val p = LocalPalette.current
    val view = LocalView.current
    val scale = Store.textScale.value
    val fling = with(LocalDensity.current) { 600.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val visible by remember { derivedStateOf { ui.drawer.value > 0.001f } }

    val shown = remember(Apps.all, Store.hidden.value) { Apps.all.filter { it.key !in Store.hidden.value } }
    val searching = ui.query.isNotBlank()
    val launches = Store.launches.value
    // Your apps on top: most-launched first, skipping what's already on the home screen (one swipe away anyway).
    val favorites = Store.favorites.value
    val top = remember(shown, launches, favorites) {
        val onHome = favorites.mapTo(HashSet()) { it.key }
        shown.filter { (launches[it.key] ?: 0) > 0 && it.key !in onHome }.sortedByDescending { launches[it.key] }.take(5)
    }
    val extras = Apps.extras

    // People: "call mom", "text sam", or just "mom". Looked up off the main thread, a beat after typing pauses.
    val ctx = LocalContext.current
    var contactsAllowed by remember { mutableStateOf(Contacts.allowed(ctx)) }
    LaunchedEffect(ui.drawerOpen) { if (ui.drawerOpen) contactsAllowed = Contacts.allowed(ctx) } // granted in Settings meanwhile?
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsAllowed = it }
    val (verb, name) = remember(ui.query) { Contacts.parse(ui.query) }
    var people by remember { mutableStateOf(emptyList<ContactHit>()) }
    LaunchedEffect(ui.query, contactsAllowed) {
        if (!searching || !contactsAllowed) {
            people = emptyList()
            return@LaunchedEffect
        }
        delay(120)
        people = withContext(Dispatchers.IO) { Contacts.search(ctx, name, text = verb == true) }
    }
    // Calls need their own permission; without it the dialer opens with the number filled in instead.
    var pendingCall by remember { mutableStateOf<ContactHit?>(null) }
    val askCall = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingCall?.let { dial(view, it, direct = granted) }
        pendingCall = null
    }

    val results: List<Hit> = remember(shown, ui.query, launches, extras, people, contactsAllowed) {
        // Not searching: the apps grid shows them all (AppGrid), so there's no list to build.
        if (searching) searchAll(shown, extras, people, ui.query, launches, verb, name, askContacts = !contactsAllowed) else emptyList()
    }
    fun open(hit: Hit, coords: LayoutCoordinates?) {
        when (hit) {
            is AppEntry -> ui.pairFirst?.let { first ->
                // Picking the second half of a split screen.
                if (hit.key != first.key) {
                    if (ui.pairSave) Store.favorites.value += Favorite(Apps.pairKey(first.key, hit.key), "${first.label} + ${hit.label}")
                    Apps.openPair(view, first.key, hit.key)
                }
                ui.pairFirst = null
            } ?: Apps.launch(view, hit.key, coords.toRect())
            is Extra -> if (hit.intent != null) start(view, hit.intent, coords.toRect()) else Apps.launch(view, hit.key, coords.toRect())
            is AskContacts -> askContacts.launch(Manifest.permission.READ_CONTACTS)
            is ContactHit -> when {
                hit.text -> start(view, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", hit.number, null)))
                ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED -> dial(view, hit, direct = true)
                pendingCall == null -> { // one request at a time: Android denies a second one outright
                    pendingCall = hit
                    askCall.launch(Manifest.permission.CALL_PHONE)
                }
                else -> Unit
            }
        }
    }
    // Enter: the top match, or the web when nothing matches.
    fun go() = results.firstOrNull()?.let { open(it, null) } ?: run { if (searching) searchWeb(view, ui.query) }

    // Keyed on the query, not the results: launching bumps launch counts, which recomputes results. Waits for a
    // pause in typing, so passing through a single match on the way to a longer query ("cal" → "call mom") doesn't fire.
    LaunchedEffect(ui.query) {
        if (searching && results.size == 1 && results[0] is AppEntry && Store.autoOpenMatch.value) {
            delay(300)
            open(results[0], null)
        }
    }

    // Pull down from the top of the list (or on the search bar) to close; fling decides.
    fun settle(v: Float) {
        if (v > fling || (v > -fling && ui.drawer.value < 0.7f)) ui.closeDrawer(-v / travel) else ui.drawer.animateTo(1f, -v / travel)
    }
    val pullToClose = remember(travel) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || ui.drawer.value >= 1f || available.y >= 0f) return Offset.Zero
                ui.drawer.snap((ui.drawer.value - available.y / travel).coerceAtMost(1f)) // pulling back up re-opens first
                return Offset(0f, available.y)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                ui.drawer.snap((ui.drawer.value - available.y / travel).coerceAtLeast(0f))
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (ui.drawer.value >= 1f || !ui.drawerOpen) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }

    val activity = LocalActivity.current
    if (Build.VERSION.SDK_INT >= 34 && activity != null) {
        // Overlay priority outranks the keyboard's own back callback, so one back closes keyboard and drawer
        // together instead of the first back only dropping the keyboard.
        DisposableEffect(ui.drawerOpen, activity) {
            if (!ui.drawerOpen) return@DisposableEffect onDispose {}
            val callback = object : OnBackAnimationCallback {
                override fun onBackProgressed(e: BackEvent) = ui.drawer.snap(1f - 0.3f * e.progress)
                override fun onBackInvoked() = ui.closeDrawer()
                override fun onBackCancelled() = ui.drawer.animateTo(1f)
            }
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
            onDispose { activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback) }
        }
    } else {
        PredictiveBackHandler(ui.drawerOpen) { events ->
            try {
                events.collect { ui.drawer.snap(1f - 0.3f * it.progress) }
                ui.closeDrawer()
            } catch (e: CancellationException) {
                ui.drawer.animateTo(1f)
                throw e
            }
        }
    }

    // The cascade's own clock. While the finger drags, the drawer's progress drives the rows; once released,
    // the drawer springs open in ~0.2s, far too fast to see a cascade, so the rows carry on from where the
    // finger left them at their own pace. Also replays when the results change mode (first letter, or cleared).
    val rise = with(LocalDensity.current) { 44.dp.toPx() }
    val originX = when (Store.align.value) { "center" -> 0.5f; "end" -> 1f; else -> 0f }
    val listIn = remember { Animatable(1f) }
    LaunchedEffect(ui.drawerOpen) {
        if (!ui.drawerOpen) return@LaunchedEffect
        val from = ui.drawer.value
        listIn.snapTo(from)
        listIn.animateTo(1f, tween((620 * (1f - from)).toInt().coerceAtLeast(320), easing = LinearEasing))
    }
    LaunchedEffect(searching) {
        listIn.snapTo(0f)
        listIn.animateTo(1f, tween(460, easing = LinearEasing))
    }

    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(ui.wantKeyboard) {
        if (ui.wantKeyboard) {
            withFrameNanos {} // the drawer may only be placed this frame
            runCatching { focus.requestFocus() }
            keyboard?.show()
        } else {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    // Tapping Search opens the keyboard without touching wantKeyboard, so closing the drawer must drop it explicitly.
    LaunchedEffect(ui.drawerOpen) {
        if (!ui.drawerOpen) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    Box(
        Modifier.fillMaxSize()
            .placeWhen { visible }
            .graphicsLayer { alpha = ui.drawer.value.coerceIn(0f, 1f) }
            .background(if (ui.frosted) p.frost else p.sheet) // frosted: the blur carries most of the contrast
    ) {
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxSize()
                .graphicsLayer { translationY = (1f - ui.drawer.value) * travel * 0.5f }
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Results grow upward from the search bar, so the best match sits right above the thumb.
            if (!searching) AppGrid(ui, shown, ::open, Modifier.weight(1f).fillMaxWidth().nestedScroll(pullToClose)) else SearchGrid(ui, results, ::open, Modifier.weight(1f).fillMaxWidth().nestedScroll(pullToClose)) {
                searchWeb(view, ui.query)
            }
            // Pulling down here, or on the search bar, closes the drawer.
            val dragDrawer = rememberDraggableState { dy -> ui.drawer.snap((ui.drawer.value - dy / travel).coerceIn(0f, 1f)) }
            ui.pairFirst?.let { first -> PairBanner(ui, first) }
            AnimatedVisibility( // tucks away while typing so results get the room
                Store.topApps.value && !searching && top.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                TopApps(
                    ui, top, ::open,
                    Modifier.draggable(dragDrawer, Orientation.Vertical, onDragStopped = { settle(it) })
                        .graphicsLayer { // first in the cascade: it's nearest the search bar
                            val t = cascade(0, min(ui.drawer.value, listIn.value))
                            alpha = t
                            translationY = (1f - t) * rise
                        },
                )
            }
            BasicTextField(
                value = ui.query,
                onValueChange = { ui.query = it },
                singleLine = true,
                textStyle = p.style(24.sp * scale),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { go() }),
                modifier = Modifier.fillMaxWidth()
                    .onPreviewKeyEvent { // hardware keyboards (DeX, Bluetooth) send Enter as a key, not an IME action
                        if (it.key != Key.Enter) return@onPreviewKeyEvent false
                        if (it.type == KeyEventType.KeyUp) go()
                        true // swallow the key-down too, or the field also fires its Go action and Enter acts twice
                    }
                    .draggable(dragDrawer, Orientation.Vertical, onDragStopped = { settle(it) })
                    .focusRequester(focus)
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                decorationBox = { field ->
                    Box {
                        if (ui.query.isEmpty()) Text("Search", style = p.style(24.sp * scale, color = p.dim))
                        field()
                    }
                },
            )
        }
    }
}

/**
 * Two motions on one layer, both read in the draw phase only (no recomposition per frame):
 * - Cascade: rows rise into place one after another from the search bar, driven by the drawer's progress
 *   (so it follows the finger and reverses on close) and by [listIn] after release or a search mode change.
 * - Curve (full list only): rows bend along the edge of a big circle fixed to the screen, the middle row
 *   farthest out and rows above and below curving back by up to [CURVE_DP], so the list reads as a wheel.
 *   Center alignment stays straight; end alignment mirrors it.
 * - Wheel (full list only): rows in the outer 30% of the list at each end (the top edge and the search bar)
 *   tilt away, shrink and fade, rolling flat as they scroll toward the middle. Sized to the visible list, so it
 *   feels the same with the keyboard open (short list) or closed (tall list). An edge only rolls when there's
 *   more list hidden beyond it, so the first and last apps sit flat at either end, and search results never tilt.
 */
/** One row of your most-used apps right above the search bar: icon over a short name, same tap and long-press as the list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopApps(ui: HomeState, apps: List<AppEntry>, open: (AppEntry, LayoutCoordinates?) -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val scale = Store.textScale.value
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 28.dp).fillMaxWidth().height(1.dp).background(p.dim.copy(alpha = 0.18f)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            apps.forEach { app ->
                var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                Column(
                    Modifier.weight(1f)
                        .onGloballyPositioned { coords = it }
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                ui.menu = Menu.App(app)
                            },
                            onClick = { open(app, coords) },
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (Store.drawerIcons.value) {
                        val icon = Apps.icons[app.key]
                        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        else Spacer(Modifier.size(40.dp))
                    }
                    Row(Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.label,
                            style = p.style((if (Store.drawerIcons.value) 12.sp else 15.sp) * scale),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (Dots.has(app)) Dot(Modifier.padding(start = 4.dp))
                    }
                    AppMenu(ui, app)
                }
            }
        }
    }
}

/**
 * Apps, plus app shortcuts and Settings pages from the second letter on (at most 6 of those, so a short query
 * isn't buried). Merged by match quality; on a tie apps come first. An extra matched only through its everyday
 * words ranks one step below a title match.
 */
private fun searchAll(
    apps: List<AppEntry>, extras: List<Extra>, people: List<ContactHit>, query: String, launches: Map<String, Int>,
    verb: Boolean?, name: String, askContacts: Boolean,
): List<Hit> {
    // "call …"/"text …": people first (or the ask to read contacts), then whatever else the words match.
    if (verb != null) return (if (askContacts) listOf(AskContacts(name)) else people) + searchAll(apps, extras, emptyList(), query, launches, null, name, false)
    val appHits = rank(apps, query, AppEntry::label) { launches[it.key] ?: 0 }.map { it to matchScore(it.label, query)!! }
    if (query.trim().length < 2) return appHits.map { it.first }
    val shownApps = apps.mapTo(HashSet()) { it.key } // shortcuts of hidden apps stay hidden
    val extraHits = extras.asSequence()
        .filter { it.app == null || it.app.key in shownApps }
        .mapNotNull { e ->
            // Everyday words match one at a time: as a phrase, "browser launcher" would answer to the initials "bl".
            val viaWords = e.words.split(' ').mapNotNull { matchScore(it, query) }.minOrNull()?.plus(1)
            (matchScore(e.label, query) ?: viaWords)?.let { e to it }
        }
        .sortedBy { it.second }.take(6).toList()
    // Plain names find people too, after apps and shortcuts that match as well, at most 3.
    val peopleHits = people.take(3).map { it to (matchScore(it.label, query) ?: 1) }
    return (appHits.map { Triple(it.first as Hit, it.second, 0) } + extraHits.map { Triple(it.first, it.second, 1) } +
        peopleHits.map { Triple(it.first, it.second, 2) })
        .sortedWith(compareBy({ it.second }, { it.third })) // stable: apps keep their launch-count order
        .map { it.first }
}

/** A shortcut or Settings page: its app's icon, the name, then "· Chrome" or "· Settings" in small print. */
@Composable
private fun ExtraRow(hit: Extra, best: Boolean, modifier: Modifier, onOpen: (LayoutCoordinates?) -> Unit) {
    val p = LocalPalette.current
    val scale = Store.textScale.value
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val color by animateColorAsState(if (best) p.accent else p.text, tween(160))
    Row(
        modifier.fillMaxWidth().onGloballyPositioned { coords = it }.clickable(null, null) { onOpen(coords) }
            .padding(horizontal = 28.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (Store.drawerIcons.value) {
            val icon = hit.app?.let { Apps.icons[it.key] }
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(ICON_DP.dp))
            else Spacer(Modifier.size(ICON_DP.dp))
            Spacer(Modifier.width(16.dp))
        }
        Text(hit.label, style = p.style(22.sp * scale, color = color), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        Text("  ·  ${if (hit.intent != null) "Settings" else hit.app?.label.orEmpty()}", style = p.style(15.sp * scale, color = p.dim), maxLines = 1)
    }
}

/** Unread notifications: a small dot in the accent colour after the name. */
@Composable
fun Dot(modifier: Modifier = Modifier) = Box(modifier.size(7.dp).background(LocalPalette.current.accent, CircleShape))

/** "Pick an app to open beside Chrome", with a switch to keep the pair on the home screen. */
@Composable
private fun PairBanner(ui: HomeState, first: AppEntry) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Pick an app to open beside ${first.label}", style = p.style(15.sp, color = p.accent), maxLines = 2,
            modifier = Modifier.weight(1f))
        Text(if (ui.pairSave) "Save to home  \u2713" else "Save to home", style = p.style(14.sp, color = if (ui.pairSave) p.accent else p.dim),
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { ui.pairSave = !ui.pairSave }.padding(10.dp))
        Text("Cancel", style = p.style(14.sp, color = p.dim),
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { ui.pairFirst = null }.padding(10.dp))
    }
}

/** The full list's row nearest the middle of the arc. */
private fun middleRow(info: LazyListLayoutInfo): Int? {
    val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index
}

/**
 * The default web-search app if there is one; otherwise a Google results page in the default browser. With
 * several search apps and no default, ACTION_WEB_SEARCH would put an "Open with" chooser in the way every time.
 */
private fun searchWeb(view: View, query: String) {
    val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
    val handler = view.context.packageManager.resolveActivity(search, 0)?.activityInfo?.packageName
    if (handler != null && handler != "android" && start(view, search)) return // "android" = the chooser
    start(view, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
}

private fun dial(view: View, hit: ContactHit, direct: Boolean) =
    start(view, Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", hit.number, null)))

/** A person: their initial in a soft circle, the name, then "· call mobile" or "· text mobile". */
@Composable
private fun ContactRow(hit: ContactHit, best: Boolean, modifier: Modifier, onOpen: () -> Unit) {
    val p = LocalPalette.current
    val scale = Store.textScale.value
    val color by animateColorAsState(if (best) p.accent else p.text, tween(160))
    Row(
        modifier.fillMaxWidth().clickable(null, null, onClick = onOpen).padding(horizontal = 28.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (Store.drawerIcons.value) {
            Box(Modifier.size(ICON_DP.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Text(hit.label.take(1).uppercase(), style = p.style(16.sp, FontWeight.Medium, color = p.accent))
            }
            Spacer(Modifier.width(16.dp))
        }
        Text(hit.label, style = p.style(22.sp * scale, color = color), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        Text("  ·  ${if (hit.text) "text" else "call"} ${hit.kind}", style = p.style(15.sp * scale, color = p.dim), maxLines = 1)
    }
}

@Composable
private fun ActionRow(text: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier.fillMaxWidth().clickable(null, null, onClick = onClick).padding(horizontal = 28.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = p.accent, modifier = Modifier.size(ICON_DP.dp).padding(4.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = p.style(22.sp * Store.textScale.value, color = p.accent),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Where row [rank] (0 = nearest the search bar) is in the opening cascade at drawer progress [p], 0..1. Start
 * times step 0.03 apart (up to 12 rows), each row then takes 0.54 of the run: the 12th lands exactly at 1.
 */
private fun cascade(rank: Int, p: Float) =
    FastOutSlowInEasing.transform(((p - 0.1f - rank.coerceIn(0, 12) * 0.03f) / 0.54f).coerceIn(0f, 1f))

/** How far the curved list's top and bottom rows bend in, at most. Rows keep this much free space so names aren't cut off. */
private const val CURVE_DP = 80

private fun Modifier.rowMotion(
    index: Int, list: LazyListState, searching: Boolean, ui: HomeState,
    listIn: Animatable<Float, *>, rise: Float, originX: Float,
) = graphicsLayer {
    val info = list.layoutInfo
    // Searching uses reverseLayout, so index 0 already sits on the search bar; otherwise count up from the last visible row.
    val rank = if (searching) index - list.firstVisibleItemIndex else (info.visibleItemsInfo.lastOrNull()?.index ?: index) - index
    val t = cascade(rank, min(ui.drawer.value, listIn.value))
    var fade = t
    translationY = (1f - t) * rise

    val item = if (searching) null else info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item != null) {
        val edge = (info.viewportEndOffset - info.viewportStartOffset) * 0.3f
        val last = info.visibleItemsInfo.last()
        val hiddenAbove = if (list.firstVisibleItemIndex > 0) edge else list.firstVisibleItemScrollOffset.toFloat()
        val hiddenBelow = if (last.index < info.totalItemsCount - 1) edge
        else (last.offset + last.size + info.afterContentPadding - info.viewportEndOffset).toFloat()
        val center = item.offset + item.size / 2f
        val fromTop = center - info.viewportStartOffset
        val fromBottom = info.viewportEndOffset - center
        val nearTop = fromTop < fromBottom
        val room = (if (nearTop) hiddenAbove else hiddenBelow).coerceIn(0f, edge) / edge
        val k = (1f - (minOf(fromTop, fromBottom) / edge).coerceIn(0f, 1f)) * room // grows evenly toward the edge

        // Circle through the middle row and the two ends of the list, bulging [bulge] at the ends:
        // r = (h² + b²) / 2b, and a row d from the middle sits r - √(r² - d²) further in.
        val h = (info.viewportEndOffset - info.viewportStartOffset) / 2f
        val d = center - (info.viewportStartOffset + h)
        val bulge = (0.13f * 2 * h).coerceIn(40.dp.toPx(), CURVE_DP.dp.toPx())
        val r = (h * h + bulge * bulge) / (2 * bulge)
        translationX = (1f - 2 * originX) * (r - sqrt((r * r - d * d).coerceAtLeast(0f)))

        transformOrigin = TransformOrigin(originX, 0.5f)
        cameraDistance = 24f // 3x the default: rows fold and fade, without perspective leaning the text into italics
        rotationX = (if (nearTop) 1f else -1f) * 42f * k
        scaleX = 1f - 0.1f * k
        scaleY = scaleX
        fade *= 1f - 0.75f * k
    }
    // Default compositing on purpose: a tilted row is also faded, so it renders flat into a buffer that is tilted as
    // one texture. ModulateAlpha skips the buffer but draws the text in perspective: 2.5x the GPU time per scroll frame.
    alpha = fade
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerRow(ui: HomeState, app: AppEntry, best: Boolean, curved: Boolean, modifier: Modifier, open: (AppEntry, LayoutCoordinates?) -> Unit) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Row(
        modifier.fillMaxWidth()
            .onGloballyPositioned { coords = it }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    ui.menu = Menu.App(app)
                },
                onClick = { open(app, coords) },
            )
            // The curve shifts rows toward the far side; keep that much room so long names still end in "…" on screen.
            .padding(
                start = 28.dp + if (curved && Store.align.value == "end") CURVE_DP.dp else 0.dp,
                end = 28.dp + if (curved && Store.align.value == "start") CURVE_DP.dp else 0.dp,
                top = 10.dp,
                bottom = 10.dp,
            )
            .graphicsLayer { alpha = if (pressed) 0.5f else 1f },
        horizontalArrangement = when (Store.align.value) {
            "center" -> Arrangement.Center
            "end" -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (Store.drawerIcons.value) {
            // The slot is reserved before the icon arrives, so the name never jumps sideways.
            val icon = Apps.icons[app.key]
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(ICON_DP.dp))
            else Spacer(Modifier.size(ICON_DP.dp))
            Spacer(Modifier.width(16.dp))
        }
        val color by animateColorAsState(if (best) p.accent else p.text, tween(160)) // highlight slides between rows
        Text(
            if (app.isWork) "${app.label}  ·  work" else app.label,
            style = p.style(22.sp * Store.textScale.value, color = color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (Dots.has(app)) Dot(Modifier.padding(start = 10.dp))
        AppMenu(ui, app)
    }
}

@Composable
private fun AppMenu(ui: HomeState, app: AppEntry) {
    val ctx = LocalContext.current
    val view = LocalView.current
    GlassMenu(ui.menu == Menu.App(app), { ui.menu = null }) {
        MenuTitle(app.label)
        val shortcuts = remember { Apps.shortcuts(app) }
        shortcuts.forEach { s ->
            GlassItem(s.shortLabel?.toString() ?: s.id) {
                ui.menu = null
                Apps.launch(view, Apps.shortcutKey(s), null)
            }
        }
        if (shortcuts.isNotEmpty()) GlassDivider()
        if (Apps.mainIntent(app.key) != null) GlassItem("Open in split screen") {
            ui.menu = null
            ui.query = ""
            ui.pairSave = false
            ui.pairFirst = app
        }
        val onHome = Store.favorites.value.any { it.key == app.key }
        GlassItem(if (onHome) "Remove from home" else "Add to home") {
            ui.menu = null
            Store.favorites.value = if (onHome) Store.favorites.value.filterNot { it.key == app.key }
            else Store.favorites.value + Favorite(app.key, app.label)
        }
        GlassItem("Rename") { ui.menu = null; ui.rename = Favorite(app.key, app.label) }
        GlassItem("Hide") { ui.menu = null; Store.hidden.value += app.key }
        GlassItem("App info") { ui.menu = null; Apps.appInfo(app.key) }
        if (!app.isWork) GlassItem("Uninstall", danger = true) { ui.menu = null; Apps.uninstall(ctx, app) }
    }
}

/**
 * All apps, A to Z, as an icon grid on full-screen glass (the home dock's look). Swipes only scroll; a tap opens
 * (sliding along a magnifying pill opened apps by accident while scrolling). Long-press for the app's menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGrid(ui: HomeState, apps: List<AppEntry>, open: (AppEntry, LayoutCoordinates?) -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val glass = LocalGlass.current
    val haptic = LocalHapticFeedback.current
    val iconPx = with(LocalDensity.current) { 52.dp.toPx().roundToInt() * 2 }
    // Sections like Social, Money, Shopping: guessed once per app list, off the main thread.
    val ctx = LocalContext.current
    val sections by produceState(emptyList<Pair<String, List<AppEntry>>>(), apps) {
        value = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            apps.groupBy { app ->
                val pkg = app.component.packageName
                categorize(pkg, app.label, runCatching { pm.getApplicationInfo(pkg, 0).category }.getOrDefault(-1))
            }.toList().sortedBy { CATEGORIES.indexOf(it.first) }
        }
    }
    // Opening wave: icons pop in one after another, read only in graphicsLayer (draw-only, no recomposition).
    val appear = remember { Animatable(1f) }
    LaunchedEffect(ui.drawerOpen) {
        if (!ui.drawerOpen) return@LaunchedEffect
        appear.snapTo(0f)
        appear.animateTo(1f, tween(560, easing = LinearEasing))
    }
    LazyVerticalGrid(
        GridCells.Adaptive(84.dp),
        modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            .drawBehind {
                val r = CornerRadius(28.dp.toPx())
                drawRoundRect(glass.fill, cornerRadius = r)
                drawRoundRect(glass.rim, cornerRadius = r, style = Stroke(1.dp.toPx()))
            },
        contentPadding = PaddingValues(10.dp),
    ) {
        // Wave positions counted from the same read of [sections] the items use: a separately remembered list
        // can lag a recomposition behind the grid's content and index past its end.
        var start = 0
        sections.forEach { (name, group) ->
            val base = start
            start += group.size
            item(key = "h:$name", span = { GridItemSpan(maxLineSpan) }) {
                Text(name.uppercase(), style = p.style(12.sp, FontWeight.Medium, color = p.dim).copy(letterSpacing = 1.5.sp),
                    modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 2.dp))
            }
            itemsIndexed(group, key = { _, app -> app.key }) { i, app ->
                AppCell(ui, app, open, modifier = Modifier.popIn(base + i) { appear.value })
            }
        }
    }
}

/** One app in the grid: big icon, name below. Tap opens, long-press for its menu; [best] (what Go opens) is ringed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(ui: HomeState, app: AppEntry, open: (AppEntry, LayoutCoordinates?) -> Unit, best: Boolean = false, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val iconPx = with(LocalDensity.current) { 52.dp.toPx().roundToInt() * 2 }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val grow by animateFloatAsState(if (pressed) 1.1f else 1f, spring(0.55f, 900f), label = "press")
    Column(
        modifier.onGloballyPositioned { coords = it }
            .clip(RoundedCornerShape(18.dp))
            .then(if (best) Modifier.background(p.accent.copy(alpha = 0.18f)) else Modifier)
            .combinedClickable(
                interactionSource = press,
                indication = null, // the icon's own grow is the feedback
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    ui.menu = Menu.App(app)
                },
                onClick = { open(app, coords) },
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.graphicsLayer { scaleX = grow; scaleY = grow }) {
            DockIcon(Favorite(app.key, app.label), iconPx, Modifier.size(52.dp))
            if (Dots.has(app)) Dot(Modifier.align(Alignment.TopEnd))
        }
        Text(app.label, style = p.style(12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp))
        AppMenu(ui, app)
    }
}

/** The same glass panel as the apps grid, filling up from the search bar: matching apps as icons (the best one
 * bottom-left, nearest the thumb and ringed), then shortcuts, Settings pages and people as rows above them. */
@Composable
private fun SearchGrid(ui: HomeState, results: List<Hit>, open: (Hit, LayoutCoordinates?) -> Unit, modifier: Modifier, web: () -> Unit) {
    val p = LocalPalette.current
    val glass = LocalGlass.current
    val apps = results.filterIsInstance<AppEntry>()
    val others = results.filter { it !is AppEntry }
    val bestKey = results.firstOrNull()?.key
    LazyVerticalGrid(
        GridCells.Adaptive(84.dp),
        modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            .drawBehind {
                val r = CornerRadius(28.dp.toPx())
                drawRoundRect(glass.fill, cornerRadius = r)
                drawRoundRect(glass.rim, cornerRadius = r, style = Stroke(1.dp.toPx()))
            },
        reverseLayout = true,
        contentPadding = PaddingValues(10.dp),
    ) {
        items(apps, key = { it.key }) { app -> AppCell(ui, app, { a, c -> open(a, c) }, best = app.key == bestKey) }
        items(others, key = { it.key }, span = { GridItemSpan(maxLineSpan) }) { hit ->
            when (hit) {
                is Extra -> ExtraRow(hit, best = hit.key == bestKey, Modifier) { open(hit, it) }
                is ContactHit -> ContactRow(hit, best = hit.key == bestKey, Modifier) { open(hit, null) }
                is AskContacts -> ActionRow("Allow contacts to find \u201C${hit.label}\u201D", Modifier) { open(hit, null) }
                else -> Unit
            }
        }
        if (results.isEmpty()) item(key = "web", span = { GridItemSpan(maxLineSpan) }) {
            ActionRow("Search the web for \u201C${ui.query}\u201D", Modifier) { web() }
        }
    }
}

/** Pop-in for grid cell [index] as [progress] runs 0 to 1: a 30 ms stagger, capped so far-down cells don't wait. */
private fun Modifier.popIn(index: Int, progress: () -> Float) = graphicsLayer {
    val t = ((progress() - minOf(index, 16) * 0.035f) / 0.45f).coerceIn(0f, 1f)
    val e = 1f - (1f - t) * (1f - t) // ease out
    alpha = t
    scaleX = 0.82f + 0.18f * e
    scaleY = scaleX
    translationY = (1f - e) * 14.dp.toPx()
}

/** Fluid's hold menus (apps in the grid, dock icons): a dark glass card instead of the stock Material dropdown. */
@Composable
fun GlassMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) = DropdownMenu(
    expanded, onDismiss,
    shape = RoundedCornerShape(22.dp),
    containerColor = Color(0xF2202024),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    shadowElevation = 16.dp,
    modifier = Modifier.widthIn(min = 220.dp),
    content = content,
)

/** The app's name, small and dim, at the top of its menu. */
@Composable
fun MenuTitle(text: String) = Text(
    text, style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, fontWeight = FontWeight.Medium),
    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
)

@Composable
fun GlassItem(text: String, danger: Boolean = false, onClick: () -> Unit) = DropdownMenuItem(
    { Text(text, style = TextStyle(color = if (danger) Color(0xFFFF8A80) else Color.White, fontSize = 16.sp)) },
    onClick,
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
)

@Composable
fun GlassDivider() = Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
