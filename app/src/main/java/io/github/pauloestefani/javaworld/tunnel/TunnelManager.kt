package io.github.pauloestefani.javaworld.tunnel

import android.content.Context
import android.util.Log
import io.github.pauloestefani.javaworld.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.SecureRandom

data class TunnelSettings(
    val host: String = "",
    val controlPort: Int = 7000,
    val token: String = "",
    val remotePort: Int = 25565,
    val autoStart: Boolean = true,
) {
    val isConfigured: Boolean get() = host.isNotBlank() && token.isNotBlank()
    val publicAddress: String get() = if (remotePort == 25565) host else "$host:$remotePort"
}

enum class TunnelStatus(val label: String) {
    OFF("Desligado"),
    CONNECTING("Conectando"),
    ONLINE("No ar"),
    ERROR("Com problema"),
}

data class TunnelState(val status: TunnelStatus = TunnelStatus.OFF, val message: String? = null)

/**
 * Túnel próprio usando o frp (github.com/fatedier/frp).
 * O celular conecta numa máquina sua na nuvem (ex.: Oracle Cloud em São Paulo)
 * e os amigos entram pelo IP dessa máquina.
 */
class TunnelManager(private val app: App) {
    private val prefs = app.getSharedPreferences("tunnel", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<TunnelSettings> = _settings.asStateFlow()

    private val _state = MutableStateFlow(TunnelState())
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    @Volatile private var process: Process? = null
    @Volatile private var stopping = false

    val isRunning: Boolean get() = process?.isAlive == true

    private fun load(): TunnelSettings = TunnelSettings(
        host = prefs.getString("host", "").orEmpty(),
        controlPort = prefs.getInt("controlPort", 7000),
        token = prefs.getString("token", "").orEmpty(),
        remotePort = prefs.getInt("remotePort", 25565),
        autoStart = prefs.getBoolean("autoStart", true),
    )

    fun save(s: TunnelSettings) {
        prefs.edit()
            .putString("host", s.host.trim())
            .putInt("controlPort", s.controlPort)
            .putString("token", s.token.trim())
            .putInt("remotePort", s.remotePort)
            .putBoolean("autoStart", s.autoStart)
            .apply()
        _settings.value = s.copy(host = s.host.trim(), token = s.token.trim())
    }

    /** Garante que existe um token (senha do túnel) e devolve ele. */
    fun ensureToken(): String {
        val current = _settings.value.token
        if (current.isNotBlank()) return current
        val t = newToken()
        save(_settings.value.copy(token = t))
        return t
    }

    fun newToken(): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        return (1..24).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
    }

    private fun addLog(line: String) {
        val l = _log.value
        _log.value = (if (l.size > 300) l.drop(l.size - 300) else l) + line
    }

    fun start(localPort: Int) {
        if (isRunning) return
        val s = _settings.value
        if (!s.isConfigured) {
            _state.value = TunnelState(TunnelStatus.ERROR, "Configure o endereço e o token do túnel primeiro")
            return
        }
        val bin = app.paths.frpc
        if (!bin.exists()) {
            _state.value = TunnelState(TunnelStatus.ERROR, "O programa do túnel não veio no app (frpc)")
            return
        }
        val cfg = File(app.paths.tunnel, "frpc.toml")
        cfg.writeText(config(s, localPort))
        _log.value = emptyList()
        stopping = false
        _state.value = TunnelState(TunnelStatus.CONNECTING, "Conectando em ${s.host}…")
        val proc = try {
            ProcessBuilder(bin.path, "-c", cfg.path)
                .directory(app.paths.tunnel)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            _state.value = TunnelState(TunnelStatus.ERROR, "Não consegui abrir o túnel: ${e.message}")
            return
        }
        process = proc
        app.scope.launch(Dispatchers.IO) {
            runCatching {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    val clean = line.replace(Regex("\u001B\\[[0-9;]*m"), "")
                    addLog(clean)
                    Log.i("JavaWorldTunnel", clean)
                    handle(clean, s)
                }
            }
            val code = runCatching { proc.waitFor() }.getOrDefault(-1)
            process = null
            _state.value = if (stopping || code == 0) TunnelState(TunnelStatus.OFF)
            else TunnelState(TunnelStatus.ERROR, "O túnel fechou (código $code). Veja o registro abaixo.")
        }
    }

    private fun handle(line: String, s: TunnelSettings) {
        when {
            line.contains("start proxy success") ->
                _state.value = TunnelState(TunnelStatus.ONLINE, "Seus amigos entram por ${s.publicAddress}")
            line.contains("login to server success") ->
                _state.value = TunnelState(TunnelStatus.CONNECTING, "Conectado na nuvem, abrindo a porta…")
            line.contains("token") && (line.contains("mismatch") || line.contains("doesn't match") || line.contains("authorization")) ->
                _state.value = TunnelState(TunnelStatus.ERROR, "O token não bate com o da nuvem")
            line.contains("port not allowed") || line.contains("port unavailable") || line.contains("port already used") ->
                _state.value = TunnelState(TunnelStatus.ERROR, "A nuvem recusou a porta ${s.remotePort}")
            line.contains("login to the server failed") || line.contains("connect to server error") ->
                _state.value = TunnelState(TunnelStatus.ERROR, "Não consegui falar com ${s.host}:${s.controlPort}. Tentando de novo…")
        }
    }

    fun stop() {
        stopping = true
        process?.destroy()
    }

    private fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun config(s: TunnelSettings, localPort: Int): String = """
        serverAddr = ${q(s.host)}
        serverPort = ${s.controlPort}
        loginFailExit = false
        dnsServer = "8.8.8.8"
        auth.method = "token"
        auth.token = ${q(s.token)}
        log.to = "console"
        log.level = "info"
        log.disablePrintColor = true

        [[proxies]]
        name = "javaworld-minecraft"
        type = "tcp"
        localIP = "127.0.0.1"
        localPort = $localPort
        remotePort = ${s.remotePort}
    """.trimIndent() + "\n"

    /** Script para colar no "cloud-init" ao criar a máquina na Oracle. */
    fun cloudInitScript(): String {
        val s = _settings.value
        val token = ensureToken()
        return """
            #!/bin/bash
            export TOKEN=${q(token)}
            export BIND_PORT=${s.controlPort}
            export MC_PORTS=${q(s.remotePort.toString())}
            curl -fsSL $INSTALL_URL | bash
        """.trimIndent() + "\n"
    }

    /** Comando para rodar por SSH numa máquina que já existe. */
    fun sshCommand(): String {
        val s = _settings.value
        val token = ensureToken()
        return "curl -fsSL $INSTALL_URL | sudo TOKEN=${q(token)} BIND_PORT=${s.controlPort} MC_PORTS=${q(s.remotePort.toString())} bash"
    }

    companion object {
        const val INSTALL_URL = "https://raw.githubusercontent.com/pauloestefani/JavaWorld/main/tunel/instalar-frps.sh"
    }
}
