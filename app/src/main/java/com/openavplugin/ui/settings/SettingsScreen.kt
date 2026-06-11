package com.openavplugin.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openavplugin.R
import com.openavplugin.data.ConfigManager
import com.openavplugin.data.RuntimeMode
import com.openavplugin.root.RootChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configManager: ConfigManager
) : ViewModel() {
    val runtimeMode = configManager.runtimeMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), RuntimeMode.AUTO
    )
    val logLevel = configManager.logLevel.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "INFO"
    )
    val bootAutoStart = configManager.bootAutoStart.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    fun setRuntimeMode(mode: RuntimeMode) {
        viewModelScope.launch { configManager.setRuntimeMode(mode) }
    }

    fun setLogLevel(level: String) {
        viewModelScope.launch { configManager.setLogLevel(level) }
    }

    fun setBootAutoStart(enabled: Boolean) {
        viewModelScope.launch { configManager.setBootAutoStart(enabled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val runtimeMode by viewModel.runtimeMode.collectAsState()
    val logLevel by viewModel.logLevel.collectAsState()
    val bootAutoStart by viewModel.bootAutoStart.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(stringResource(R.string.runtime_mode), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        RuntimeMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        when (mode) {
                                            RuntimeMode.AUTO -> stringResource(R.string.auto_detect)
                                            RuntimeMode.LSPOSED -> stringResource(R.string.lsposed_mode)
                                            RuntimeMode.ROOT -> stringResource(R.string.root_mode_option)
                                        }
                                    )
                                    Text(
                                        when (mode) {
                                            RuntimeMode.AUTO -> stringResource(R.string.auto_detect_desc)
                                            RuntimeMode.LSPOSED -> stringResource(R.string.lsposed_mode_desc)
                                            RuntimeMode.ROOT -> stringResource(R.string.root_mode_desc)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                RadioButton(
                                    selected = runtimeMode == mode,
                                    onClick = { viewModel.setRuntimeMode(mode) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${stringResource(R.string.detected)} ${
                                when {
                                    RootChecker.isLSPosedActive() -> "LSPosed"
                                    RootChecker.isRooted() -> "Root"
                                    else -> stringResource(R.string.none)
                                }
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.general), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.boot_auto_start))
                            Switch(
                                checked = bootAutoStart,
                                onCheckedChange = { viewModel.setBootAutoStart(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        var logLevelExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = logLevelExpanded,
                            onExpandedChange = { logLevelExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = logLevel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.log_level)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = logLevelExpanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = logLevelExpanded,
                                onDismissRequest = { logLevelExpanded = false }
                            ) {
                                listOf("DEBUG", "INFO", "WARN", "ERROR").forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text(level) },
                                        onClick = {
                                            viewModel.setLogLevel(level)
                                            logLevelExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text("${stringResource(R.string.version)} 1.0.0")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.about_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        content = content
    )
}
