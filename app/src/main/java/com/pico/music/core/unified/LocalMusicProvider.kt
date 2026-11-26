/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.core.unified

import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.features.local.data.repository.MusicRepository

/**
 * Adapts the existing local [MusicRepository] into the unified [MusicProvider] contract.
 */
class LocalMusicProvider(
    private val localRepository: MusicRepository
) : MusicProvider {

    override val providerId: String = "local"
    override val displayName: String = "Local Library"
    override val sourceType: SourceType = SourceType.LOCAL
    override val isEnabled: Boolean = true

    override suspend fun search(
        query: String,
        filter: SearchFilter,
        limit: Int
    ): UnifiedSearchResult {
        if (query.isBlank()) return UnifiedSearchResult()
        val normalizedQuery = query.trim()

        val tracks = if (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) {
            localRepository.loadSongs()
                .asSequence()
                .filter {
                    it.title.contains(normalizedQuery, ignoreCase = true) ||
                        it.artist.contains(normalizedQuery, ignoreCase = true) ||
                        it.album.contains(normalizedQuery, ignoreCase = true)
                }
                .take(limit)
                .map { song ->
                    UnifiedTrack(
                        id = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        duration = song.duration,
                        artworkUri = song.artworkUri?.toString(),
                        playbackUri = song.uri.toString(),
                        musicSource = MusicSource.Local,
                        albumId = song.albumId.takeIf { it.isNotBlank() },
                        qualityLabel = song.codec ?: "Local",
                        trackNumber = song.trackNumber.takeIf { it > 0 },
                        year = song.year.takeIf { it > 0 }
                    )
                }
                .toList()
        } else emptyList()

        val albums = if (filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) {
            localRepository.loadAlbums()
                .asSequence()
                .filter {
                    it.title.contains(normalizedQuery, ignoreCase = true) ||
                        it.artist.contains(normalizedQuery, ignoreCase = true)
                }
                .take(limit)
                .map { album ->
                    UnifiedAlbum(
                        id = album.id,
                        title = album.title,
                        artist = album.artist,
                        artworkUri = album.artworkUri?.toString(),
                        year = album.year.takeIf { it > 0 },
                        trackCount = album.numberOfSongs,
                        musicSource = MusicSource.Local
                    )
                }
                .toList()
        } else emptyList()

        val artists = if (filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) {
            localRepository.loadArtists()
                .asSequence()
                .filter { it.name.contains(normalizedQuery, ignoreCase = true) }
                .take(limit)
                .map { artist ->
                    UnifiedArtist(
                        id = artist.id,
                        name = artist.name,
                        artworkUri = artist.artworkUri?.toString(),
                        subtitle = "${artist.numberOfTracks} songs",
                        musicSource = MusicSource.Local
                    )
                }
                .toList()
        } else emptyList()

        return UnifiedSearchResult(
            tracks = tracks,
            albums = albums,
            artists = artists
        )
    }
}
