package com.crosstrainer.companion.data

import android.content.Context
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

data class DailyHealthMetrics(val weight: Double? = null, val bloodSugar: Double? = null)
data class DailyHealthMetricsEntry(val date: String, val weight: Double?, val bloodSugar: Double?)

class DailyHealthMetricsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun needsPromptToday(): Boolean {
        val metrics = todayMetrics()
        return metrics.weight == null || metrics.bloodSugar == null
    }

    fun todayMetrics(): DailyHealthMetrics {
        val date = today()
        val history = JSONArray(preferences.getString(KEY_HISTORY, "[]"))
        val entry = (0 until history.length()).asSequence()
            .mapNotNull { history.optJSONObject(it) }
            .firstOrNull { it.optString("date") == date }
            ?: return DailyHealthMetrics()
        return DailyHealthMetrics(
            weight = entry.takeIf { it.has("weight") }?.optDouble("weight"),
            bloodSugar = entry.takeIf { it.has("bloodSugar") }?.optDouble("bloodSugar"),
        )
    }

    fun history(): List<DailyHealthMetricsEntry> = JSONArray(preferences.getString(KEY_HISTORY, "[]"))
        .let { history ->
            (0 until history.length()).mapNotNull { index ->
                history.optJSONObject(index)?.let { entry ->
                    DailyHealthMetricsEntry(
                        date = entry.optString("date"),
                        weight = entry.takeIf { it.has("weight") }?.optDouble("weight"),
                        bloodSugar = entry.takeIf { it.has("bloodSugar") }?.optDouble("bloodSugar"),
                    )
                }
            }.sortedBy { it.date }
        }

    fun save(weight: Double?, bloodSugar: Double?) {
        val date = today()
        val history = JSONArray(preferences.getString(KEY_HISTORY, "[]"))
        val updated = JSONArray()
        val previous = todayMetrics()
        for (index in 0 until history.length()) {
            val entry = history.optJSONObject(index) ?: continue
            if (entry.optString("date") != date) updated.put(entry)
        }
        updated.put(JSONObject().apply {
            put("date", date)
            (weight ?: previous.weight)?.let { put("weight", it) }
            (bloodSugar ?: previous.bloodSugar)?.let { put("bloodSugar", it) }
        })
        preferences.edit()
            .putString(KEY_HISTORY, updated.toString())
            .apply()
    }

    fun dismissToday() = Unit

    private fun today(): String = LocalDate.now().toString()

    private companion object {
        const val PREFERENCES_NAME = "daily_health_metrics"
        const val KEY_HISTORY = "history"
    }
}
