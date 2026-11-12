package com.uplants.app.data.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uplants.app.UPlantsApplication
import java.util.concurrent.TimeUnit

/** Background job: checks for new activity and shows notifications. Runs even when the app is closed. */
class ActivityCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as UPlantsApplication).container
        val found = container.activityChecker.check()
        container.notifier.show(found)
        return Result.success()
    }
}

/** Schedules [ActivityCheckWorker]: every 15 minutes (Android's minimum), plus right away on demand. */
class NotificationScheduler(context: Context) {
    private val work = WorkManager.getInstance(context.applicationContext)
    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<ActivityCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(needsNetwork)
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** E.g. when the app comes to the foreground. */
    fun checkNow() {
        val request = OneTimeWorkRequestBuilder<ActivityCheckWorker>().setConstraints(needsNetwork).build()
        work.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** On log out: no more checks until someone logs in again. */
    fun cancel() {
        work.cancelUniqueWork(PERIODIC)
        work.cancelUniqueWork(NOW)
    }

    private companion object {
        const val PERIODIC = "activity-check"
        const val NOW = "activity-check-now"
    }
}
