package com.openavplugin.ui.apps

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openavplugin.R
import com.openavplugin.data.SharedConfigManager
import com.openavplugin.data.db.AppRule
import com.openavplugin.data.db.RuleDao
import com.openavplugin.data.db.SourceType
import com.openavplugin.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppConfigViewModel @Inject constructor(
    private val ruleDao: RuleDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _appRule = mutableStateOf<AppRule?>(null)
    val appRule: State<AppRule?> = _appRule

    private val sharedConfigManager = SharedConfigManager(context)

    fun loadAppRule(packageName: String) {
        viewModelScope.launch {
            _appRule.value = ruleDao.getRule(packageName)
        }
    }

    fun saveOrUpdateRule(rule: AppRule) {
        viewModelScope.launch {
            val ruleWithTimestamp = rule.copy(updatedAt = System.currentTimeMillis(),
                createdAt = if (rule.createdAt == 0L) System.currentTimeMillis() else rule.createdAt)
            ruleDao.insertRule(ruleWithTimestamp)
            sharedConfigManager.saveRule(ruleWithTimestamp)
            _appRule.value = ruleWithTimestamp
        }
    }

    fun setCameraSourceType(packageName: String, appName: String, sourceType: SourceType) {
        Logger.i("Config", "Camera source → $sourceType for $packageName")
        viewModelScope.launch {
            val current = ruleDao.getRule(packageName)
            val rule = (current ?: AppRule(packageName = packageName, appName = appName)).copy(
                cameraEnabled = sourceType != SourceType.NONE,
                cameraSourceType = sourceType,
                updatedAt = System.currentTimeMillis()
            )
            ruleDao.insertRule(rule)
            sharedConfigManager.saveRule(rule)
            _appRule.value = rule
            android.widget.Toast.makeText(context, "$appName: 摄像头 → ${sourceTypeLabel(sourceType, context)}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun setMicSourceType(packageName: String, appName: String, sourceType: SourceType) {
        Logger.i("Config", "Mic source → $sourceType for $packageName")
        viewModelScope.launch {
            val current = ruleDao.getRule(packageName)
            val rule = (current ?: AppRule(packageName = packageName, appName = appName)).copy(
                micEnabled = sourceType != SourceType.NONE,
                micSourceType = sourceType,
                updatedAt = System.currentTimeMillis()
            )
            ruleDao.insertRule(rule)
            sharedConfigManager.saveRule(rule)
            _appRule.value = rule
            android.widget.Toast.makeText(context, "$appName: 麦克风 → ${sourceTypeLabel(sourceType, context)}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

// ── SourceType → display string helpers ────────────────────────

private val videoSourceTypes = listOf(SourceType.NONE, SourceType.CAMERA_BLOCK, SourceType.LOCAL_VIDEO, SourceType.NETWORK_STREAM, SourceType.SCREEN_CAPTURE)
private val audioSourceTypes = listOf(SourceType.NONE, SourceType.SILENCE, SourceType.LOCAL_AUDIO, SourceType.SYSTEM_AUDIO)

fun sourceTypeLabel(sourceType: SourceType, context: android.content.Context): String = when (sourceType) {
    SourceType.NONE -> context.getString(R.string.inactive)
    SourceType.CAMERA_BLOCK -> context.getString(R.string.camera_block)
    SourceType.SILENCE -> context.getString(R.string.mic_mute)
    SourceType.LOCAL_VIDEO -> context.getString(R.string.local_video_files)
    SourceType.LOCAL_AUDIO -> context.getString(R.string.local_audio_files)
    SourceType.NETWORK_STREAM -> context.getString(R.string.network_streams)
    SourceType.SCREEN_CAPTURE -> context.getString(R.string.screen_capture)
    SourceType.SYSTEM_AUDIO -> context.getString(R.string.system_audio_capture)
}

@Composable
private fun sourceTypeDisplayName(sourceType: SourceType): String = when (sourceType) {
    SourceType.NONE -> stringResource(R.string.inactive)
    SourceType.CAMERA_BLOCK -> stringResource(R.string.camera_block)
    SourceType.SILENCE -> stringResource(R.string.mic_mute)
    SourceType.LOCAL_VIDEO -> stringResource(R.string.local_video_files)
    SourceType.LOCAL_AUDIO -> stringResource(R.string.local_audio_files)
    SourceType.NETWORK_STREAM -> stringResource(R.string.network_streams)
    SourceType.SCREEN_CAPTURE -> stringResource(R.string.screen_capture)
    SourceType.SYSTEM_AUDIO -> stringResource(R.string.system_audio_capture)
}

// ── Screen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppConfigScreen(
    packageName: String,
    viewModel: AppConfigViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val appRule by viewModel.appRule
    var cameraType by remember { mutableStateOf(SourceType.NONE) }
    var micType by remember { mutableStateOf(SourceType.NONE) }
    var appName by remember { mutableStateOf(packageName) }

    // Intercept system back button
    BackHandler { onNavigateBack() }

    LaunchedEffect(packageName) {
        viewModel.loadAppRule(packageName)
    }

    LaunchedEffect(appRule) {
        appRule?.let {
            cameraType = it.cameraSourceType
            micType = it.micSourceType
            appName = it.appName
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_config)) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )

            Divider()

            // ── Camera source ──
            SourceTypeSelectorCard(
                title = stringResource(R.string.virtual_camera),
                description = stringResource(R.string.camera_config_desc),
                sourceTypes = videoSourceTypes,
                currentType = cameraType,
                onTypeSelected = { type ->
                    cameraType = type
                    viewModel.setCameraSourceType(packageName, appName, type)
                }
            )

            // ── Mic source ──
            SourceTypeSelectorCard(
                title = stringResource(R.string.virtual_microphone),
                description = stringResource(R.string.mic_config_desc),
                sourceTypes = audioSourceTypes,
                currentType = micType,
                onTypeSelected = { type ->
                    micType = type
                    viewModel.setMicSourceType(packageName, appName, type)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceTypeSelectorCard(
    title: String,
    description: String,
    sourceTypes: List<SourceType>,
    currentType: SourceType,
    onTypeSelected: (SourceType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = sourceTypeDisplayName(currentType),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    sourceTypes.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    sourceTypeDisplayName(type)
                                )
                            },
                            onClick = {
                                onTypeSelected(type)
                                expanded = false
                            },
                            leadingIcon = {
                                RadioButton(
                                    selected = currentType == type,
                                    onClick = null
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
