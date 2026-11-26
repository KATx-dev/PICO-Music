/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.extensions

import android.content.Context
import android.net.Uri
import com.pico.music.core.unified.StreamQuality
import com.pico.music.core.unified.UnifiedStreamResolverRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists installed BitChord-compatible extensions and registers the `pico-ext://` stream resolver.
 */
class ExtensionRegistry(
    context: Context,
    private val client: BitChordAddonClient = BitChordAddonClient()
) {
    companion object {
        private const val PREFS_NAME = "pico_extensions_prefs"
        private const val KEY_EXTENSIONS = "installed_extensions_json"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _extensions = MutableStateFlow<List<InstalledExtension>>(loadFromPrefs())
    val extensions: StateFlow<List<InstalledExtension>> = _extensions.asStateFlow()

    init {
        UnifiedStreamResolverRegistry.registerResolver(
            UnifiedStreamResolverRegistry.SCHEME_EXTENSION
        ) { uri ->
            val segments = uri.pathSegments
            val addonId = uri.host ?: return@registerResolver null
            val trackId = segments.firstOrNull()?.let { Uri.decode(it) } ?: return@registerResolver null
            val ext = _extensions.value.firstOrNull { it.manifest.id == addonId && it.enabled }
                ?: return@registerResolver null
            client.resolveStream(ext, trackId, StreamQuality.HIGH)
        }
    }

    suspend fun addOrUpdateFromUrl(rawUrl: String): Result<InstalledExtension> = runCatching {
        val normalizedUrl = BitChordAddonClient.normalizeBaseUrl(rawUrl)
        val manifest = client.fetchManifest(normalizedUrl)
        val existing = _extensions.value.firstOrNull { it.manifest.id == manifest.id }
        val updatedItem = InstalledExtension(
            baseUrl = normalizedUrl,
            manifest = manifest,
            enabled = existing?.enabled ?: true,
            priority = existing?.priority ?: _extensions.value.size,
            customSettings = existing?.customSettings ?: manifest.settings.associate { it.key to it.defaultValue },
            lastHealthOk = true,
            lastHealthMessage = "Connected (${manifest.version})"
        )
        val nextList = _extensions.value.filterNot { it.manifest.id == manifest.id } + updatedItem
        saveAndEmit(nextList.sortedBy { it.priority })
        updatedItem
    }

    suspend fun testHealth(addonId: String): Result<InstalledExtension> = runCatching {
        val current = _extensions.value.firstOrNull { it.manifest.id == addonId }
            ?: error("Extension not found: $addonId")
        val manifest = client.fetchManifest(current.baseUrl)
        val updated = current.copy(
            manifest = manifest,
            lastHealthOk = true,
            lastHealthMessage = "Healthy (${manifest.version})"
        )
        replaceExtension(updated)
        updated
    }.onFailure { err ->
        val current = _extensions.value.firstOrNull { it.manifest.id == addonId }
        if (current != null) {
            replaceExtension(
                current.copy(
                    lastHealthOk = false,
                    lastHealthMessage = err.message ?: "Health check failed"
                )
            )
        }
    }

    fun setEnabled(addonId: String, enabled: Boolean) {
        val next = _extensions.value.map {
            if (it.manifest.id == addonId) it.copy(enabled = enabled) else it
        }
        saveAndEmit(next)
    }

    fun updateSetting(addonId: String, key: String, value: String) {
        val next = _extensions.value.map { ext ->
            if (ext.manifest.id == addonId) {
                ext.copy(customSettings = ext.customSettings + (key to value))
            } else ext
        }
        saveAndEmit(next)
    }

    fun remove(addonId: String) {
        UnifiedStreamResolverRegistry.cache.invalidateByPrefix(
            "${UnifiedStreamResolverRegistry.SCHEME_EXTENSION}://${Uri.encode(addonId)}/"
        )
        saveAndEmit(_extensions.value.filterNot { it.manifest.id == addonId })
    }

    private fun replaceExtension(updated: InstalledExtension) {
        saveAndEmit(_extensions.value.map { if (it.manifest.id == updated.manifest.id) updated else it })
    }

    private fun saveAndEmit(list: List<InstalledExtension>) {
        _extensions.value = list
        val arr = JSONArray()
        list.forEach { ext ->
            val obj = JSONObject().apply {
                put("baseUrl", ext.baseUrl)
                put("enabled", ext.enabled)
                put("priority", ext.priority)
                put("manifest", JSONObject().apply {
                    put("id", ext.manifest.id)
                    put("name", ext.manifest.name)
                    put("version", ext.manifest.version)
                    put("description", ext.manifest.description)
                    put("resources", JSONArray(ext.manifest.resources))
                    put("allowDownloads", ext.manifest.allowDownloads)
                    put("checkValidLossless", ext.manifest.checkValidLossless)
                    put("bitchordAuth", ext.manifest.bitchordAuth)
                    put("settings", JSONArray().apply {
                        ext.manifest.settings.forEach { s ->
                            put(JSONObject().apply {
                                put("key", s.key)
                                put("label", s.label)
                                put("type", s.type)
                                put("default", s.defaultValue)
                                put("required", s.required)
                            })
                        }
                    })
                })
                put("customSettings", JSONObject(ext.customSettings))
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_EXTENSIONS, arr.toString()).apply()
    }

    private fun loadFromPrefs(): List<InstalledExtension> {
        val raw = prefs.getString(KEY_EXTENSIONS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val m = obj.optJSONObject("manifest") ?: continue
                    val settingsArr = m.optJSONArray("settings") ?: JSONArray()
                    val settings = buildList {
                        for (j in 0 until settingsArr.length()) {
                            val s = settingsArr.optJSONObject(j) ?: continue
                            add(
                                BitChordAddonSetting(
                                    key = s.optString("key"),
                                    label = s.optString("label"),
                                    type = s.optString("type", "text"),
                                    defaultValue = s.optString("default", ""),
                                    required = s.optBoolean("required", false)
                                )
                            )
                        }
                    }
                    val resArr = m.optJSONArray("resources") ?: JSONArray()
                    val resources = buildList {
                        for (j in 0 until resArr.length()) {
                            resArr.optString(j).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                    val customMap = mutableMapOf<String, String>()
                    obj.optJSONObject("customSettings")?.let { cs ->
                        val keys = cs.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            customMap[k] = cs.optString(k)
                        }
                    }
                    add(
                        InstalledExtension(
                            baseUrl = obj.optString("baseUrl"),
                            enabled = obj.optBoolean("enabled", true),
                            priority = obj.optInt("priority", i),
                            customSettings = customMap,
                            manifest = BitChordAddonManifest(
                                id = m.optString("id"),
                                name = m.optString("name"),
                                version = m.optString("version"),
                                description = m.optString("description", ""),
                                resources = resources,
                                settings = settings,
                                allowDownloads = m.optBoolean("allowDownloads", false),
                                checkValidLossless = m.optBoolean("checkValidLossless", false),
                                bitchordAuth = m.optBoolean("bitchordAuth", false)
                            )
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
