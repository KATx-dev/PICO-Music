/*
 * SPDX-FileCopyrightText: 2026 PICO Music Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.pico.music.innertube

import com.pico.music.core.unified.MusicSource
import com.pico.music.core.unified.SearchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InnertubeXJsonParserTest {

    @Test
    fun `parses song search response into UnifiedTrack with pico-yt scheme`() {
        val sampleJson = """
            {
              "contents": {
                "tabbedSearchResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "musicShelfRenderer": {
                              "contents": [{
                                "musicResponsiveListItemRenderer": {
                                  "navigationEndpoint": {
                                    "watchEndpoint": { "videoId": "abc123XYZ" }
                                  },
                                  "flexColumns": [
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": { "runs": [{ "text": "Midnight City" }] }
                                      }
                                    },
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": {
                                          "runs": [
                                            { "text": "Song" },
                                            { "text": " • " },
                                            { "text": "M83" },
                                            { "text": " • " },
                                            { "text": "Hurry Up, We're Dreaming" },
                                            { "text": " • " },
                                            { "text": "4:03" }
                                          ]
                                        }
                                      }
                                    }
                                  ],
                                  "thumbnail": {
                                    "musicThumbnailRenderer": {
                                      "thumbnail": {
                                        "thumbnails": [
                                          { "url": "https://lh3.googleusercontent.com/small", "width": 60 },
                                          { "url": "https://lh3.googleusercontent.com/large", "width": 544 }
                                        ]
                                      }
                                    }
                                  }
                                }
                              }]
                            }
                          }]
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val result = InnertubeXJsonParser.parseSearchResponse(sampleJson, SearchFilter.SONGS)
        assertEquals(1, result.tracks.size)
        val track = result.tracks.first()
        assertEquals("Midnight City", track.title)
        assertEquals("M83", track.artist)
        assertEquals("Hurry Up, We're Dreaming", track.album)
        assertEquals(243_000L, track.duration)
        assertEquals("pico-yt://track/abc123XYZ", track.playbackUri)
        assertEquals(MusicSource.InnertubeX, track.musicSource)
        assertEquals("https://lh3.googleusercontent.com/large", track.artworkUri)
    }

    @Test
    fun `returns empty result on blank or malformed json without fabricating data`() {
        assertTrue(InnertubeXJsonParser.parseSearchResponse("", SearchFilter.ALL).isEmpty)
        assertTrue(InnertubeXJsonParser.parseSearchResponse("{invalid", SearchFilter.ALL).isEmpty)
        assertTrue(InnertubeXJsonParser.parseHomeBrowseResponse("{}").isEmpty())
    }
}
