package com.dealmate.app.notifications

import com.dealmate.app.domain.model.AppNotification
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.repository.NotificationRepository
import com.dealmate.app.domain.repository.SettingsRepository
import com.dealmate.app.domain.repository.WishlistRepository
import javax.inject.Inject

/** Shows a server notification on the device. */
interface AlertPresenter {
    /** false when the user has not granted the notification permission / disabled the channel. */
    fun canPost(): Boolean
    fun show(notification: AppNotification, productId: Long?)
}

/**
 * One polling round: fetch unread server notifications, show them locally, then mark as read the
 * ones that were actually shown. Used by the periodic worker (works without FCM).
 */
class AlertPoller @Inject constructor(
    private val settings: SettingsRepository,
    private val notifications: NotificationRepository,
    private val wishlist: WishlistRepository,
    private val presenter: AlertPresenter,
) {
    enum class Result { SKIPPED, DONE, RETRY }

    suspend fun poll(): Result {
        val current = settings.current()
        if (!current.notificationsEnabled || !current.isConfigured || !presenter.canPost()) return Result.SKIPPED
        val unread = when (val outcome = notifications.unread()) {
            is Outcome.Success -> outcome.data
            is Outcome.Failure -> return if (outcome.error.isConnectivity) Result.RETRY else Result.DONE
        }
        for (notification in unread) {
            val productId = notification.variantId?.let { wishlist.productIdForVariant(it) }
            presenter.show(notification, productId)
            notifications.markRead(notification.id)
        }
        return Result.DONE
    }
}
