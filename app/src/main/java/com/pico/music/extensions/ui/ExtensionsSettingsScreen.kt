/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pico.music.extensions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import com.pico.music.di.PicoMusicModule
import com.pico.music.extensions.InstalledExtension
import kotlinx.coroutines.launch

/**
 * Settings screen for managing BitChord-compatible HTTP JSON Addon Protocol v1 extensions.
 */
@Composable
fun ExtensionsSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val registry = remember(context) { PicoMusicModule.provideExtensionRegistry(context) }
    val extensions by registry.extensions.collectAsState()

    var addonUrlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    CollapsibleHeaderScreen(
        title = "Extensions",
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = MaterialSymbolIcon("extension", filled = true),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "BitChord Addon Protocol v1",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Connect compatible HTTP JSON addons (/manifest.json, /search, /stream). " +
                                "Remote addons must use HTTPS (HTTP is permitted only for localhost/LAN). " +
                                "PICO Music identifies honestly as PICO-Music and does not support DRM-encrypted streams.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Add Extension from URL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = addonUrlInput,
                            onValueChange = { addonUrlInput = it },
                            label = { Text("Addon URL or manifest.json") },
                            placeholder = { Text("https://music.example.com/manifest.json") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Button(
                                enabled = addonUrlInput.isNotBlank() && !isLoading,
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        statusMessage = null
                                        isError = false
                                        registry.addOrUpdateFromUrl(addonUrlInput)
                                            .onSuccess { added ->
                                                statusMessage = "Added ${added.manifest.name} v${added.manifest.version}"
                                                addonUrlInput = ""
                                            }
                                            .onFailure { err ->
                                                isError = true
                                                statusMessage = err.message ?: "Failed to add extension"
                                            }
                                        isLoading = false
                                    }
                                }
                            ) {
                                Text("Add & Verify")
                            }
                        }

                        statusMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (extensions.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No Extensions Installed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Add a BitChord-compatible addon URL above to search and stream from external or self-hosted sources.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(extensions, key = { it.manifest.id }) { ext ->
                    ExtensionCard(
                        extension = ext,
                        onToggleEnabled = { enabled -> registry.setEnabled(ext.manifest.id, enabled) },
                        onTestHealth = {
                            scope.launch {
                                registry.testHealth(ext.manifest.id)
                            }
                        },
                        onUpdateSetting = { key, value ->
                            registry.updateSetting(ext.manifest.id, key, value)
                        },
                        onRemove = { registry.remove(ext.manifest.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionCard(
    extension: InstalledExtension,
    onToggleEnabled: (Boolean) -> Unit,
    onTestHealth: () -> Unit,
    onUpdateSetting: (String, String) -> Unit,
    onRemove: () -> Unit
) {
    var expandedSettings by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.manifest.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v${extension.manifest.version} • ${extension.baseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = extension.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            if (extension.manifest.description.isNotBlank()) {
                Text(
                    text = extension.manifest.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (extension.manifest.resources.isNotEmpty()) {
                Text(
                    text = "Capabilities: ${extension.manifest.resources.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            extension.lastHealthMessage?.let { healthMsg ->
                Text(
                    text = healthMsg,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (extension.lastHealthOk == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }

            if (extension.manifest.settings.isNotEmpty() && expandedSettings) {
                Spacer(modifier = Modifier.height(4.dp))
                extension.manifest.settings.forEach { setting ->
                    var value by remember(extension.manifest.id, setting.key) {
                        mutableStateOf(extension.customSettings[setting.key] ?: setting.defaultValue)
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            value = it
                            onUpdateSetting(setting.key, it)
                        },
                        label = { Text(setting.label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onTestHealth) {
                    Text("Test")
                }
                if (extension.manifest.settings.isNotEmpty()) {
                    OutlinedButton(onClick = { expandedSettings = !expandedSettings }) {
                        Text(if (expandedSettings) "Hide Config" else "Configure")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onRemove) {
                    Text(
                        text = "Remove",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
