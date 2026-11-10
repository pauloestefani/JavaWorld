package io.github.pauloestefani.javaworld.server

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.R
import io.github.pauloestefani.javaworld.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Mantém o servidor vivo com a tela desligada (serviço em primeiro plano + wake lock). */
class ServerService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var watcher: Job? = null
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = App.instance
        if (intent?.action == ACTION_STOP) {
            app.servers.stop()
            if (!foreground) {
                goForeground(app.servers.info.value)
            }
            return START_NOT_STICKY
        }
        goForeground(app.servers.info.value)
        acquireLocks()
        if (watcher == null) {
            watcher = app.scope.launch(Dispatchers.Main) {
                app.servers.info.collect { info ->
                    if (!info.status.active) {
                        stopForegroundCompat()
                        stopSelf()
                    } else {
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        nm.notify(NOTIFICATION_ID, build(info))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun goForeground(info: RunInfo) {
        val n = build(info)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        foreground = true
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
    }

    private fun build(info: RunInfo): Notification {
        val app = App.instance
        val name = info.serverId?.let { app.repo.get(it)?.name } ?: "Servidor"
        val text = when (info.status) {
            Status.RUNNING -> if (info.players.isEmpty()) "Online • ninguém jogando agora"
            else "Online • ${info.players.size} jogando: ${info.players.joinToString(", ")}"
            else -> info.message ?: info.status.label
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, App.CHANNEL_SERVER)
            .setSmallIcon(R.drawable.ic_stat_server)
            .setContentTitle(name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (info.status == Status.STOPPING) "Forçar desligamento" else "Desligar", stop)
            .setProgress(
                if (info.status == Status.PREPARING && info.progress != null) 100 else 0,
                ((info.progress ?: 0f) * 100).toInt(),
                info.status == Status.PREPARING && info.progress == null
            )
            .build()
    }

    @SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JavaWorld:servidor").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "JavaWorld:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    override fun onDestroy() {
        watcher?.cancel()
        watcher = null
        runCatching { wakeLock?.release() }
        runCatching { wifiLock?.release() }
        wakeLock = null
        wifiLock = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "io.github.pauloestefani.javaworld.STOP"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ServerService::class.java))
        }
    }
}
