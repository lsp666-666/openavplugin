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
            if (mode == RuntimeMode.LSPOSED) syncConfigToFile()
        }
    }

    private suspend fun syncConfigToFile() {
        val config = SharedConfigManager(appContext)
        val rules = ruleDao.getAllRules().first()
        val file = java.io.File(android.os.Environment.getExternalStorageDirectory(), "openavplugin_rules.json")
        for (rule in rules) config.saveRule(rule)
        Logger.i("Settings", "Synced ${rules.size} rules → ${file.absolutePath}")
    }

    fun setLogLevel(level: String) {
        Logger.i("Settings", "Log level → $level")
        viewModelScope.launch { configManager.setLogLevel(level); Logger.setLevel(level) }
    }

    fun setBootAutoStart(enabled: Boolean) {
        viewModelScope.launch { configManager.setBootAutoStart(enabled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val runtimeMode by viewModel.runtimeMode.collectAsState()
    val logLevel by viewModel.logLevel.collectAsState()
    val bootAutoStart by viewModel.bootAutoStart.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Runtime Mode ──
        item {
            Text(stringResource(R.string.runtime_mode), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    RuntimeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(when (mode) {
                                    RuntimeMode.AUTO -> stringResource(R.string.auto_detect)
                                    RuntimeMode.LSPOSED -> stringResource(R.string.lsposed_mode)
                                    RuntimeMode.ROOT -> stringResource(R.string.root_mode_option)
                                })
                                Text(when (mode) {
                                    RuntimeMode.AUTO -> stringResource(R.string.auto_detect_desc)
                                    RuntimeMode.LSPOSED -> stringResource(R.string.lsposed_mode_desc)
                                    RuntimeMode.ROOT -> stringResource(R.string.root_mode_desc)
                                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            RadioButton(selected = runtimeMode == mode, onClick = { viewModel.setRuntimeMode(mode) })
                        }
                    }
                    val detectedMode = when {
                        RootChecker.isLSPosedActive(context) -> "LSPosed"
                        RootChecker.isRooted() -> "Root"
                        else -> stringResource(R.string.none)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${stringResource(R.string.detected)} $detectedMode",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ── General ──
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.general), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.boot_auto_start))
                        Switch(checked = bootAutoStart, onCheckedChange = { viewModel.setBootAutoStart(it) })
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    var logLevelExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = logLevelExpanded, onExpandedChange = { logLevelExpanded = it }) {
                        OutlinedTextField(
                            value = logLevel, onValueChange = {}, readOnly = true,
                            label = { Text(stringResource(R.string.log_level)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = logLevelExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = logLevelExpanded, onDismissRequest = { logLevelExpanded = false }) {
                            listOf("DEBUG", "INFO", "WARN", "ERROR").forEach { level ->
                                DropdownMenuItem(text = { Text(level) }, onClick = {
                                    viewModel.setLogLevel(level); logLevelExpanded = false
                                })
                            }
                        }
                    }
                }
            }
        }

        // ── About ──
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
                    Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // ── Debug ──
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))
            Text("\uD83D\uDEE0 调试", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Log Server
        item {
            var serverRunning by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                if (!LogServer.isRunning()) {
                    LogServer.start(context); serverRunning = LogServer.isRunning()
                    Logger.i("Settings", "LogServer auto-started: ${LogServer.getAddress(context)}")
                } else serverRunning = true
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("日志服务器", style = MaterialTheme.typography.labelMedium)
                        Text(if (serverRunning) LogServer.getAddress(context) else "已关闭",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (serverRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    }
                    Switch(checked = serverRunning, onCheckedChange = { enabled ->
                        if (enabled) { LogServer.start(context); serverRunning = LogServer.isRunning() }
                        else { LogServer.stop(); serverRunning = false }
                    })
                }
            }
        }

        // Hook Status
        item {
            val hookStatus = remember { mutableStateOf(readHookStatus(context)) }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Hook 状态", style = MaterialTheme.typography.labelMedium)
                    Text(hookStatus.value.takeLast(200).ifEmpty { "无活动" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hookStatus.value.contains("installed")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline)
                }
            }
        }

        // Restart buttons
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Thread {
                    try { Runtime.getRuntime().exec(arrayOf("su", "-c", "killall system_server lspd 2>/dev/null")).waitFor() }
                    catch (_: Exception) { }
                }.start() }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("软重启", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(onClick = { Thread {
                    try { Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot")).waitFor() }
                    catch (_: Exception) { }
                }.start() }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("硬重启", style = MaterialTheme.typography.labelSmall) }
            }
        }

        // Diagnostic buttons
        item {
            OutlinedButton(onClick = {
                Logger.i("Diag", "=== 诊断 ===")
                Logger.i("Diag", "LSPosed: ${RootChecker.isLSPosedActive(context)}")
                val f = java.io.File(android.os.Environment.getExternalStorageDirectory(), "openavplugin_rules.json")
                Logger.i("Diag", "Config: ${f.absolutePath} (exists=${f.exists()}, size=${f.length()})")
                val sf = java.io.File(context.filesDir, "hook_status.txt")
                val status = try { sf.readText().trim().take(200) } catch (_: Exception) { "no status" }
                Logger.i("Diag", "Hook: $status")
                val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
                Logger.i("Diag", "APK: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(pkg.lastUpdateTime))}")
                Logger.i("Diag", "Module: ${try { java.io.File("/data/local/tmp/openavplugin_module_path.txt").readText().trim() } catch (_: Exception) { "?" }}")
                Logger.i("Diag", "=== End ===")
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("运行诊断", style = MaterialTheme.typography.labelSmall)
            }
        }

        item {
            OutlinedButton(onClick = {
                Logger.i("LSPosed", "=== 读取 LSPosed 日志 ===")
                Thread({
                    try {
                        val logDir = java.io.File("/data/adb/lspd/log")
                        val logFiles = logDir.listFiles()?.filter { it.name.endsWith(".log") || it.name.endsWith(".txt") }
                            ?.sortedByDescending { it.lastModified() }
                        if (logFiles.isNullOrEmpty()) { trySu(context) }
                        else { logFiles.first().useLines { lines ->
                            lines.filter { it.contains("OpenAVPlugin", true) }.forEach { Logger.i("LSPosed", it.take(300)) }
                        } }
                    } catch (_: Exception) { trySu(context) }
                    Logger.i("LSPosed", "=== End ===")
                }, "LSPosedReader").start()
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("读取 LSPosed 日志", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun LazyColumn(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier, contentPadding = contentPadding, verticalArrangement = verticalArrangement, content = content
    )
}

private fun trySu(context: android.content.Context) {
    try {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d"))
        val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
        val errReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
        var count = 0
        reader.forEachLine { line ->
            if (line.contains("LSPosedFramework") && line.contains("OpenAVPlugin")) {
                if (count < 200) { Logger.i("LSPosed", line.take(400)); count++ }
            }
        }
        reader.close(); val err = errReader.readText(); process.waitFor()
        if (count == 0) {
            Logger.i("LSPosed", "(无 LSPosedFramework 行 — QQ 重启了吗？)")
            if (err.isNotBlank()) Logger.i("LSPosed", "stderr: ${err.take(200)}")
        }
    } catch (e: Exception) { Logger.i("LSPosed", "logcat 失败: ${e.message}") }
}

private fun readHookStatus(context: android.content.Context): String {
    return try {
        val f = java.io.File(context.filesDir, "hook_status.txt")
        if (f.exists()) f.readText().trim() else "未检测到 Hook 活动"
    } catch (_: Exception) { "无法读取 Hook 状态" }
}
