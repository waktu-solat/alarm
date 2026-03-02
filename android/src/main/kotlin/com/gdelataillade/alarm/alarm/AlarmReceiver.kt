package com.gdelataillade.alarm.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

import io.flutter.Log

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ALARM_STOP = "com.gdelataillade.alarm.ACTION_STOP"
        const val EXTRA_ALARM_ACTION = "EXTRA_ALARM_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        /// Stop alarm from notification stop button.
        if (action == ACTION_ALARM_STOP) {
            val id = intent.getIntExtra("id", 0)
            Log.d("AlarmReceiver", "Received stop alarm command from notification, id: $id")

            if (id == 0) {
                Log.w("AlarmReceiver", "Ignoring stop alarm command because id is missing.")
            } else {
                AlarmService.instance?.handleStopAlarmCommand(id)
            }

            // Always return for STOP actions to avoid starting AlarmService with invalid extras.
            return
        }

        val id = intent.getIntExtra("id", 0)
        val alarmSettingsJson = intent.getStringExtra("alarmSettings")
        if (id == 0 || alarmSettingsJson.isNullOrEmpty()) {
            Log.e(
                "AlarmReceiver",
                "Ignoring alarm trigger due to missing extras. id=$id hasAlarmSettings=${!alarmSettingsJson.isNullOrEmpty()}"
            )
            return
        }

        // Start Alarm Service
        val serviceIntent = Intent(context, AlarmService::class.java)
        serviceIntent.putExtras(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val pendingIntent = PendingIntent.getForegroundService(
                context,
                id,
                serviceIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            pendingIntent.send()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
