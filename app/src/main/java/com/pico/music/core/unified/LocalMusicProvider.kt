/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.core.unified

import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.core.domain.repository.MusicRepository

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

        val tracks = if (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) {
            localRepository.searchSongs(query).take(limit).map { item ->
                UnifiedTrack(
                    id = item.id,
                    title = item.title,
                    artist = item.artist,
                    album = item.album,
                    duration = item.duration,
                    artworkUri = item.artworkUri,
                    playbackUri = item.getPlaybackUri(),
                    musicSource = MusicSource.Local,
                    qualityLabel = "Local"
                )
            }
        } else emptyList()

        val albums = if (filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) {
            localRepository.searchAlbums(query).take(limit).map { album ->
                UnifiedAlbum(
                    id = album.id,
                    title = album.title,
                    artist = album.artist,
                    artworkUri = album.artworkUri,
                    year = album.year,
                    trackCount = album.songCount,
                    musicSource = MusicSource.Local
                )
            }
        } else emptyList()

        val artists = if (filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) {
            localRepository.searchArtists(query).take(limit).map { artist ->
                UnifiedArtist(
                    id = artist.id,
                    name = artist.name,
                    artworkUri = artist.artworkUri,
                    subtitle = "${artist.songCount} songs",
                    musicSource = MusicSource.Local
                )
            }
        } else emptyList()

        val playlists = if (filter == SearchFilter.ALL || filter == SearchFilter.PLAYLISTS) {
            localRepository.searchPlaylists(query).take(limit).map { playlist ->
                UnifiedPlaylist(
                    id = playlist.id,
                    title = playlist.name,
                    artworkUri = playlist.artworkUri,
                    trackCount = playlist.songCount,
                    musicSource = MusicSource.Local
                )
            }
        } else emptyList()

        return UnifiedSearchResult(
            tracks = tracks,
            albums = albums,
            artists = artists,
            playlists = playlists
        )
    }
}
