/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.extensions

import com.pico.music.core.unified.ManifestType

/**
 * Models for the BitChord HTTP JSON Addon Protocol v1.
 */
data class BitChordAddonManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val resources: List<String> = emptyList(),
    val settings: List<BitChordAddonSetting> = emptyList(),
    val allowDownloads: Boolean = false,
    val checkValidLossless: Boolean = false,
    val bitchordAuth: Boolean = false
)

data class BitChordAddonSetting(
    val key: String,
    val label: String,
    val type: String = "text",
    val defaultValue: String = "",
    val required: Boolean = false
)

data class BitChordAddonTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val streamUrl: String? = null,
    val quality: String? = null,
    val format: String? = null,
    val isExplicit: Boolean = false
)

data class BitChordAddonStreamResponse(
    val url: String,
    val format: String? = null,
    val quality: String? = null,
    val codec: String? = null,
    val container: String? = null,
    val manifestType: ManifestType = ManifestType.PROGRESSIVE,
    val mimeType: String? = null,
    val encrypted: Boolean = false,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val bitrateKbps: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val error: String? = null
)

data class InstalledExtension(
    val baseUrl: String,
    val manifest: BitChordAddonManifest,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val customSettings: Map<String, String> = emptyMap(),
    val lastHealthOk: Boolean? = null,
    val lastHealthMessage: String? = null
)
