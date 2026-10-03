package net.ustyle.app.scheduleimager.data

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleEvent(
    val id: String,
    val title: String,
    /** Epoch milliseconds (UTC). */
    val start: Long,
    /** Epoch milliseconds (UTC). */
    val end: Long,
    val location: String?,
    val isAllDay: Boolean,
)

/**
 * Snapshot written after every refresh and read by both the host app and the
 * widget. Both run in the same process, so this is just a plain JSON file in
 * app-private storage rather than anything shared across processes.
 */
@Serializable
data class ScheduleSnapshot(
    val events: List<ScheduleEvent>,
    val lastUpdated: Long,
    val signedIn: Boolean,
)
