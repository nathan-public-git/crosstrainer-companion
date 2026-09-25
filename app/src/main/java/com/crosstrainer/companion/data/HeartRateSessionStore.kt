package com.crosstrainer.companion.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedHeartRateSession(
    val completedAtMillis: Long,
    val averageBpm: Int,
    val maximumBpm: Int? = null,
    val averageRpm: Int? = null,
    val maximumRpm: Int? = null,
    val durationSeconds: Long? = null,
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
                    val maximumBpm = entry.optionalPositiveInt("maximumBpm")
                    val averageRpm = entry.optionalPositiveInt("averageRpm")
                    val maximumRpm = entry.optionalPositiveInt("maximumRpm")
                    val duration = entry.takeIf { it.has("durationSeconds") }
                        ?.optLong("durationSeconds")
                        ?.takeIf { it >= 0 }
                    if (completedAt > 0 && average > 0) {
                        SavedHeartRateSession(completedAt, average, maximumBpm, averageRpm, maximumRpm, duration)
                    } else null
                }
            }.sortedBy { it.completedAtMillis }
        }

    fun save(
        averageBpm: Int,
        maximumBpm: Int,
        averageRpm: Int?,
        maximumRpm: Int?,
        durationSeconds: Long? = null,
        completedAtMillis: Long = System.currentTimeMillis(),
    ) {
        if (averageBpm <= 0) return
        val updated = JSONArray(preferences.getString(KEY_HISTORY, "[]"))
        updated.put(JSONObject().apply {
            put("completedAt", completedAtMillis)
            put("averageBpm", averageBpm)
            put("maximumBpm", maximumBpm)
            averageRpm?.let { put("averageRpm", it) }
            maximumRpm?.let { put("maximumRpm", it) }
            durationSeconds?.let { put("durationSeconds", it.coerceAtLeast(0)) }
        })
        preferences.edit().putString(KEY_HISTORY, updated.toString()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "heart_rate_sessions"
        const val KEY_HISTORY = "history"
    }
}

private fun JSONObject.optionalPositiveInt(key: String): Int? =
    takeIf { has(key) }?.optInt(key)?.takeIf { it > 0 }
