package com.zerotier.proxy

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var pylonRunner: PylonRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        pylonRunner = PylonRunner(this, settingsManager)

        setContent {
            MaterialTheme {
                MainScreen(
                    settingsManager = settingsManager,
                    pylonRunner = pylonRunner,
                    onSplitTunnelStart = { requestVpnPermissionAndStart() },
                    onSplitTunnelStop = { stopSplitTunnel() }
                )
            }
        }
    }

    private fun requestVpnPermissionAndStart() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            startActivityForResult(prepareIntent, VPN_REQUEST_CODE)
        } else {
            startSplitTunnel()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startSplitTunnel()
        }
    }

    private fun startSplitTunnel() {
        val intent = Intent(this, ZTVpnService::class.java).apply { action = ZTVpnService.ACTION_START }
        startService(intent)
    }

    private fun stopSplitTunnel() {
        val intent = Intent(this, ZTVpnService::class.java).apply { action = ZTVpnService.ACTION_STOP }
        startService(intent)
    }

    companion object {
        private const val VPN_REQUEST_CODE = 1001
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    settingsManager: SettingsManager,
    pylonRunner: PylonRunner,
    onSplitTunnelStart: () -> Unit,
    onSplitTunnelStop: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var proxyMode by rememberSaveable { mutableStateOf(true) }
    var running by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Idle") }

    val planetPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) { settingsManager.savePlanet(uri) }
                    .onSuccess { status = "Planet imported" }
                    .onFailure { status = "Planet import failed: ${it.message}" }
            }
        }
    }

    val moonPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) { settingsManager.saveMoon(uri) }
                    .onSuccess { status = "Moon imported" }
                    .onFailure { status = "Moon import failed: ${it.message}" }
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("ZeroTierProxy") }) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (proxyMode) "Proxy Mode" else "Split Tunnel Mode")
                Switch(checked = proxyMode, onCheckedChange = { proxyMode = it })
            }

            Text("Settings")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { planetPicker.launch(arrayOf("*/*")) }) { Text("Import Planet") }
                Button(onClick = { moonPicker.launch(arrayOf("*/*")) }) { Text("Import Moon") }
            }

            Button(
                onClick = {
                    scope.launch {
                        if (!running) {
                            if (proxyMode) {
                                withContext(Dispatchers.IO) { pylonRunner.start() }
                                    .onSuccess {
                                        running = true
                                        status = "Proxy started"
                                    }
                                    .onFailure { status = "Proxy start failed: ${it.message}" }
                            } else {
                                onSplitTunnelStart()
                                running = true
                                status = "Split tunnel starting"
                            }
                        } else {
                            if (proxyMode) {
                                withContext(Dispatchers.IO) { pylonRunner.stop() }
                                    .onSuccess {
                                        running = false
                                        status = "Proxy stopped"
                                    }
                                    .onFailure { status = "Proxy stop failed: ${it.message}" }
                            } else {
                                onSplitTunnelStop()
                                running = false
                                status = "Split tunnel stopped"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (running) "Stop" else "Start")
            }

            Text("Status: $status")
        }
    }
}
