package net.ustyle.app.scheduleimager.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import net.ustyle.app.scheduleimager.Config
import net.ustyle.app.scheduleimager.data.ScheduleStore

/** Below this width, only 2 day-columns are drawn instead of 3 (see [TimetableContent]). */
private val THREE_DAY_WIDTH_THRESHOLD = 200.dp

class ScheduleWidget : GlanceAppWidget() {
    // Exact (rather than a fixed/responsive size set) hands TimetableContent the
    // real host-allocated size on every resize, since its layout is computed
    // proportionally from whatever width/height it's given rather than assuming
    // one of a few fixed breakpoints — home screen grid cell sizes vary too much
    // across launchers/devices for a fixed breakpoint set to line up with them.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Glance re-invokes provideGlance on every update request; loading the
        // cache here (rather than wiring up a GlanceStateDefinition) keeps this
        // widget's data flow a straight port of the iOS TimelineProvider, which
        // also just reads the shared cache once per timeline refresh.
        val snapshot = ScheduleStore.load(context)

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val dayCount = if (size.width < THREE_DAY_WIDTH_THRESHOLD) 2 else 3
                val openCalendarIntent = Intent(Intent.ACTION_VIEW, Uri.parse(Config.OUTLOOK_CALENDAR_URL))

                TimetableContent(
                    events = snapshot?.events.orEmpty(),
                    signedIn = snapshot?.signedIn ?: false,
                    dayCount = dayCount,
                    totalWidth = size.width,
                    totalHeight = size.height,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .clickable(actionStartActivity(openCalendarIntent)),
                )
            }
        }
    }
}
