package net.ustyle.app.scheduleimager.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle as JavaDayOfWeekTextStyle
import java.util.Locale
import net.ustyle.app.scheduleimager.data.ScheduleEvent

private val ZONE = ZoneId.systemDefault()
private const val DAY_START_HOUR = 8
private const val DAY_END_HOUR = 21
private const val SECONDS_PER_HOUR = 3600.0
private val HOUR_MARKS = DAY_START_HOUR..DAY_END_HOUR step 3
private val ALL_DAY_STRIP_HEIGHT = 10.dp
private val NOW_LINE_HEIGHT = 2.dp
private val MIN_EVENT_HEIGHT = 4.dp

/**
 * A 2- or 3-day timetable: one column per day, with events drawn as
 * time-proportional blocks stacked top to bottom. Today's column is
 * highlighted and gets a "now" line so it reads as the active day at a
 * glance — a port of the iOS widget's TimetableEntryView. Unlike the iOS
 * version's SwiftUI ZStack (which layers events at absolute offsets and lets
 * them visually overlap), events here are laid out sequentially with gap
 * spacers ahead of each one: Glance's Box does not reliably keep more than
 * one of several same-shaped, absolutely-offset siblings visible at once, so
 * true free-form overlap isn't available. This assumes a day's events don't
 * overlap in time, which holds for a single personal calendar.
 */
@Composable
fun TimetableContent(
    events: List<ScheduleEvent>,
    signedIn: Boolean,
    dayCount: Int,
    totalWidth: Dp,
    totalHeight: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    if (!signedIn) {
        Box(modifier = modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            Text(
                "Outlookにサインインしてください",
                style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
        }
        return
    }

    val today = LocalDate.now(ZONE)
    val days = (0 until dayCount).map { today.plusDays(it.toLong()) }

    val headerHeight = 14.dp
    val rulerWidth = 16.dp
    val spacing = 4.dp
    val horizontalPadding = 6.dp

    val plotHeight = (totalHeight - headerHeight - 8.dp).coerceAtLeast(20.dp)
    val availableWidth = (totalWidth - horizontalPadding * 2 - rulerWidth - spacing * dayCount).coerceAtLeast(40.dp)
    val columnWidth = availableWidth / dayCount
    val eventFontSize = if (totalHeight >= 200.dp) 11.sp else 8.sp

    Row(modifier = modifier.padding(horizontal = horizontalPadding, vertical = 4.dp)) {
        HourRuler(width = rulerWidth, headerHeight = headerHeight, plotHeight = plotHeight)
        days.forEach { day ->
            Spacer(modifier = GlanceModifier.width(spacing))
            DayColumn(
                day = day,
                today = today,
                events = events,
                width = columnWidth,
                headerHeight = headerHeight,
                plotHeight = plotHeight,
                eventFontSize = eventFontSize,
            )
        }
    }
}

@Composable
private fun HourRuler(width: Dp, headerHeight: Dp, plotHeight: Dp) {
    Column(modifier = GlanceModifier.width(width)) {
        Spacer(modifier = GlanceModifier.height(headerHeight))
        var cursor = 0.dp
        HOUR_MARKS.forEach { hour ->
            val top = verticalOffset(hour * SECONDS_PER_HOUR, plotHeight)
            val gap = (top - cursor).coerceAtLeast(0.dp)
            if (gap > 0.dp) Spacer(modifier = GlanceModifier.height(gap))
            Text("$hour", style = TextStyle(fontSize = 7.sp, color = GlanceTheme.colors.onSurfaceVariant))
            cursor = top + 9.dp // approximate glyph height, close enough for ruler tick spacing
        }
    }
}

/** One item in a day column's top-to-bottom timeline: a real event, or the current-time marker. */
private class TimelineItem(val top: Dp, val bottom: Dp, val event: ScheduleEvent?)

@Composable
private fun DayColumn(
    day: LocalDate,
    today: LocalDate,
    events: List<ScheduleEvent>,
    width: Dp,
    headerHeight: Dp,
    plotHeight: Dp,
    eventFontSize: TextUnit,
) {
    val isToday = day == today
    val dayEvents = events.filter { Instant.ofEpochMilli(it.start).atZone(ZONE).toLocalDate() == day }
    val hasAllDay = dayEvents.any { it.isAllDay }
    val timedEvents = dayEvents.filter { !it.isAllDay }.sortedBy { it.start }
    val startOfDaySeconds = day.atStartOfDay(ZONE).toInstant().epochSecond

    val items = buildList {
        if (hasAllDay) {
            add(TimelineItem(0.dp, ALL_DAY_STRIP_HEIGHT, null))
        }
        timedEvents.forEach { event ->
            val startSeconds = (Instant.ofEpochMilli(event.start).epochSecond - startOfDaySeconds).toDouble()
            val endSeconds = (Instant.ofEpochMilli(event.end).epochSecond - startOfDaySeconds).toDouble()
            val top = verticalOffset(startSeconds, plotHeight)
            val bottom = verticalOffset(endSeconds, plotHeight).coerceAtLeast(top + MIN_EVENT_HEIGHT)
            add(TimelineItem(top, bottom, event))
        }
    }.sortedBy { it.top.value }

    val nowItem = if (isToday) {
        nowOffset(plotHeight)?.let { TimelineItem(it, it + NOW_LINE_HEIGHT, null) }
    } else {
        null
    }
    val allItems = if (nowItem != null) (items + nowItem).sortedBy { it.top.value } else items

    Column(modifier = GlanceModifier.width(width)) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(headerHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                dayHeaderLabel(day, isToday),
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                ),
            )
        }

        Column(
            modifier = GlanceModifier
                .width(width)
                .height(plotHeight)
                .background(if (isToday) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant)
                .cornerRadius(4.dp),
        ) {
            var cursor = 0.dp
            allItems.forEach { item ->
                val gap = (item.top - cursor).coerceAtLeast(0.dp)
                if (gap > 0.dp) Spacer(modifier = GlanceModifier.height(gap))
                val itemHeight = item.bottom - item.top
                when {
                    item.event != null -> EventRow(item.event, itemHeight, eventFontSize)
                    itemHeight > 0.dp -> AllDayRow(itemHeight)
                    else -> NowLineRow()
                }
                cursor = maxOf(cursor, item.bottom)
            }
        }
    }
}

@Composable
private fun EventRow(event: ScheduleEvent, height: Dp, fontSize: TextUnit) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(GlanceTheme.colors.primary)
            .cornerRadius(3.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = event.title,
            maxLines = 2,
            modifier = GlanceModifier.padding(horizontal = 2.dp),
            style = TextStyle(fontSize = fontSize, color = GlanceTheme.colors.onPrimary),
        )
    }
}

@Composable
private fun AllDayRow(height: Dp) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(GlanceTheme.colors.primary)
            .cornerRadius(3.dp),
    ) {}
}

@Composable
private fun NowLineRow() {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(NOW_LINE_HEIGHT)
            .background(ColorProvider(Color.Red)),
    ) {}
}

private fun verticalOffset(seconds: Double, plotHeight: Dp): Dp {
    val rangeStart = DAY_START_HOUR * SECONDS_PER_HOUR
    val rangeEnd = DAY_END_HOUR * SECONDS_PER_HOUR
    val clamped = seconds.coerceIn(rangeStart, rangeEnd)
    val fraction = ((clamped - rangeStart) / (rangeEnd - rangeStart)).toFloat()
    return plotHeight * fraction
}

private fun nowOffset(plotHeight: Dp): Dp? {
    val nowSeconds = LocalTime.now(ZONE).toSecondOfDay().toDouble()
    val rangeStart = DAY_START_HOUR * SECONDS_PER_HOUR
    val rangeEnd = DAY_END_HOUR * SECONDS_PER_HOUR
    if (nowSeconds < rangeStart || nowSeconds > rangeEnd) return null
    return verticalOffset(nowSeconds, plotHeight)
}

private fun dayHeaderLabel(day: LocalDate, isToday: Boolean): String {
    if (isToday) return "今日 ${day.monthValue}/${day.dayOfMonth}"
    val dow = day.dayOfWeek.getDisplayName(JavaDayOfWeekTextStyle.SHORT, Locale.JAPANESE)
    return "${day.monthValue}/${day.dayOfMonth}($dow)"
}
