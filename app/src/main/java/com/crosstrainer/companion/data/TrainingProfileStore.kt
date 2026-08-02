package com.crosstrainer.companion.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrainingProfileState(
    val age: Int? = null,
    val hasSeenPrompt: Boolean = false,
)

class TrainingProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        TrainingProfileState(
            age = preferences.getInt(KEY_AGE, NO_AGE).takeUnless { it == NO_AGE },
            hasSeenPrompt = preferences.getBoolean(KEY_HAS_SEEN_PROMPT, false),
        ),
    )
    val state: StateFlow<TrainingProfileState> = _state.asStateFlow()

    fun saveAge(age: Int) {
        preferences.edit().putInt(KEY_AGE, age).putBoolean(KEY_HAS_SEEN_PROMPT, true).apply()
        _state.value = TrainingProfileState(age = age, hasSeenPrompt = true)
    }

    fun clearAge() {
        preferences.edit().remove(KEY_AGE).putBoolean(KEY_HAS_SEEN_PROMPT, true).apply()
        _state.value = TrainingProfileState(age = null, hasSeenPrompt = true)
    }

    fun markPromptSeen() {
        preferences.edit().putBoolean(KEY_HAS_SEEN_PROMPT, true).apply()
        _state.value = _state.value.copy(hasSeenPrompt = true)
    }

    companion object {
        const val MINIMUM_AGE = 18
        const val MAXIMUM_AGE = 100
        private const val PREFERENCES_NAME = "training_profile"
        private const val KEY_AGE = "age"
        private const val KEY_HAS_SEEN_PROMPT = "has_seen_prompt"
        private const val NO_AGE = -1
    }
}
