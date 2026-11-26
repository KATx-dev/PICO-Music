/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.credits

/**
 * Verified open-source community acknowledgements displayed in About -> Acknowledgements
 * and mirrored in README.md ("With Thanks to the Music Community").
 */
data class CommunityCredit(
    val name: String,
    val url: String,
    val relationship: String, // e.g. "Upstream Base", "Direct Dependency", "Protocol Inspiration"
    val license: String,
    val contribution: String
)

object CommunityCredits {
    val entries: List<CommunityCredit> = listOf(
        CommunityCredit(
            name = "Rhythm (by Anjishnu Nandi / cromaguy)",
            url = "https://github.com/cromaguy/Rhythm",
            relationship = "Upstream Base",
            license = "GPL-3.0-or-later",
            contribution = "Original Material 3 Expressive Android music player, Media3/ExoPlayer dual-player crossfade engine, ReplayGain, and local library foundation upon which PICO Music is forked."
        ),
        CommunityCredit(
            name = "InnerTubeX (MetrolistGroup)",
            url = "https://github.com/MetrolistGroup/innertubex",
            relationship = "Direct Dependency",
            license = "GPL-3.0",
            contribution = "Kotlin Multiplatform InnerTube API client, QuackJS cipher deobfuscation, and stream extraction stack powering PICO Music's online streaming integration."
        ),
        CommunityCredit(
            name = "BitChord (by kushagrasinghx)",
            url = "https://github.com/kushagrasinghx/BitChord",
            relationship = "Protocol & Architecture Reference",
            license = "GPL-3.0",
            contribution = "Inspired PICO Music's pluggable extension ecosystem through the BitChord HTTP JSON Addon Protocol v1 (/manifest.json, /search, /stream)."
        ),
        CommunityCredit(
            name = "bitchord-selfhosted-addon (by rairulyle)",
            url = "https://github.com/rairulyle/bitchord-selfhosted-addon",
            relationship = "Protocol Reference",
            license = "MIT",
            contribution = "Reference self-hosted bridge demonstrating how Plex, Jellyfin, Emby, and Navidrome libraries expose compatible BitChord addon endpoints."
        ),
        CommunityCredit(
            name = "Metrolist",
            url = "https://github.com/mostafaalagamy/Metrolist",
            relationship = "Ecosystem Inspiration",
            license = "GPL-3.0",
            contribution = "Open-source YouTube Music client ecosystem and architecture behind InnerTubeX."
        ),
        CommunityCredit(
            name = "AndroidX Media3 & ExoPlayer",
            url = "https://github.com/androidx/media",
            relationship = "Direct Dependency",
            license = "Apache-2.0",
            contribution = "Unified playback engine, MediaSession notifications, lock-screen/Bluetooth controls, HLS/DASH, and ResolvingDataSource."
        ),
        CommunityCredit(
            name = "Jellyfin Media3 FFmpeg Decoder",
            url = "https://github.com/jellyfin/jellyfin-androidtv",
            relationship = "Direct Dependency",
            license = "GPL-3.0",
            contribution = "Software audio codec decoding support for lossless and high-resolution formats."
        ),
        CommunityCredit(
            name = "Coil, OkHttp, Retrofit, Ktor & Room",
            url = "https://github.com/coil-kt/coil",
            relationship = "Direct Dependencies",
            license = "Apache-2.0",
            contribution = "Artwork loading, hardened networking, HTTP client engines, and local metadata persistence."
        )
    )
}
