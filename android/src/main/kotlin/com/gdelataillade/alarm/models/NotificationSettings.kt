package com.gdelataillade.alarm.models

import android.graphics.Color
import com.gdelataillade.alarm.generated.NotificationSettingsWire
import kotlinx.serialization.Serializable

@Serializable
data class NotificationSettings(
    val title: String,
    val body: String,
    val stopButton: String? = null,
    val icon: String? = null,
    val iconColor: Int? = null,
    val keepNotificationAfterAlarmEnds: Boolean = false,
    val androidSnoozeButton: String? = null,
    // Defaults to true so alarms persisted before this flag existed, which
    // decode without the key, keep stopping on a dismiss as they did.
    val androidStopAlarmOnDismiss: Boolean = true,
    // Appended last, and defaulted, so alarms persisted before this flag
    // existed still decode. fromWire passes arguments positionally, so a new
    // field inserted anywhere but the end would silently shift every field
    // after it.
    val androidLocalOnly: Boolean = false,
    // App-defined action (label + opaque payload). Appended last and
    // defaulted for the same reasons as androidLocalOnly.
    val androidActionButton: String? = null,
    val androidActionPayload: String? = null,
) {
    companion object {
        fun fromWire(e: NotificationSettingsWire): NotificationSettings {
            val a = e.iconColorAlpha?.toFloat()
            val r = e.iconColorRed?.toFloat()
            val g = e.iconColorGreen?.toFloat()
            val b = e.iconColorBlue?.toFloat()

            var iconColor: Int? = null
            if (a != null && r != null && g != null && b != null) {
                iconColor = Color.argb(a, r, g, b)
            }

            return NotificationSettings(
                e.title,
                e.body,
                e.stopButton,
                e.icon,
                iconColor,
                e.keepNotificationAfterAlarmEnds,
                e.androidSnoozeButton,
                e.androidStopAlarmOnDismiss,
                e.androidLocalOnly,
                e.androidActionButton,
                e.androidActionPayload,
            )
        }
    }
}