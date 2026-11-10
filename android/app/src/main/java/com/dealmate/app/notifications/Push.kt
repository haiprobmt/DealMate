package com.dealmate.app.notifications

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.dealmate.app.BuildConfig
import com.dealmate.app.domain.repository.NotificationRepository
import com.dealmate.app.domain.repository.SettingsRepository
import com.dealmate.app.domain.repository.WishlistRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Firebase settings come from optional Gradle properties; push exists only when all four are set. */
object PushConfig {
    val isConfigured: Boolean
        get() = listOf(
            BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_SENDER_ID,
        ).all { it.isNotBlank() }
}

/**
 * Manual Firebase initialisation (no google-services plugin, auto-init provider removed in the
 * manifest). Without configuration nothing Firebase-related is ever touched and the messaging
 * service component stays disabled.
 */
@Singleton
class PushManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifications: NotificationRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialised = false

    fun initIfConfigured(): Boolean {
        if (!PushConfig.isConfigured) return false
        if (initialised) return true
        return runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                        .build(),
                )
            }
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, DealMateMessagingService::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            initialised = true
            true
        }.onFailure { Log.w(TAG, "Firebase init failed; push disabled", it) }.getOrDefault(false)
    }

    /** Sends the current FCM token to the backend (`POST /devices`). No-op without Firebase config. */
    fun registerDevice() {
        if (!initIfConfigured()) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) scope.launch { notifications.registerDevice(token) }
            }
        }.onFailure { Log.w(TAG, "FCM token unavailable", it) }
    }

    private companion object {
        const val TAG = "DealMatePush"
    }
}

/**
 * Receives FCM data messages. Expected data keys (all strings): `title`, `body`, optional
 * `variant_id`, `product_id`, `notification_id`, `is_mock`. Disabled in the manifest by default and
 * enabled by [PushManager] only when Firebase is configured.
 */
@AndroidEntryPoint
class DealMateMessagingService : FirebaseMessagingService() {
    @Inject lateinit var notifier: PriceAlertNotifier
    @Inject lateinit var notifications: NotificationRepository
    @Inject lateinit var wishlist: WishlistRepository
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            val current = settings.current()
            if (current.isConfigured && current.notificationsEnabled) notifications.registerDevice(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body.orEmpty()
        val variantId = data["variant_id"]?.toLongOrNull()
        val notificationId = data["notification_id"]?.toLongOrNull()
        scope.launch {
            if (!settings.current().notificationsEnabled) return@launch
            val productId = data["product_id"]?.toLongOrNull()
                ?: variantId?.let { wishlist.productIdForVariant(it) }
            notifier.show(
                id = (notificationId ?: System.currentTimeMillis()).toInt(),
                title = title,
                body = body,
                productId = productId,
                variantId = variantId,
                isMock = data["is_mock"] == "true",
            )
            // Avoid the hourly poll showing the same alert a second time.
            if (notificationId != null && notifier.canPost()) notifications.markRead(notificationId)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
