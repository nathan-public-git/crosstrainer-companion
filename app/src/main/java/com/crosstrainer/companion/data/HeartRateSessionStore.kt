package com.crosstrainer.companion.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedHeartRateSession(
    val completedAtMillis: Long,
    val averageBpm: Int,
)

/** Persists each completed monitor connection as its own workout session. */
class HeartRateSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun history(): List<SavedHeartRateSession> = JSONArray(preferences.getString(KEY_HISTORY, "[]"))
        .let { sessions ->
            (0 until sessions.length()).mapNotNull { index ->
                sessions.optJSONObject(index)?.let { entry ->
                    val completedAt = entry.optLong("completedAt", 0L)
                    val average = entry.optInt("averageBpm", 0)
                    if (completedAt > 0 && average > 0) SavedHeartRateSession(completedAt, average) else null
                }
            }.sortedBy { it.completedAtMillis }
        }

    fun save(averageBpm: Int, completedAtMillis: Long = System.currentTimeMillis()) {
        if (averageBpm <= 0) return
        val updated = JSONArray(preferences.getString(KEY_HISTORY, "[]"))
        updated.put(JSONObject().apply {
            put("completedAt", completedAtMillis)
            put("averageBpm", averageBpm)
        })
        preferences.edit().putString(KEY_HISTORY, updated.toString()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "heart_rate_sessions"
        const val KEY_HISTORY = "history"
    }
}
