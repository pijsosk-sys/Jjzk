package com.redcore.booster.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.redcore.booster.app
import com.redcore.booster.data.NotifMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

data class BarrageItem(val app: String, val title: String, val text: String)

/** In-memory hand-off from the notification listener to the overlay. Nothing is persisted. */
object BarrageBus {
    private val _items = MutableSharedFlow<BarrageItem>(extraBufferCapacity = 16)
    val items: SharedFlow<BarrageItem> = _items
    @Volatile var mode: NotifMode = NotifMode.BARRAGE
    fun post(i: BarrageItem) { _items.tryEmit(i) }
}

/**
 * While a game session is running with notifications set to "Barrage", messages that Do Not Disturb
 * keeps silent are shown as a small scrolling line over the game (like a gaming phone's barrage mode).
 */
class GameNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (app.boost.session.value == null || BarrageBus.mode != NotifMode.BARRAGE) return
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val n = sbn.notification
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (n.category == Notification.CATEGORY_SERVICE || n.category == Notification.CATEGORY_PROGRESS || n.category == Notification.CATEGORY_TRANSPORT) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        BarrageBus.post(BarrageItem(app.games.label(sbn.packageName), title.take(60), text.replace('\n', ' ').take(140)))
    }
}
