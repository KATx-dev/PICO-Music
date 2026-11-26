/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.core.unified

import android.net.Uri
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Central ephemeral stream resolver for PICO Music's unified playback engine.
 *
 * Dispatches custom scheme URIs (`pico-yt://`, `pico-ext://`, `streaming://`) to their
 * registered [SchemeStreamResolver], caches resolved URLs ephemerally in memory via
 * [StreamResolutionCache], and exposes invalidation hooks for playback error recovery.
 */
object UnifiedStreamResolverRegistry {
    private const val TAG = "UnifiedStreamResolver"

    const val SCHEME_INNERTUBEX = "pico-yt"
    const val SCHEME_EXTENSION = "pico-ext"
    const val SCHEME_STREAMING = "streaming"

    fun interface SchemeStreamResolver {
        suspend fun resolve(uri: Uri): ResolvedStream?
    }

    private val resolvers = ConcurrentHashMap<String, SchemeStreamResolver>()
    val cache = StreamResolutionCache()

    fun registerResolver(scheme: String, resolver: SchemeStreamResolver) {
        resolvers[scheme] = resolver
    }

    fun isManagedScheme(scheme: String?): Boolean {
        return scheme == SCHEME_INNERTUBEX ||
            scheme == SCHEME_EXTENSION ||
            scheme == SCHEME_STREAMING
    }

    fun cacheKeyForUri(uri: Uri): String = uri.toString()

    suspend fun resolve(uri: Uri, forceRefresh: Boolean = false): ResolvedStream? {
        val scheme = uri.scheme ?: return null
        val key = cacheKeyForUri(uri)
        if (!forceRefresh) {
            cache.get(key)?.let { return it }
        } else {
            cache.invalidate(key)
        }

        val resolver = resolvers[scheme]
        if (resolver == null) {
            Log.w(TAG, "No stream resolver registered for scheme=$scheme")
            return null
        }

        val resolved = runCatching { resolver.resolve(uri) }
            .onFailure { Log.w(TAG, "Stream resolution failed for $scheme", it) }
            .getOrNull()
            ?: return null

        if (resolved.url.isNotBlank()) {
            cache.put(key, resolved)
            return resolved
        }
        return null
    }

    fun invalidate(uri: Uri) {
        cache.invalidate(cacheKeyForUri(uri))
    }

    fun buildInnertubeUri(videoId: String): String = "$SCHEME_INNERTUBEX://track/$videoId"

    fun buildExtensionUri(addonId: String, trackId: String): String =
        "$SCHEME_EXTENSION://${Uri.encode(addonId)}/${Uri.encode(trackId)}"
}
