package io.github.pauloestefani.javaworld.tatao

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.server.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * O Tátão dentro do JavaWorld: a mesma tela do Tátão do PC roda num WebView, e esta classe é a ponte.
 * Com o servidor ligado e a pessoa numa sala, o servidor é anunciado pra sala e quem está nela
 * entra pelo túnel (a conexão chega aqui e é repassada pro servidor em 127.0.0.1).
 * O WebView vive fora da tela (fica conectado mesmo quando a pessoa muda de aba).
 */
class TataoHost(private val app: App) {
    private val main = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences("tatao", Context.MODE_PRIVATE)
    private var web: WebView? = null
    private val tuns = ConcurrentHashMap<Int, Tun>()
    private val seq = AtomicInteger()
    @Volatile private var watching = false

    private class Tun(val socket: Socket) {
        val out = LinkedBlockingQueue<ByteArray>()
        val ack = Semaphore(0)
        val lock = Object()
        @Volatile var paused = true
        @Volatile var closed = false
    }

    init {
        // enquanto a pessoa está numa sala, o servidor ligado é anunciado a cada poucos segundos
        app.scope.launch {
            while (true) {
                delay(4000)
                if (watching) announce()
            }
        }
    }

    /** Porta do servidor que está online agora (só ela pode ser alcançada pelo túnel). */
    private fun runningServer(): Pair<Int, String>? {
        val info = app.servers.info.value
        if (info.status != Status.RUNNING) return null
        val profile = info.serverId?.let { app.repo.get(it) } ?: return null
        return profile.port to profile.name
    }

    private fun announce() {
        val (port, name) = runningServer() ?: return
        emit(JSONObject().put("ev", "world").put("port", port).put("motd", name))
        Log.i(TAG, "mundo anunciado na sala porta=$port")
    }

    private fun emit(o: JSONObject) {
        val js = "window.__tn&&window.__tn($o)"
        main.post { web?.evaluateJavascript(js, null) }
    }

    private fun reply(id: Int, data: Any?) = emit(JSONObject().put("ev", "reply").put("id", id).put("data", data ?: JSONObject.NULL))

    private fun hasMic() = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Coloca o WebView na tela (chamar na thread principal). */
    fun attach(ctx: Context): WebView {
        val w = web ?: create().also { web = it }
        (w.context as? MutableContextWrapper)?.baseContext = ctx
        (w.parent as? ViewGroup)?.removeView(w)
        return w
    }

    /** A tela saiu: o WebView continua vivo (e conectado), só sem a Activity. */
    fun detach() {
        val w = web ?: return
        (w.parent as? ViewGroup)?.removeView(w)
        (w.context as? MutableContextWrapper)?.baseContext = app
    }

    /** Usado pelos testes: abre o Tátão sem tela e roda um script nele. */
    fun runScript(js: String) {
        main.post {
            val w = web ?: create().also { web = it }
            w.evaluateJavascript(js, null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView {
        val w = WebView(MutableContextWrapper(app))
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(app))
            .build()
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.setBackgroundColor(0xFF20252D.toInt())
        w.addJavascriptInterface(Bridge(), "TataoNative")
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == HOST) return false
                openExternal(request.url.toString())
                return true
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val ok = request.resources.filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE && hasMic() }
                if (ok.isEmpty()) request.deny() else request.grant(ok.toTypedArray())
            }

            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                val t = m.message()
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR || t.startsWith("[tatao]")) Log.i(TAG, "tela: $t")
                return true
            }
        }
        w.loadUrl("https://$HOST/assets/tatao/index.html")
        return w
    }

    private fun openExternal(url: String) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun closeAll() {
        tuns.keys.toList().forEach { close(it, tell = false) }
    }

    private fun close(sid: Int, tell: Boolean) {
        val t = tuns.remove(sid) ?: return
        t.closed = true
        runCatching { t.socket.close() }
        t.out.offer(ByteArray(0))
        synchronized(t.lock) { t.lock.notifyAll() }
        t.ack.release()
        if (tell) emit(JSONObject().put("ev", "tunClose").put("sid", sid))
    }

    private fun connect(id: Int, port: Int) {
        val server = runningServer()
        if (server == null || server.first != port) {
            reply(id, JSONObject().put("ok", false).put("error", "o servidor não está ligado"))
            return
        }
        thread(name = "tatao-tun") {
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress("127.0.0.1", port), 3000)
            } catch (e: Exception) {
                runCatching { socket.close() }
                reply(id, JSONObject().put("ok", false).put("error", e.message ?: "sem conexão"))
                return@thread
            }
            val sid = seq.incrementAndGet()
            val t = Tun(socket)
            tuns[sid] = t
            reply(id, JSONObject().put("ok", true).put("id", sid))
            // quem está na sala -> servidor
            thread(name = "tatao-tun-w") {
                try {
                    val os = socket.getOutputStream()
                    while (!t.closed) {
                        val b = t.out.take()
                        if (b.isEmpty()) break
                        os.write(b)
                        os.flush()
                    }
                } catch (_: Exception) {
                }
                close(sid, tell = true)
            }
            // servidor -> quem está na sala (um bloco por vez: só lê o próximo quando a tela confirmar)
            try {
                val ins = socket.getInputStream()
                val buf = ByteArray(16 * 1024)
                while (!t.closed) {
                    synchronized(t.lock) { while (t.paused && !t.closed) t.lock.wait(1000) }
                    val n = ins.read(buf)
                    if (n < 0) break
                    if (n == 0) continue
                    emit(JSONObject().put("ev", "tunData").put("sid", sid).put("b64", Base64.encodeToString(buf, 0, n, Base64.NO_WRAP)))
                    t.ack.tryAcquire(10, TimeUnit.SECONDS)
                }
            } catch (_: Exception) {
            }
            close(sid, tell = true)
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun post(json: String) {
            try {
                val m = JSONObject(json)
                when (m.optString("op")) {
                    "hello" -> { // a tela (re)carregou
                        watching = false
                        closeAll()
                    }
                    "getCfg" -> reply(m.getInt("id"), prefs.getString("cfg", null))
                    "saveCfg" -> prefs.edit().putString("cfg", m.optString("json")).apply()
                    "mcWatch" -> {
                        watching = m.optBoolean("on")
                        Log.i(TAG, "na sala=$watching")
                        if (watching) announce()
                    }
                    "tunConnect" -> connect(m.getInt("id"), m.optInt("port"))
                    "tunWrite" -> tuns[m.optInt("sid")]?.out?.offer(Base64.decode(m.optString("b64"), Base64.DEFAULT))
                    "tunPause" -> tuns[m.optInt("sid")]?.let { t ->
                        t.paused = m.optBoolean("on")
                        synchronized(t.lock) { t.lock.notifyAll() }
                    }
                    "ack" -> tuns[m.optInt("sid")]?.ack?.release()
                    "tunEnd" -> close(m.optInt("sid"), tell = false)
                    "open" -> openExternal(m.optString("url"))
                }
            } catch (e: Exception) {
                Log.w(TAG, "ponte: ${e.message}")
            }
        }
    }

    companion object {
        const val TAG = "JavaWorldTatao"
        private const val HOST = "appassets.androidplatform.net"
    }
}
