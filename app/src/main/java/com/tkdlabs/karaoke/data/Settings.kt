package com.tkdlabs.karaoke.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URI

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class Settings(private val context: Context) {
    private val backendUrlKey = stringPreferencesKey("backend_url")
    private val defaultVocalIndexKey = intPreferencesKey("default_vocal_index")
    private val defaultLyricsModeKey = intPreferencesKey("default_lyrics_mode")
    private val lyricsSourceKey = intPreferencesKey("lyrics_source")

    val backendUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[backendUrlKey] ?: DEFAULT_URL
    }

    val defaultVocalIndex: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[defaultVocalIndexKey] ?: DEFAULT_VOCAL_INDEX
    }

    val defaultLyricsMode: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[defaultLyricsModeKey] ?: DEFAULT_LYRICS_MODE
    }

    val defaultLyricsSource: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[lyricsSourceKey] ?: DEFAULT_LYRICS_SOURCE
    }

    suspend fun setBackendUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[backendUrlKey] = url }
    }

    suspend fun setDefaultVocalIndex(index: Int) {
        context.dataStore.edit { prefs -> prefs[defaultVocalIndexKey] = index }
    }

    suspend fun setDefaultLyricsMode(mode: Int) {
        context.dataStore.edit { prefs -> prefs[defaultLyricsModeKey] = mode }
    }

    suspend fun setDefaultLyricsSource(index: Int) {
        context.dataStore.edit { prefs -> prefs[lyricsSourceKey] = index }
    }

    companion object {
        const val DEFAULT_URL = ""
        const val DEFAULT_VOCAL_INDEX = 1 // Guide
        const val DEFAULT_LYRICS_MODE = 1 // Lyrics (original language)
        const val DEFAULT_LYRICS_SOURCE = 0 // Video overlay

        // Lyrics modes: 0=Off, 1=Lyrics, 2=Romanized (video overlay variants)
        val LYRICS_MODES = listOf(
            LyricsMode(0, "Off", "original"),
            LyricsMode(1, "Lyrics", "overlay_lyrics"),
            LyricsMode(2, "Romanized", "overlay_romanized"),
        )

        // Lyrics source: how lyrics are rendered in the player
        val LYRICS_SOURCES = listOf(
            LyricsSource(0, "Video overlay"),
            LyricsSource(1, "Native (LRC)"),
        )

        /**
         * Normalize a user-entered backend address into a canonical base URL.
         *
         * Accepts "host", "host:port", or a full http(s) URL and returns a value
         * without a trailing slash. Retrofit needs the trailing slash, so callers
         * append it when configuring the client.
         */
        fun normalizeBackendUrl(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isBlank()) return null
            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            return try {
                val uri = URI(withScheme)
                val scheme = uri.scheme?.lowercase()
                val validScheme = scheme == "http" || scheme == "https"
                val validUrl = validScheme &&
                    !uri.host.isNullOrBlank() &&
                    uri.query == null &&
                    uri.fragment == null
                if (validUrl) withScheme else null
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class LyricsMode(val index: Int, val label: String, val videoVariant: String)
data class LyricsSource(val index: Int, val label: String)
