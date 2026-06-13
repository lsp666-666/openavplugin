package com.openavplugin.ui.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openavplugin.R
import com.openavplugin.data.db.AppRule
import com.openavplugin.data.db.RuleDao
import com.openavplugin.permission.PermissionHelper
import com.openavplugin.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val iconBitmap: Bitmap? = null
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    private val ruleDao: RuleDao
) : ViewModel() {
    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _installedApps = mutableStateOf<List<InstalledApp>>(emptyList())
    val installedApps: State<List<InstalledApp>> = _installedApps

    private val _isLoading = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    val rules = ruleDao.getAllRules().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _needsAppListPermission = mutableStateOf(false)
    val needsAppListPermission: State<Boolean> = _needsAppListPermission

    fun loadApps(context: android.content.Context) {
        viewModelScope.launch {
            _isLoading.value = true
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                try {
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                        .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                        .map { appInfo ->
                            val icon = try {
                                val drawable = pm.getApplicationIcon(appInfo)
                                drawable.toBitmap(96, 96)
                            } catch (e: Exception) {
                                null
                            }
                            InstalledApp(
                                packageName = appInfo.packageName,
                                appName = pm.getApplicationLabel(appInfo).toString(),
                                iconBitmap = icon
                            )
                        }
                        .sortedBy { it.appName.lowercase() }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _installedApps.value = apps
            _isLoading.value = false
            Logger.i("Apps", "Loaded ${apps.size} installed apps")
            // Detect if QUERY_ALL_PACKAGES is missing (only self visible)
            _needsAppListPermission.value = apps.size <= 1
                    && !PermissionHelper.hasAppListPermission(context)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addRule(rule: AppRule) {
        viewModelScope.launch { ruleDao.insertRule(rule) }
    }

    fun updateRule(rule: AppRule) {
        viewModelScope.launch { ruleDao.updateRule(rule) }
    }

    fun deleteRule(packageName: String) {
        viewModelScope.launch { ruleDao.deleteByPackageName(packageName) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel = hiltViewModel(),
    onAppClick: (String) -> Unit
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery
    val rules by viewModel.rules.collectAsState()
    val installedApps by viewModel.installedApps
    val isLoading by viewModel.isLoading
    val needsAppListPermission by viewModel.needsAppListPermission

    LaunchedEffect(Unit) {
        viewModel.loadApps(context)
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            if (needsAppListPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
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
                            Text(
                                stringResource(R.string.app_list_permission),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                stringResource(R.string.app_list_permission_hint),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text(stringResource(R.string.grant_permission))
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.no_apps_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val rule = rules.find { it.packageName == app.packageName }

                        ListItem(
                            headlineContent = {
                                Text(
                                    app.appName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    app.packageName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            leadingContent = {
                                if (app.iconBitmap != null) {
                                    Image(
                                        bitmap = app.iconBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (rule != null) {
                                        if (rule.cameraEnabled) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        if (rule.micEnabled) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onAppClick(app.packageName) }) {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
