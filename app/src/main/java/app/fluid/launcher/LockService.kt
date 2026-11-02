package app.fluid.launcher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** Only exists for two global actions: lock screen (keeps biometrics, unlike device admin) and open the shade. */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile var instance: LockService? = null; private set

        fun lock() = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true

        @SuppressLint("WrongConstant", "PrivateApi")
        fun openShade(context: Context) {
            if (instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true) return
            // Same hidden call Olauncher uses at targetSdk 36; needs EXPAND_STATUS_BAR.
            runCatching {
                Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel")
                    .invoke(context.getSystemService("statusbar"))
            }
        }
    }
}
