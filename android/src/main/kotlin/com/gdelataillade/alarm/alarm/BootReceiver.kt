package com.gdelataillade.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gdelataillade.alarm.models.AlarmEventCause
import com.gdelataillade.alarm.models.AlarmEventVerb
import com.gdelataillade.alarm.models.AlarmSettings
import com.gdelataillade.alarm.models.HostAlarmEvent
import com.gdelataillade.alarm.services.AlarmStorage
import com.gdelataillade.alarm.api.AlarmApiImpl

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"

        /**
         * How late an alarm may be at boot and still ring.
         *
         * Backported from upstream 4a08937 (5.12), minus its per-alarm
         * `androidStaleAfter` setting and Pigeon change: every stored alarm was
         * re-armed at boot with no age limit, so every alarm that came due while
         * the phone was off rang at once when it came back. 15 minutes is
         * upstream's default — a reboot straddling the alarm still rings, a
         * phone switched on hours later stays quiet. It must stay well above
         * Dart's `_ringStartGrace` (3 min), or Dart would spare an alarm this
         * path had already discarded. Drop this patch when taking upstream 5.12+.
         */
        internal const val STALE_AFTER_MILLIS = 15L * 60 * 1000

        internal fun isStale(alarm: AlarmSettings, now: Long): Boolean =
            now - alarm.dateTime.time > STALE_AFTER_MILLIS

        /**
         * Discards [alarm] and records why, instead of sounding it late.
         *
         * Nothing is reported to Dart from here: no Flutter engine is running at
         * boot. The DROPPED marker is drained on the next `Alarm.init()`.
         */
        internal fun discardStaleAlarm(alarm: AlarmSettings, storage: AlarmStorage, now: Long) {
            val event = HostAlarmEvent(
                alarmId = alarm.id,
                verb = AlarmEventVerb.DROPPED,
                cause = AlarmEventCause.STALE_AT_BOOT,
                atMillis = alarm.dateTime.time,
                recordedAtMillis = now,
            )
            // Same order AlarmService uses when it drops a refused ring:
            // unsaveAlarm deliberately keeps DROPPED markers.
            storage.saveAlarmEvent(event)
            storage.unsaveAlarm(alarm.id)
            Log.i(
                TAG,
                "Alarm ${alarm.id} was due at ${alarm.dateTime}, more than " +
                    "${STALE_AFTER_MILLIS / 60000} min before boot; discarded instead of ringing."
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device rebooted, rescheduling alarms")

            // Anything thrown here surfaces as "Unable to start receiver" and kills the app on
            // every boot, so failing to reschedule must never take the process down with it.
            try {
                rescheduleAlarms(context)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to reschedule alarms after boot", e)
            }
        }
    }

    private fun rescheduleAlarms(context: Context) {
        val alarmStorage = AlarmStorage(context)
        val storedAlarms = alarmStorage.getSavedAlarms()
        // One reading for the whole batch, so alarms due at the same time are
        // not judged differently because the loop took a moment.
        val now = System.currentTimeMillis()

        Log.i(TAG, "Rescheduling ${storedAlarms.size} alarms")

        for (alarm in storedAlarms) {
            try {
                if (isStale(alarm, now)) {
                    discardStaleAlarm(alarm, alarmStorage, now)
                    continue
                }

                Log.d(TAG, "Rescheduling alarm with ID: ${alarm.id}")
                Log.d(TAG, "Alarm details: $alarm")

                // Call the setAlarm method in AlarmPlugin with the custom context
                val alarmApi = AlarmApiImpl(context)
                if (alarmApi.setAlarm(alarm)) {
                    Log.d(TAG, "Alarm rescheduled successfully for ID: ${alarm.id}")
                } else {
                    // Deliberately left in storage, unlike the Pigeon set path which
                    // unsaves so Dart never reports an alarm the platform never armed.
                    // Here that record is the only thing a later attempt could re-arm
                    // from, and there is no engine running to be told about the
                    // failure — dropping it would silently lose the user's alarm for
                    // good. A boot-time failure is often transient anyway, because the
                    // system is still coming up.
                    Log.e(
                        TAG,
                        "Failed to re-arm alarm ${alarm.id} after reboot. It stays stored " +
                            "so a later Alarm.init() or reboot can retry."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while rescheduling alarm: $alarm", e)
            }
        }
    }
}