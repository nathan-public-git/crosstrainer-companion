package com.crosstrainer.companion.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateZoneTest {
    private val age = 20 // Estimated maximum is exactly 200 BPM.

    @Test
    fun `unavailable reading remains neutral even with a profile`() {
        assertEquals(HeartRateZone.UNAVAILABLE, heartRateZone(null, age))
    }

    @Test
    fun `live reading without a profile remains uncolored`() {
        assertEquals(HeartRateZone.NO_PROFILE, heartRateZone(150, null))
    }

    @Test
    fun `moderate zone starts at 50 percent and ends below 70 percent`() {
        assertEquals(HeartRateZone.BELOW_MODERATE, heartRateZone(99, age))
        assertEquals(HeartRateZone.MODERATE, heartRateZone(100, age))
        assertEquals(HeartRateZone.MODERATE, heartRateZone(139, age))
    }

    @Test
    fun `vigorous zone starts at 70 percent and includes 85 percent`() {
        assertEquals(HeartRateZone.VIGOROUS, heartRateZone(140, age))
        assertEquals(HeartRateZone.VIGOROUS, heartRateZone(170, age))
    }

    @Test
    fun `high zone starts above 85 percent`() {
        assertEquals(HeartRateZone.HIGH, heartRateZone(171, age))
    }

    @Test
    fun `estimated maximum uses 220 minus age`() {
        assertEquals(180, estimatedMaximumHeartRate(40))
    }

    @Test
    fun `ranges use exact integer breakpoints for saved age`() {
        val ranges = heartRateZoneRanges(48) // Estimated maximum 172.

        assertEquals(HeartRateZoneRange(HeartRateZone.BELOW_MODERATE, 0, 85), ranges[0])
        assertEquals(HeartRateZoneRange(HeartRateZone.MODERATE, 86, 120), ranges[1])
        assertEquals(HeartRateZoneRange(HeartRateZone.VIGOROUS, 121, 146), ranges[2])
        assertEquals(HeartRateZoneRange(HeartRateZone.HIGH, 147, null), ranges[3])
    }

    @Test
    fun `range breakpoints and zone mapping stay aligned`() {
        heartRateZoneRanges(48).forEach { range ->
            assertEquals(range.zone, heartRateZone(range.minimumBpm, 48))
            range.maximumBpm?.let { assertEquals(range.zone, heartRateZone(it, 48)) }
        }
    }
}
