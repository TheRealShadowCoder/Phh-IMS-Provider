// SPDX-License-Identifier: GPL-2.0
package me.phh.sip

import android.util.Xml
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.xmlpull.v1.XmlPullParser

/** Helpers for the application/vnd.3gpp.ussd+xml payload used by 3GPP TS 24.390. */
internal object SipUssdCodec {
    const val CONTENT_TYPE = "application/vnd.3gpp.ussd+xml"
    const val INFO_PACKAGE = "g.3gpp.ussd"

    data class Message(
        val text: String?,
        val expectsUserInput: Boolean,
        val notifyOnly: Boolean,
        val errorCode: Int?,
    )

    fun normalizeDialString(value: String): String? {
        val normalized = value.trim()
        if (normalized.length !in 2..182) return null
        if (!normalized.startsWith('*') || !normalized.endsWith('#')) return null
        if (normalized.any { it !in '0'..'9' && it != '*' && it != '#' }) return null
        return normalized
    }

    fun buildXml(text: String, includeRequestMarker: Boolean = false): ByteArray {
        val escaped = escapeXml(text)
        val requestMarker = if (includeRequestMarker) {
            "<anyExt><UnstructuredSS-Request/></anyExt>"
        } else ""
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
            "<ussd-data><language>en</language><ussd-string>$escaped</ussd-string>" +
            requestMarker + "</ussd-data>").toByteArray(StandardCharsets.UTF_8)
    }

    fun buildInviteBody(dialString: String, localAddress: String, boundary: String): ByteArray {
        val addressType = if (localAddress.contains(':')) "IP6" else "IP4"
        val sdp = """v=0
o=- 0 0 IN $addressType $localAddress
s=-
c=IN $addressType $localAddress
t=0 0
m=audio 0 RTP/AVP 97 96
a=rtpmap:97 AMR
a=rtpmap:96 telephone-event
""".trimIndent().replace("\n", "\r\n")
        val xml = String(buildXml(dialString), StandardCharsets.UTF_8)
        val body = """--$boundary
Content-Type: application/sdp

$sdp
--$boundary
Content-Type: $CONTENT_TYPE
Content-Disposition: render;handling=optional

$xml
--$boundary--
""".trimIndent() + "\r\n"
        return body.toByteArray(StandardCharsets.UTF_8)
    }

    /** Extract the USSD XML entity from either a direct SIP body or multipart body. */
    fun extractXml(contentType: String?, body: ByteArray): ByteArray? {
        if (body.isEmpty() || body.size > 16 * 1024) return null
        return when {
            SipMultipartBody.isContentType(contentType, CONTENT_TYPE) -> body
            SipMultipartBody.isMultipart(contentType) ->
                SipMultipartBody.extractPartBody(contentType, body, CONTENT_TYPE)
            // Be tolerant of peers which omit the top-level Content-Type but
            // still include the multipart part headers in the body.
            contentType == null -> SipMultipartBody.extractPartBody(null, body, CONTENT_TYPE)
            else -> null
        }
    }

    fun parse(body: ByteArray): Message? {
        if (body.isEmpty() || body.size > 16 * 1024) return null
        return try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(ByteArrayInputStream(body), "UTF-8")
            }
            var text: String? = null
            var inString = false
            var request = false
            var notify = false
            var error: Int? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                        "ussd-string" -> inString = true
                        "UnstructuredSS-Request" -> request = true
                        "UnstructuredSS-Notify" -> notify = true
                        "error-code" -> {
                            val raw = parser.nextText().trim()
                            error = raw.toIntOrNull()
                            event = parser.eventType
                            continue
                        }
                    }
                    XmlPullParser.TEXT -> if (inString) {
                        text = (text ?: "") + parser.text
                    }
                    XmlPullParser.END_TAG -> if (parser.name.substringAfter(':') == "ussd-string") {
                        inString = false
                    }
                }
                event = parser.next()
            }
            if (text == null && error == null) null
            else Message(text?.trim(), request, notify, error)
        } catch (_: Exception) {
            null
        }
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            append(when (ch) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '\"' -> "&quot;"
                '\'' -> "&apos;"
                else -> ch.toString()
            })
        }
    }
}
