package com.openavplugin.ui.sources

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openavplugin.R
import com.openavplugin.data.db.SourceType
import com.openavplugin.util.Logger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManagerScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.video),
        stringResource(R.string.audio)
    )

    // State hoisted to parent so selections survive tab switches
    var videoSourceType by remember { mutableStateOf<SourceType>(SourceType.LOCAL_VIDEO) }
    var audioSourceType by remember { mutableStateOf<SourceType>(SourceType.SILENCE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                0 -> VideoSourceContent(
                    selectedSourceType = videoSourceType,
                    onSourceTypeChanged = { videoSourceType = it }
                )
                1 -> AudioSourceContent(
                    selectedSourceType = audioSourceType,
                    onSourceTypeChanged = { audioSourceType = it }
                )
            }
        }
    }

@Composable
fun VideoSourceContent(
    selectedSourceType: SourceType,
    onSourceTypeChanged: (SourceType) -> Unit
) {
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
        // ── Privacy: Camera Block ──
        item {
            SourceTypeCard(
                title = stringResource(R.string.camera_block),
                description = stringResource(R.string.camera_block_desc),
                isSelected = selectedSourceType == SourceType.CAMERA_BLOCK,
                onClick = {
                    onSourceTypeChanged(SourceType.CAMERA_BLOCK)
                    Logger.i("Sources", "Video source → CAMERA_BLOCK")
                },
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.local_video_files), style = MaterialTheme.typography.titleMedium)
        }

        // ── Local video files ──
        items(videoFiles) { file ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedSourceType == SourceType.LOCAL_VIDEO && videoFiles.isNotEmpty())
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.substringAfterLast("/"))
                        Text(
                            stringResource(R.string.local_video_files),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { videoFiles = videoFiles - file }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    filePicker.launch("video/*")
                    onSourceTypeChanged(SourceType.LOCAL_VIDEO)
                    Logger.i("Sources", "Video source → LOCAL_VIDEO")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_video_file))
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.network_streams), style = MaterialTheme.typography.titleMedium)
        }

        // ── Network streams ──
        items(streamUrls) { url ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedSourceType == SourceType.NETWORK_STREAM && streamUrls.isNotEmpty())
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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
                onClick = {
                    /* Show dialog to add URL */
                    onSourceTypeChanged(SourceType.NETWORK_STREAM)
                    Logger.i("Sources", "Video source → NETWORK_STREAM")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_stream_url))
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.screen_capture), style = MaterialTheme.typography.titleMedium)
        }

        // ── Screen capture ──
        item {
            SourceTypeCard(
                title = stringResource(R.string.screen_capture),
                description = stringResource(R.string.configure_screen_capture),
                isSelected = selectedSourceType == SourceType.SCREEN_CAPTURE,
                onClick = {
                    onSourceTypeChanged(SourceType.SCREEN_CAPTURE)
                    Logger.i("Sources", "Video source → SCREEN_CAPTURE")
                    /* Request screen capture permission */
                }
            )
        }
    }
}

@Composable
fun AudioSourceContent(
    selectedSourceType: SourceType,
    onSourceTypeChanged: (SourceType) -> Unit
) {
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
        // ── Privacy: Mic Mute ──
        item {
            SourceTypeCard(
                title = stringResource(R.string.mic_mute),
                description = stringResource(R.string.mic_mute_desc),
                isSelected = selectedSourceType == SourceType.SILENCE,
                onClick = {
                    onSourceTypeChanged(SourceType.SILENCE)
                    Logger.i("Sources", "Audio source → SILENCE (mute)")
                },
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.local_audio_files), style = MaterialTheme.typography.titleMedium)
        }

        // ── Local audio files ──
        items(audioFiles) { file ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedSourceType == SourceType.LOCAL_AUDIO && audioFiles.isNotEmpty())
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.substringAfterLast("/"))
                        Text(
                            stringResource(R.string.local_audio_files),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { audioFiles = audioFiles - file }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    filePicker.launch("audio/*")
                    onSourceTypeChanged(SourceType.LOCAL_AUDIO)
                    Logger.i("Sources", "Audio source → LOCAL_AUDIO")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_audio_file))
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.system_audio_capture), style = MaterialTheme.typography.titleMedium)
        }

        // ── System audio capture ──
        item {
            SourceTypeCard(
                title = stringResource(R.string.system_audio_capture),
                description = stringResource(R.string.configure_system_audio),
                isSelected = selectedSourceType == SourceType.SYSTEM_AUDIO,
                onClick = {
                    onSourceTypeChanged(SourceType.SYSTEM_AUDIO)
                    Logger.i("Sources", "Audio source → SYSTEM_AUDIO")
                    /* Request system audio capture permission */
                }
            )
        }
    }
}

/**
 * Reusable selectable source-type card with radio-style highlight.
 */
@Composable
fun SourceTypeCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = null // handled by the card click
            )
        }
    }
}
