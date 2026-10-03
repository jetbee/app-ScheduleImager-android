package net.ustyle.app.scheduleimager

/**
 * Values that must be filled in after registering an app in Azure AD. See
 * README.md for step-by-step setup instructions. The MSAL client id, redirect
 * URI, and tenant live in res/raw/msal_config.json instead of here, since
 * MSAL Android reads its configuration from that file.
 */
object Config {
    const val GRAPH_SCOPE = "Calendars.Read"

    /** Opened when the widget is tapped, instead of launching this app. */
    const val OUTLOOK_CALENDAR_URL = "https://outlook.office.com/calendar/view/day"

    /** How far ahead to fetch events on every refresh. */
    const val LOOKAHEAD_DAYS = 7L

    /** How often the background worker refreshes the widget's cache. */
    const val REFRESH_INTERVAL_MINUTES = 30L
}
