package com.zerotier.proxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

class ZTVpnService : VpnService() {
    private var tunInterface: ParcelFileDescriptor? = null
    private lateinit var settingsManager: SettingsManager
    private lateinit var libztBridge: LibztBridge

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        libztBridge = LibztBridge(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSplitTunnel(intent)
            ACTION_STOP -> stopSplitTunnel()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopSplitTunnel()
        super.onDestroy()
    }

    private fun startSplitTunnel(intent: Intent) {
        if (tunInterface != null) return

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val config = LibztConfig(
            planetPath = intent.getStringExtra(EXTRA_PLANET_PATH) ?: settingsManager.planetFile()?.absolutePath,
            moonPath = intent.getStringExtra(EXTRA_MOON_PATH) ?: settingsManager.moonFile()?.absolutePath,
            storageDir = java.io.File(filesDir, "libzt-state").apply { mkdirs() },
            servicePort = 9993
        )
        libztBridge.startNode(config).getOrThrow()

        val extraRoutes = intent.getStringArrayExtra(EXTRA_ROUTES)?.toList().orEmpty()
        val routes = (DEFAULT_ZT_ROUTES + extraRoutes).distinct()

        val builder = Builder()
            .setSession("ZeroTier Split Tunnel")
            .setBlocking(false)
            .addAddress("100.64.0.2", 32)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")

        for (route in routes) {
            val parts = route.split("/")
            if (parts.size == 2) {
                builder.addRoute(parts[0], parts[1].toInt())
            }
        }

        tunInterface = builder.establish()
    }

    private fun stopSplitTunnel() {
        tunInterface?.close()
        tunInterface = null
        libztBridge.stopNode()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, ZTVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            10,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZeroTierProxy")
            .setContentText("Split tunnel active")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ZeroTierProxy", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val ACTION_START = "com.zerotier.proxy.START"
        const val ACTION_STOP = "com.zerotier.proxy.STOP"
        const val EXTRA_ROUTES = "extra_routes"
        const val EXTRA_PLANET_PATH = "planet_path"
        const val EXTRA_MOON_PATH = "moon_path"

        private const val CHANNEL_ID = "zt_proxy_vpn"
        private const val NOTIFICATION_ID = 700

        val DEFAULT_ZT_ROUTES = listOf(
            "172.22.0.0/15",
            "192.168.191.0/24",
            "10.147.17.0/24"
        )
    }
}
