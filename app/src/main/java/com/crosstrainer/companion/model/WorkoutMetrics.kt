package com.crosstrainer.companion.model

/** A device-independent snapshot that can later be populated by BLE data sources. */
data class WorkoutMetrics(
    val currentCadenceRpm: Int? = null,
    val averageCadenceRpm: Int? = null,
    val currentHeartRateBpm: Int? = null,
    val averageHeartRateBpm: Int? = null,
)
