# PICO Music Extension Architecture (BitChord HTTP JSON Addon Protocol v1)

PICO Music implements support for external and self-hosted music extensions using the **BitChord HTTP JSON Addon Protocol v1**, allowing additional music providers to integrate with `UnifiedMusicRepository` and the unified Media3 playback engine without modifying core playback code.

## Compatibility & Attribution

- **Protocol Reference**: [kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord) (`GPL-3.0`) and [rairulyle/bitchord-selfhosted-addon](https://github.com/rairulyle/bitchord-selfhosted-addon) (`MIT`).
- **Scope**: PICO Music implements the HTTP JSON Addon Protocol v1 (`/manifest.json`, `/search`, `/stream/{trackId}`). Legacy QuickJS `SpineModule` scripts are **not** executed.
- **Honest Identification**: PICO Music always sends `User-Agent: PICO-Music/<version> (Android)` and never spoofs BitChord attestation headers. Addons that return `bitchordAuth: true` or HTTP `401` are surfaced clearly as requiring BitChord client authentication.

---

## Required & Optional Endpoints

### 1. `GET /manifest.json`
Describes the addon's identity, capabilities, and user-configurable settings.

```json
{
  "id": "my-addon",
  "name": "My Music Addon",
  "version": "1.0.0",
  "description": "Streams lossless tracks from my self-hosted server",
  "resources": ["search", "stream"],
  "allowDownloads": false,
  "checkValidLossless": true,
  "bitchordAuth": false,
  "settings": [
    {
      "key": "token",
      "label": "API Token",
      "type": "text",
      "default": "",
      "required": false
    }
  ]
}
```

If `/manifest.json` returns `404`, PICO Music falls back to probing `GET /search?q=music` to verify basic availability.

### 2. `GET /search?q={query}&quality={tier}`
`quality` is one of `low`, `normal`, `high`, `lossless`, `hires`. Any configured addon `settings` are passed as query parameters.

```json
{
  "tracks": [
    {
      "id": "track-123",
      "title": "Song Title",
      "artist": "Artist Name",
      "album": "Album Name",
      "durationMs": 215000,
      "artworkUrl": "https://example.com/cover.jpg",
      "quality": "FLAC 24-bit",
      "explicit": false
    }
  ],
  "albums": [],
  "artists": [],
  "playlists": []
}
```

### 3. `GET /stream/{trackId}?quality={tier}`
Resolves a playable ephemeral stream URL for `UnifiedStreamResolverRegistry` (`pico-ext://{addonId}/{trackId}`).

```json
{
  "url": "https://example.com/audio/track-123.flac",
  "codec": "flac",
  "manifest": "none",
  "mimeType": "audio/flac",
  "encrypted": false,
  "sampleRate": 96000,
  "bitDepth": 24,
  "bitrate": 2400,
  "headers": {}
}
```

---

## Security Constraints Enforced by PICO Music

1. **HTTPS Required**: Remote addons and resolved stream URLs must use `https://`. Plain `http://` is permitted **only** for loopback/LAN addresses (`localhost`, `127.0.0.1`, `10.0.2.2`, `::1`, `*.local`).
2. **No Encrypted/DRM Streams**: Responses with `"encrypted": true` are rejected cleanly.
3. **Ephemeral URLs Only**: Resolved stream URLs live only in memory inside `StreamResolutionCache` and are never persisted to disk or cloud backups.
4. **Rate-Limit Handling**: HTTP `429` responses parse `Retry-After` and surface clear errors without hammering the addon.
