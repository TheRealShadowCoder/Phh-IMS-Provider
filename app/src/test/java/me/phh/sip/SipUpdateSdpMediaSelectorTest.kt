package me.phh.sip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SipUpdateSdpMediaSelectorTest {
    @Test
    fun compatibleAmrNbIsAcceptedWithoutTelephoneEvent8000() {
        val warnings = mutableListOf<String>()
        val selected = SipUpdateSdpMediaSelector.select(
            logTag = "test",
            attributes = listOf(
                "rtpmap:96 AMR/8000",
                "fmtp:96 octet-align=0",
                "rtpmap:110 telephone-event/16000",
            ),
            offeredPayloads = setOf(96, 110),
            selectedAudioCodec = SipAudioCodecs.AMR_NB,
            requestCallId = "test-call",
            logDebug = {},
            logWarning = warnings::add,
        )

        assertNotNull(selected)
        assertEquals(96, selected!!.amrTrack)
        assertNull(selected.dtmfTrack)
        assertEquals(1, warnings.size)
    }

    @Test
    fun matchingTelephoneEvent8000IsRetained() {
        val selected = SipUpdateSdpMediaSelector.select(
            logTag = "test",
            attributes = listOf(
                "rtpmap:96 AMR/8000",
                "fmtp:96 octet-align=0",
                "rtpmap:101 telephone-event/8000",
            ),
            offeredPayloads = setOf(96, 101),
            selectedAudioCodec = SipAudioCodecs.AMR_NB,
            requestCallId = "test-call",
            logDebug = {},
            logWarning = {},
        )

        assertNotNull(selected)
        assertEquals(101, selected!!.dtmfTrack)
        assertEquals("rtpmap:101 telephone-event/8000", selected.dtmfTrackDesc)
    }
}
