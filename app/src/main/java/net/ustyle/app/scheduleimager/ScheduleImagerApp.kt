package net.ustyle.app.scheduleimager

import android.app.Application
import net.ustyle.app.scheduleimager.work.ScheduleRefreshWorker

class ScheduleImagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ScheduleRefreshWorker.schedulePeriodic(this)
    }
}
