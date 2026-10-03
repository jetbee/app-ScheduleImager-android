package net.ustyle.app.scheduleimager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class GraphException(message: String) : Exception(message)

/**
 * Minimal Microsoft Graph client for reading the signed-in user's Outlook
 * calendar. Used by both the host app (manual refresh) and the background
 * worker that keeps the widget's cache up to date.
 */
object GraphService {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class CalendarViewResponse(val value: List<GraphEvent> = emptyList())

    @Serializable
    private data class GraphEvent(
        val id: String,
        val subject: String? = null,
        val start: DateTimeZone,
        val end: DateTimeZone,
        val location: Location? = null,
        val isAllDay: Boolean = false,
    )

    @Serializable
    private data class DateTimeZone(val dateTime: String)

    @Serializable
    private data class Location(val displayName: String? = null)

    // Graph returns "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS" (7 fractional digits) in
    // the zone requested via the Prefer header, which we fix to UTC below.
    private val graphFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS")
    private val isoFormatter = DateTimeFormatter.ISO_INSTANT

    /** Fetches events between [start] and [end], ordered by start time. */
    suspend fun fetchEvents(accessToken: String, start: Instant, end: Instant): List<ScheduleEvent> =
        withContext(Dispatchers.IO) {
            val url = "https://graph.microsoft.com/v1.0/me/calendarview".toHttpUrl()
                .newBuilder()
                .addQueryParameter("startDateTime", isoFormatter.format(start))
                .addQueryParameter("endDateTime", isoFormatter.format(end))
                .addQueryParameter("\$orderby", "start/dateTime")
                .addQueryParameter("\$top", "25")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Prefer", "outlook.timezone=\"UTC\"")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw GraphException("Graph request failed: HTTP ${response.code}")
                }
                val body = response.body?.string() ?: throw GraphException("Empty response body")
                val decoded = json.decodeFromString<CalendarViewResponse>(body)
                decoded.value.mapNotNull { event ->
                    val startMillis = parseGraphUtc(event.start.dateTime) ?: return@mapNotNull null
                    val endMillis = parseGraphUtc(event.end.dateTime) ?: return@mapNotNull null
                    ScheduleEvent(
                        id = event.id,
                        title = event.subject?.takeIf { it.isNotBlank() } ?: "(タイトルなし)",
                        start = startMillis,
                        end = endMillis,
                        location = event.location?.displayName,
                        isAllDay = event.isAllDay,
                    )
                }
            }
        }

    private fun parseGraphUtc(value: String): Long? = try {
        LocalDateTime.parse(value, graphFormatter).toInstant(ZoneOffset.UTC).toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
