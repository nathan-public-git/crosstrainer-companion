package com.crosstrainer.companion.data

/** Parses the Bluetooth SIG Heart Rate Measurement characteristic (0x2A37). */
object HeartRateMeasurementParser {
    fun parseBpm(value: ByteArray): Int? {
        if (value.size < 2) return null
        val flags = value[0].toInt() and 0xff
        return if (flags and 0x01 == 0) {
            value[1].toInt() and 0xff
        } else {
            if (value.size < 3) null
            else (value[1].toInt() and 0xff) or ((value[2].toInt() and 0xff) shl 8)
        }
    }
}

data class HeartRateSession(val total: Long = 0, val samples: Int = 0) {
    val averageBpm: Int? get() = if (samples == 0) null else (total / samples).toInt()

    fun add(bpm: Int): HeartRateSession = copy(total = total + bpm, samples = samples + 1)
}
