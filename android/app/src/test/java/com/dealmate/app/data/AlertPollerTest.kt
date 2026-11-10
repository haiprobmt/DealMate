package com.dealmate.app.data

import com.dealmate.app.data.remote.dto.DealMateJson
import com.dealmate.app.data.remote.dto.NotificationListDto
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.AppSettings
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.notifications.AlertPoller
import com.dealmate.app.testutil.FakeAlertPresenter
import com.dealmate.app.testutil.FakeNotificationRepository
import com.dealmate.app.testutil.FakeSettingsRepository
import com.dealmate.app.testutil.FakeWishlistRepository
import com.dealmate.app.testutil.Fixtures
import com.dealmate.app.testutil.failure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPollerTest {
    private val settings = FakeSettingsRepository(
        AppSettings(baseUrl = "https://dealmate.test/", token = "t", notificationsEnabled = true),
    )
    private val notifications = FakeNotificationRepository().apply {
        unreadOutcome = Outcome.Success(
            DealMateJson.decodeFromString(NotificationListDto.serializer(), Fixtures.text("notifications_list.json"))
                .items.map { it.toDomain() },
        )
    }
    private val wishlist = FakeWishlistRepository().apply { items.value = Fixtures.wishlist() }
    private val presenter = FakeAlertPresenter()
    private val poller = AlertPoller(settings, notifications, wishlist, presenter)

    @Test
    fun `shows unread notifications with their product and marks them read`() = runTest {
        assertEquals(AlertPoller.Result.DONE, poller.poll())
        assertEquals(listOf(61L, 62L), presenter.shown.map { it.first.id })
        // variant 11 is in the cached wishlist -> product 1; the second has no variant
        assertEquals(listOf(1L, null), presenter.shown.map { it.second })
        assertEquals(listOf(61L, 62L), notifications.read)
    }

    @Test
    fun `does nothing when notifications are disabled, unconfigured or not permitted`() = runTest {
        settings.setNotificationsEnabled(false)
        assertEquals(AlertPoller.Result.SKIPPED, poller.poll())
        settings.setNotificationsEnabled(true)
        settings.setServer("https://dealmate.test/", "")
        assertEquals(AlertPoller.Result.SKIPPED, poller.poll())
        settings.setServer("https://dealmate.test/", "t")
        presenter.allowed = false
        assertEquals(AlertPoller.Result.SKIPPED, poller.poll())
        assertTrue(presenter.shown.isEmpty())
        assertTrue(notifications.read.isEmpty())
    }

    @Test
    fun `connectivity failure asks for a retry, other failures do not`() = runTest {
        notifications.unreadOutcome = failure(AppError.Kind.OFFLINE)
        assertEquals(AlertPoller.Result.RETRY, poller.poll())
        notifications.unreadOutcome = failure(AppError.Kind.UNAUTHORIZED)
        assertEquals(AlertPoller.Result.DONE, poller.poll())
        assertTrue(notifications.read.isEmpty())
    }
}
