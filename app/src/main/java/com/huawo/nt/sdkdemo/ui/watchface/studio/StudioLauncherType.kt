package com.huawo.nt.sdkdemo.ui.watchface.studio

/**
 * Launcher / complication IDs for [com.huawo.watchface.qjs.widgets.AppLinkWidget].
 *
 * Matches firmware complication enums (HS01 `COMPLICATION_*`).
 * Sent via [AppLinkWidget.setValueType]; tapping the hot zone opens the app on the watch.
 *
 * Studio UI currently exposes: Phone / Notifications / Activity / Weather.
 * [CALL_RECORD] is reserved for SDK parity / future UI.
 */
object StudioLauncherType {
    /** Call history */
    const val CALL_RECORD = 1006

    /** Weather app */
    const val WEATHER = 1027

    /** Activity / workout data */
    const val ACTIVITY = 1101

    /** Phone dialer */
    const val PHONE = 1102

    /** Notification center */
    const val NOTIFICATIONS = 1115
}
