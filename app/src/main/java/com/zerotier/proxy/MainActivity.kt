package com.zerotier.proxy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    pylonRunner = pylonRunner
                )
            }
        }
    }

    companion object {
        fun startSplitTunnel(context: Context) {
            val intent = Intent(context, ZTVpnService::class.java).apply {
                action = ZTVpnService.ACTION_START
            }
            context.startService(intent)
        }

        fun stopSplitTunnel(context: Context) {
            val intent = Intent(context, ZTVpnService::class.java).apply {
                action = ZTVpnService.ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    settingsManager: SettingsManager,
    pylonRunner: PylonRunner
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var proxyMode by rememberSaveable { mutableStateOf(true) }
    var running by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Idle") }
    var planetPath by rememberSaveable { mutableStateOf(settingsManager.planetPath()) }
    var moonPath by rememberSaveable { mutableStateOf(settingsManager.moonPath()) }

    fun refreshPaths() {
        planetPath = settingsManager.planetPath()
        moonPath = settingsManager.moonPath()
    }

    // VPN permission launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            running = true
            status = "Split tunnel active"
            MainActivity.startSplitTunnel(context)
        } else {
            status = "VPN permission denied"
        }
    }

    // Planet file picker
    val planetPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                status = "Importing planet..."
                withContext(Dispatchers.IO) { settingsManager.savePlanet(uri) }
                    .onSuccess {
                        refreshPaths()
                        status = "Planet imported ✓"
                    }
                    .onFailure { status = "Planet import failed: ${it.message}" }
            }
        }
    }

    // Moon file picker
    val moonPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                status = "Importing moon..."
                withContext(Dispatchers.IO) { settingsManager.saveMoon(uri) }
                    .onSuccess {
                        refreshPaths()
                        status = "Moon imported ✓"
                    }
                    .onFailure { status = "Moon import failed: ${it.message}" }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ZeroTierProxy", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "v1.0.0",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ═══════════════════════════════════════════
            // STATUS INDICATOR
            // ═══════════════════════════════════════════
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        running && proxyMode -> Color(0xFF1B5E20).copy(alpha = 0.15f)
                        running && !proxyMode -> Color(0xFF0D47A1).copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Status",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            status,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    running -> Color(0xFF4CAF50)
                                    else -> Color(0xFF9E9E9E)
                                }
                            )
                    )
                }
            }

            // ═══════════════════════════════════════════
            // MODE SELECTOR
            // ═══════════════════════════════════════════
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Operation Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Proxy Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (proxyMode) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                "Runs pylon_arm64 binary",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Split Tunnel",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (!proxyMode) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                "Android VpnService",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Switch(
                        checked = proxyMode,
                        onCheckedChange = {
                            proxyMode = it
                            if (running) {
                                running = false
                                status = "Idle"
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            // ═══════════════════════════════════════════
            // ZEROTIER CONFIGURATION
            // ═══════════════════════════════════════════
            Text(
                "ZeroTier Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Planet File Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (settingsManager.hasPlanet())
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Planet File",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (settingsManager.hasPlanet()) "✔" else "—",
                            color = if (settingsManager.hasPlanet()) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = planetPath,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { planetPicker.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (settingsManager.hasPlanet()) "Change Planet" else "Import Planet",
                                fontSize = 13.sp
                            )
                        }
                        if (settingsManager.hasPlanet()) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { settingsManager.clearPlanet() }
                                        refreshPaths()
                                        status = "Planet file cleared"
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Clear", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Moon File Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (settingsManager.hasMoon())
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Moon File",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (settingsManager.hasMoon()) "✔" else "—",
                            color = if (settingsManager.hasMoon()) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = moonPath,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { moonPicker.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (settingsManager.hasMoon()) "Change Moon" else "Import Moon",
                                fontSize = 13.sp
                            )
                        }
                        if (settingsManager.hasMoon()) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { settingsManager.clearMoon() }
                                        refreshPaths()
                                        status = "Moon file cleared"
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Clear", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════
            // START / STOP BUTTON
            // ═══════════════════════════════════════════
            Button(
                onClick = {
                    scope.launch {
                        if (!running) {
                            if (proxyMode) {
                                if (!settingsManager.hasPlanet()) {
                                    status = "Warning: No planet file configured"
                                }
                                withContext(Dispatchers.IO) { pylonRunner.start() }
                                    .onSuccess {
                                        running = true
                                        status = "Proxy started"
                                    }
                                    .onFailure { status = "Proxy start failed: ${it.message}" }
                            } else {
                                val prepareIntent = VpnService.prepare(context)
                                if (prepareIntent != null) {
                                    vpnPermissionLauncher.launch(prepareIntent)
                                } else {
                                    running = true
                                    status = "Split tunnel active"
                                    MainActivity.startSplitTunnel(context)
                                }
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
                                MainActivity.stopSplitTunnel(context)
                                running = false
                                status = "Split tunnel stopped"
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    if (running) "■  STOP" else "▶  START",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}