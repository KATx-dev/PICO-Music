/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.innertube

import chromahub.rhythm.app.core.domain.model.SourceType
import com.pico.music.core.unified.MusicProvider
import com.pico.music.core.unified.ResolvedStream
import com.pico.music.core.unified.SearchFilter
import com.pico.music.core.unified.StreamQuality
import com.pico.music.core.unified.UnifiedHomeSection
import com.pico.music.core.unified.UnifiedSearchResult
import com.pico.music.core.unified.UnifiedStreamResolverRegistry
import com.pico.music.core.unified.UnifiedTrack

/**
 * [MusicProvider] backed by [InnertubeXClientManager] (`MetrolistGroup/innertubex`).
 */
class InnertubeXMusicProvider(
    private val clientManager: InnertubeXClientManager = InnertubeXClientManager(),
    private val enabledProvider: () -> Boolean = { true },
    private val qualityProvider: () -> StreamQuality = { StreamQuality.HIGH }
) : MusicProvider {

    override val providerId: String = "innertubex"
    override val displayName: String = "InnertubeX"
    override val sourceType: SourceType = SourceType.YOUTUBE_MUSIC
    override val isEnabled: Boolean
        get() = enabledProvider()

    init {
        UnifiedStreamResolverRegistry.registerResolver(
            UnifiedStreamResolverRegistry.SCHEME_INNERTUBEX
        ) { uri ->
            val videoId = uri.lastPathSegment ?: return@registerResolver null
            clientManager.extractAudioStream(videoId, qualityProvider())
        }
    }

    override suspend fun search(
        query: String,
        filter: SearchFilter,
        limit: Int
    ): UnifiedSearchResult {
        if (!isEnabled || query.isBlank()) return UnifiedSearchResult()
        val raw = clientManager.searchRaw(query, filter)
            ?: return UnifiedSearchResult(
                providerErrors = mapOf(displayName to "Unable to reach InnertubeX")
            )
        return InnertubeXJsonParser.parseSearchResponse(raw, filter, limit)
    }

    override suspend fun resolveStream(
        track: UnifiedTrack,
        quality: StreamQuality
    ): ResolvedStream? {
        if (!isEnabled) return null
        val videoId = track.playbackUri.substringAfterLast("/").takeIf { it.isNotBlank() } ?: return null
        return clientManager.extractAudioStream(videoId, quality)
    }

    override suspend fun getHomeSections(): List<UnifiedHomeSection> {
        if (!isEnabled) return emptyList()
        val raw = clientManager.browseRaw("FEmusic_home") ?: return emptyList()
        return InnertubeXJsonParser.parseHomeBrowseResponse(raw)
    }

    override suspend fun getRelatedTracks(track: UnifiedTrack, limit: Int): List<UnifiedTrack> {
        if (!isEnabled) return emptyList()
        val videoId = track.playbackUri.substringAfterLast("/").takeIf { it.isNotBlank() } ?: return emptyList()
        val raw = clientManager.nextRaw(videoId) ?: return emptyList()
        return InnertubeXJsonParser.parseNextRelatedTracks(raw, limit)
    }

    suspend fun getSearchSuggestions(query: String): List<String> {
        if (!isEnabled || query.isBlank()) return emptyList()
        val raw = clientManager.getSearchSuggestionsRaw(query) ?: return emptyList()
        return InnertubeXJsonParser.parseSearchSuggestions(raw)
    }
}
