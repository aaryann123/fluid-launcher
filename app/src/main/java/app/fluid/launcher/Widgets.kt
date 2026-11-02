package app.fluid.launcher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.CircleShape
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Outline
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewOutlineProvider
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

const val HOST_ID = 0x464C
private const val REQ_BIND = 101
private const val REQ_CONFIG = 102

/**
 * Detects long-press itself (in dispatchTouchEvent, so it sees every event even if no child takes the
 * down), then cancels the widget's own gesture. Claims the stream so the timer is always cleared on up.
 */
class HostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null

    // Rounded like One UI, with the system's own widget corner radius. Done on the view itself: a Compose clip
    // around an AndroidView doesn't reach the widget's own rendering, so square widgets stayed square.
    init {
        val radius = resources.getDimension(android.R.dimen.system_app_widget_background_radius)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
        clipToOutline = true
    }

    // Android pads older widgets by 8dp inside the host view, which left their square corners just inside the
    // rounded clip. Without it the widget fills the view and the rounding lands on its own corners.
    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        setPadding(0, 0, 0, 0)
    }

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val longPress = Runnable {
        fired = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        cancelChildren()
        onLongPress?.invoke()
    }

    private fun cancelChildren() {
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                downX = ev.x
                downY = ev.y
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(longPress)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> removeCallbacks(longPress)
        }
        if (fired) return true
        super.dispatchTouchEvent(ev)
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPress)
        super.onDetachedFromWindow()
    }

    private var reported = ""
    /**
     * Tell the provider every width this widget is shown at (cover and inner screen) so folding just
     * switches layouts locally instead of asking every provider for new ones.
     */
    fun reportSize(widthsDp: List<Int>, heightDp: Int) {
        val k = "$widthsDp×$heightDp"
        if (k == reported) return
        reported = k
        updateAppWidgetSize(Bundle(), widthsDp.map { SizeF(it.toFloat(), heightDp.toFloat()) })
    }
}

class FluidWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
    override fun onCreateView(context: Context, id: Int, info: AppWidgetProviderInfo?): AppWidgetHostView = HostView(context)
}

fun bindIntent(id: Int, info: AppWidgetProviderInfo) = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)

class Widgets(private val activity: Activity) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    val host get() = FluidApp.widgetHost
    private val views = HashMap<Int, HostView>()
    /** Bumped when day/night flips: host views never re-read their resources, so they are rebuilt. */
    var generation by mutableIntStateOf(0); private set
    private var pending = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    init {
        // Drop ids that were allocated but never placed (cancelled binds, process death mid-configure).
        val placed = Store.widgets.value.map { it.id }.toSet()
        host.appWidgetIds.filter { it !in placed }.forEach(host::deleteAppWidgetId)
    }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun rebuild() {
        views.clear()
        generation++
    }

    // startListening returns every pending update over binder and throws if system_server hiccups.
    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    /** Views are cached so a widget is created once and survives every re-layout. */
    fun view(id: Int): HostView? = views[id] ?: info(id)?.let { info ->
        (host.createView(activity, id, info) as HostView).also { views[id] = it }
    }

    /** Every home-screen widget, grouped by app; labels loaded once here so the picker's search stays cheap. */
    fun providers(): List<Pair<String, List<PickerWidget>>> {
        val pm = activity.packageManager
        val collator = Collator.getInstance()
        return Apps.launcherApps.profiles.flatMap { manager.getInstalledProvidersForProfile(it) }
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            .groupBy { it.activityInfo.applicationInfo.loadLabel(pm).toString() }
            .map { (app, list) -> app to list.map { PickerWidget(it, it.loadLabel(pm)) }.sortedWith(compareBy(collator) { it.label }) }
            .sortedWith(compareBy(collator) { it.first })
    }

    fun add(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pending = id
        pendingInfo = info
        if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) return configure()
        // First widget ever: the system asks "Allow Fluid to create widgets?" (with "always allow").
        activity.startActivityForResult(bindIntent(id, info), REQ_BIND)
    }

    private fun configure() {
        val info = pendingInfo ?: return
        val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (info.configure == null || optional) return place()
        try {
            host.startAppWidgetConfigureActivityForResult(activity, pending, 0, REQ_CONFIG, launchOptions().toBundle())
        } catch (e: Exception) {
            cancel()
            Apps.toast("That widget can't be set up")
        }
    }

    fun canReconfigure(id: Int) = info(id)?.let {
        it.configure != null && it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
    } == true

    fun reconfigure(id: Int) = runCatching {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, 0, launchOptions().toBundle())
    }

    fun onResult(request: Int, result: Int) {
        when (request) {
            REQ_BIND -> if (result == Activity.RESULT_OK) configure() else cancel()
            REQ_CONFIG -> if (result == Activity.RESULT_OK) place() else cancel()
        }
    }

    private fun place() {
        val info = pendingInfo ?: return
        Store.widgets.value += PlacedWidget(pending, defaultHeightDp(info, activity))
        pending = -1
        pendingInfo = null
    }

    private fun cancel() {
        if (pending != -1) host.deleteAppWidgetId(pending)
        pending = -1
        pendingInfo = null
    }

    fun remove(id: Int) {
        host.deleteAppWidgetId(id)
        views.remove(id)
        Store.widgets.value = Store.widgets.value.filterNot { it.id == id }
    }

    fun move(id: Int, by: Int) {
        val list = Store.widgets.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = (from + by).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return
        list.add(to, list.removeAt(from))
        Store.widgets.value = list
    }

    fun resize(id: Int, heightDp: Int) {
        Store.widgets.value = Store.widgets.value.map { if (it.id == id) it.copy(heightDp = heightDp) else it }
    }
}

private fun Context.dp(px: Int) = px / resources.displayMetrics.density

fun defaultHeightDp(info: AppWidgetProviderInfo, ctx: Context): Int =
    max(ctx.dp(info.minHeight), info.targetCellHeight * 96f).roundToInt().coerceIn(72, 480)

private fun minHeightDp(info: AppWidgetProviderInfo?, ctx: Context): Float =
    info?.let { ctx.dp(if (it.minResizeHeight > 0) it.minResizeHeight else it.minHeight) }?.coerceAtLeast(48f) ?: 48f

// ---------- Widgets pane ----------

@Composable
fun WidgetsPane(ui: HomeState, widgets: Widgets, modifier: Modifier) {
    val p = LocalPalette.current
    val placed = Store.widgets.value
    val offscreen by remember { derivedStateOf { ui.page.value < 0.5f } }
    BackHandler(ui.editing != null) { ui.editing = null }

    // One centred column, capped so widgets don't stretch across the whole inner screen.
    Box(
        modifier
            .then(if (offscreen) Modifier.clearAndSetSemantics {} else Modifier)
            .pointerInput(ui.editing) { if (ui.editing != null) detectTapGestures { ui.editing = null } }
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) { Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val glass = LocalGlass.current
        Text("Widgets", style = p.style(34.sp, FontWeight.SemiBold), modifier = Modifier.padding(start = 8.dp, top = 12.dp))
        if (placed.isEmpty()) Text("Weather, calendar, music, notes: anything your apps offer.", style = p.style(16.sp, color = p.dim),
            modifier = Modifier.padding(horizontal = 8.dp))
        // Each widget sits on a glass card (translated with the page, never scaled: see Home's page slide).
        placed.forEach { w ->
            key(w.id) {
                Box(Modifier.clip(RoundedCornerShape(28.dp)).background(glass.fill).border(1.dp, glass.rim, RoundedCornerShape(28.dp)).padding(8.dp)) {
                    WidgetSlot(ui, widgets, w)
                }
            }
        }
        Text("+  Add widget", style = p.style(16.sp, FontWeight.Medium),
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50)).background(glass.fill)
                .border(1.dp, glass.rim, RoundedCornerShape(50)).clickable { ui.picker = true }.padding(horizontal = 22.dp, vertical = 12.dp))
    } }
}

@Composable
private fun WidgetSlot(ui: HomeState, widgets: Widgets, w: PlacedWidget) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val view = remember(w.id, widgets.generation) { widgets.view(w.id) }
    var liveH by remember(w.id, w.heightDp) { mutableFloatStateOf(w.heightDp.toFloat()) }
    var widthDp by remember { mutableIntStateOf(0) }

    if (view == null) { // provider uninstalled or disabled
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Widget unavailable", style = p.style(16.sp, color = p.dim), modifier = Modifier.weight(1f))
            TextButton({ widgets.remove(w.id) }) { Text("Remove") }
        }
        return
    }
    LaunchedEffect(view, widthDp, w.heightDp) {
        if (widthDp <= 0) return@LaunchedEffect
        if (widthDp !in Store.widgetWidths.value) Store.widgetWidths.value = (Store.widgetWidths.value + widthDp).takeLast(4)
        view.reportSize(Store.widgetWidths.value.sorted(), w.heightDp)
    }

    // Same radius HostView rounds the widget to, for the edit outline.
    val shape = RoundedCornerShape(dimensionResource(android.R.dimen.system_app_widget_background_radius))
    Box(Modifier.fillMaxWidth().height(liveH.dp).onSizeChanged { widthDp = with(density) { it.width.toDp().value.roundToInt() } }) {
        key(widgets.generation) {
            AndroidView(
                factory = {
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.onLongPress = { ui.editing = w.id }
                    view
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        when (ui.editing) {
            null -> Unit
            w.id -> {
                val minH = remember(w.id) { minHeightDp(widgets.info(w.id), ctx) }
                Box(Modifier.matchParentSize().border(2.dp, p.accent, shape).pointerInput(Unit) { detectTapGestures { } }) {
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50)),
                    ) {
                        val index = Store.widgets.value.indexOfFirst { it.id == w.id }
                        if (index > 0) TextButton({ widgets.move(w.id, -1) }) { Text("Up") }
                        if (index < Store.widgets.value.lastIndex) TextButton({ widgets.move(w.id, 1) }) { Text("Down") }
                        if (widgets.canReconfigure(w.id)) TextButton({ widgets.reconfigure(w.id) }) { Text("Edit") }
                        TextButton({ ui.editing = null; widgets.remove(w.id) }) { Text("Remove") }
                        TextButton({ ui.editing = null }) { Text("Done") }
                    }
                    Box(
                        Modifier.align(Alignment.BottomCenter).size(96.dp, 32.dp)
                            .draggable(
                                rememberDraggableState { dy -> liveH = (liveH + dy / density.density).coerceIn(minH, 640f) },
                                Orientation.Vertical,
                                onDragStopped = { widgets.resize(w.id, liveH.roundToInt()) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(44.dp, 5.dp).background(p.accent, RoundedCornerShape(50)))
                    }
                }
            }
            // While another widget is being edited, a tap here just ends editing instead of reaching this widget.
            else -> Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { ui.editing = null } })
        }
    }
}

// ---------- Picker ----------

class PickerWidget(val info: AppWidgetProviderInfo, val label: String)

@Composable
fun WidgetPicker(ui: HomeState, widgets: Widgets) {
    val groups by produceState<List<Pair<String, List<PickerWidget>>>?>(null) {
        value = withContext(Dispatchers.Default) { widgets.providers() }
    }
    var query by remember { mutableStateOf("") }
    // An app name match keeps all its widgets; otherwise only the widgets whose own name matches.
    val shown = remember(groups, query) {
        groups?.mapNotNull { (app, list) ->
            if (query.isBlank() || matchScore(app, query) != null) app to list
            else list.filter { matchScore(it.label, query) != null }.takeIf { it.isNotEmpty() }?.let { app to it }
        }
    }
    BackHandler { ui.picker = false }
    // Solid near-black: the live widgets page sits right behind and shows through any tint. Glass cards on top.
    Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0B0D), contentColor = Color.White) { Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().widthIn(max = 640.dp).align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Widgets", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { ui.picker = false },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", style = MaterialTheme.typography.titleMedium) }
            }
            OutlinedTextField(
                query, { query = it },
                singleLine = true,
                placeholder = { Text("Search widgets") },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f), focusedContainerColor = Color.White.copy(alpha = 0.12f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f), focusedBorderColor = Color.White.copy(alpha = 0.3f),
                ),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                when {
                    shown == null -> item { Text("Loading…", Modifier.padding(8.dp)) }
                    shown.isEmpty() -> item { Text("No widgets match \"$query\"", Modifier.padding(8.dp)) }
                }
                shown?.forEach { (app, list) ->
                    item(key = "app:$app") {
                        Text(app.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp), color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.animateItem().padding(start = 12.dp, top = 22.dp, bottom = 6.dp))
                    }
                    items(list, key = { "${it.info.provider.flattenToString()}#${it.info.profile.hashCode()}" }) { w ->
                        PickerItem(w.info, w.label, Modifier.animateItem()) {
                            ui.picker = false
                            widgets.add(w.info)
                        }
                    }
                }
            }
        }
    } }
}

@Composable
private fun PickerItem(info: AppWidgetProviderInfo, label: String, modifier: Modifier, onPick: () -> Unit) {
    val ctx = LocalContext.current
    val description = remember(info) { info.loadDescription(ctx)?.toString() }
    val previewH = remember(info) { defaultHeightDp(info, ctx).coerceIn(80, 200).dp }
    Column(
        modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .clickable(onClick = onPick).padding(14.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(previewH)) {
            WidgetPreview(info, Modifier.fillMaxSize())
            Box(Modifier.matchParentSize().clickable(onClick = onPick)) // the preview is a picture, not a live widget
        }
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
        description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f)) }
    }
}

/** Generated preview (Android 15+), else the preview layout, else the preview image. */
private fun loadPreview(ctx: Context, info: AppWidgetProviderInfo): Any? {
    val home = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
    if (Build.VERSION.SDK_INT >= 35 && info.generatedPreviewCategories and home != 0) {
        // Only readable once the user has allowed Fluid to bind widgets; null before that.
        runCatching { AppWidgetManager.getInstance(ctx).getWidgetPreview(info.provider, info.profile, home) }.getOrNull()?.let { return it }
    }
    if (info.previewLayout != Resources.ID_NULL) return info.previewLayout
    return loadImage(ctx, info)
}

/** The static preview image, else the widget's icon. */
private fun loadImage(ctx: Context, info: AppWidgetProviderInfo): ImageBitmap? = runCatching {
    val d = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0) ?: return null
    val w = d.intrinsicWidth.coerceAtLeast(1)
    val h = d.intrinsicHeight.coerceAtLeast(1)
    val s = minOf(1f, 900f / max(w, h)) // keep the aspect ratio, cap the memory
    d.toBitmap((w * s).roundToInt().coerceAtLeast(1), (h * s).roundToInt().coerceAtLeast(1)).asImageBitmap()
}.getOrNull()

/**
 * Some providers ship preview layouts that only inflate inside their own app (Samsung's Device care uses
 * ConstraintLayout). The host view then shows "Can't load widget"; this one reports it instead.
 */
private class PreviewHostView(context: Context, private val onError: () -> Unit) : AppWidgetHostView(context) {
    override fun getErrorView(): View {
        post(onError)
        return super.getErrorView()
    }
}

@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo, modifier: Modifier) {
    val ctx = LocalContext.current
    var failed by remember(info) { mutableStateOf(false) }
    val preview by produceState<Any?>(null, info, failed) {
        value = withContext(Dispatchers.IO) { if (failed) loadImage(ctx, info) else loadPreview(ctx, info) }
    }
    when (val p = preview) {
        is RemoteViews -> AndroidView({ c ->
            PreviewHostView(c) { failed = true }.apply {
                setAppWidget(-1, info)
                updateAppWidget(p)
            }
        }, modifier)
        // Launcher3's trick: a copy whose initial layout is the preview layout, shown as the "loading" view of an
        // unbound host view. Works for work-profile providers too, unlike RemoteViews(pkg, id).
        is Int -> AndroidView({ c ->
            PreviewHostView(c) { failed = true }.apply {
                setAppWidget(-1, info.clone().apply { initialLayout = p })
                updateAppWidget(null)
            }
        }, modifier)
        is ImageBitmap -> Image(p, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    }
}
