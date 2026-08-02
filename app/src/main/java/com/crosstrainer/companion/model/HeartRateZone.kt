package com.crosstrainer.companion.model

enum class HeartRateZone {
    UNAVAILABLE,
    NO_PROFILE,
    BELOW_MODERATE,
    MODERATE,
    VIGOROUS,
    HIGH,
}

fun estimatedMaximumHeartRate(age: Int): Int = 220 - age

data class HeartRateZoneRange(
    val zone: HeartRateZone,
    val minimumBpm: Int,
    val maximumBpm: Int?,
)

fun heartRateZoneRanges(age: Int): List<HeartRateZoneRange> {
    val maximum = estimatedMaximumHeartRate(age)
    val moderateStart = ceilingPercent(maximum, 50)
    val vigorousStart = ceilingPercent(maximum, 70)
    val highStart = (maximum * 85 / 100) + 1
    return listOf(
        HeartRateZoneRange(HeartRateZone.BELOW_MODERATE, 0, moderateStart - 1),
        HeartRateZoneRange(HeartRateZone.MODERATE, moderateStart, vigorousStart - 1),
        HeartRateZoneRange(HeartRateZone.VIGOROUS, vigorousStart, highStart - 1),
        HeartRateZoneRange(HeartRateZone.HIGH, highStart, null),
    )
}

fun heartRateZone(currentBpm: Int?, age: Int?): HeartRateZone {
    if (currentBpm == null) return HeartRateZone.UNAVAILABLE
    if (age == null) return HeartRateZone.NO_PROFILE
    return heartRateZoneRanges(age).first { range ->
        currentBpm >= range.minimumBpm && (range.maximumBpm == null || currentBpm <= range.maximumBpm)
    }.zone
}

private fun ceilingPercent(value: Int, percent: Int): Int = (value * percent + 99) / 100
