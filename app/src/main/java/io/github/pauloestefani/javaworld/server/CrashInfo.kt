package io.github.pauloestefani.javaworld.server

import android.os.Build
import java.io.File
import java.util.concurrent.TimeUnit

/** Junta pistas de por que o Java fechou, para mostrar no console. */
object CrashInfo {
    /** No Android, quando um processo morre por um sinal, o código de saída é o número do sinal. */
    fun explain(code: Int): String? = when (code) {
        4 -> "O Java travou: instrução não suportada pelo processador (sinal 4)."
        6 -> "O Java travou e foi abortado (sinal 6)."
        7, 11 -> "O Java travou por um erro de memória (sinal $code)."
        9 -> "O Android fechou o servidor à força. Normalmente é falta de RAM: tente usar menos memória nos Ajustes."
        90, 91, 92 -> "Não consegui carregar o Java (código $code). Tente remover o Java na aba Celular e ligar de novo."
        else -> null
    }

    fun collect(pid: Int, serverDir: File?): List<String> {
        val out = mutableListOf<String>()
        out += "Celular: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.SUPPORTED_ABIS.firstOrNull()}"
        // Relatório de erro do Java (hs_err_pid*.log), se existir
        val hsErr = serverDir?.listFiles()?.filter { it.name.startsWith("hs_err_pid") }?.maxByOrNull { it.lastModified() }
        if (hsErr != null && System.currentTimeMillis() - hsErr.lastModified() < 10 * 60_000) {
            out += "Relatório do Java (${hsErr.name}):"
            out += runCatching { hsErr.readLines().filter { it.isNotBlank() }.take(40) }.getOrDefault(emptyList())
        }
        // Mensagens do Android sobre o processo (o app pode ler os próprios logs)
        if (pid > 0) {
            val lines = runCatching {
                val p = ProcessBuilder("logcat", "-d", "-v", "brief", "--pid=$pid", "-t", "600")
                    .redirectErrorStream(true).start()
                val text = p.inputStream.bufferedReader().readLines()
                p.waitFor(5, TimeUnit.SECONDS)
                text
            }.getOrDefault(emptyList())
            val relevant = lines.filter {
                it.startsWith("F/") || it.startsWith("E/") || it.startsWith("W/libc") ||
                    it.contains("Abort", true) || it.contains("fdsan") || it.contains("tag")
            }.takeLast(30)
            if (relevant.isNotEmpty()) {
                out += "Mensagens do Android:"
                out += relevant
            }
        }
        return out
    }
}
