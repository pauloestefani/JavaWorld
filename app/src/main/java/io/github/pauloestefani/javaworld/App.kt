package io.github.pauloestefani.javaworld

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.pauloestefani.javaworld.data.ServerRepository
import io.github.pauloestefani.javaworld.monitor.DeviceMonitor
import io.github.pauloestefani.javaworld.server.ServerManager
import io.github.pauloestefani.javaworld.tatao.TataoHost
import io.github.pauloestefani.javaworld.tunnel.TunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class App : Application() {
    lateinit var paths: Paths
        private set
    lateinit var repo: ServerRepository
        private set
    lateinit var servers: ServerManager
        private set
    lateinit var tunnel: TunnelManager
        private set
    lateinit var monitor: DeviceMonitor
        private set
    lateinit var tatao: TataoHost
        private set

    /** Por que o app fechou sozinho da última vez (mostrado uma vez ao abrir). */
    @Volatile var lastExit: String? = null

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        LastExit.install(this)
        lastExit = runCatching { LastExit.consume(this) }.getOrNull()
        paths = Paths(this)
        repo = ServerRepository(paths)
        tunnel = TunnelManager(this)
        servers = ServerManager(this)
        monitor = DeviceMonitor(this)
        tatao = TataoHost(this)
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_SERVER,
            "Servidor rodando",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mostra o status do servidor de Minecraft enquanto ele está ligado"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_SERVER = "server"
        lateinit var instance: App
            private set
    }
}

class Paths(ctx: Context) {
    val root: File = ctx.filesDir
    val servers = File(root, "servers").apply { mkdirs() }
    val runtimes = File(root, "runtimes").apply { mkdirs() }
    val tunnel = File(root, "tunnel").apply { mkdirs() }
    val tmp = File(ctx.cacheDir, "tmp").apply { mkdirs() }
    val downloads = File(ctx.cacheDir, "downloads").apply { mkdirs() }
    val nativeDir = File(ctx.applicationInfo.nativeLibraryDir)

    fun serverDir(id: String) = File(servers, id)
    val launcher: File get() = File(nativeDir, "libjwlauncher.so")
    val frpc: File get() = File(nativeDir, "libfrpc.so")
}
