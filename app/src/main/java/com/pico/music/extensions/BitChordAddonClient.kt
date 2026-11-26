/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.extensions

import chromahub.rhythm.app.BuildConfig
import com.pico.music.core.unified.ManifestType
import com.pico.music.core.unified.MusicSource
import com.pico.music.core.unified.ResolvedStream
import com.pico.music.core.unified.SearchFilter
import com.pico.music.core.unified.StreamQuality
import com.pico.music.core.unified.UnifiedAlbum
import com.pico.music.core.unified.UnifiedArtist
import com.pico.music.core.unified.UnifiedPlaylist
import com.pico.music.core.unified.UnifiedSearchResult
import com.pico.music.core.unified.UnifiedStreamResolverRegistry
import com.pico.music.core.unified.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for BitChord-compatible HTTP JSON Addon Protocol v1 extensions.
 *
 * Security & attribution guarantees:
 * - Requires HTTPS for remote hosts (HTTP permitted only on loopback/LAN `.local`).
 * - Identifies honestly as `PICO-Music/<version>` (never spoofs BitChord attestation headers).
 * - Rejects `encrypted == true` streams and surfaces `bitchordAuth` / `401` / `429` cleanly.
 */
class BitChordAddonClient(
    private val okHttpClient: OkHttpClient = defaultClient()
) {
    companion object {
        private val USER_AGENT = "PICO-Music/${BuildConfig.VERSION_NAME} (Android)"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        fun normalizeBaseUrl(rawUrl: String): String {
            var trimmed = rawUrl.trim()
            if (trimmed.endsWith("/manifest.json", ignoreCase = true)) {
                trimmed = trimmed.removeSuffix("/manifest.json")
            }
            return trimmed.trimEnd('/')
        }

        fun isAllowedAddonUrl(rawUrl: String): Boolean {
            val url = rawUrl.toHttpUrlOrNull() ?: return false
            if (url.scheme == "https") return true
            if (url.scheme == "http") {
                val host = url.host.lowercase()
                return host == "localhost" ||
                    host == "127.0.0.1" ||
                    host == "10.0.2.2" ||
                    host == "::1" ||
                    host.endsWith(".local")
            }
            return false
        }
    }

    open class AddonException(message: String) : IOException(message)
    class InsecureUrlException(url: String) :
        AddonException("Extension URL must use HTTPS (HTTP allowed only for localhost/LAN): $url")

    class BitChordAuthRequiredException(message: String) : AddonException(message)
    class RateLimitedException(val retryAfterSeconds: Long?) :
        AddonException("Extension rate-limited (429)${retryAfterSeconds?.let { ", retry after ${it}s" } ?: ""}")

    class EncryptedStreamUnsupportedException :
        AddonException("Extension returned an encrypted/DRM stream which PICO Music does not support")

    suspend fun fetchManifest(rawBaseUrl: String): BitChordAddonManifest = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(rawBaseUrl)
        if (!isAllowedAddonUrl(baseUrl)) throw InsecureUrlException(baseUrl)

        val manifestUrl = "$baseUrl/manifest.json"
        val request = buildRequest(manifestUrl)
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw BitChordAuthRequiredException("Extension requires BitChord authentication (401)")
            }
            if (response.code == 429) {
                throw RateLimitedException(response.header("Retry-After")?.toLongOrNull())
            }
            if (response.code == 404) {
                // Fallback probe to /search?q=music if manifest.json is omitted
                probeFallbackSearch(baseUrl)
                val hostId = baseUrl.toHttpUrlOrNull()?.host ?: "addon"
                return@use BitChordAddonManifest(
                    id = hostId,
                    name = hostId,
                    version = "1.0.0",
                    resources = listOf("search", "stream")
                )
            }
            if (!response.isSuccessful) {
                throw AddonException("Manifest request failed with HTTP ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            if (json.optBoolean("bitchordAuth", false)) {
                throw BitChordAuthRequiredException(
                    "This addon requires BitChord client authentication (bitchordAuth=true)"
                )
            }

            val settings = mutableListOf<BitChordAddonSetting>()
            val settingsArray = json.optJSONArray("settings") ?: JSONArray()
            for (i in 0 until settingsArray.length()) {
                val obj = settingsArray.optJSONObject(i) ?: continue
                val key = obj.optString("key").takeIf { it.isNotBlank() } ?: continue
                settings.add(
                    BitChordAddonSetting(
                        key = key,
                        label = obj.optString("label", key),
                        type = obj.optString("type", "text"),
                        defaultValue = obj.optString("default", ""),
                        required = obj.optBoolean("required", false)
                    )
                )
            }

            val resources = mutableListOf<String>()
            val resArray = json.optJSONArray("resources") ?: JSONArray()
            for (i in 0 until resArray.length()) {
                resArray.optString(i).takeIf { it.isNotBlank() }?.let { resources.add(it) }
            }

            val fallbackId = baseUrl.toHttpUrlOrNull()?.host ?: "addon"
            BitChordAddonManifest(
                id = json.optString("id").ifBlank { fallbackId },
                name = json.optString("name").ifBlank { fallbackId },
                version = json.optString("version").ifBlank { "1.0.0" },
                description = json.optString("description", ""),
                resources = resources,
                settings = settings,
                allowDownloads = json.optBoolean("allowDownloads", false),
                checkValidLossless = json.optBoolean("checkValidLossless", false),
                bitchordAuth = json.optBoolean("bitchordAuth", false)
            )
        }
    }

    private fun probeFallbackSearch(baseUrl: String) {
        val probeUrl = "$baseUrl/search?q=music"
        okHttpClient.newCall(buildRequest(probeUrl)).execute().use { response ->
            if (!response.isSuccessful) {
                throw AddonException("Addon probe failed with HTTP ${response.code}")
            }
        }
    }

    suspend fun search(
        extension: InstalledExtension,
        query: String,
        filter: SearchFilter,
        quality: StreamQuality = StreamQuality.HIGH,
        limit: Int = 20
    ): UnifiedSearchResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(extension.baseUrl)
        if (!isAllowedAddonUrl(baseUrl)) throw InsecureUrlException(baseUrl)

        val httpUrl = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment("search")
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("quality", mapQualityTier(quality))
            ?.apply {
                extension.customSettings.forEach { (k, v) ->
                    if (v.isNotBlank()) addQueryParameter(k, v)
                }
            }
            ?.build()
            ?: throw InsecureUrlException(baseUrl)

        val source = MusicSource.BitChordExtension(extension.manifest.id, extension.manifest.name)

        okHttpClient.newCall(buildRequest(httpUrl.toString())).execute().use { response ->
            handleErrorCodes(response.code, response.header("Retry-After"))
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)

            val tracks = if (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) {
                parseTracks(json.optJSONArray("tracks"), extension, source, limit)
            } else emptyList()

            val albums = if (filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) {
                parseAlbums(json.optJSONArray("albums"), source, limit)
            } else emptyList()

            val artists = if (filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) {
                parseArtists(json.optJSONArray("artists"), source, limit)
            } else emptyList()

            val playlists = if (filter == SearchFilter.ALL || filter == SearchFilter.PLAYLISTS) {
                parsePlaylists(json.optJSONArray("playlists"), source, limit)
            } else emptyList()

            UnifiedSearchResult(
                tracks = tracks,
                albums = albums,
                artists = artists,
                playlists = playlists
            )
        }
    }

    suspend fun resolveStream(
        extension: InstalledExtension,
        trackId: String,
        quality: StreamQuality = StreamQuality.HIGH
    ): ResolvedStream = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(extension.baseUrl)
        if (!isAllowedAddonUrl(baseUrl)) throw InsecureUrlException(baseUrl)

        val httpUrl = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment("stream")
            ?.addPathSegment(trackId)
            ?.addQueryParameter("quality", mapQualityTier(quality))
            ?.apply {
                extension.customSettings.forEach { (k, v) ->
                    if (v.isNotBlank()) addQueryParameter(k, v)
                }
            }
            ?.build()
            ?: throw InsecureUrlException(baseUrl)

        okHttpClient.newCall(buildRequest(httpUrl.toString())).execute().use { response ->
            handleErrorCodes(response.code, response.header("Retry-After"))
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)

            val errorMsg = json.optString("error").takeIf { it.isNotBlank() }
            if (errorMsg != null) {
                throw AddonException(errorMsg)
            }
            if (json.optBoolean("encrypted", false)) {
                throw EncryptedStreamUnsupportedException()
            }

            val streamUrl = json.optString("url")
            if (streamUrl.isBlank() || !isAllowedAddonUrl(streamUrl)) {
                throw AddonException("Extension returned invalid or insecure stream URL")
            }

            val manifestStr = json.optString("manifest", "none").lowercase()
            val manifestType = when (manifestStr) {
                "hls" -> ManifestType.HLS
                "dash" -> ManifestType.DASH
                else -> ManifestType.PROGRESSIVE
            }

            val headers = mutableMapOf<String, String>()
            json.optJSONObject("headers")?.let { hdrObj ->
                val keys = hdrObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = hdrObj.optString(k)
                    if (v.isNotBlank()) headers[k] = v
                }
            }

            ResolvedStream(
                url = streamUrl,
                headers = headers,
                mimeType = json.optString("mimeType").takeIf { it.isNotBlank() },
                manifestType = manifestType,
                bitrateKbps = json.optInt("bitrate", 0).takeIf { it > 0 },
                codec = json.optString("codec").takeIf { it.isNotBlank() },
                sampleRateHz = json.optInt("sampleRate", 0).takeIf { it > 0 },
                bitDepth = json.optInt("bitDepth", 0).takeIf { it > 0 }
            )
        }
    }

    private fun parseTracks(
        array: JSONArray?,
        extension: InstalledExtension,
        source: MusicSource.BitChordExtension,
        limit: Int
    ): List<UnifiedTrack> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until minOf(array.length(), limit)) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val title = obj.optString("title").takeIf { it.isNotBlank() } ?: continue
                val artist = obj.optString("artist").ifBlank { "Unknown Artist" }
                val album = obj.optString("album", "")
                val duration = obj.optLong("durationMs", obj.optLong("duration", 0L)).let { raw ->
                    if (raw in 1..10_000L && !obj.has("durationMs")) raw * 1000L else raw
                }
                val artwork = obj.optString("artworkUrl")
                    .ifBlank { obj.optString("artwork") }
                    .ifBlank { obj.optString("cover") }
                    .takeIf { it.isNotBlank() }
                val quality = obj.optString("quality").ifBlank { obj.optString("format") }.ifBlank { extension.manifest.name }

                add(
                    UnifiedTrack(
                        id = "ext_${extension.manifest.id}_$id",
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        artworkUri = artwork,
                        playbackUri = UnifiedStreamResolverRegistry.buildExtensionUri(extension.manifest.id, id),
                        musicSource = source,
                        qualityLabel = quality,
                        isExplicit = obj.optBoolean("explicit", false)
                    )
                )
            }
        }
    }

    private fun parseAlbums(
        array: JSONArray?,
        source: MusicSource.BitChordExtension,
        limit: Int
    ): List<UnifiedAlbum> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until minOf(array.length(), limit)) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val title = obj.optString("title").takeIf { it.isNotBlank() } ?: continue
                add(
                    UnifiedAlbum(
                        id = "ext_album_${source.addonId}_$id",
                        title = title,
                        artist = obj.optString("artist", "Unknown Artist"),
                        artworkUri = obj.optString("artworkUrl").ifBlank { obj.optString("cover") }.takeIf { it.isNotBlank() },
                        year = obj.optInt("year", 0).takeIf { it > 0 },
                        trackCount = obj.optInt("trackCount", 0),
                        musicSource = source
                    )
                )
            }
        }
    }

    private fun parseArtists(
        array: JSONArray?,
        source: MusicSource.BitChordExtension,
        limit: Int
    ): List<UnifiedArtist> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until minOf(array.length(), limit)) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                add(
                    UnifiedArtist(
                        id = "ext_artist_${source.addonId}_$id",
                        name = name,
                        artworkUri = obj.optString("artworkUrl").ifBlank { obj.optString("image") }.takeIf { it.isNotBlank() },
                        subtitle = source.addonName,
                        musicSource = source
                    )
                )
            }
        }
    }

    private fun parsePlaylists(
        array: JSONArray?,
        source: MusicSource.BitChordExtension,
        limit: Int
    ): List<UnifiedPlaylist> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until minOf(array.length(), limit)) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val title = obj.optString("title").takeIf { it.isNotBlank() } ?: continue
                add(
                    UnifiedPlaylist(
                        id = "ext_pl_${source.addonId}_$id",
                        title = title,
                        author = obj.optString("author").takeIf { it.isNotBlank() },
                        artworkUri = obj.optString("artworkUrl").ifBlank { obj.optString("cover") }.takeIf { it.isNotBlank() },
                        trackCount = obj.optInt("trackCount", 0),
                        musicSource = source
                    )
                )
            }
        }
    }

    private fun handleErrorCodes(code: Int, retryAfterHeader: String?) {
        when (code) {
            401 -> throw BitChordAuthRequiredException("Extension requires BitChord authentication (401)")
            429 -> throw RateLimitedException(retryAfterHeader?.toLongOrNull())
            !in 200..299 -> throw AddonException("Extension returned HTTP $code")
        }
    }

    private fun mapQualityTier(quality: StreamQuality): String = when (quality) {
        StreamQuality.LOW -> "low"
        StreamQuality.NORMAL -> "normal"
        StreamQuality.HIGH -> "high"
        StreamQuality.LOSSLESS -> "lossless"
        StreamQuality.HI_RES -> "hires"
    }

    private fun buildRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json")
        .build()
}
