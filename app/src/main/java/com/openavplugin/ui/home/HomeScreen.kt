package com.openavplugin.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openavplugin.R
import com.openavplugin.data.ConfigManager
import com.openavplugin.data.RuntimeMode
import com.openavplugin.data.db.RuleDao
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
        viewModelScope.launch { configManager.setGlobalCameraEnabled(enabled) }
    }

    fun toggleGlobalMic(enabled: Boolean) {
        viewModelScope.launch { configManager.setGlobalMicEnabled(enabled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToApps: () -> Unit,
    onNavigateToSources: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissionGuide: () -> Unit = {}
) {
    val cameraEnabled by viewModel.globalCameraEnabled.collectAsState()
    val micEnabled by viewModel.globalMicEnabled.collectAsState()
    val activeRules by viewModel.activeRules.collectAsState()
    val runtimeMode by viewModel.runtimeMode.collectAsState()

    val displayMode = when (runtimeMode) {
        RuntimeMode.LSPOSED -> stringResource(R.string.lsposed_mode)
        RuntimeMode.ROOT -> stringResource(R.string.root_mode)
        RuntimeMode.AUTO -> stringResource(R.string.auto_detect)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, stringResource(R.string.nav_home)) },
                    label = { Text(stringResource(R.string.nav_home)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToApps,
                    icon = { Icon(Icons.Default.List, stringResource(R.string.nav_apps)) },
                    label = { Text(stringResource(R.string.nav_apps)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToSources,
                    icon = { Icon(Icons.Default.PlayArrow, stringResource(R.string.nav_sources)) },
                    label = { Text(stringResource(R.string.nav_sources)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToSettings,
                    icon = { Icon(Icons.Default.Settings, stringResource(R.string.nav_settings)) },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
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
        }
    }
}
