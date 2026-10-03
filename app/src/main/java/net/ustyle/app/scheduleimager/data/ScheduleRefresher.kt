package net.ustyle.app.scheduleimager.data

import android.content.Context
import android.util.Log
import java.time.Instant
import java.time.temporal.ChronoUnit
import net.ustyle.app.scheduleimager.Config
import net.ustyle.app.scheduleimager.auth.AuthManager

private const val TAG = "ScheduleRefresher"

/**
 * Shared refresh routine: pulls the next [Config.LOOKAHEAD_DAYS] of events
 * from Outlook and writes them to the on-disk cache. Called both from the
 * app (manual "sync now") and from the background worker that keeps the
 * widget current.
 */
object ScheduleRefresher {
    suspend fun refresh(context: Context): ScheduleSnapshot {
        val auth = AuthManager.getInstance(context)
        if (!auth.isSignedIn()) {
            val snapshot = ScheduleSnapshot(events = emptyList(), lastUpdated = System.currentTimeMillis(), signedIn = false)
            ScheduleStore.save(context, snapshot)
            return snapshot
        }

        return try {
            val token = auth.acquireTokenSilently()
            val now = Instant.now()
            val weekAhead = now.plus(Config.LOOKAHEAD_DAYS, ChronoUnit.DAYS)
            val events = GraphService.fetchEvents(token, now, weekAhead)
            val snapshot = ScheduleSnapshot(events = events, lastUpdated = now.toEpochMilli(), signedIn = true)
            ScheduleStore.save(context, snapshot)
            snapshot
        } catch (e: Exception) {
            Log.w(TAG, "Schedule refresh failed", e)
            ScheduleStore.load(context) ?: ScheduleSnapshot(
                events = emptyList(),
                lastUpdated = System.currentTimeMillis(),
                signedIn = true,
            )
        }
    }
}
