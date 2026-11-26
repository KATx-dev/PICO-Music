/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.core.unified

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamResolutionCacheTest {

    @Test
    fun `cache respects explicit expiresAt with safety margin`() {
        var now = 1_000_000L
        val cache = StreamResolutionCache(
            clockMs = { now },
            defaultTtlMs = 900_000L,
            expirySafetyMarginMs = 60_000L
        )

        val stream = ResolvedStream(
            url = "https://example.com/audio.webm",
            expiresAtMs = now + 180_000L
        )
        cache.put("pico-yt://track/abc", stream)

        assertNotNull(cache.get("pico-yt://track/abc"))

        // At 120s (60s before expiry), entry must be evicted
        now += 120_000L
        assertNull(cache.get("pico-yt://track/abc"))
    }

    @Test
    fun `cache invalidation removes entry immediately`() {
        val cache = StreamResolutionCache()
        cache.put("k1", ResolvedStream(url = "https://example.com/1"))
        cache.put("k2", ResolvedStream(url = "https://example.com/2"))
        assertEquals(2, cache.size())

        cache.invalidate("k1")
        assertNull(cache.get("k1"))
        assertNotNull(cache.get("k2"))
    }

    @Test
    fun `stream error classifier targets managed schemes only`() {
        assertTrue(
            StreamErrorClassifier.isRecoverableStreamExpiration(
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                cause = null,
                uriScheme = UnifiedStreamResolverRegistry.SCHEME_INNERTUBEX
            )
        )
        assertTrue(
            StreamErrorClassifier.isRecoverableStreamExpiration(
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                cause = null,
                uriScheme = UnifiedStreamResolverRegistry.SCHEME_EXTENSION
            )
        )
        assertFalse(
            StreamErrorClassifier.isRecoverableStreamExpiration(
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                cause = null,
                uriScheme = "content"
            )
        )
    }
}
