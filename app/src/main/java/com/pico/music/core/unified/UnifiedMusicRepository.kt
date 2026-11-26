/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.core.unified

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Coordinates Local, InnertubeX, and BitChord-compatible Extension providers
 * under a single unified search, discovery, and stream-resolution facade.
 */
class UnifiedMusicRepository(
    providers: List<MusicProvider> = emptyList()
) {
    private val registeredProviders = CopyOnWriteArrayList<MusicProvider>(providers)

    fun registerProvider(provider: MusicProvider) {
        if (registeredProviders.none { it.providerId == provider.providerId }) {
            registeredProviders.add(provider)
        }
    }

    fun getProviders(): List<MusicProvider> = registeredProviders.toList()

    suspend fun searchAll(
        query: String,
        filter: SearchFilter = SearchFilter.ALL,
        limitPerProvider: Int = 20,
        enabledProviderIds: Set<String>? = null
    ): UnifiedSearchResult = coroutineScope {
        if (query.isBlank()) return@coroutineScope UnifiedSearchResult()

        val activeProviders = registeredProviders.filter { provider ->
            provider.isEnabled && (enabledProviderIds == null || provider.providerId in enabledProviderIds)
        }

        val perProviderResults = activeProviders.map { provider ->
            async {
                provider to runCatching { provider.search(query, filter, limitPerProvider) }
            }
        }.awaitAll()

        val tracks = mutableListOf<UnifiedTrack>()
        val albums = mutableListOf<UnifiedAlbum>()
        val artists = mutableListOf<UnifiedArtist>()
        val playlists = mutableListOf<UnifiedPlaylist>()
        val errors = mutableMapOf<String, String>()

        for ((provider, outcome) in perProviderResults) {
            outcome.onSuccess { res ->
                tracks.addAll(res.tracks)
                albums.addAll(res.albums)
                artists.addAll(res.artists)
                playlists.addAll(res.playlists)
                errors.putAll(res.providerErrors)
            }.onFailure { err ->
                errors[provider.displayName] = err.message ?: "Provider search failed"
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

    suspend fun getHomeSections(): List<UnifiedHomeSection> = coroutineScope {
        registeredProviders
            .filter { it.isEnabled }
            .map { provider ->
                async {
                    runCatching { provider.getHomeSections() }.getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
    }
}
