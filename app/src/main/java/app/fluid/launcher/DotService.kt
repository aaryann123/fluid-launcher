package app.fluid.launcher

import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which apps have unread notifications, for the small dots after their names. */
object Dots {
    var active by mutableStateOf(emptySet<String>()); internal set

    fun key(pkg: String, user: UserHandle) = "$pkg|${user.hashCode()}" // UserHandle's hash is its user id
    fun has(app: AppEntry?) = app != null && key(app.component.packageName, app.user) in active
}

/**
 * Notification access, used only to know which apps have something unread. Ongoing ones (music, navigation,
 * downloads) don't count, and neither do apps whose badges you've turned off in Samsung settings.
 */
class DotService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onListenerDisconnected() { Dots.active = emptySet() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()

    private fun refresh() {
        val ranking = runCatching { currentRanking }.getOrNull()
        val r = Ranking()
        Dots.active = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { !it.isOngoing && (ranking == null || !ranking.getRanking(it.key, r) || r.canShowBadge()) }
            .mapTo(HashSet()) { Dots.key(it.packageName, it.user) }
    }
}
