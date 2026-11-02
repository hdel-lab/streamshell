package com.streamshell.core.privacy

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FilterUpdateEntryPoint {
    fun filterLists(): FilterListRepository
}

/** Scheduled refresh of the enabled filter lists. Failures keep the last-known-good lists and are retried later. */
class FilterUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = EntryPointAccessors.fromApplication(applicationContext, FilterUpdateEntryPoint::class.java).filterLists()
        repo.initialize()
        val summary = repo.updateAll()
        // Retry (with backoff) only when something failed; never loop on success.
        return if (summary.anyFailed && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

object FilterUpdateScheduler {
    const val UNIQUE_NAME = "filter-list-update"

    /** [intervalHours] = 0 cancels automatic updates. WorkManager's minimum period is 15 minutes; the UI offers hours/days. */
    fun apply(context: Context, intervalHours: Int) {
        val wm = WorkManager.getInstance(context)
        if (intervalHours <= 0) {
            wm.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<FilterUpdateWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
