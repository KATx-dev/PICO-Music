/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.innertube

import com.pico.music.core.unified.MusicSource
import com.pico.music.core.unified.SearchFilter
import com.pico.music.core.unified.UnifiedAlbum
import com.pico.music.core.unified.UnifiedArtist
import com.pico.music.core.unified.UnifiedHomeSection
import com.pico.music.core.unified.UnifiedPlaylist
import com.pico.music.core.unified.UnifiedSearchResult
import com.pico.music.core.unified.UnifiedTrack
import com.pico.music.core.unified.UnifiedStreamResolverRegistry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses real YouTube Music InnerTube JSON payloads (`search`, `browse`, `next`, suggestions)
 * into PICO Music's unified domain models. Never fabricates placeholder results.
 */
object InnertubeXJsonParser {

    fun parseSearchResponse(
        jsonBody: String,
        filter: SearchFilter,
        limit: Int = 20
    ): UnifiedSearchResult {
        if (jsonBody.isBlank()) return UnifiedSearchResult()
        val root = runCatching { JSONObject(jsonBody) }.getOrNull() ?: return UnifiedSearchResult()

        val tracks = mutableListOf<UnifiedTrack>()
        val albums = mutableListOf<UnifiedAlbum>()
        val artists = mutableListOf<UnifiedArtist>()
        val playlists = mutableListOf<UnifiedPlaylist>()

        val shelves = collectMusicShelves(root)
        for (shelf in shelves) {
            val contents = shelf.optJSONArray("contents") ?: continue
            for (i in 0 until contents.length()) {
                val item = contents.optJSONObject(i) ?: continue
                val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                classifyAndAddListItem(renderer, filter, tracks, albums, artists, playlists, limit)
            }
        }

        // Also scan top-level two-row or responsive items if shelf wrapper differs
        if (tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()) {
            val renderers = mutableListOf<JSONObject>()
            collectObjectsByKey(root, "musicResponsiveListItemRenderer", renderers)
            for (renderer in renderers) {
                classifyAndAddListItem(renderer, filter, tracks, albums, artists, playlists, limit)
            }
        }

        return UnifiedSearchResult(
            tracks = tracks.take(limit),
            albums = albums.take(limit),
            artists = artists.take(limit),
            playlists = playlists.take(limit)
        )
    }

    fun parseHomeBrowseResponse(
        jsonBody: String,
        limitPerSection: Int = 12
    ): List<UnifiedHomeSection> {
        if (jsonBody.isBlank()) return emptyList()
        val root = runCatching { JSONObject(jsonBody) }.getOrNull() ?: return emptyList()

        val sections = mutableListOf<UnifiedHomeSection>()
        val carouselShelves = mutableListOf<JSONObject>()
        collectObjectsByKey(root, "musicCarouselShelfRenderer", carouselShelves)

        carouselShelves.forEachIndexed { index, shelf ->
            val title = extractRunsText(
                shelf.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?.optJSONObject("title")
            ).ifBlank { "Discover" }

            val tracks = mutableListOf<UnifiedTrack>()
            val albums = mutableListOf<UnifiedAlbum>()
            val playlists = mutableListOf<UnifiedPlaylist>()

            val contents = shelf.optJSONArray("contents") ?: JSONArray()
            for (i in 0 until contents.length()) {
                val entry = contents.optJSONObject(i) ?: continue
                entry.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                    parseTrackFromListItem(r)?.let { if (tracks.size < limitPerSection) tracks.add(it) }
                }
                entry.optJSONObject("musicTwoRowItemRenderer")?.let { r ->
                    parseTwoRowItem(r, tracks, albums, playlists, limitPerSection)
                }
            }

            if (tracks.isNotEmpty() || albums.isNotEmpty() || playlists.isNotEmpty()) {
                sections.add(
                    UnifiedHomeSection(
                        id = "yt_home_$index",
                        title = title,
                        subtitle = "InnertubeX",
                        musicSource = MusicSource.InnertubeX,
                        tracks = tracks,
                        albums = albums,
                        playlists = playlists
                    )
                )
            }
        }

        return sections
    }

    fun parseNextRelatedTracks(jsonBody: String, limit: Int = 15): List<UnifiedTrack> {
        if (jsonBody.isBlank()) return emptyList()
        val root = runCatching { JSONObject(jsonBody) }.getOrNull() ?: return emptyList()
        val panelRenderers = mutableListOf<JSONObject>()
        collectObjectsByKey(root, "playlistPanelVideoRenderer", panelRenderers)

        return panelRenderers.mapNotNull { renderer ->
            val videoId = renderer.optString("videoId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = extractRunsText(renderer.optJSONObject("title")).ifBlank { return@mapNotNull null }
            val artist = extractRunsText(renderer.optJSONObject("shortBylineText"))
                .ifBlank { extractRunsText(renderer.optJSONObject("longBylineText")) }
                .ifBlank { "Unknown Artist" }
            val durationMs = parseDurationTextToMs(extractRunsText(renderer.optJSONObject("lengthText")))
            val thumb = extractBestThumbnail(renderer.optJSONObject("thumbnail"))

            UnifiedTrack(
                id = "yt_$videoId",
                title = title,
                artist = artist,
                album = "",
                duration = durationMs,
                artworkUri = thumb,
                playbackUri = UnifiedStreamResolverRegistry.buildInnertubeUri(videoId),
                musicSource = MusicSource.InnertubeX,
                qualityLabel = "InnertubeX",
                externalUrl = "https://music.youtube.com/watch?v=$videoId"
            )
        }.distinctBy { it.playbackUri }.take(limit)
    }

    fun parseSearchSuggestions(jsonBody: String): List<String> {
        if (jsonBody.isBlank()) return emptyList()
        val root = runCatching { JSONObject(jsonBody) }.getOrNull() ?: return emptyList()
        val renderers = mutableListOf<JSONObject>()
        collectObjectsByKey(root, "searchSuggestionRenderer", renderers)
        return renderers.mapNotNull { r ->
            extractRunsText(r.optJSONObject("suggestion")).takeIf { it.isNotBlank() }
        }.distinct()
    }

    private fun classifyAndAddListItem(
        renderer: JSONObject,
        filter: SearchFilter,
        tracks: MutableList<UnifiedTrack>,
        albums: MutableList<UnifiedAlbum>,
        artists: MutableList<UnifiedArtist>,
        playlists: MutableList<UnifiedPlaylist>,
        limit: Int
    ) {
        val navEndpoint = renderer.optJSONObject("navigationEndpoint")
        val watchVideoId = navEndpoint?.optJSONObject("watchEndpoint")?.optString("videoId")
        val browseEndpoint = navEndpoint?.optJSONObject("browseEndpoint")
        val browseId = browseEndpoint?.optString("browseId").orEmpty()
        val pageType = browseEndpoint
            ?.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.optString("pageType")
            .orEmpty()

        when {
            !watchVideoId.isNullOrBlank() || filter == SearchFilter.SONGS -> {
                if (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) {
                    parseTrackFromListItem(renderer)?.let { if (tracks.size < limit) tracks.add(it) }
                }
            }
            pageType.contains("ALBUM") || browseId.startsWith("MPREb_") -> {
                if (filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) {
                    parseAlbumFromListItem(renderer, browseId)?.let { if (albums.size < limit) albums.add(it) }
                }
            }
            pageType.contains("ARTIST") || browseId.startsWith("UC") -> {
                if (filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) {
                    parseArtistFromListItem(renderer, browseId)?.let { if (artists.size < limit) artists.add(it) }
                }
            }
            pageType.contains("PLAYLIST") || browseId.startsWith("VL") || browseId.startsWith("PL") -> {
                if (filter == SearchFilter.ALL || filter == SearchFilter.PLAYLISTS) {
                    parsePlaylistFromListItem(renderer, browseId)?.let { if (playlists.size < limit) playlists.add(it) }
                }
            }
            else -> {
                if (filter == SearchFilter.ALL) {
                    parseTrackFromListItem(renderer)?.let { if (tracks.size < limit) tracks.add(it) }
                }
            }
        }
    }

    private fun parseTrackFromListItem(renderer: JSONObject): UnifiedTrack? {
        val videoId = renderer.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
            ?: renderer.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
            ?: return null

        val columns = renderer.optJSONArray("flexColumns") ?: return null
        val title = getFlexColumnText(columns, 0).ifBlank { return null }
        val subtitleRuns = getFlexColumnRuns(columns, 1)
        val (artist, album, durationMs) = parseSubtitleParts(subtitleRuns)
        val thumb = extractBestThumbnail(
            renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
        )

        return UnifiedTrack(
            id = "yt_$videoId",
            title = title,
            artist = artist,
            album = album,
            duration = durationMs,
            artworkUri = thumb,
            playbackUri = UnifiedStreamResolverRegistry.buildInnertubeUri(videoId),
            musicSource = MusicSource.InnertubeX,
            qualityLabel = "InnertubeX",
            externalUrl = "https://music.youtube.com/watch?v=$videoId"
        )
    }

    private fun parseAlbumFromListItem(renderer: JSONObject, browseId: String): UnifiedAlbum? {
        if (browseId.isBlank()) return null
        val columns = renderer.optJSONArray("flexColumns") ?: return null
        val title = getFlexColumnText(columns, 0).ifBlank { return null }
        val subtitleRuns = getFlexColumnRuns(columns, 1)
        val artist = subtitleRuns.firstOrNull { it != "Album" && it != "Single" && it != "EP" && it != " • " }
            ?: "Unknown Artist"
        val year = subtitleRuns.firstNotNullOfOrNull { it.toIntOrNull() }
        val thumb = extractBestThumbnail(
            renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
        )

        return UnifiedAlbum(
            id = "yt_album_$browseId",
            title = title,
            artist = artist,
            artworkUri = thumb,
            year = year,
            musicSource = MusicSource.InnertubeX
        )
    }

    private fun parseArtistFromListItem(renderer: JSONObject, browseId: String): UnifiedArtist? {
        if (browseId.isBlank()) return null
        val columns = renderer.optJSONArray("flexColumns") ?: return null
        val name = getFlexColumnText(columns, 0).ifBlank { return null }
        val subtitle = getFlexColumnText(columns, 1).takeIf { it.isNotBlank() }
        val thumb = extractBestThumbnail(
            renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
        )

        return UnifiedArtist(
            id = "yt_artist_$browseId",
            name = name,
            artworkUri = thumb,
            subtitle = subtitle,
            musicSource = MusicSource.InnertubeX
        )
    }

    private fun parsePlaylistFromListItem(renderer: JSONObject, browseId: String): UnifiedPlaylist? {
        if (browseId.isBlank()) return null
        val columns = renderer.optJSONArray("flexColumns") ?: return null
        val title = getFlexColumnText(columns, 0).ifBlank { return null }
        val subtitle = getFlexColumnText(columns, 1).takeIf { it.isNotBlank() }
        val thumb = extractBestThumbnail(
            renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
        )

        return UnifiedPlaylist(
            id = "yt_pl_$browseId",
            title = title,
            author = subtitle,
            artworkUri = thumb,
            musicSource = MusicSource.InnertubeX
        )
    }

    private fun parseTwoRowItem(
        renderer: JSONObject,
        tracks: MutableList<UnifiedTrack>,
        albums: MutableList<UnifiedAlbum>,
        playlists: MutableList<UnifiedPlaylist>,
        limit: Int
    ) {
        val title = extractRunsText(renderer.optJSONObject("title")).ifBlank { return }
        val subtitle = extractRunsText(renderer.optJSONObject("subtitle"))
        val thumb = extractBestThumbnail(
            renderer.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
        )
        val navEndpoint = renderer.optJSONObject("navigationEndpoint")
        val watchVideoId = navEndpoint?.optJSONObject("watchEndpoint")?.optString("videoId")
        val browseId = navEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()

        when {
            !watchVideoId.isNullOrBlank() -> {
                if (tracks.size < limit) {
                    tracks.add(
                        UnifiedTrack(
                            id = "yt_$watchVideoId",
                            title = title,
                            artist = subtitle.substringBefore(" • ").ifBlank { "Unknown Artist" },
                            album = "",
                            duration = 0L,
                            artworkUri = thumb,
                            playbackUri = UnifiedStreamResolverRegistry.buildInnertubeUri(watchVideoId),
                            musicSource = MusicSource.InnertubeX,
                            qualityLabel = "InnertubeX",
                            externalUrl = "https://music.youtube.com/watch?v=$watchVideoId"
                        )
                    )
                }
            }
            browseId.startsWith("MPREb_") -> {
                if (albums.size < limit) {
                    albums.add(
                        UnifiedAlbum(
                            id = "yt_album_$browseId",
                            title = title,
                            artist = subtitle.substringAfter(" • ", subtitle).ifBlank { "Unknown Artist" },
                            artworkUri = thumb,
                            musicSource = MusicSource.InnertubeX
                        )
                    )
                }
            }
            browseId.startsWith("VL") || browseId.startsWith("PL") -> {
                if (playlists.size < limit) {
                    playlists.add(
                        UnifiedPlaylist(
                            id = "yt_pl_$browseId",
                            title = title,
                            author = subtitle,
                            artworkUri = thumb,
                            musicSource = MusicSource.InnertubeX
                        )
                    )
                }
            }
        }
    }

    private fun collectMusicShelves(root: JSONObject): List<JSONObject> {
        val shelves = mutableListOf<JSONObject>()
        collectObjectsByKey(root, "musicShelfRenderer", shelves)
        return shelves
    }

    private fun collectObjectsByKey(node: Any?, targetKey: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                if (node.has(targetKey)) {
                    node.optJSONObject(targetKey)?.let { out.add(it) }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key != targetKey) {
                        collectObjectsByKey(node.opt(key), targetKey, out)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectObjectsByKey(node.opt(i), targetKey, out)
                }
            }
        }
    }

    private fun getFlexColumnText(columns: JSONArray, index: Int): String {
        return getFlexColumnRuns(columns, index).joinToString("")
    }

    private fun getFlexColumnRuns(columns: JSONArray, index: Int): List<String> {
        val col = columns.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?: return emptyList()
        val runs = col.optJSONArray("runs") ?: return emptyList()
        return buildList {
            for (i in 0 until runs.length()) {
                val text = runs.optJSONObject(i)?.optString("text").orEmpty()
                if (text.isNotEmpty()) add(text)
            }
        }
    }

    private fun extractRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString()
    }

    private fun parseSubtitleParts(runs: List<String>): Triple<String, String, Long> {
        val clean = runs.map { it.trim() }.filter { it.isNotEmpty() && it != "•" }
        val filtered = clean.filterNot { it.equals("Song", true) || it.equals("Video", true) }
        val durationToken = filtered.lastOrNull { it.matches(Regex("\\d+:\\d{2}(:\\d{2})?")) }
        val durationMs = durationToken?.let { parseDurationTextToMs(it) } ?: 0L
        val withoutDuration = if (durationToken != null) filtered.dropLast(1) else filtered
        val artist = withoutDuration.getOrNull(0).orEmpty().ifBlank { "Unknown Artist" }
        val album = withoutDuration.getOrNull(1).orEmpty()
        return Triple(artist, album, durationMs)
    }

    internal fun parseDurationTextToMs(text: String): Long {
        val parts = text.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            else -> 0L
        }
    }

    private fun extractBestThumbnail(thumbnailObj: JSONObject?): String? {
        val array = thumbnailObj?.optJSONArray("thumbnails") ?: return null
        var bestUrl: String? = null
        var bestWidth = -1
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = item.optString("url")
            val width = item.optInt("width", 0)
            if (url.isNotBlank() && width >= bestWidth) {
                bestWidth = width
                bestUrl = if (url.startsWith("//")) "https:$url" else url
            }
        }
        return bestUrl
    }
}
