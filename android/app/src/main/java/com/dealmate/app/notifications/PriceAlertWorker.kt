package com.dealmate.app.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Hourly poll of `GET /notifications?unread_only=true` (the push-less way to deliver price alerts). */
@HiltWorker
class PriceAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val poller: AlertPoller,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (poller.poll()) {
        AlertPoller.Result.RETRY -> if (runAttemptCount < 3) Result.retry() else Result.success()
        else -> Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "price_alert_poll"
    }
}

@Singleton
class AlertWorkScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    /** Keeps the periodic poll scheduled only while notifications are enabled and a server is set. */
    fun sync(enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(PriceAlertWorker.UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<PriceAlertWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            PriceAlertWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }
}
