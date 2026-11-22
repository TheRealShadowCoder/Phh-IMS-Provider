package me.phh.sip

import org.junit.Assert.assertEquals
import org.junit.Test

class SipPcmRmsTest {
    @Test
    fun zeroSamplesHaveZeroRms() {
        assertEquals(0.0, SipPcmRms.normalizedPcm16(byteArrayOf(0, 0, 0, 0), 4), 0.0)
    }

    @Test
    fun rmsMeasuresSignedLittleEndianPcm16() {
        val pcm = byteArrayOf(
            0xE8.toByte(), 0x03, // +1000
            0x18, 0xFC.toByte(), // -1000
        )

        assertEquals(1000.0 / 32768.0, SipPcmRms.normalizedPcm16(pcm, pcm.size), 1e-9)
    }

    @Test
    fun ignoresIncompleteTrailingByte() {
        val pcm = byteArrayOf(0xE8.toByte(), 0x03, 0x7f)

        assertEquals(1000.0 / 32768.0, SipPcmRms.normalizedPcm16(pcm, pcm.size), 1e-9)
    }

    @Test
    fun clampsReadSizeToBuffer() {
        val pcm = byteArrayOf(0xE8.toByte(), 0x03)

        assertEquals(1000.0 / 32768.0, SipPcmRms.normalizedPcm16(pcm, 100), 1e-9)
    }
}
