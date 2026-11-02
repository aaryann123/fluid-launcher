package app.fluid.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.PinItemRequest
import android.os.Bundle

/** Other apps' "Add to home screen" (Chrome PWAs, contacts, Maps directions) and "add widget" buttons land here. */
class PinActivity : Activity() {
    private var request: PinItemRequest? = null
    private var info: AppWidgetProviderInfo? = null
    private var widgetId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val req = getSystemService(LauncherApps::class.java).getPinItemRequest(intent)?.takeIf { it.isValid } ?: return finish()
        request = req
        when (req.requestType) {
            // Only a foreground app can ask, so accepting without a second confirmation is fine.
            PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                val s = req.shortcutInfo
                if (s != null && req.accept()) {
                    val key = Apps.shortcutKey(s)
                    if (Store.favorites.value.none { it.key == key }) {
                        Store.favorites.value += Favorite(key, (s.shortLabel ?: s.longLabel ?: "Shortcut").toString())
                    }
                    Apps.toast("Added to home")
                }
                finish()
            }
            // The widget must be bound first, which needs the user's one-time "allow widgets" OK.
            PinItemRequest.REQUEST_TYPE_APPWIDGET -> {
                val provider = req.getAppWidgetProviderInfo(this) ?: return finish()
                info = provider
                widgetId = FluidApp.widgetHost.allocateAppWidgetId()
                if (AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(widgetId, provider.profile, provider.provider, null)) place()
                else startActivityForResult(bindIntent(widgetId, provider), 0)
            }
            else -> finish()
        }
    }

    @Deprecated("Plain Activity; the bind dialog only reports here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) place() else drop()
    }

    private fun place() {
        val provider = info
        if (provider != null && request?.accept(Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId) }) == true) {
            Store.widgets.value += PlacedWidget(widgetId, defaultHeightDp(provider, this))
            Apps.toast("Widget added")
            finish()
        } else drop()
    }

    private fun drop() {
        FluidApp.widgetHost.deleteAppWidgetId(widgetId)
        finish()
    }
}
