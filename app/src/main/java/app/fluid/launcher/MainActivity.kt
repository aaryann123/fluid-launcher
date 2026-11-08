package app.fluid.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    lateinit var ui: HomeState
    lateinit var widgets: Widgets
    private val askHome = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    private var uiMode = 0
    private var restarted = false
    private var startedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        // Before the first frame, so a non-wallpaper theme never flashes the wallpaper.
        if (Store.theme.value != "wallpaper") window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        super.onCreate(savedInstanceState)
        // Back on the bare home must never reach the default handler: it finish()es a HOME task root (only
        // LAUNCHER roots get moved to the back) and the system rebuilds the whole home. Registered first, so
        // every other back handler (drawer, picker, widget edit, widgets page) outranks it.
        if (Build.VERSION.SDK_INT >= 34) onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, HomeBack())
        else onBackPressedDispatcher.addCallback(this) {}
        uiMode = resources.configuration.uiMode
        // AndroidUiDispatcher.Main carries the Choreographer frame clock that Compose's animate() needs.
        ui = HomeState(CoroutineScope(lifecycleScope.coroutineContext + AndroidUiDispatcher.Main))
        widgets = Widgets(this)
        setContent { Home(ui, widgets) }
        if (!Store.askedDefault.value) {
            Store.askedDefault.value = true
            val roles = getSystemService(RoleManager::class.java)
            if (!roles.isRoleHeld(RoleManager.ROLE_HOME)) askHome.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }

    override fun onStart() {
        super.onStart()
        startedAt = SystemClock.uptimeMillis()
        widgets.startListening()
        Media.init(this) // re-read media sessions: the changed-listener has gone quiet before (new app's session missed)
    }

    override fun onRestart() {
        super.onRestart()
        restarted = true
    }

    override fun onResume() {
        super.onResume()
        // A predictive back-to-home peek starts us long before we resume, with the home already on screen;
        // only play the scale-in when start and resume arrive together (home button/gesture).
        if (restarted && SystemClock.uptimeMillis() - startedAt < 200) ui.playReturn()
        restarted = false
    }

    // An app now covers the home: reset everything while nobody can see it.
    override fun onStop() {
        super.onStop()
        widgets.stopListening()
        ui.reset()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Widget views never re-read resources, so a day/night flip would leave them in the old colours.
        if (newConfig.uiMode != uiMode) widgets.rebuild()
        uiMode = newConfig.uiMode
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already looking at the home (Launcher3's test): close whatever is open, animated.
        val alreadyHome = hasWindowFocus() && intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0
        if (alreadyHome) ui.home()
    }

    /**
     * Samsung's back gesture owns the screen edges, so a swipe that starts right at an edge arrives as "back".
     * From the left edge it's the same motion as swipe-right-for-widgets, so it drives that page; from the
     * right edge it's the swipe-left pull, with the same two stops. 3-button back does nothing.
     */
    @RequiresApi(34)
    private inner class HomeBack : OnBackAnimationCallback {
        private var mode = 0 // 0 nothing, 1 widgets page, 2 swipe-left pull
        private var stage = 0

        // Measured from the screen edge the swipe began at, using the finger's real position: BackEvent.progress
        // grows far slower than the finger, and the first event only arrives after the finger has already moved.
        override fun onBackStarted(e: BackEvent) {
            stage = 0
            mode = when {
                ui.page.value >= 0.5f -> 0
                e.swipeEdge == BackEvent.EDGE_LEFT -> 1
                e.swipeEdge == BackEvent.EDGE_RIGHT -> 2
                else -> 0
            }
        }

        override fun onBackProgressed(e: BackEvent) {
            when (mode) {
                1 -> ui.page.snap((e.touchX / window.decorView.width).coerceIn(0f, 1f))
                2 -> {
                    ui.pull.snap((e.touchX - window.decorView.width).coerceAtMost(0f))
                    val now = pullStage(ui.pull.value, resources.displayMetrics.density)
                    if (now > stage) window.decorView.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
                    stage = now
                }
            }
        }

        override fun onBackInvoked() {
            when (mode) {
                1 -> ui.page.animateTo(1f)
                2 -> {
                    pullTarget(stage)?.let { Apps.launch(window.decorView, it, null) }
                    ui.pull.animateTo(0f, spec = spring(0.75f, 500f))
                }
            }
            mode = 0
        }

        override fun onBackCancelled() {
            when (mode) {
                1 -> ui.page.animateTo(0f)
                2 -> ui.pull.animateTo(0f, spec = spring(0.75f, 500f))
            }
            mode = 0
        }
    }

    @Deprecated("AppWidgetHost's configure launcher only reports here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgets.onResult(requestCode, resultCode)
    }
}

/** A float the finger drives directly and springs settle; read it only inside graphicsLayer {} to stay draw-only. */
@Stable
class Motion(private val scope: CoroutineScope, initial: Float) {
    var value by mutableFloatStateOf(initial); private set
    var target = initial; private set
    private var job: Job? = null

    fun snap(v: Float) {
        job?.cancel()
        value = v
        target = v
    }

    fun animateTo(to: Float, velocity: Float = 0f, spec: AnimationSpec<Float> = spring(1f, 600f), done: () -> Unit = {}) {
        job?.cancel()
        target = to
        job = scope.launch {
            animate(value, to, velocity, spec) { v, _ -> value = v }
            done()
        }
    }
}

sealed interface Menu {
    data class Home(val x: Float, val y: Float) : Menu
    data class Favorite(val fav: app.fluid.launcher.Favorite) : Menu
    data class App(val app: AppEntry) : Menu
}

@Stable
class HomeState(val scope: CoroutineScope) {
    val drawer = Motion(scope, 0f) // 0 closed .. 1 open
    val page = Motion(scope, 0f) // cover screen: 0 home .. 1 widgets
    val pull = Motion(scope, 0f) // swipe-left rubber band, raw finger px (<= 0)
    val enter = Motion(scope, 1f) // return-to-home scale-in
    var drawerOpen by mutableStateOf(false); private set
    /** True while the wallpaper is really being blurred: the drawer and widgets page then use a lighter tint. */
    var frosted by mutableStateOf(false)
    var wantKeyboard by mutableStateOf(false)
    var query by mutableStateOf("")
    var picker by mutableStateOf(false)
    var editing by mutableStateOf<Int?>(null)
    var menu by mutableStateOf<Menu?>(null)
    var rename by mutableStateOf<Favorite?>(null)
    /** The home dock's glass, in root px: a vertical swipe that starts on it scrubs the dock instead of opening the drawer. */
    var dock: androidx.compose.ui.geometry.Rect? = null
    /** Split screen in progress: the first app is chosen, the drawer is waiting for the second. */
    var pairFirst by mutableStateOf<AppEntry?>(null)
    var pairSave by mutableStateOf(false)

    fun openDrawer(velocity: Float = 0f) {
        drawerOpen = true
        // ponytail: no keyboard on open: it would squash the apps pill. Tapping Search brings it up.
        wantKeyboard = false
        drawer.animateTo(1f, velocity)
    }

    fun closeDrawer(velocity: Float = 0f) {
        drawerOpen = false
        wantKeyboard = false
        pairFirst = null
        drawer.animateTo(0f, velocity) { query = "" }
    }

    fun home() {
        when {
            menu != null || rename != null -> { menu = null; rename = null }
            editing != null -> editing = null
            picker -> picker = false
            drawerOpen -> closeDrawer()
            page.target != 0f -> page.animateTo(0f)
        }
    }

    fun reset() {
        drawerOpen = false
        wantKeyboard = false
        pairFirst = null
        drawer.snap(0f)
        page.snap(0f)
        pull.snap(0f)
        query = ""
        picker = false
        editing = null
        menu = null
        rename = null
    }

    fun playReturn() {
        enter.snap(0f)
        enter.animateTo(1f, spec = spring(0.8f, 380f))
    }
}
