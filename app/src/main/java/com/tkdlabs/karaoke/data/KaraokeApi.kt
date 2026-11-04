package com.tkdlabs.karaoke.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface KaraokeApi {
    @GET("/api/songs")
    suspend fun listSongs(): List<Song>

    @GET("/api/health")
    suspend fun health(): Map<String, String>

    @GET("/api/songs/{songId}/lyrics")
    suspend fun getLyrics(
        @Path("songId") songId: String,
        @Query("romanize") romanize: Boolean,
    ): LyricsResponse

    companion object {
        fun thumbnailUrl(baseUrl: String, songId: String): String =
            "$baseUrl/api/songs/$songId/thumbnail"

        fun videoUrl(baseUrl: String, songId: String, variant: String): String =
            "$baseUrl/api/songs/$songId/video/$variant"

        fun variantUrl(baseUrl: String, songId: String, pitch: Int, vocal: Int): String =
            "$baseUrl/api/songs/$songId/variants/$pitch/$vocal"

        val PITCHES = listOf(-3, -2, -1, 0, 1, 2, 3)
        val VOCALS = listOf(
            VocalPreset(0, "Off"),
            VocalPreset(25, "Guide"),
            VocalPreset(100, "Full"),
        )
    }
}

data class VocalPreset(val value: Int, val label: String)

@JsonClass(generateAdapter = true)
data class LyricsLine(
    @Json(name = "time_ms") val timeMs: Int,
    val text: String,
    val romanized: String?,
)

@JsonClass(generateAdapter = true)
data class LyricsResponse(
    @Json(name = "song_id") val songId: String,
    @Json(name = "has_lyrics") val hasLyrics: Boolean,
    val lines: List<LyricsLine>,
)
