/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.innertube

import MetrolistGroup.innertubex.InnerTube
import MetrolistGroup.innertubex.YouTubeClient
import MetrolistGroup.innertubex.extractor.AudioQuality
import MetrolistGroup.innertubex.extractor.ContentHints
import MetrolistGroup.innertubex.extractor.InnerTubeExtractor
import MetrolistGroup.innertubex.extractor.PlayerConfigRepository
import MetrolistGroup.innertubex.extractor.RemotePlayerConfigStore
import MetrolistGroup.innertubex.extractor.YouTubeCipherService
import MetrolistGroup.innertubex.extractor.YtConfigParserImpl
import android.util.Log
import com.pico.music.core.unified.ManifestType
import com.pico.music.core.unified.ResolvedStream
import com.pico.music.core.unified.SearchFilter
import com.pico.music.core.unified.StreamQuality
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.statement.bodyAsText

/**
 * Manages the InnertubeX KMP client and stream extraction stack for PICO Music.
 *
 * Security & privacy:
 * - Uses strict default TLS (never bypasses certificate validation).
 * - Never logs extracted signed URLs, PO-tokens, or session cookies.
 */
class InnertubeXClientManager {
    companion object {
        private const val TAG = "InnertubeXClientManager"

        // Standard YouTube Music search filter params
        private const val PARAM_SONGS = "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D"
        private const val PARAM_ALBUMS = "EgWKAQIYAWoKEAoQAxAEEAkQBQ%3D%3D"
        private const val PARAM_ARTISTS = "EgWKAQIgAWoKEAoQAxAEEAkQBQ%3D%3D"
        private const val PARAM_PLAYLISTS = "EgWKAQIoAWoKEAoQAxAEEAkQBQ%3D%3D"
    }

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp)
    }

    val innerTube: InnerTube by lazy {
        InnerTube(httpClient = httpClient)
    }

    private val configStore: RemotePlayerConfigStore by lazy {
        RemotePlayerConfigStore(
            httpClient = innerTube.httpClient,
            repository = PlayerConfigRepository.disabled()
        )
    }

    private val cipherService: YouTubeCipherService by lazy {
        YouTubeCipherService(innerTube.httpClient, configStore)
    }

    private val extractor: InnerTubeExtractor by lazy {
        InnerTubeExtractor(
            configParser = YtConfigParserImpl(
                innerTube.httpClient,
                innerTube,
                configStore,
                cipherService = cipherService
            ),
            cipherService = cipherService,
            innerTube = innerTube
        )
    }

    suspend fun searchRaw(query: String, filter: SearchFilter): String? {
        val params = when (filter) {
            SearchFilter.SONGS -> PARAM_SONGS
            SearchFilter.ALBUMS -> PARAM_ALBUMS
            SearchFilter.ARTISTS -> PARAM_ARTISTS
            SearchFilter.PLAYLISTS -> PARAM_PLAYLISTS
            SearchFilter.ALL -> null
        }
        return runCatching {
            innerTube.search(
                client = YouTubeClient.WEB_REMIX,
                query = query,
                params = params
            ).bodyAsText()
        }.onFailure {
            Log.w(TAG, "InnertubeX search failed for filter=$filter", it)
        }.getOrNull()
    }

    suspend fun browseRaw(browseId: String, params: String? = null): String? {
        return runCatching {
            innerTube.browse(
                client = YouTubeClient.WEB_REMIX,
                browseId = browseId,
                params = params
            ).bodyAsText()
        }.onFailure {
            Log.w(TAG, "InnertubeX browse failed for browseId=$browseId", it)
        }.getOrNull()
    }

    suspend fun nextRaw(videoId: String): String? {
        return runCatching {
            innerTube.next(
                client = YouTubeClient.WEB_REMIX,
                videoId = videoId
            ).bodyAsText()
        }.onFailure {
            Log.w(TAG, "InnertubeX next failed for videoId=$videoId", it)
        }.getOrNull()
    }

    suspend fun getSearchSuggestionsRaw(query: String): String? {
        return runCatching {
            innerTube.getSearchSuggestions(
                client = YouTubeClient.WEB_REMIX,
                input = query
            ).bodyAsText()
        }.onFailure {
            Log.w(TAG, "InnertubeX search suggestions failed", it)
        }.getOrNull()
    }

    suspend fun extractAudioStream(
        videoId: String,
        quality: StreamQuality = StreamQuality.HIGH
    ): ResolvedStream? {
        val audioQuality = when (quality) {
            StreamQuality.LOW -> AudioQuality.LOW
            StreamQuality.NORMAL -> AudioQuality.MEDIUM
            StreamQuality.HIGH,
            StreamQuality.LOSSLESS,
            StreamQuality.HI_RES -> AudioQuality.HIGH
        }

        val extracted = runCatching {
            extractor.extract(
                videoId = videoId,
                hints = ContentHints(wantVideo = false),
                audioQuality = audioQuality
            )
        }.onFailure {
            Log.w(TAG, "InnertubeX stream extraction failed for videoId=$videoId", it)
        }.getOrNull() ?: return null

        val url = extracted.url
        if (url.isBlank()) return null

        val mime = extracted.mimeType
        val manifestType = when {
            mime?.contains("mpegurl", ignoreCase = true) == true || url.contains(".m3u8") -> ManifestType.HLS
            mime?.contains("dash+xml", ignoreCase = true) == true || url.contains(".mpd") -> ManifestType.DASH
            else -> ManifestType.PROGRESSIVE
        }

        return ResolvedStream(
            url = url,
            headers = extracted.headers,
            mimeType = mime,
            manifestType = manifestType,
            expiresAtMs = null // Uses StreamResolutionCache's ephemeral TTL
        )
    }
}
