package com.crosstrainer.companion.data

import android.content.Context
import java.time.LocalDate

class DailyWorkoutDurationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun elapsedSeconds(): Long = synchronized(this) {
        resetForNewDayIfNeeded()
        preferences.getLong(KEY_ELAPSED_SECONDS, 0L)
    }

    fun addSecond(): Long = synchronized(this) {
        resetForNewDayIfNeeded()
        val elapsed = preferences.getLong(KEY_ELAPSED_SECONDS, 0L) + 1L
        preferences.edit().putLong(KEY_ELAPSED_SECONDS, elapsed).apply()
        elapsed
    }

    private fun resetForNewDayIfNeeded() {
        val today = LocalDate.now().toString()
        if (preferences.getString(KEY_DATE, null) != today) {
            preferences.edit().putString(KEY_DATE, today).putLong(KEY_ELAPSED_SECONDS, 0L).apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "daily_workout_duration"
        const val KEY_DATE = "date"
        const val KEY_ELAPSED_SECONDS = "elapsed_seconds"
    }
}
