# PICO Music

**Your Music. Everywhere.**

PICO Music is an independent, open-source Android music player (`com.pico.music`) forked from [Rhythm](https://github.com/cromaguy/Rhythm) and licensed under **GPL-3.0-or-later**. It combines Rhythm's Material 3 Expressive UI and Media3/ExoPlayer audio engine with a unified playback pipeline for **local music**, **InnertubeX online streaming**, and **BitChord-compatible HTTP JSON extensions**.

> [!NOTE]
> PICO Music is an independent fork and is **not** an official Rhythm release. All upstream copyright notices and GPL-3.0-or-later obligations are preserved in full.

---

## Key Architecture

- **Unified Playback Engine**: `UnifiedMusicRepository`, `UnifiedStreamResolverRegistry`, `StreamResolutionCache` (ephemeral in-memory TTL cache — temporary signed URLs are never persisted to disk), and `ResolvingDataSource` inside `RhythmPlayerEngine` & `PreloadController` with automatic expired-stream recovery (`StreamErrorClassifier`).
- **Local Music Library**: Preserves Rhythm's MediaStore + Room library, playlists, favorites, ReplayGain, crossfade, 10-band EQ, FFmpeg software decoding, Glance widgets, and Android Auto/MediaSession controls.
- **InnertubeX Streaming (`MetrolistGroup/innertubex:v0.13.1`)**: Client-side Kotlin Multiplatform InnerTube client (`WEB_REMIX`), `InnerTubeExtractor`, and QuackJS cipher deobfuscation for song/album/artist/playlist search, home discovery, related tracks (`next`), and ephemeral audio stream resolution.
  - *Honest caveats*: Direct client-side extraction relies on YouTube/InnerTube availability, rolling cipher updates, and network/region access; no fake fallback results are ever returned.
- **BitChord Extension Support**: Implements **BitChord HTTP JSON Addon Protocol v1** (`/manifest.json`, `/search`, `/stream/{trackId}`) via `Settings → Extensions`, with HTTPS enforcement for remote hosts (HTTP permitted only on `localhost`/LAN `.local`), encrypted/DRM stream rejection, `429 Retry-After` and `401 bitchordAuth` handling, and honest `PICO-Music/<version>` User-Agent identification. See [docs/EXTENSIONS.md](docs/EXTENSIONS.md).
- **Security Hardening**: Global `usesCleartextTraffic="false"` (`network_security_config.xml`), path traversal sanitization in `RhythmAlbumArtProvider`, removal of hardcoded upstream API keys/JWTs, and cloud backup exclusion of credentials and extension configs.

---

## Building in the Cloud (GitHub Actions)

This repository includes [.github/workflows/android.yml](.github/workflows/android.yml) to run:
1. `./gradlew testFdroidDebugUnitTest`
2. `./gradlew lintFdroidDebug`
3. `./gradlew assembleFdroidDebug`

Workflow artifacts include unit test reports, lint reports, and the `PICO-Music-*.apk` debug build.

---

## With Thanks to the Music Community

PICO Music is built on the shoulders of the open-source Android music community. Every acknowledgement below reflects verified projects, repositories, and licences:

- **Rhythm** ([`cromaguy/Rhythm`](https://github.com/cromaguy/Rhythm)) — *by Anjishnu Nandi (`cromaguy`)* (`GPL-3.0-or-later`)  
  PICO Music is directly forked from Rhythm. Its Material 3 Expressive components, dual-player Media3/ExoPlayer crossfade engine, ReplayGain pipeline, Equalizer, Glance widgets, and local library architecture form the foundation of PICO Music. Thank you for building such a thoughtful, high-craft open-source player.

- **InnerTubeX** ([`MetrolistGroup/innertubex`](https://github.com/MetrolistGroup/innertubex)) — *Maintained by MetrolistGroup* (`GPL-3.0`)  
  Direct Kotlin Multiplatform dependency (`v0.13.1`) powering PICO Music's InnerTube API client, QuackJS cipher deobfuscation, and `InnerTubeExtractor` stream resolution. Thank you for maintaining a clean, security-conscious KMP extraction stack.

- **BitChord** ([`kushagrasinghx/BitChord`](https://github.com/kushagrasinghx/BitChord)) — *by `kushagrasinghx`* (`GPL-3.0`)  
  Protocol and architecture inspiration for PICO Music's pluggable extension system (`BitChord HTTP JSON Addon Protocol v1`). Thank you for pioneering an open, HTTP-based addon model for music clients.

- **bitchord-selfhosted-addon** ([`rairulyle/bitchord-selfhosted-addon`](https://github.com/rairulyle/bitchord-selfhosted-addon)) — *by `rairulyle`* (`MIT`)  
  Reference implementation demonstrating how self-hosted music servers (Plex, Jellyfin, Emby, Navidrome) expose `/manifest.json`, `/search`, and `/stream` endpoints. Thank you for documenting real-world self-hosted addon interoperability.

- **Metrolist** ([`mostafaalagamy/Metrolist`](https://github.com/mostafaalagamy/Metrolist)) (`GPL-3.0`)  
  Ecosystem inspiration behind InnerTubeX and modern Material YouTube Music client design.

- **AndroidX Media3 & ExoPlayer** ([`androidx/media`](https://github.com/androidx/media)) (`Apache-2.0`)  
  Direct dependency providing unified playback, `MediaSession`, lock-screen/Bluetooth controls, HLS/DASH, and `ResolvingDataSource`.

- **Jellyfin Media3 FFmpeg Decoder** ([`jellyfin/jellyfin-androidtv`](https://github.com/jellyfin/jellyfin-androidtv)) (`GPL-3.0`)  
  Direct dependency enabling broad software audio codec decoding.

- **Coil, OkHttp, Retrofit, Ktor, Room & Material Components** (`Apache-2.0`)  
  Core libraries powering image loading, hardened TLS networking, HTTP clients, and local persistence.

---

## Licence

PICO Music is free software licensed under the **GNU General Public License v3.0 or later (`GPL-3.0-or-later`)**. See [NOTICE.md](NOTICE.md) and [docs/LICENSE](docs/LICENSE) for full details.
