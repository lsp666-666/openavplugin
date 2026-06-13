package com.openavplugin.ui.settings

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
import android.content.Context
import com.openavplugin.R
import com.openavplugin.data.ConfigManager
import com.openavplugin.data.RuntimeMode
import com.openavplugin.data.SharedConfigManager
import com.openavplugin.data.db.RuleDao
import com.openavplugin.root.RootChecker
import com.openavplugin.util.LogServer
import com.openavplugin.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configManager: ConfigManager,
    private val ruleDao: RuleDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context
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
        viewModelScope.launch {
            configManager.setRuntimeMode(mode)
            if (mode == RuntimeMode.LSPOSED) {
                syncConfigToFile()
            }
        }
    }

    private suspend fun syncConfigToFile() {
        val config = SharedConfigManager(appContext)
        val rules = ruleDao.getAllRules().first()
        val file = java.io.File(android.os.Environment.getExternalStorageDirectory(), "openavplugin_rules.json")
        for (rule in rules) {
            config.saveRule(rule)
        }
        Logger.i("Settings", "Synced ${rules.size} rules → ${file.absolutePath} (exists=${file.exists()}, size=${file.length()})")
    }

    fun setLogLevel(level: String) {
        Logger.i("Settings", "Log level → $level")
        viewModelScope.launch {
            configManager.setLogLevel(level)
            Logger.setLevel(level)
        }
    }

    fun setBootAutoStart(enabled: Boolean) {
        viewModelScope.launch { configManager.setBootAutoStart(enabled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val runtimeMode by viewModel.runtimeMode.collectAsState()
    val logLevel by viewModel.logLevel.collectAsState()
    val bootAutoStart by viewModel.bootAutoStart.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
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

                        val detectedMode = when {
                            RootChecker.isLSPosedActive(context) -> "LSPosed"
                            RootChecker.isRooted() -> "Root"
                            else -> stringResource(R.string.none)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${stringResource(R.string.detected)} $detectedMode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                Logger.i("Diag", "=== Diagnostics ===")
                                Logger.i("Diag", "LSPosed active: ${RootChecker.isLSPosedActive(context)}")
                                Logger.i("Diag", "Rooted: ${RootChecker.isRooted()}")
                                val f = java.io.File(android.os.Environment.getExternalStorageDirectory(), "openavplugin_rules.json")
                                Logger.i("Diag", "Config: ${f.absolutePath} (exists=${f.exists()}, size=${f.length()})")
                                val sf = java.io.File(context.filesDir, "hook_status.txt")
                                val status = try { sf.readText().trim().take(200) } catch (_: Exception) { "no status" }
                                Logger.i("Diag", "Hook status: $status")
                                Logger.i("Diag", "=== End Diagnostics ===")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Diagnostics", style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                Logger.i("LSPosed", "=== Reading LSPosed logs ===")
                                Thread({
                                    try {
                                        // Try direct file read first
                                        val logDir = java.io.File("/data/adb/lspd/log")
                                        val logFiles = logDir.listFiles()?.filter {
                                            it.name.endsWith(".log") || it.name.endsWith(".txt")
                                        }?.sortedByDescending { it.lastModified() }
                                        if (logFiles.isNullOrEmpty()) {
                                            Logger.i("LSPosed", "No log files found directly, trying su...")
                                            trySu(context)
                                        } else {
                                            val latest = logFiles.first()
                                            Logger.i("LSPosed", "Reading: ${latest.name}")
                                            latest.useLines { lines ->
                                                lines.filter { it.contains("OpenAVPlugin") || it.contains("openavplugin") }
                                                    .forEach { Logger.i("LSPosed", it.take(300)) }
                                            }
                                        }
                                    } catch (_: Exception) {
                                        trySu(context)
                                    }
                                    Logger.i("LSPosed", "=== End LSPosed logs ===")
                                }, "LSPosedReader").start()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Read LSPosed Logs", style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                Thread({
                                    try {
                                        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "killall system_server"))
                                        process.waitFor()
                                        Logger.i("Settings", "Soft restart executed — system_server killed")
                                    } catch (e: Exception) {
                                        Logger.i("Settings", "Soft restart failed (no root?): ${e.message}")
                                    }
                                }).start()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Soft Restart", style = MaterialTheme.typography.labelSmall)
                        }
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

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))

                        var serverRunning by remember { mutableStateOf(true) }
                        // Auto-start log server on first composition
                        LaunchedEffect(Unit) {
                            if (!LogServer.isRunning()) {
                                LogServer.start(context)
                                serverRunning = LogServer.isRunning()
                                Logger.i("Settings", "LogServer auto-started: ${LogServer.getAddress(context)}")
                            } else {
                                serverRunning = true
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.log_server))
                                Text(
                                    if (serverRunning)
                                        LogServer.getAddress(context)
                                    else
                                        stringResource(R.string.log_server_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (serverRunning) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = serverRunning,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        LogServer.start(context)
                                        serverRunning = LogServer.isRunning()
                                        Logger.i("Settings", "LogServer started: ${LogServer.getAddress(context)}")
                                    } else {
                                        LogServer.stop()
                                        serverRunning = false
                                        Logger.i("Settings", "LogServer stopped")
                                    }
                                }
                            )
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

private fun trySu(context: android.content.Context) {
    try {
        // Try logcat — just dump all and filter in Java
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d"))
        val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
        val errReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
        var count = 0
        reader.forEachLine { line ->
            if (line.contains("LSPosedFramework") && line.contains("OpenAVPlugin")) {
                if (count < 200) {
                    Logger.i("LSPosed", line.take(400))
                    count++
                }
            }
        }
        reader.close()
        val err = errReader.readText()
        process.waitFor()
        if (count == 0) {
            Logger.i("LSPosed", "(no LSPosedFramework lines — was QQ restarted?)")
            if (err.isNotBlank()) Logger.i("LSPosed", "logcat stderr: ${err.take(200)}")
            Logger.i("LSPosed", "Grant READ_LOGS: adb shell pm grant com.openavplugin android.permission.READ_LOGS")
        }
    } catch (e: Exception) {
        Logger.i("LSPosed", "logcat failed: ${e.message}")
    }
}
