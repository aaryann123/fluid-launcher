package app.fluid.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

class FluidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Apps.init(this)
        widgetHost = FluidWidgetHost(this)
        // Lumen's quote widget turns over each time the screen goes off, like a rotating lock screen wallpaper.
        // Screen-off only reaches receivers registered at runtime, and the launcher is the one app always running.
        // Its clock ticks on a non-wakeup alarm that Samsung holds back while Lumen is frozen (seen minutes stale),
        // so screen-on redraws it the moment the lock screen shows.
        // ponytail: hard-wired to Lumen; make it a list if another widget wants the same nudge
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val (action, cls) = if (i.action == Intent.ACTION_SCREEN_ON) "TICK" to "ClockWidget" else "NEXT" to "QuoteWidget"
                runCatching { sendBroadcast(Intent("app.lumen.$action").setClassName("app.lumen", "app.lumen.$cls")) }
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    companion object {
        /** One per process: a second host with the same id that stops listening would cut off the home's updates. */
        lateinit var widgetHost: FluidWidgetHost; private set
    }
}
