package com.uplants.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.uplants.app.MainActivity
import com.uplants.app.R

/** Shows [AppNotification]s as system notifications; tapping one opens the app on the right screen. */
class Notifier(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(notifications: List<AppNotification>) {
        if (notifications.isEmpty() || !canNotify()) return
        ensureChannel()
        val manager = NotificationManagerCompat.from(context)
        notifications.forEach { n ->
            val id = n.key.hashCode()
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(n.title)
                .setContentText(n.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
                .setContentIntent(pendingIntentFor(n.link, id))
                .setAutoCancel(true)
                .build()
            try {
                manager.notify(id, notification)
            } catch (e: SecurityException) {
                // Permission revoked between the check and now: nothing to show.
            }
        }
    }

    private fun pendingIntentFor(link: DeepLink, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .also { DeepLinks.writeTo(it, link) }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_ID = "activity"
    }
}

/** Puts a [DeepLink] into an Intent (notification) and reads it back (MainActivity). */
object DeepLinks {
    private const val TYPE = "uplants.link.type"
    private const val ID = "uplants.link.id"
    private const val NAME = "uplants.link.name"

    fun writeTo(intent: Intent, link: DeepLink) {
        when (link) {
            is DeepLink.Post -> intent.putExtra(TYPE, "post").putExtra(ID, link.postId)
            is DeepLink.Chat -> intent.putExtra(TYPE, "chat").putExtra(ID, link.userId).putExtra(NAME, link.name)
            is DeepLink.Profile -> intent.putExtra(TYPE, "profile").putExtra(ID, link.userId)
        }
    }

    fun readFrom(intent: Intent?): DeepLink? {
        val id = intent?.getStringExtra(ID) ?: return null
        return when (intent.getStringExtra(TYPE)) {
            "post" -> DeepLink.Post(id)
            "chat" -> DeepLink.Chat(id, intent.getStringExtra(NAME).orEmpty())
            "profile" -> DeepLink.Profile(id)
            else -> null
        }
    }
}
