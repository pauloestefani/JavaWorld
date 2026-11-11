package io.github.pauloestefani.javaworld

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Descobre por que o app fechou sozinho da última vez, pra mostrar ao abrir de novo. */
object LastExit {
    private fun crashFile(ctx: Context) = File(ctx.filesDir, "last-crash.txt")

    /** Guarda o erro quando o app quebra por uma exceção. */
    fun install(ctx: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                crashFile(ctx).writeText("Erro na thread ${t.name}:\n$sw")
            }
            prev?.uncaughtException(t, e)
        }
    }

    /** Texto pra mostrar (ou null se o app não fechou sozinho). Só devolve cada ocorrência uma vez. */
    fun consume(ctx: Context): String? {
        val parts = mutableListOf<String>()
        val f = crashFile(ctx)
        if (f.exists()) {
            runCatching { parts += f.readText().take(6000) }
            f.delete()
        }
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            val prefs = ctx.getSharedPreferences("lastexit", Context.MODE_PRIVATE)
            val seen = prefs.getLong("seen", 0)
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val list = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 5).filter { it.timestamp > seen }
            list.maxOfOrNull { it.timestamp }?.let { prefs.edit().putLong("seen", it).apply() }
            val bad = list.firstOrNull {
                it.processName == ctx.packageName && it.reason in setOf(
                    ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_LOW_MEMORY,
                    ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_SIGNALED, ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
                    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
                )
            }
            if (bad != null && seen > 0L || bad != null && parts.isNotEmpty() || bad != null && bad.reason != ApplicationExitInfo.REASON_SIGNALED) {
                val why = when (bad!!.reason) {
                    ApplicationExitInfo.REASON_CRASH -> "erro no app (CRASH)"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "erro em código nativo (CRASH_NATIVE)"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "o Android fechou por falta de memória (LOW_MEMORY)"
                    ApplicationExitInfo.REASON_ANR -> "o app travou (ANR)"
                    ApplicationExitInfo.REASON_SIGNALED -> "fechado por sinal ${bad.status} (SIGNALED)"
                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "uso excessivo de recursos"
                    else -> "motivo ${bad.reason}"
                }
                val whenTxt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date(bad.timestamp))
                var txt = "Motivo dado pelo Android: $why\nQuando: $whenTxt\nDetalhe: ${bad.description ?: "-"}\nStatus: ${bad.status} · memória do app: ${bad.pss / 1024} MB"
                if (Build.VERSION.SDK_INT >= 31 && bad.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) runCatching {
                    // o relatório nativo vem em formato binário; tira só os trechos de texto que ajudam
                    val bytes = bad.traceInputStream?.use { it.readBytes() }
                    if (bytes != null) {
                        val strings = Regex("[\\x20-\\x7e]{6,}").findAll(String(bytes, Charsets.ISO_8859_1)).map { it.value }
                            .filter { s -> listOf("signal", "SIG", "Abort", "abort", "lib", "fault", "Cause", "#0", "Build").any { s.contains(it) } }
                            .take(40).toList()
                        if (strings.isNotEmpty()) txt += "\nRelatório nativo:\n" + strings.joinToString("\n")
                    }
                }
                parts += txt
            }
        }
        if (parts.isEmpty()) return null
        val head = "JavaWorld ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        return (listOf(head) + parts).joinToString("\n\n")
    }
}
