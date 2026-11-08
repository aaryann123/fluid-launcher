package app.fluid.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** What's playing anywhere on the phone (YT Music, Spotify, ...), read off its MediaSession. */
data class Track(val title: String, val artist: String, val art: ImageBitmap?, val playing: Boolean)

private const val LUMEN_MEDIA = "app.lumen.MEDIA"

/** Prefer the session that's actually playing, else the first that has a track loaded. */
internal fun <T> choose(list: List<T>, playing: (T) -> Boolean, loaded: (T) -> Boolean): T? =
    list.firstOrNull(playing) ?: list.firstOrNull(loaded)

/**
 * The phone's active media sessions, through the notification access Fluid already holds for its dots.
 * Until that access is granted every call here throws quietly and [track] stays null, which hides the UI;
 * DotService.onListenerConnected calls [init] again the moment access arrives.
 */
object Media {
    var track by mutableStateOf<Track?>(null); private set
    private var manager: MediaSessionManager? = null
    private var listener: ComponentName? = null
    private var controller: MediaController? = null
    private var registered = false
    private var app: Context? = null
    private var pushed: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = read()
        override fun onPlaybackStateChanged(state: PlaybackState?) = read()
        override fun onSessionDestroyed() = rescan()
    }

    fun init(ctx: Context) {
        if (manager == null) {
            app = ctx.applicationContext
            manager = ctx.getSystemService(MediaSessionManager::class.java)
            listener = ComponentName(ctx, DotService::class.java)
        }
        if (!registered) registered = runCatching {
            manager!!.addOnActiveSessionsChangedListener({ pick(it) }, listener, handler)
        }.isSuccess
        rescan()
    }

    private fun rescan() {
        runCatching { pick(manager?.getActiveSessions(listener)) }
    }

    private fun pick(sessions: List<MediaController>?) {
        val next = choose(sessions.orEmpty(),
            { it.playbackState?.state == PlaybackState.STATE_PLAYING },
            { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null })
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = next
            next?.registerCallback(callback, handler)
        }
        read()
    }

    private fun read() {
        val m = controller?.metadata
        val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
        track = if (m == null || title.isNullOrBlank()) null else Track(
            title = title,
            artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            art = (m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.asImageBitmap(),
            playing = controller?.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
        push()
    }

    /** Hands the track to Lumen's lock screen widget; only when the song changes, never on play/pause. */
    private fun push() {
        val ctx = app ?: return
        val t = track
        val key = t?.let { "${it.title}|${it.artist}" }.orEmpty()
        if (key == pushed) return
        pushed = key
        val i = Intent(LUMEN_MEDIA).setClassName("app.lumen", "app.lumen.MediaWidget")
            .putExtra("title", t?.title.orEmpty())
            .putExtra("artist", t?.artist.orEmpty())
            .putExtra("pkg", controller?.packageName.orEmpty())
        t?.art?.let { art ->
            val bmp = art.asAndroidBitmap()
            val k = 192f / maxOf(bmp.width, bmp.height, 1)
            val small = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.PNG, 90, out)
            i.putExtra("art", out.toByteArray())
        }
        runCatching { ctx.sendBroadcast(i) }
    }

    fun playPause() = controller?.run {
        if (playbackState?.state == PlaybackState.STATE_PLAYING) transportControls.pause() else transportControls.play()
    } ?: Unit

    fun next() = controller?.transportControls?.skipToNext() ?: Unit
    fun prev() = controller?.transportControls?.skipToPrevious() ?: Unit

    /**
     * The player app's front door. Not sessionActivity: sending another app's PendingIntent from here
     * is silently dropped by background-launch rules on new Android, which looked like a dead tap.
     */
    fun open(view: View) {
        val pkg = controller?.packageName ?: return
        view.context.packageManager.getLaunchIntentForPackage(pkg)?.let { start(view, it) }
    }
}

/**
 * Now playing, under the clock in the same Desaturate language: greyscale art, the title as a heavy
 * Montserrat word, the artist in spaced small caps, then prev/play/next as small glass circles.
 * The inner screen gets the big version, the cover screen a mini one.
 */
@Composable
fun NowPlaying(align: Alignment.Horizontal) {
    val t = Media.track ?: return
    if (!Store.showMedia.value) return
    val p = LocalPalette.current
    val glass = LocalGlass.current
    val view = LocalView.current
    val scale = Store.textScale.value
    val inner = LocalConfiguration.current.screenWidthDp >= 600

    val artDp = if (inner) 96.dp else 52.dp
    val titleStyle = p.style(if (inner) 22.sp * scale else 15.sp * scale, FontWeight.ExtraBold).copy(fontFamily = Montserrat)
    val artistStyle = p.style(11.sp * scale, FontWeight.Medium, p.dim).copy(fontFamily = Montserrat, letterSpacing = 0.2.em)

    Column(horizontalAlignment = align, modifier = Modifier.padding(top = if (inner) 32.dp else 20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(if (inner) 24.dp else 16.dp))
                .clickable { Media.open(view) }
                .padding(if (inner) 10.dp else 6.dp)
                .widthIn(max = if (inner) 420.dp else 280.dp),
        ) {
            if (t.art != null) Image(
                t.art, contentDescription = null, contentScale = ContentScale.Crop, // full colour, per the user
                modifier = Modifier.size(artDp).clip(RoundedCornerShape(if (inner) 20.dp else 12.dp)),
            )
            Column(Modifier.padding(start = if (t.art != null) 14.dp else 0.dp)) {
                Text(t.title, style = titleStyle, maxLines = if (inner) 2 else 1, overflow = TextOverflow.Ellipsis)
                if (t.artist.isNotEmpty()) Text(t.artist.uppercase(), style = artistStyle,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Row(Modifier.padding(top = if (inner) 10.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val button = if (inner) 48.dp else 40.dp
            @Composable fun Transport(icon: Int, label: String, onClick: () -> Unit) = Box(
                Modifier.padding(end = 10.dp).size(button).clip(CircleShape)
                    .background(glass.fill).clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = label, tint = p.text,
                    modifier = Modifier.size(if (inner) 22.dp else 18.dp))
            }
            Transport(R.drawable.ic_prev, "Previous") { Media.prev() }
            Transport(if (t.playing) R.drawable.ic_pause else R.drawable.ic_play, if (t.playing) "Pause" else "Play") { Media.playPause() }
            Transport(R.drawable.ic_next, "Next") { Media.next() }
        }
    }
}
