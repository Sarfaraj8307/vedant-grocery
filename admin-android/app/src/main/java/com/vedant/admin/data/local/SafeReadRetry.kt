package com.vedant.admin.data.local

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vedant.admin.VedantAdminApplication
import com.vedant.admin.core.logging.AppLog
import java.util.concurrent.TimeUnit

private const val TAG = "ReadRetry"

/**
 * Schedules a retry of **safe reads only**.
 *
 * The interface is the enforcement: there is no method that could queue a
 * mutation, so a write can never be deferred and later replayed against a server
 * whose state has moved on. A mutation that cannot reach the server is **disabled
 * in the UI** and left to the operator, never queued.
 */
interface ReadRetryScheduler {

    /** Requests a single retry once a network is available. */
    fun scheduleSafeReadRetry()

    /** Cancels any pending retry — used on sign-out. */
    fun cancel()
}

/**
 * The real [ReadRetryScheduler], backed by WorkManager.
 *
 * The work is **unique** and `KEEP`, so repeated failures collapse into one
 * pending job, and it requires a connected network with exponential backoff, so
 * it does not spin while the device is offline.
 */
class WorkManagerReadRetryScheduler(private val context: Context) : ReadRetryScheduler {

    override fun scheduleSafeReadRetry() {
        val request = OneTimeWorkRequestBuilder<SafeReadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        runCatching {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }.onFailure { AppLog.w(TAG, "could not enqueue the read retry", it) }
    }

    override fun cancel() {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
            .onFailure { AppLog.w(TAG, "could not cancel the read retry", it) }
    }

    private companion object {
        const val WORK_NAME = "safe-read-retry"
        const val BACKOFF_SECONDS = 30L
    }
}

/**
 * Re-runs the app's safe reads and refreshes the cache.
 *
 * It reads through `AppContainer.refreshCachedReads()`, which touches **only**
 * reads. On a network failure it asks WorkManager to retry, up to a bounded
 * number of attempts, so a permanently dead backend does not retry forever.
 */
class SafeReadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? VedantAdminApplication
            ?: return Result.failure()
        return if (app.container.refreshCachedReads()) {
            AppLog.i(TAG, "cached reads refreshed")
            Result.success()
        } else if (runAttemptCount < MAX_ATTEMPTS) {
            AppLog.i(TAG, "cached reads still unavailable; retrying")
            Result.retry()
        } else {
            AppLog.w(TAG, "cached reads gave up after $runAttemptCount attempts")
            Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
