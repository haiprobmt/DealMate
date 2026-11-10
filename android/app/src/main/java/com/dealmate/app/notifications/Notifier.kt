package com.dealmate.app.notifications

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
import com.dealmate.app.MainActivity
import com.dealmate.app.R
import com.dealmate.app.domain.model.AppNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlertPresenter {

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    override fun show(notification: AppNotification, productId: Long?) {
        show(
            id = notification.id.toInt(),
            title = notification.title,
            body = notification.body,
            productId = productId,
            variantId = notification.variantId,
            isMock = notification.isMock,
        )
    }

    fun show(id: Int, title: String, body: String, productId: Long?, variantId: Long?, isMock: Boolean) {
        if (!canPost()) return
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (productId != null) {
                putExtra(MainActivity.EXTRA_PRODUCT_ID, productId)
                variantId?.let { putExtra(MainActivity.EXTRA_VARIANT_ID, it) }
            } else {
                putExtra(MainActivity.EXTRA_OPEN_WISHLIST, true)
            }
        }
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Sample data stays recognisable even in the notification shade.
        val shownTitle = if (isMock) context.getString(R.string.notification_demo_prefix, title) else title
        val built = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(shownTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, built)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call: nothing to show.
        }
    }

    companion object {
        const val CHANNEL_ID = "price_alerts"
    }
}
