package com.openavplugin.ui.apps

import android.content.Context
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
            ruleDao.insertRule(rule)
            sharedConfigManager.saveRule(rule)
            _appRule.value = rule
        }
    }

    fun toggleCamera(packageName: String, appName: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = ruleDao.getRule(packageName)
            val rule = (current ?: AppRule(packageName = packageName, appName = appName)).copy(
                cameraEnabled = enabled,
                cameraSourceType = if (enabled) SourceType.LOCAL_VIDEO else SourceType.NONE,
                updatedAt = System.currentTimeMillis()
            )
            ruleDao.insertRule(rule)
            sharedConfigManager.saveRule(rule)
            _appRule.value = rule
        }
    }

    fun toggleMicrophone(packageName: String, appName: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = ruleDao.getRule(packageName)
            val rule = (current ?: AppRule(packageName = packageName, appName = appName)).copy(
                micEnabled = enabled,
                micSourceType = if (enabled) SourceType.SILENCE else SourceType.NONE,
                updatedAt = System.currentTimeMillis()
            )
            ruleDao.insertRule(rule)
            sharedConfigManager.saveRule(rule)
            _appRule.value = rule
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppConfigScreen(
    packageName: String,
    viewModel: AppConfigViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val appRule by viewModel.appRule
    var cameraEnabled by remember { mutableStateOf(false) }
    var micEnabled by remember { mutableStateOf(false) }
    var appName by remember { mutableStateOf(packageName) }

    LaunchedEffect(packageName) {
        viewModel.loadAppRule(packageName)
    }

    LaunchedEffect(appRule) {
        appRule?.let {
            cameraEnabled = it.cameraEnabled
            micEnabled = it.micEnabled
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

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.virtual_camera), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.camera_config_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = cameraEnabled,
                        onCheckedChange = { enabled ->
                            cameraEnabled = enabled
                            viewModel.toggleCamera(packageName, appName, enabled)
                        }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.virtual_microphone), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.mic_config_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = micEnabled,
                        onCheckedChange = { enabled ->
                            micEnabled = enabled
                            viewModel.toggleMicrophone(packageName, appName, enabled)
                        }
                    )
                }
            }
        }
    }
}
