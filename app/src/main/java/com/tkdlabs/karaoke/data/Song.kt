package com.tkdlabs.karaoke.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Song(
    val id: String,
    val title: String,
    val artist: String?,
    val duration: Double?,
    val status: String,
    @Json(name = "thumbnail_url") val thumbnailUrl: String?,
    @Json(name = "variants_status") val variantsStatus: String?,
    @Json(name = "overlay_status") val overlayStatus: String?,
) {
    val isPlayable: Boolean get() = variantsStatus == "ready"
    val hasOverlay: Boolean get() = overlayStatus == "ready"

    val durationFormatted: String
        get() {
            val secs = duration?.toInt() ?: return "--:--"
            val m = secs / 60
            val s = secs % 60
            return "%d:%02d".format(m, s)
        }
}
