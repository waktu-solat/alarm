package com.gdelataillade.alarm.services

import com.gdelataillade.alarm.models.AlarmSettings

import android.content.Context
import io.flutter.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

const val SHARED_PREFERENCES_NAME = "AlarmSharedPreferences"

// A truncated or otherwise damaged preferences_pb (interrupted write, low storage) makes DataStore
// throw CorruptionException on every read. With no handler that escapes BootReceiver and kills the
// app on every boot, so recover by resetting to empty preferences instead.
private val Context.dataStore: DataStore<Preferences> by
preferencesDataStore(
    name = SHARED_PREFERENCES_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { exception ->
        Log.e("AlarmStorage", "Alarm preferences were corrupted, resetting them", exception)
        emptyPreferences()
    }
)

class AlarmStorage(context: Context) {
    companion object {
        private const val TAG = "AlarmStorage"
        private const val PREFIX = "__alarm_id__"
    }

    private val dataStore = context.dataStore

    fun saveAlarm(alarmSettings: AlarmSettings) {
        return runBlocking {
            val key = stringPreferencesKey("$PREFIX${alarmSettings.id}")
            val value = Json.encodeToString(alarmSettings)
            dataStore.edit { preferences -> preferences[key] = value }
        }
    }

    fun unsaveAlarm(id: Int) {
        return runBlocking {
            val key = stringPreferencesKey("$PREFIX$id")
            dataStore.edit { preferences -> preferences.remove(key) }
        }
    }

    fun getSavedAlarms(): List<AlarmSettings> {
        return runBlocking {
            val preferences = dataStore.data.map { prefs ->
                prefs.asMap().filterKeys { it.name.startsWith(PREFIX) }
            }.catch { e ->
                // The corruption handler above covers CorruptionException; this covers the rest
                // (unreadable file, no space left) so callers never see a read failure as a throw.
                Log.e(TAG, "Error reading saved alarms", e)
                emit(emptyMap())
            }.first()

            val alarms = mutableListOf<AlarmSettings>()
            preferences.forEach { (key, value) ->
                if (value is String) {
                    try {
                        val alarm = Json.decodeFromString<AlarmSettings>(value)
                        alarms.add(alarm)
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Error parsing alarm settings for key ${key.name}: ${e.message}"
                        )
                    }
                } else {
                    Log.w(TAG, "Skipping non-alarm preference with key: ${key.name}")
                }
            }
            alarms
        }
    }
}
