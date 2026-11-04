package com.tkdlabs.karaoke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tkdlabs.karaoke.data.KaraokeApi
import com.tkdlabs.karaoke.data.Settings
import com.tkdlabs.karaoke.data.Song
import com.tkdlabs.karaoke.ui.PlayerScreen
import com.tkdlabs.karaoke.ui.SettingsScreen
import com.tkdlabs.karaoke.ui.SongGridScreen
import com.tkdlabs.karaoke.ui.SongsState
import com.tkdlabs.karaoke.ui.theme.KaraokeTheme
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(applicationContext)

        setContent {
            KaraokeTheme {
                val navController = rememberNavController()
                val storedUrl by settings.backendUrl.collectAsState(initial = Settings.DEFAULT_URL)
                val baseUrl = remember(storedUrl) { Settings.normalizeBackendUrl(storedUrl) }
                val defaultVocalIndex by settings.defaultVocalIndex.collectAsState(initial = Settings.DEFAULT_VOCAL_INDEX)
                val defaultLyricsMode by settings.defaultLyricsMode.collectAsState(initial = Settings.DEFAULT_LYRICS_MODE)
                val defaultLyricsSource by settings.defaultLyricsSource.collectAsState(initial = Settings.DEFAULT_LYRICS_SOURCE)
                var songsState by remember { mutableStateOf<SongsState>(SongsState.Loading) }
                var selectedSong by remember { mutableStateOf<Song?>(null) }
                val scope = rememberCoroutineScope()

                // Build the API client only when a backend URL has been configured.
                val api: KaraokeApi? = remember(baseUrl) {
                    baseUrl?.let { url ->
                        val moshi = Moshi.Builder()
                            .addLast(KotlinJsonAdapterFactory())
                            .build()
                        Retrofit.Builder()
                            .baseUrl("$url/")
                            .addConverterFactory(MoshiConverterFactory.create(moshi))
                            .build()
                            .create(KaraokeApi::class.java)
                    }
                }

                // Load songs when the API client changes.
                LaunchedEffect(api) {
                    if (api == null) {
                        songsState = SongsState.Setup
                        return@LaunchedEffect
                    }
                    songsState = SongsState.Loading
                    songsState = try {
                        SongsState.Success(api.listSongs())
                    } catch (e: Exception) {
                        SongsState.Error(e.message ?: "Unknown error")
                    }
                }

                NavHost(navController = navController, startDestination = "grid") {
                    composable("grid") {
                        SongGridScreen(
                            state = songsState,
                            baseUrl = baseUrl ?: "",
                            onSongSelected = { song ->
                                selectedSong = song
                                navController.navigate("player")
                            },
                            onSettingsClick = {
                                navController.navigate("settings")
                            },
                        )
                    }

                    composable("player") {
                        selectedSong?.let { song ->
                            api?.let { client ->
                                PlayerScreen(
                                    song = song,
                                    baseUrl = baseUrl ?: "",
                                    api = client,
                                    defaultVocalIndex = defaultVocalIndex,
                                    defaultLyricsMode = defaultLyricsMode,
                                    nativeLyrics = defaultLyricsSource == 1,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        }
                    }

                    composable("settings") {
                        SettingsScreen(
                            currentUrl = baseUrl ?: "",
                            currentVocalIndex = defaultVocalIndex,
                            currentLyricsMode = defaultLyricsMode,
                            currentLyricsSource = defaultLyricsSource,
                            onSaveUrl = { newUrl ->
                                scope.launch { settings.setBackendUrl(newUrl) }
                            },
                            onSaveVocalIndex = { index ->
                                scope.launch { settings.setDefaultVocalIndex(index) }
                            },
                            onSaveLyricsMode = { mode ->
                                scope.launch { settings.setDefaultLyricsMode(mode) }
                            },
                            onSaveLyricsSource = { index ->
                                scope.launch { settings.setDefaultLyricsSource(index) }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
