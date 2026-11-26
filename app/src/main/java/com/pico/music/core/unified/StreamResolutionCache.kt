/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.core.unified

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory ephemeral cache for resolved streaming URLs.
 *
 * Temporary signed stream URLs (InnertubeX / BitChord extensions / self-hosted) are kept strictly
 * in memory and automatically evicted before expiration, never persisted to disk or Room.
 */
class StreamResolutionCache(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val defaultTtlMs: Long = DEFAULT_TTL_MS,
    private val expirySafetyMarginMs: Long = EXPIRY_SAFETY_MARGIN_MS
) {
    companion object {
        const val DEFAULT_TTL_MS = 15L * 60L * 1000L // 15 minutes
        const val EXPIRY_SAFETY_MARGIN_MS = 60L * 1000L // 60 seconds
    }

    private data class Entry(
        val stream: ResolvedStream,
        val validUntilMs: Long
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun get(key: String): ResolvedStream? {
        val entry = entries[key] ?: return null
        if (clockMs() >= entry.validUntilMs) {
            entries.remove(key, entry)
            return null
        }
        return entry.stream
    }

    fun put(key: String, stream: ResolvedStream) {
        val now = clockMs()
        val rawExpiry = stream.expiresAtMs ?: (now + defaultTtlMs)
        val validUntil = (rawExpiry - expirySafetyMarginMs).coerceAtLeast(now)
        entries[key] = Entry(stream = stream, validUntilMs = validUntil)
    }

    fun invalidate(key: String) {
        entries.remove(key)
    }

    fun invalidateByPrefix(prefix: String) {
        val keys = entries.keys.filter { it.startsWith(prefix) }
        keys.forEach { entries.remove(it) }
    }

    fun clear() {
        entries.clear()
    }

    fun size(): Int = entries.size
}
