package com.unixshells.devbrowser

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionCodecTest {

    private fun sampleSession(): SavedSession = SavedSession(
        selectedTabId = 2,
        nextId = 5,
        desktopMode = true,
        tabs = listOf(
            SavedTab(id = 0, url = "https://example.com/a", title = "A", webViewState = byteArrayOf(1, 2, 3)),
            SavedTab(id = 2, url = "https://example.org/b?q=ç", title = "Título ✓", webViewState = null),
            SavedTab(id = 4, url = "about:blank", title = "", webViewState = ByteArray(0))
        )
    )

    @Test
    fun roundTripPreservesEverything() {
        val original = sampleSession()
        val decoded = SessionCodec.decode(SessionCodec.encode(original))

        requireNotNull(decoded)
        assertEquals(original.selectedTabId, decoded.selectedTabId)
        assertEquals(original.nextId, decoded.nextId)
        assertEquals(original.desktopMode, decoded.desktopMode)
        assertEquals(original.tabs.size, decoded.tabs.size)
        for (i in original.tabs.indices) {
            val a = original.tabs[i]
            val b = decoded.tabs[i]
            assertEquals(a.id, b.id)
            assertEquals(a.url, b.url)
            assertEquals(a.title, b.title)
            if (a.webViewState == null) assertNull(b.webViewState)
            else assertArrayEquals(a.webViewState, b.webViewState)
        }
    }

    @Test
    fun emptySessionRoundTrips() {
        val empty = SavedSession(selectedTabId = -1, nextId = 0, desktopMode = false, tabs = emptyList())
        val decoded = SessionCodec.decode(SessionCodec.encode(empty))
        requireNotNull(decoded)
        assertEquals(0, decoded.tabs.size)
        assertEquals(-1, decoded.selectedTabId)
    }

    @Test
    fun wrongMagicIsRejected() {
        val bytes = SessionCodec.encode(sampleSession())
        bytes[0] = 0x00
        assertNull(SessionCodec.decode(bytes))
    }

    @Test
    fun unknownVersionIsRejected() {
        val bytes = SessionCodec.encode(sampleSession())
        // VERSION is the second int (bytes 4..7); set it to 99.
        bytes[7] = 99
        assertNull(SessionCodec.decode(bytes))
    }

    @Test
    fun truncatedDataIsRejectedWithoutThrowing() {
        val bytes = SessionCodec.encode(sampleSession())
        for (cut in 0 until bytes.size) {
            // Any prefix must either decode to null or fail gracefully, never throw.
            SessionCodec.decode(bytes.copyOf(cut))
        }
        assertNull(SessionCodec.decode(bytes.copyOf(bytes.size - 1)))
    }

    @Test
    fun emptyInputIsRejected() {
        assertNull(SessionCodec.decode(ByteArray(0)))
    }

    @Test
    fun oversizedUrlIsClippedNotCorrupted() {
        val longUrl = "https://example.com/" + "a".repeat(40_000)
        val session = SavedSession(0, 1, true, listOf(SavedTab(0, longUrl, "t", null)))
        val decoded = SessionCodec.decode(SessionCodec.encode(session))
        requireNotNull(decoded)
        assertEquals(16_000, decoded.tabs[0].url.length)
        assertEquals(longUrl.substring(0, 16_000), decoded.tabs[0].url)
    }
}
