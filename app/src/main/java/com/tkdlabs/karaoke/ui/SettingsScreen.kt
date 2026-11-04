package com.tkdlabs.karaoke.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.tkdlabs.karaoke.data.KaraokeApi
import com.tkdlabs.karaoke.data.Settings

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUrl: String,
    currentVocalIndex: Int,
    currentLyricsMode: Int,
    currentLyricsSource: Int,
    onSaveUrl: (String) -> Unit,
    onSaveVocalIndex: (Int) -> Unit,
    onSaveLyricsMode: (Int) -> Unit,
    onSaveLyricsSource: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var backendUrl by remember(currentUrl) { mutableStateOf(currentUrl) }
    var urlError by remember { mutableStateOf<String?>(null) }

    fun applyUrl() {
        val normalized = Settings.normalizeBackendUrl(backendUrl)
        if (normalized == null) {
            urlError = "Enter a valid http:// or https:// address"
            return
        }
        backendUrl = normalized
        urlError = null
        onSaveUrl(normalized)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
        )

        // Backend URL
        Text(
            text = "Backend URL",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Address of the Karaoke backend, for example http://192.168.1.50:8001",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = backendUrl,
                onValueChange = {
                    backendUrl = it
                    urlError = null
                },
                label = { androidx.compose.material3.Text("http://host:8001") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { applyUrl() }),
                modifier = Modifier.width(460.dp),
            )
            Button(
                onClick = { applyUrl() },
                enabled = backendUrl.isNotBlank(),
            ) {
                Text("Set")
            }
        }

        urlError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Default Vocal Level
        Text(
            text = "Default Vocal Level",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KaraokeApi.VOCALS.forEachIndexed { index, preset ->
                if (index == currentVocalIndex) {
                    Button(onClick = {}) {
                        Text("● ${preset.label}")
                    }
                } else {
                    WideButton(onClick = { onSaveVocalIndex(index) }) {
                        Text(preset.label)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Default Lyrics Mode
        Text(
            text = "Default Lyrics",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Settings.LYRICS_MODES.forEach { mode ->
                if (mode.index == currentLyricsMode) {
                    Button(onClick = {}) {
                        Text("● ${mode.label}")
                    }
                } else {
                    WideButton(onClick = { onSaveLyricsMode(mode.index) }) {
                        Text(mode.label)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Lyrics Source
        Text(
            text = "Lyrics Source",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Settings.LYRICS_SOURCES.forEach { source ->
                if (source.index == currentLyricsSource) {
                    Button(onClick = {}) {
                        Text("● ${source.label}")
                    }
                } else {
                    WideButton(onClick = { onSaveLyricsSource(source.index) }) {
                        Text(source.label)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { onBack() }) {
            Text("Back")
        }
    }
}
