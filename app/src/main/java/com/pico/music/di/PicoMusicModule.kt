/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.di

import android.content.Context
import chromahub.rhythm.app.features.local.di.LocalMusicModule
import com.pico.music.core.unified.LocalMusicProvider
import com.pico.music.core.unified.UnifiedMusicRepository
import com.pico.music.extensions.BitChordAddonClient
import com.pico.music.extensions.ExtensionMusicProvider
import com.pico.music.extensions.ExtensionRegistry
import com.pico.music.innertube.InnertubeXClientManager
import com.pico.music.innertube.InnertubeXMusicProvider

/**
 * Manual DI container for PICO Music's unified playback, InnertubeX, and BitChord extension modules.
 */
object PicoMusicModule {

    @Volatile
    private var extensionRegistry: ExtensionRegistry? = null

    @Volatile
    private var innertubeProvider: InnertubeXMusicProvider? = null

    @Volatile
    private var extensionProvider: ExtensionMusicProvider? = null

    @Volatile
    private var unifiedRepository: UnifiedMusicRepository? = null

    fun provideExtensionRegistry(context: Context): ExtensionRegistry {
        return extensionRegistry ?: synchronized(this) {
            extensionRegistry ?: ExtensionRegistry(
                context = context.applicationContext,
                client = BitChordAddonClient()
            ).also { extensionRegistry = it }
        }
    }

    fun provideInnertubeXProvider(): InnertubeXMusicProvider {
        return innertubeProvider ?: synchronized(this) {
            innertubeProvider ?: InnertubeXMusicProvider(
                clientManager = InnertubeXClientManager()
            ).also { innertubeProvider = it }
        }
    }

    fun provideExtensionProvider(context: Context): ExtensionMusicProvider {
        return extensionProvider ?: synchronized(this) {
            extensionProvider ?: ExtensionMusicProvider(
                registry = provideExtensionRegistry(context)
            ).also { extensionProvider = it }
        }
    }

    fun provideUnifiedMusicRepository(context: Context): UnifiedMusicRepository {
        return unifiedRepository ?: synchronized(this) {
            unifiedRepository ?: UnifiedMusicRepository(
                providers = listOf(
                    LocalMusicProvider(LocalMusicModule.provideMusicRepository(context.applicationContext)),
                    provideInnertubeXProvider(),
                    provideExtensionProvider(context)
                )
            ).also { unifiedRepository = it }
        }
    }
}
