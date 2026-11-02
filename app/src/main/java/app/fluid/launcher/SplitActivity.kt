package app.fluid.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Opens two apps side by side. Android only splits the screen when a full-screen app asks to open something
 * "adjacent" to itself (Chrome's "open in new window"), which a home screen can't do. So this blank full-screen
 * stand-in asks for the second app beside itself, then opens the first app in its own half and steps away.
 */
class SplitActivity : Activity() {
    private var first: Intent? = null
    private var second: Intent? = null
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        first = intent.getStringExtra(FIRST)?.let(Apps::mainIntent)
        second = intent.getStringExtra(SECOND)?.let(Apps::mainIntent)
        if (first == null || second == null) finish()
    }

    // Only once the stand-in is actually on screen can it be one half of a split: asked earlier, Samsung finds
    // nothing to pair with ("malformed enter transition") and drops the split straight away.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val (a, b) = (first ?: return) to (second ?: return)
        if (!hasFocus || started) return
        started = true
        val view = window.decorView
        view.postDelayed({
            runCatching { startActivity(b.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)) }
            // Once the split exists, the first app takes this stand-in's half.
            view.postDelayed({
                runCatching { startActivity(a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                view.postDelayed({ finish() }, 300)
            }, 600)
        }, 120)
    }

    companion object {
        const val FIRST = "first"
        const val SECOND = "second"
    }
}
