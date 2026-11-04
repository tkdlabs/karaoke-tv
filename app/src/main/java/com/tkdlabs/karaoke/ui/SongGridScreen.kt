package com.tkdlabs.karaoke.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.tkdlabs.karaoke.data.KaraokeApi
import com.tkdlabs.karaoke.data.Song

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SongGridScreen(
  state: SongsState,
  baseUrl: String,
  onSongSelected: (Song) -> Unit,
  onSettingsClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Karaoke TV",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Button(onClick = { onSettingsClick() }) {
        Text("Settings")
      }
    }

    when (state) {
      is SongsState.Setup -> SetupScreen(onSettingsClick = onSettingsClick)
      is SongsState.Loading -> LoadingScreen()
      is SongsState.Error -> ErrorScreen(message = state.message, baseUrl = baseUrl)
      is SongsState.Success -> {
        val playable = remember(state.songs) { state.songs.filter { it.isPlayable } }
        if (playable.isEmpty()) {
          EmptyScreen()
        } else {
          TvLazyVerticalGrid(
            columns = TvGridCells.Adaptive(200.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(playable, key = { it.id }) { song ->
              SongCard(
                song = song,
                baseUrl = baseUrl,
                onClick = { onSongSelected(song) },
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SetupScreen(onSettingsClick: () -> Unit) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(text = "🎤", fontSize = 80.sp)
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = "Welcome to Karaoke TV",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "Connect to your karaoke backend to get started.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(24.dp))
      Button(onClick = onSettingsClick) {
        Text("Open Settings")
      }
    }
  }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LoadingScreen() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(text = "🎤", fontSize = 80.sp)
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = "Loading songs…",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "♩ ♪ ♫ ♬",
        fontSize = 28.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorScreen(message: String, baseUrl: String) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(text = "😶", fontSize = 80.sp)
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = "Could not reach server",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = baseUrl,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 600.dp),
      )
    }
  }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EmptyScreen() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      text = "No songs found",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SongCard(
  song: Song,
  baseUrl: String,
  onClick: () -> Unit,
) {
  Card(
    onClick = onClick,
    border = CardDefaults.border(
      focusedBorder = Border(
        border = BorderStroke(2.dp, Color.White),
      ),
    ),
  ) {
    Column {
      AsyncImage(
        model = KaraokeApi.thumbnailUrl(baseUrl, song.id),
        contentDescription = song.title,
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(16f / 9f),
        contentScale = ContentScale.Crop,
      )
      Column(modifier = Modifier.padding(8.dp)) {
        Text(
          text = song.title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        song.artist?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          text = song.durationFormatted,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
