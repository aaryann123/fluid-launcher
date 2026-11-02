package app.fluid.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import kotlin.math.roundToInt

/**
 * Package -> Phosphor glyph (phosphoricons.com, MIT), regular weight. Your own apps go in the gitignored
 * assets/glyph-map.txt (package TAB glyph), read on top of this. After adding one, run Tools/glyphs.py to copy its
 * path into assets/glyphs.txt.
 */
internal val GLYPHS = mapOf(
    "app.fluid.launcher" to "drop", "app.lumen" to "quotes", "com.Slack" to "slack-logo",
    "com.Splitwise.SplitwiseMobile" to "receipt", "com.adobe.scan.android" to "scan", "com.airbnb.android" to "bed",
    "com.amazon.mShop.android.shopping" to "amazon-logo", "com.android.settings" to "gear-six",
    "com.android.vending" to "google-play-logo", "com.anthropic.claude" to "asterisk", "com.dd.doordash" to "moped",
    "com.discord" to "discord-logo", "com.dominospizza" to "pizza", "com.ebay.mobile" to "tag",
    "com.facebook.katana" to "facebook-logo", "com.facebook.orca" to "messenger-logo",
    "com.google.android.apps.maps" to "map-trifold", "com.google.android.apps.messaging" to "chat-circle-dots",
    "com.google.android.apps.photos" to "google-photos-logo", "com.google.android.apps.tachyon" to "video-conference",
    "com.google.android.apps.walletnfcrel" to "wallet", "com.google.android.apps.youtube.music" to "music-notes",
    "com.google.android.gm" to "envelope-simple", "com.google.android.googlequicksearchbox" to "google-logo",
    "com.google.android.youtube" to "youtube-logo", "com.instagram.android" to "instagram-logo",
    "com.linkedin.android" to "linkedin-logo", "com.netflix.mediaclient" to "popcorn",
    "com.paypal.android.p2pmobile" to "paypal-logo", "com.pinterest" to "pinterest-logo",
    "com.reddit.frontpage" to "reddit-logo", "com.samsung.android.app.contacts" to "address-book",
    "com.samsung.android.app.find" to "crosshair-simple", "com.samsung.android.app.notes" to "note-pencil",
    "com.samsung.android.app.reminder" to "list-checks", "com.samsung.android.app.sketchbook" to "pen-nib",
    "com.samsung.android.app.spage" to "newspaper", "com.samsung.android.app.tips" to "lightbulb",
    "com.samsung.android.app.watchmanager" to "watch", "com.samsung.android.bixby.agent" to "sparkle",
    "com.samsung.android.calendar" to "calendar-blank", "com.samsung.android.dialer" to "phone",
    "com.samsung.android.goodlock" to "puzzle-piece", "com.samsung.android.oneconnect" to "house-simple",
    "com.samsung.android.spay" to "contactless-payment", "com.samsung.android.themedesigner" to "palette",
    "com.samsung.android.wonderland.wallpaper" to "mountains", "com.samsung.vvm" to "voicemail",
    "com.sec.android.app.camera" to "camera", "com.sec.android.app.clockpackage" to "alarm",
    "com.sec.android.app.myfiles" to "folder-simple", "com.sec.android.app.popupcalculator" to "calculator",
    "com.sec.android.app.samsungapps" to "shopping-bag", "com.sec.android.app.sbrowser" to "globe",
    "com.sec.android.app.shealth" to "heartbeat", "com.sec.android.app.voicenote" to "microphone",
    "com.sec.android.gallery3d" to "images", "com.snapchat.android" to "snapchat-logo",
    "com.squareup.cash" to "currency-dollar", "com.twitter.android" to "x-logo", "com.ubercab" to "car",
    "com.venmo" to "hand-coins", "com.whatsapp" to "whatsapp-logo", "com.zillow.android.zillowmap" to "house",
    "me.lyft.android" to "car-simple", "notion.id" to "notion-logo",
)

/** Apps installed later that have no themed icon of their own: a word in their name that starts with one of these. */
internal val GLYPH_WORDS = listOf(
    "bank" to "bank", "pay" to "wallet", "wallet" to "wallet", "music" to "music-notes", "radio" to "radio",
    "podcast" to "microphone-stage", "video" to "video-camera", "tv" to "television-simple", "camera" to "camera",
    "photo" to "images", "gallery" to "images", "mail" to "envelope-simple", "chat" to "chat-circle", "messag" to "chat-circle",
    "phone" to "phone", "map" to "map-trifold", "weather" to "cloud-sun", "news" to "newspaper", "shop" to "shopping-bag",
    "store" to "shopping-bag", "food" to "fork-knife", "eat" to "fork-knife", "pizza" to "pizza", "ride" to "car",
    "taxi" to "car", "fit" to "barbell", "gym" to "barbell", "health" to "heartbeat", "game" to "game-controller",
    "book" to "book-open", "read" to "book-open", "note" to "note-pencil", "calendar" to "calendar-blank", "clock" to "alarm",
    "calc" to "calculator", "file" to "folder-simple", "scan" to "scan", "vpn" to "shield", "translat" to "translate",
    "learn" to "graduation-cap", "travel" to "airplane-tilt", "flight" to "airplane-tilt", "home" to "house", "print" to "printer",
)

internal fun guessGlyph(label: String): String? = label.lowercase().split(Regex("[^a-z]+"))
    .firstNotNullOfOrNull { w -> GLYPH_WORDS.firstOrNull { w.startsWith(it.first) }?.second }

/**
 * Halo icons: every app as a white glyph with the lock screen's soft shadow, no tile. The glyph is, in order: its
 * [GLYPHS] entry, the app's own themed-icon glyph (Android 13+), a [guessGlyph] from its name, or its first letter.
 */
object Halo {
    private const val GLYPH = 0.6f // of the icon box; the rest is room for the shadow

    private var glyphData: Map<String, String>? = null
    private var localMap: Map<String, String>? = null
    private val paths = HashMap<String, Path?>()

    @Synchronized
    private fun path(ctx: Context, name: String): Path? = paths.getOrPut(name) {
        val data = glyphData ?: ctx.assets.open("glyphs.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.associate { it.substringBefore('\t') to it.substringAfter('\t') }
        }.also { glyphData = it }
        data[name]?.let { runCatching { PathParser.createPathFromPathData(it) }.getOrNull() }
    }

    /** assets/glyph-map.txt, if this build has one. */
    @Synchronized
    private fun localGlyph(ctx: Context, pkg: String): String? = (localMap ?: runCatching {
        ctx.assets.open("glyph-map.txt").bufferedReader().useLines { lines ->
            lines.filter { '\t' in it && !it.startsWith("#") }.associate { it.substringBefore('\t') to it.substringAfter('\t').trim() }
        }
    }.getOrDefault(emptyMap()).also { localMap = it })[pkg]

    fun icon(ctx: Context, pkg: String, label: String, icon: Drawable?, px: Int): Bitmap {
        val glyph = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(glyph)
        val named = (GLYPHS[pkg] ?: localGlyph(ctx, pkg))?.let { path(ctx, it) }
        val mono = if (named == null && Build.VERSION.SDK_INT >= 33) (icon as? AdaptiveIconDrawable)?.monochrome else null
        val guessed = if (named == null && mono == null) guessGlyph(label)?.let { path(ctx, it) } else null
        when {
            named != null -> drawPath(canvas, named, px)
            mono != null -> drawMono(canvas, mono, px)
            guessed != null -> drawPath(canvas, guessed, px)
            else -> drawLetter(ctx, canvas, label, px)
        }
        return withShadow(glyph, px)
    }

    private fun drawPath(canvas: Canvas, path: Path, px: Int) {
        val g = px * GLYPH
        val m = Matrix().apply { setScale(g / 256f, g / 256f); postTranslate((px - g) / 2, (px - g) / 2) }
        canvas.drawPath(Path(path).apply { transform(m) }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    // A themed-icon layer is drawn like its adaptive icon would be, a little smaller: its logo then matches a glyph's size.
    private fun drawMono(canvas: Canvas, mono: Drawable, px: Int) {
        val size = (px * 1.25f).roundToInt()
        val at = (px - size) / 2
        mono.mutate().apply {
            colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            setBounds(at, at, at + size, at + size)
            draw(canvas)
        }
    }

    private fun drawLetter(ctx: Context, canvas: Canvas, label: String, px: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = ResourcesCompat.getFont(ctx, R.font.montserrat_heavy)
            textSize = px * 0.42f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label.trim().take(1).uppercase(), px / 2f, px / 2f - (paint.ascent() + paint.descent()) / 2, paint)
    }

    /** The lock screen text's shadow: a tight dark edge plus a wide soft one, so white reads on any wallpaper. */
    private fun withShadow(glyph: Bitmap, px: Int): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val alpha = glyph.extractAlpha()
        fun shade(color: Int, radius: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(out).apply {
            drawBitmap(alpha, 0f, px * 0.02f, shade(0xE6000000.toInt(), px * 0.06f))
            drawBitmap(alpha, 0f, 0f, shade(0x8C000000.toInt(), px * 0.15f))
            drawBitmap(glyph, 0f, 0f, null)
        }
        alpha.recycle()
        glyph.recycle()
        return out
    }
}
