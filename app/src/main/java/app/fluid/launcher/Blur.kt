package app.fluid.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View

/**
 * Frosted glass: blurs what's behind Fluid's window (the wallpaper) over the whole screen.
 *
 * Samsung switches Android's public window blur off (`isCrossWindowBlurEnabled` is false on the Fold) and does
 * its own through `View.semSetBlurInfo`, which One UI uses for its frosted surfaces. Reached by reflection since
 * it isn't in the SDK; other phones get the public API instead, and neither means no blur (the tint stays).
 */
object Blur {
    private const val BLUR_MODE_WINDOW = 0 // SemBlurInfo.BLUR_MODE_WINDOW

    @SuppressLint("PrivateApi")
    private val sem = runCatching {
        val info = Class.forName("android.view.SemBlurInfo")
        val builder = Class.forName("android.view.SemBlurInfo\$Builder")
        object {
            val set = View::class.java.getMethod("semSetBlurInfo", info)
            val new = builder.getConstructor(Int::class.javaPrimitiveType)
            val radius = builder.getMethod("setRadius", Int::class.javaPrimitiveType)
            val color = builder.getMethod("setBackgroundColor", Int::class.javaPrimitiveType)
            val build = builder.getMethod("build")
        }
    }.getOrNull()

    fun supported(activity: Activity) = sem != null || activity.windowManager.isCrossWindowBlurEnabled

    /** [radius] 0 removes the blur. [tint] is laid over the blurred wallpaper (Samsung only; elsewhere Compose tints). */
    fun set(activity: Activity, view: View, radius: Int, tint: Int) {
        val s = sem
        if (s == null) {
            activity.window.setBackgroundBlurRadius(radius)
            return
        }
        runCatching {
            if (radius <= 0) s.set.invoke(view, null)
            else {
                val b = s.new.newInstance(BLUR_MODE_WINDOW)
                s.radius.invoke(b, radius)
                s.color.invoke(b, tint)
                s.set.invoke(view, s.build.invoke(b))
            }
        }
    }
}
