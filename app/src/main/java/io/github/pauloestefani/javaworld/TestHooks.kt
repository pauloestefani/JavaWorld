package io.github.pauloestefani.javaworld

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.pauloestefani.javaworld.data.Loader
import io.github.pauloestefani.javaworld.mods.ModManager
import io.github.pauloestefani.javaworld.net.VersionApi
import io.github.pauloestefani.javaworld.server.InitialSettings
import io.github.pauloestefani.javaworld.server.ServerFactory
import io.github.pauloestefani.javaworld.tunnel.TunnelSettings
import io.github.pauloestefani.javaworld.ui.Nav
import kotlinx.coroutines.launch

/**
 * Comandos para os testes automáticos (adb shell am broadcast ...).
 * Protegido pela permissão DUMP, que só o adb/shell tem — apps comuns não conseguem usar.
 */
class TestHooks : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = App.instance
        val cmd = intent.getStringExtra("cmd") ?: return
        Log.i(TAG, "cmd=$cmd")
        val pending = goAsync()
        app.scope.launch {
            try {
                when (cmd) {
                    "create" -> {
                        val loader = Loader.of(intent.getStringExtra("loader"))
                        val mc = intent.getStringExtra("mc") ?: "1.21.1"
                        val lv = VersionApi.latestLoaderVersion(loader, mc)
                        val p = ServerFactory.create(
                            app, intent.getStringExtra("name") ?: "Teste", loader, mc, lv,
                            intent.getIntExtra("ram", 1024), InitialSettings(onlineMode = false, motd = "Teste do JavaWorld")
                        )
                        Log.i(TAG, "created id=${p.id}")
                    }
                    "start" -> app.servers.start(intent.getStringExtra("id") ?: app.repo.servers.value.first().id)
                    "stop" -> app.servers.stop()
                    "send" -> app.servers.send(intent.getStringExtra("text").orEmpty())
                    "tunnel" -> {
                        app.tunnel.save(
                            TunnelSettings(
                                host = intent.getStringExtra("host").orEmpty(),
                                controlPort = intent.getIntExtra("port", 7000),
                                token = intent.getStringExtra("token").orEmpty(),
                                remotePort = intent.getIntExtra("remote", 25565),
                                autoStart = true,
                            )
                        )
                        Log.i(TAG, "tunnel saved")
                    }
                    "mod" -> {
                        val id = intent.getStringExtra("id") ?: app.repo.servers.value.first().id
                        val profile = app.repo.get(id) ?: error("no server")
                        val files = ModManager(app.repo.dir(id)).installFromModrinth(intent.getStringExtra("project").orEmpty(), profile)
                        Log.i(TAG, "mod installed: $files")
                    }
                    "route" -> Nav.request(intent.getStringExtra("route").orEmpty())
                }
            } catch (e: Throwable) {
                Log.e(TAG, "cmd $cmd failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val TAG = "JavaWorldTest"
    }
}
