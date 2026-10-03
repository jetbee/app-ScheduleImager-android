package net.ustyle.app.scheduleimager.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import java.util.concurrent.TimeUnit
import net.ustyle.app.scheduleimager.Config
import net.ustyle.app.scheduleimager.data.ScheduleRefresher
import net.ustyle.app.scheduleimager.widget.ScheduleWidget

/**
 * Refreshes the on-disk schedule cache and pushes the result to the widget.
 * Runs on a [Config.REFRESH_INTERVAL_MINUTES] schedule in the background, and
 * on demand right after sign-in / the app's manual sync button — the same
 * two triggers the iOS widget's timeline provider and "sync now" button use.
 */
class ScheduleRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ScheduleRefresher.refresh(applicationContext)
        ScheduleWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "schedule_refresh_periodic"
        private const val ONE_TIME_WORK_NAME = "schedule_refresh_now"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleRefreshWorker>(
                Config.REFRESH_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScheduleRefreshWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
