package com.openavplugin.ui.home

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
import com.openavplugin.data.ConfigManager
import com.openavplugin.data.RuntimeMode
import com.openavplugin.data.db.RuleDao
import com.openavplugin.util.LogServer
import com.openavplugin.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val configManager: ConfigManager,
    private val ruleDao: RuleDao
) : ViewModel() {
    val globalCameraEnabled = configManager.globalCameraEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val globalMicEnabled = configManager.globalMicEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val activeRules = ruleDao.getEnabledRules().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val runtimeMode = configManager.runtimeMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), RuntimeMode.AUTO
    )

    fun toggleGlobalCamera(enabled: Boolean) {
        Logger.i("Home", "Global camera: $enabled")
        viewModelScope.launch { configManager.setGlobalCameraEnabled(enabled) }
    }

    fun toggleGlobalMic(enabled: Boolean) {
        Logger.i("Home", "Global mic: $enabled")
        viewModelScope.launch { configManager.setGlobalMicEnabled(enabled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToPermissionGuide: () -> Unit = {}
) {
    val context = LocalContext.current
    val cameraEnabled by viewModel.globalCameraEnabled.collectAsState()
    val micEnabled by viewModel.globalMicEnabled.collectAsState()
    val activeRules by viewModel.activeRules.collectAsState()
    val runtimeMode by viewModel.runtimeMode.collectAsState()

    val displayMode = when (runtimeMode) {
        RuntimeMode.LSPOSED -> stringResource(R.string.lsposed_mode)
        RuntimeMode.ROOT -> stringResource(R.string.root_mode)
        RuntimeMode.AUTO -> stringResource(R.string.auto_detect)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.runtime_status), style = MaterialTheme.typography.titleMedium)
                    Text(
                        displayMode,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.mode_set_in_settings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Log server address (if running)
        if (LogServer.isRunning()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Log Server", style = MaterialTheme.typography.labelMedium)
                        Text(
                            LogServer.getAddress(context),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.global_controls), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.virtual_camera))
                        Text(
                            if (cameraEnabled) stringResource(R.string.active) else stringResource(R.string.inactive),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (cameraEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = cameraEnabled,
                        onCheckedChange = { viewModel.toggleGlobalCamera(it) }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.virtual_microphone))
                        Text(
                            if (micEnabled) stringResource(R.string.active) else stringResource(R.string.inactive),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (micEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = micEnabled,
                        onCheckedChange = { viewModel.toggleGlobalMic(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onNavigateToPermissionGuide,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.permission_guide))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.active_apps), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                if (activeRules.isEmpty()) {
                    Text(
                        stringResource(R.string.no_apps_configured),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    activeRules.forEach { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(rule.appName)
                            Row {
                                if (rule.cameraEnabled) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (rule.micEnabled) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hook status card
        val hookStatus = remember { mutableStateOf(readHookStatus(context)) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (hookStatus.value.contains("installed"))
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Hook Status", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    hookStatus.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hookStatus.value.contains("installed"))
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private fun readHookStatus(context: android.content.Context): String {
    return try {
        val f = java.io.File(context.filesDir, "hook_status.txt")
        if (f.exists()) f.readText().trim().takeLast(200) else "No hook activity detected"
    } catch (_: Exception) {
        "Unable to read hook status"
    }
}
