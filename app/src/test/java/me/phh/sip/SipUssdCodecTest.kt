// SPDX-License-Identifier: GPL-2.0
package me.phh.sip

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SipUssdCodecTest {
    @Test
    fun extractsDirectUssdXmlEntity() {
        val xml = "<ussd-data><ussd-string>Balance</ussd-string></ussd-data>"
            .toByteArray(Charsets.UTF_8)

        assertArrayEquals(xml, SipUssdCodec.extractXml(SipUssdCodec.CONTENT_TYPE, xml))
    }

    @Test
    fun extractsUssdXmlFromMultipartEntity() {
        val multipart = """--session-1
Content-Type: application/sdp

v=0
--session-1
Content-Type: ${SipUssdCodec.CONTENT_TYPE}

<ussd-data><ussd-string>Balance</ussd-string></ussd-data>
--session-1--
""".toByteArray(Charsets.UTF_8)
        val expected = "<ussd-data><ussd-string>Balance</ussd-string></ussd-data>"
            .toByteArray(Charsets.UTF_8)

        assertArrayEquals(
            expected,
            SipUssdCodec.extractXml("multipart/mixed; boundary=\"session-1\"", multipart),
        )
    }
}
