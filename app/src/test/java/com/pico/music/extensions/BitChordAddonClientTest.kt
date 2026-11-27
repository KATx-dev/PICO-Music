/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.extensions

import com.pico.music.core.unified.ManifestType
import com.pico.music.core.unified.SearchFilter
import com.pico.music.core.unified.StreamQuality
import com.pico.music.core.unified.UnifiedStreamResolverRegistry
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BitChordAddonClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: BitChordAddonClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = BitChordAddonClient()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `fetches manifest, searches tracks, and resolves unencrypted stream`() = runBlocking {
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "id": "selfhosted",
                  "name": "Self-Hosted Library",
                  "version": "1.2.0",
                  "resources": ["search", "stream"],
                  "allowDownloads": true
                }
                """.trimIndent()
            ).build()
        )
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "tracks": [
                    {
                      "id": "t-42",
                      "title": "Teardrop",
                      "artist": "Massive Attack",
                      "album": "Mezzanine",
                      "durationMs": 330000,
                      "quality": "FLAC 24-bit"
                    }
                  ]
                }
                """.trimIndent()
            ).build()
        )
        val streamUrl = server.url("/audio/t-42.flac").toString()
        server.enqueue(
            MockResponse.Builder().body(
                """
                {
                  "url": "$streamUrl",
                  "codec": "flac",
                  "manifest": "none",
                  "mimeType": "audio/flac",
                  "encrypted": false,
                  "sampleRate": 96000,
                  "bitDepth": 24
                }
                """.trimIndent()
            ).build()
        )

        val baseUrl = server.url("").toString().trimEnd('/')
        val manifest = client.fetchManifest(baseUrl)
        assertEquals("selfhosted", manifest.id)
        assertEquals("Self-Hosted Library", manifest.name)

        val ext = InstalledExtension(baseUrl = baseUrl, manifest = manifest)
        val searchResult = client.search(ext, "Teardrop", SearchFilter.SONGS)
        assertEquals(1, searchResult.tracks.size)

        // Playback URIs are minted by the registry; android.net.Uri is a stub on the
        // JVM test classpath, so assert against the same builder instead of a literal.
        val firstTrack = searchResult.tracks.first()
        assertEquals(
            UnifiedStreamResolverRegistry.buildExtensionUri("selfhosted", "t-42"),
            firstTrack.playbackUri
        )
        // android.net.Uri is stubbed on the JVM, so the URI text is not real here;
        // assert the parsed track identity instead of URI string shape.
        assertEquals("ext_selfhosted_t-42", firstTrack.id)
        assertEquals("Teardrop", firstTrack.title)
        assertEquals("FLAC 24-bit", firstTrack.qualityLabel)

        val resolved = client.resolveStream(ext, "t-42", StreamQuality.HI_RES)
        assertEquals(streamUrl, resolved.url)
        assertEquals(ManifestType.PROGRESSIVE, resolved.manifestType)
        assertEquals(96000, resolved.sampleRateHz)
        assertEquals(24, resolved.bitDepth)

        // Verify honest User-Agent (never spoofs BitChord)
        val recorded = server.takeRequest()
        assertTrue(recorded.headers["User-Agent"]?.startsWith("PICO-Music/") == true)
    }

    @Test
    fun `rejects insecure remote http urls while allowing localhost`() {
        assertFalse(BitChordAddonClient.isAllowedAddonUrl("http://example.com/addon"))
        assertTrue(BitChordAddonClient.isAllowedAddonUrl("https://example.com/addon"))
        assertTrue(BitChordAddonClient.isAllowedAddonUrl("http://127.0.0.1:8080"))
        assertTrue(BitChordAddonClient.isAllowedAddonUrl("http://localhost:8080"))
    }

    @Test(expected = BitChordAddonClient.EncryptedStreamUnsupportedException::class)
    fun `rejects encrypted streams`() = runBlocking {
        val streamUrl = server.url("/audio/drm.mp4").toString()
        server.enqueue(
            MockResponse.Builder().body("""{"url": "$streamUrl", "encrypted": true}""").build()
        )
        val baseUrl = server.url("").toString().trimEnd('/')
        val ext = InstalledExtension(
            baseUrl = baseUrl,
            manifest = BitChordAddonManifest(id = "drm", name = "DRM", version = "1.0.0")
        )
        client.resolveStream(ext, "1")
        Unit
    }

    @Test(expected = BitChordAddonClient.BitChordAuthRequiredException::class)
    fun `surfaces bitchordAuth 401 cleanly`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).build())
        client.fetchManifest(server.url("").toString().trimEnd('/'))
        Unit
    }

    @Test(expected = BitChordAddonClient.RateLimitedException::class)
    fun `surfaces 429 rate limit cleanly`() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(429).addHeader("Retry-After", "30").build()
        )
        val baseUrl = server.url("").toString().trimEnd('/')
        val ext = InstalledExtension(
            baseUrl = baseUrl,
            manifest = BitChordAddonManifest(id = "rl", name = "RL", version = "1.0.0")
        )
        client.resolveStream(ext, "1")
        Unit
    }
}
