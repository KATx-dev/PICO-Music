/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.core.unified

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

/**
 * Classifies Media3 playback errors that indicate an expired or invalidated remote stream URL
 * so [chromahub.rhythm.app.infrastructure.service.MediaPlaybackService] can evict the cached
 * ephemeral stream and re-resolve once before surfacing a playback failure.
 */
object StreamErrorClassifier {

    fun isRecoverableStreamExpiration(
        errorCode: Int,
        cause: Throwable?,
        uriScheme: String?
    ): Boolean {
        if (!UnifiedStreamResolverRegistry.isManagedScheme(uriScheme)) return false

        val httpResponseCode = findHttpStatusCode(cause)
        if (httpResponseCode != null && httpResponseCode in setOf(401, 403, 404, 410)) {
            return true
        }

        return errorCode in setOf(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        )
    }

    private fun findHttpStatusCode(throwable: Throwable?): Int? {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 8) {
            if (current is HttpDataSource.InvalidResponseCodeException) {
                return current.responseCode
            }
            current = current.cause
            depth++
        }
        return null
    }
}
