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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class ZTVpnService : VpnService() {
    private var tunInterface: ParcelFileDescriptor? = null
    private var vpnScope: CoroutineScope? = null
    private var vpnJob: Job? = null

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

        val extraRoutes = intent.getStringArrayExtra(EXTRA_ROUTES)?.toList().orEmpty()
        val routes = (DEFAULT_ZT_ROUTES + extraRoutes).distinct()

        val builder = Builder()
            .setSession("ZeroTier Split Tunnel")
            .setMtu(1280)
            .addAddress("100.64.0.2", 32)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")

        for (route in routes) {
            val parts = route.split("/")
            if (parts.size == 2) {
                builder.addRoute(parts[0], parts[1].toInt())
            }
        }

        tunInterface = builder.establish() ?: error("Failed to establish VPN interface")

        // Start the TUN packet loop – non-blocking read/write on the VPN fd
        vpnScope = CoroutineScope(Dispatchers.IO + Job())
        vpnJob = vpnScope?.launch {
            val inputStream = FileInputStream(tunInterface!!.fileDescriptor)
            val outputStream = FileOutputStream(tunInterface!!.fileDescriptor)
            val buffer = ByteBuffer.allocateDirect(32767)

            while (isActive) {
                buffer.clear()
                val bytesRead = inputStream.channel.read(buffer)
                if (bytesRead < 0) break

                // For a full split-tunnel, this is where you'd:
                //   1) Inspect the IP header to determine destination
                //   2) Forward packets via the real (e.g., ZeroTier) interface
                //   3) Write responses back to outputStream
                //
                // For a bare route-only split tunnel, we still need to drain
                // reads so Android's VPN stack doesn't stall.
                buffer.flip()
                outputStream.channel.write(buffer)
            }
        }
    }

    private fun stopSplitTunnel() {
        vpnJob?.cancel()
        vpnJob = null
        vpnScope?.cancel()
        vpnScope = null

        try {
            tunInterface?.close()
        } catch (_: Exception) { /* ignore */ }
        tunInterface = null

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) { /* ignore */ }

        try {
            stopSelf()
        } catch (_: Exception) { /* ignore */ }
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

        private const val CHANNEL_ID = "zt_proxy_vpn"
        private const val NOTIFICATION_ID = 700

        val DEFAULT_ZT_ROUTES = listOf(
            "172.22.0.0/15",
            "192.168.191.0/24",
            "10.147.17.0/24"
        )
    }
}
