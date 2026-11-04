package com.tkdlabs.karaoke.ui

import com.tkdlabs.karaoke.data.Song

sealed class SongsState {
  data object Loading : SongsState()
  data object Setup : SongsState()
  data class Success(val songs: List<Song>) : SongsState()
  data class Error(val message: String) : SongsState()
}
