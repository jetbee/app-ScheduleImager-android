package net.ustyle.app.scheduleimager.data

import android.content.Context
import java.io.File
import kotlinx.serialization.json.Json

/**
 * Reads and writes the cached schedule to app-private storage. The host app
 * and the widget both run in this app's own process, so (unlike the iOS
 * version, which shares an App Group container with a separate widget
 * extension process) a plain file is enough.
 */
object ScheduleStore {
    private const val FILE_NAME = "schedule_snapshot.json"
    private val json = Json { ignoreUnknownKeys = true }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun save(context: Context, snapshot: ScheduleSnapshot) {
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(json.encodeToString(ScheduleSnapshot.serializer(), snapshot))
        tmp.renameTo(file(context))
    }

    @Synchronized
    fun load(context: Context): ScheduleSnapshot? {
        val target = file(context)
        if (!target.exists()) return null
        return try {
            json.decodeFromString(ScheduleSnapshot.serializer(), target.readText())
        } catch (e: Exception) {
            null
        }
    }
}
