package com.openavplugin.ui.permissions

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.openavplugin.R
import com.openavplugin.permission.PermissionHelper
import com.openavplugin.ui.theme.OpenAVPluginTheme

class PermissionActivity : ComponentActivity() {

    private lateinit var permissionHelper: PermissionHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionHelper = PermissionHelper(this)

        setContent {
            OpenAVPluginTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionScreen(
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionHelper.hasAllPermissions()) {
            Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
