package com.crosstrainer.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateMeasurementParserTest {
    @Test
    fun `parses 8 bit heart rate`() {
        assertEquals(72, HeartRateMeasurementParser.parseBpm(byteArrayOf(0x00, 72)))
    }

    @Test
    fun `parses unsigned 8 bit heart rate`() {
        assertEquals(200, HeartRateMeasurementParser.parseBpm(byteArrayOf(0x00, 0xc8.toByte())))
    }

    @Test
    fun `parses little endian 16 bit heart rate when flag is set`() {
        assertEquals(300, HeartRateMeasurementParser.parseBpm(byteArrayOf(0x01, 0x2c, 0x01)))
    }

    @Test
    fun `rejects truncated measurements`() {
        assertNull(HeartRateMeasurementParser.parseBpm(byteArrayOf()))
        assertNull(HeartRateMeasurementParser.parseBpm(byteArrayOf(0x01, 0x2c)))
    }

    @Test
    fun `session average uses all received samples`() {
        val session = HeartRateSession().add(100).add(110).add(120)
        assertEquals(110, session.averageBpm)
    }
}
