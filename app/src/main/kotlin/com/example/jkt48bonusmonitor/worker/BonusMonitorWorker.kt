package com.example.jkt48bonusmonitor.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.jkt48bonusmonitor.data.api.BonusApiClient
import com.example.jkt48bonusmonitor.data.datastore.AppPreferences
import com.example.jkt48bonusmonitor.data.history.HistoryRepository
import com.example.jkt48bonusmonitor.data.repository.BonusMonitorRepository
import com.example.jkt48bonusmonitor.notification.NotificationHelper
import java.util.concurrent.TimeUnit

/** Unique WorkManager work name — prevents duplicate periodic workers. */
const val WORK_NAME = "jkt48_bonus_monitor_periodic"

/**
 * Periodic background worker that checks the JKT48 bonus quota.
 *
 * WorkManager does NOT guarantee exact 15-minute intervals — Android may delay
 * execution due to Doze mode, battery optimization, and system scheduling.
 * This is expected and normal WorkManager behaviour.
 */
class BonusMonitorWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = AppPreferences(context)
            val apiClient = BonusApiClient()
            val historyRepo = HistoryRepository(context)
            val notifHelper = NotificationHelper(context)
            notifHelper.createNotificationChannel()

            val repository = BonusMonitorRepository(prefs, apiClient, historyRepo, notifHelper)
            repository.performCheck()

            Result.success()
        } catch (e: Exception) {
            // Retry on unexpected failures (exponential backoff is configured at scheduling time)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        /**
         * Schedules the periodic monitoring worker. If already scheduled, the existing work
         * is kept (KEEP policy) to prevent resetting the interval timer on every app start.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<BonusMonitorWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Cancels the periodic monitoring worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
