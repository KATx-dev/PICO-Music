/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.extensions

import chromahub.rhythm.app.core.domain.model.SourceType
import com.pico.music.core.unified.MusicProvider
import com.pico.music.core.unified.ResolvedStream
import com.pico.music.core.unified.SearchFilter
import com.pico.music.core.unified.StreamQuality
import com.pico.music.core.unified.UnifiedAlbum
import com.pico.music.core.unified.UnifiedArtist
import com.pico.music.core.unified.UnifiedPlaylist
import com.pico.music.core.unified.UnifiedSearchResult
import com.pico.music.core.unified.UnifiedTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Aggregates all enabled BitChord-compatible extensions from [ExtensionRegistry]
 * into the unified [MusicProvider] interface.
 */
class ExtensionMusicProvider(
    private val registry: ExtensionRegistry,
    private val client: BitChordAddonClient = BitChordAddonClient(),
    private val qualityProvider: () -> StreamQuality = { StreamQuality.HIGH }
) : MusicProvider {

    override val providerId: String = "bitchord_extensions"
    override val displayName: String = "Extensions"
    override val sourceType: SourceType = SourceType.BITCHORD_EXTENSION
    override val isEnabled: Boolean
        get() = registry.extensions.value.any { it.enabled }

    override suspend fun search(
        query: String,
        filter: SearchFilter,
        limit: Int
    ): UnifiedSearchResult = coroutineScope {
        val enabledExtensions = registry.extensions.value.filter { it.enabled }
        if (enabledExtensions.isEmpty() || query.isBlank()) return@coroutineScope UnifiedSearchResult()

        val perAddon = enabledExtensions.map { ext ->
            async {
                ext to runCatching {
                    client.search(ext, query, filter, qualityProvider(), limit)
                }
            }
        }.awaitAll()

        val tracks = mutableListOf<UnifiedTrack>()
        val albums = mutableListOf<UnifiedAlbum>()
        val artists = mutableListOf<UnifiedArtist>()
        val playlists = mutableListOf<UnifiedPlaylist>()
        val errors = mutableMapOf<String, String>()

        for ((ext, outcome) in perAddon) {
            outcome.onSuccess { res ->
                tracks.addAll(res.tracks)
                albums.addAll(res.albums)
                artists.addAll(res.artists)
                playlists.addAll(res.playlists)
            }.onFailure { err ->
                errors[ext.manifest.name] = err.message ?: "Extension error"
            }
        }

        UnifiedSearchResult(
            tracks = tracks,
            albums = albums,
            artists = artists,
            playlists = playlists,
            providerErrors = errors
        )
    }

    override suspend fun resolveStream(
        track: UnifiedTrack,
        quality: StreamQuality
    ): ResolvedStream? {
        val addonId = (track.musicSource as? com.pico.music.core.unified.MusicSource.BitChordExtension)?.addonId
            ?: return null
        val ext = registry.extensions.value.firstOrNull { it.manifest.id == addonId && it.enabled }
            ?: return null
        val rawTrackId = track.playbackUri.substringAfterLast("/")
        return client.resolveStream(ext, rawTrackId, quality)
    }
}
