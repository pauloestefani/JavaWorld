package io.github.pauloestefani.javaworld.server

import android.util.Log
import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.data.PropertiesFile
import io.github.pauloestefani.javaworld.runtime.JavaLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.Writer

enum class Status(val label: String) {
    STOPPED("Desligado"),
    PREPARING("Preparando"),
    STARTING("Iniciando"),
    RUNNING("Online"),
    STOPPING("Desligando"),
    CRASHED("Parou com erro");

    val active: Boolean get() = this == PREPARING || this == STARTING || this == RUNNING || this == STOPPING
}

data class RunInfo(
    val serverId: String? = null,
    val status: Status = Status.STOPPED,
    val message: String? = null,
    val progress: Float? = null,
    val players: List<String> = emptyList(),
    val startedAt: Long = 0,
    val pid: Int = 0,
    val ramMb: Int = 0,
    val error: String? = null,
)

enum class LogKind { SERVER, SYSTEM, COMMAND }
enum class LogLevel { INFO, WARN, ERROR }

data class LogLine(val id: Long, val text: String, val kind: LogKind, val level: LogLevel)

/** Controla o processo do servidor de Minecraft (só um por vez, pra poupar o celular). */
class ServerManager(private val app: App) {
    private val _info = MutableStateFlow(RunInfo())
    val info: StateFlow<RunInfo> = _info.asStateFlow()

    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log: StateFlow<List<LogLine>> = _log.asStateFlow()

    private val buffer = ArrayDeque<LogLine>()
    private var seq = 0L
    @Volatile private var dirty = false

    @Volatile private var process: Process? = null
    @Volatile private var writer: Writer? = null
    @Volatile private var stopRequested = false
    private var job: Job? = null
    private val installer = ServerInstaller(app)

    private val joinRegex = Regex("""(?:: |\] )([A-Za-z0-9_.]{2,16}) joined the game""")
    private val leaveRegex = Regex("""(?:: |\] )([A-Za-z0-9_.]{2,16}) left the game""")

    init {
        app.scope.launch {
            while (true) {
                delay(120)
                if (dirty) {
                    dirty = false
                    _log.value = synchronized(buffer) { buffer.toList() }
                }
            }
        }
    }

    fun appendLog(text: String, kind: LogKind = LogKind.SERVER) {
        val level = when {
            text.contains("/ERROR]") || text.contains("ERROR") && kind == LogKind.SERVER && text.contains("Exception") -> LogLevel.ERROR
            text.contains("/WARN]") || text.contains(" WARN ") -> LogLevel.WARN
            kind == LogKind.SYSTEM && text.startsWith("✖") -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
        synchronized(buffer) {
            buffer.addLast(LogLine(seq++, text, kind, level))
            while (buffer.size > MAX_LINES) buffer.removeFirst()
        }
        dirty = true
        if (kind != LogKind.SERVER) Log.i(TAG, text)
    }

    private fun setInfo(transform: (RunInfo) -> RunInfo) {
        val before = _info.value.status
        _info.update(transform)
        val i = _info.value
        if (i.status != before) Log.i(TAG, "status=${i.status} msg=${i.message ?: ""}")
    }

    fun start(id: String) {
        if (_info.value.status.active) return
        val profile = app.repo.get(id) ?: return
        stopRequested = false
        synchronized(buffer) { buffer.clear() }
        dirty = true
        _info.value = RunInfo(serverId = id, status = Status.PREPARING, message = "Preparando…", ramMb = profile.ramMb)
        ServerService.start(app)
        job = app.scope.launch(Dispatchers.IO) {
            try {
                val (ready, java) = installer.prepare(
                    profile,
                    report = { msg, p -> setInfo { it.copy(message = msg, progress = p) } },
                    log = { appendLog(it, LogKind.SYSTEM) },
                )
                if (stopRequested) {
                    finish(0)
                    return@launch
                }
                val dir = app.repo.dir(id)
                File(dir, "eula.txt").writeText("# EULA aceito pelo usuário no app JavaWorld (https://aka.ms/MinecraftEULA)\neula=true\n")
                PropertiesFile(File(dir, "server.properties")).apply {
                    set("server-port", ready.port.toString())
                    if (get("enable-rcon") == null) set("enable-rcon", "false")
                    save()
                }
                val args = JavaLauncher.jvmArgs(java, ready.ramMb, dir, app.paths.tmp) + ready.launchArgs
                appendLog("▶ Ligando ${ready.typeLabel} (Java ${java.major}, ${ready.ramMb} MB de RAM)", LogKind.SYSTEM)
                setInfo { it.copy(status = Status.STARTING, message = "Iniciando o Minecraft…", progress = null) }
                val proc = JavaLauncher.process(app.paths, java, args, dir).start()
                process = proc
                writer = proc.outputStream.bufferedWriter(Charsets.UTF_8)
                app.repo.update(id) { it.copy(lastStarted = System.currentTimeMillis()) }
                if (app.tunnel.settings.value.autoStart && app.tunnel.settings.value.isConfigured) {
                    app.tunnel.start(ready.port)
                }
                proc.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { handleLine(it) }
                finish(proc.waitFor())
            } catch (e: CancellationException) {
                finish(0)
            } catch (e: Throwable) {
                Log.e(TAG, "start failed", e)
                appendLog("✖ ${e.message ?: e.toString()}", LogKind.SYSTEM)
                process?.destroy()
                process = null
                writer = null
                setInfo {
                    it.copy(status = Status.CRASHED, error = e.message ?: e.toString(), message = null, progress = null, pid = 0, players = emptyList())
                }
                stopTunnelIfAuto()
            }
        }
    }

    private fun handleLine(line: String) {
        if (line.startsWith("[jw-pid] ")) {
            val pid = line.removePrefix("[jw-pid] ").trim().toIntOrNull() ?: 0
            setInfo { it.copy(pid = pid) }
            return
        }
        appendLog(line)
        val st = _info.value.status
        if (st == Status.STARTING && line.contains("Done (") && line.contains(")!")) {
            setInfo { it.copy(status = Status.RUNNING, startedAt = System.currentTimeMillis(), message = null) }
            appendLog("✔ Servidor online! Seus amigos já podem entrar.", LogKind.SYSTEM)
        }
        joinRegex.find(line)?.let { m ->
            val name = m.groupValues[1]
            setInfo { it.copy(players = (it.players + name).distinct()) }
        }
        leaveRegex.find(line)?.let { m ->
            val name = m.groupValues[1]
            setInfo { it.copy(players = it.players - name) }
        }
        if (line.contains("Stopping the server") || line.contains("Stopping server")) {
            setInfo { it.copy(status = Status.STOPPING, message = "Salvando o mundo…") }
        }
        if (line.contains("FAILED TO BIND TO PORT")) {
            appendLog("✖ A porta já está em uso. Feche outro servidor ou mude a porta nos ajustes.", LogKind.SYSTEM)
        }
    }

    private fun finish(code: Int) {
        val requested = stopRequested
        process = null
        writer = null
        val crashed = code != 0 && !requested
        if (_info.value.status != Status.PREPARING || requested) {
            appendLog(if (crashed) "✖ O servidor fechou com erro (código $code)" else "■ Servidor desligado", LogKind.SYSTEM)
        }
        setInfo {
            it.copy(
                status = if (crashed) Status.CRASHED else Status.STOPPED,
                error = if (crashed) "O servidor fechou com erro (código $code). Veja o console para detalhes." else null,
                message = null, progress = null, pid = 0, players = emptyList(),
            )
        }
        stopTunnelIfAuto()
    }

    private fun stopTunnelIfAuto() {
        if (app.tunnel.isRunning) app.tunnel.stop()
    }

    fun stop() {
        when (_info.value.status) {
            Status.PREPARING -> {
                stopRequested = true
                installer.cancel()
                job?.cancel()
            }
            Status.STARTING, Status.RUNNING -> {
                stopRequested = true
                setInfo { it.copy(status = Status.STOPPING, message = "Salvando o mundo…") }
                sendRaw("stop")
                val p = process
                app.scope.launch {
                    delay(90_000)
                    if (p != null && p.isAlive) {
                        appendLog("⚠ O servidor demorou para fechar, forçando…", LogKind.SYSTEM)
                        p.destroy()
                        delay(15_000)
                        if (p.isAlive) p.destroyForcibly()
                    }
                }
            }
            Status.STOPPING -> kill()
            else -> Unit
        }
    }

    fun kill() {
        stopRequested = true
        installer.cancel()
        process?.destroyForcibly()
        job?.cancel()
    }

    fun send(command: String) {
        val cmd = command.trim().removePrefix("/")
        if (cmd.isEmpty()) return
        appendLog("> $cmd", LogKind.COMMAND)
        sendRaw(cmd)
    }

    private fun sendRaw(cmd: String) {
        val w = writer ?: return
        app.scope.launch(Dispatchers.IO) {
            runCatching {
                synchronized(w) {
                    w.write(cmd)
                    w.write("\n")
                    w.flush()
                }
            }
        }
    }

    fun isRunning(id: String): Boolean = _info.value.serverId == id && _info.value.status.active

    companion object {
        const val TAG = "JavaWorld"
        private const val MAX_LINES = 2000
    }
}
