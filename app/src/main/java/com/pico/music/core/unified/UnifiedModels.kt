/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.core.unified

import chromahub.rhythm.app.core.domain.model.PlayableItem
import chromahub.rhythm.app.core.domain.model.SourceType

/**
 * Provenance of a music item in PICO Music.
 * Distinguishes local storage, InnertubeX online streaming, BitChord-compatible extensions,
 * and optional self-hosted servers.
 */
sealed class MusicSource(val badgeLabel: String, val sourceType: SourceType) {
    data object Local : MusicSource("Local", SourceType.LOCAL)
    data object InnertubeX : MusicSource("InnertubeX", SourceType.YOUTUBE_MUSIC)
    data class BitChordExtension(
        val addonId: String,
        val addonName: String
    ) : MusicSource(addonName, SourceType.BITCHORD_EXTENSION)

    data class SelfHosted(
        val label: String,
        val type: SourceType
    ) : MusicSource(label, type)
}

enum class SearchFilter {
    ALL,
    SONGS,
    ARTISTS,
    ALBUMS,
    PLAYLISTS
}

enum class StreamQuality {
    LOW,
    NORMAL,
    HIGH,
    LOSSLESS,
    HI_RES
}

enum class ManifestType {
    PROGRESSIVE,
    HLS,
    DASH
}

/**
 * Ephemeral stream resolution result. Never persisted to disk or Room.
 */
data class ResolvedStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val manifestType: ManifestType = ManifestType.PROGRESSIVE,
    val bitrateKbps: Int? = null,
    val codec: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val expiresAtMs: Long? = null
)

data class UnifiedTrack(
    override val id: String,
    override val title: String,
    override val artist: String,
    override val album: String,
    override val duration: Long,
    override val artworkUri: String? = null,
    @get:JvmName("getTrackPlaybackUri")
    val playbackUri: String,
    val musicSource: MusicSource,
    val albumId: String? = null,
    val artistId: String? = null,
    val qualityLabel: String? = null,
    val isExplicit: Boolean = false,
    val externalUrl: String? = null,
    val trackNumber: Int? = null,
    val year: Int? = null
) : PlayableItem {
    override val sourceType: SourceType
        get() = musicSource.sourceType

    override fun getPlaybackUri(): String = playbackUri
}

data class UnifiedAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUri: String? = null,
    val year: Int? = null,
    val trackCount: Int = 0,
    val musicSource: MusicSource,
    val tracks: List<UnifiedTrack> = emptyList()
)

data class UnifiedArtist(
    val id: String,
    val name: String,
    val artworkUri: String? = null,
    val subtitle: String? = null,
    val musicSource: MusicSource
)

data class UnifiedPlaylist(
    val id: String,
    val title: String,
    val author: String? = null,
    val artworkUri: String? = null,
    val trackCount: Int = 0,
    val musicSource: MusicSource,
    val tracks: List<UnifiedTrack> = emptyList()
)

data class UnifiedSearchResult(
    val tracks: List<UnifiedTrack> = emptyList(),
    val albums: List<UnifiedAlbum> = emptyList(),
    val artists: List<UnifiedArtist> = emptyList(),
    val playlists: List<UnifiedPlaylist> = emptyList(),
    val providerErrors: Map<String, String> = emptyMap()
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

data class UnifiedHomeSection(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val musicSource: MusicSource,
    val tracks: List<UnifiedTrack> = emptyList(),
    val albums: List<UnifiedAlbum> = emptyList(),
    val playlists: List<UnifiedPlaylist> = emptyList()
)

interface MusicProvider {
    val providerId: String
    val displayName: String
    val sourceType: SourceType
    val isEnabled: Boolean

    suspend fun search(
        query: String,
        filter: SearchFilter = SearchFilter.ALL,
        limit: Int = 20
    ): UnifiedSearchResult

    suspend fun resolveStream(
        track: UnifiedTrack,
        quality: StreamQuality = StreamQuality.HIGH
    ): ResolvedStream? = null

    suspend fun getHomeSections(): List<UnifiedHomeSection> = emptyList()

    suspend fun getRelatedTracks(track: UnifiedTrack, limit: Int = 15): List<UnifiedTrack> = emptyList()
}
