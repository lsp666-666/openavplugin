package com.openavplugin.ui.sources

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openavplugin.R
import com.openavplugin.data.db.SourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManagerScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.video),
        stringResource(R.string.audio)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.source_manager)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> VideoSourceContent()
                1 -> AudioSourceContent()
            }
        }
    }
}

@Composable
fun VideoSourceContent() {
    val context = LocalContext.current
    var videoFiles by remember { mutableStateOf(listOf<String>()) }
    var streamUrls by remember { mutableStateOf(listOf<String>()) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            videoFiles = videoFiles + it.toString()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(stringResource(R.string.local_video_files), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(videoFiles) { file ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(file.substringAfterLast("/"), modifier = Modifier.weight(1f))
                    IconButton(onClick = { videoFiles = videoFiles - file }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
        }

        item {
            Button(
                onClick = { filePicker.launch("video/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_video_file))
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.network_streams), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(streamUrls) { url ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(url, modifier = Modifier.weight(1f))
                    IconButton(onClick = { streamUrls = streamUrls - url }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
        }

        item {
            Button(
                onClick = { /* Show dialog to add URL */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_stream_url))
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.screen_capture), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Button(
                onClick = { /* Request screen capture permission */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.configure_screen_capture))
            }
        }
    }
}

@Composable
fun AudioSourceContent() {
    val context = LocalContext.current
    var audioFiles by remember { mutableStateOf(listOf<String>()) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            audioFiles = audioFiles + it.toString()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(stringResource(R.string.silence_mode), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.silent_no_sound))
                    Icon(Icons.Default.Check, contentDescription = null)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.local_audio_files), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(audioFiles) { file ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(file.substringAfterLast("/"), modifier = Modifier.weight(1f))
                    IconButton(onClick = { audioFiles = audioFiles - file }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
        }

        item {
            Button(
                onClick = { filePicker.launch("audio/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_audio_file))
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.system_audio_capture), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Button(
                onClick = { /* Request system audio capture permission */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.configure_system_audio))
            }
        }
    }
}
