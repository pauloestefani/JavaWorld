package io.github.pauloestefani.javaworld.runtime

import android.os.Build
import io.github.pauloestefani.javaworld.Paths
import io.github.pauloestefani.javaworld.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

data class JavaHome(val major: Int, val home: File, val libJli: File, val libJvm: File) {
    /**
     * A pasta do libjvm precisa vir primeiro, senão o launcher do Java tenta se reiniciar.
     * As bibliotecas do app vêm antes das do sistema: alguns celulares (ex.: Xiaomi) têm um
     * libc++_shared.so antigo em /vendor que não serve para o Java 25.
     */
    fun ldLibraryPath(nativeDir: File): String {
        val jvmDir = libJvm.parentFile!!
        val libDir = jvmDir.parentFile!!
        val system = if (android.os.Process.is64Bit()) "/system/lib64" else "/system/lib"
        return listOf(jvmDir.path, File(libDir, "jli").path, libDir.path, nativeDir.path, system).joinToString(":")
    }
}

/**
 * Baixa e instala o Java (OpenJDK compilado para Android) que cada versão do Minecraft precisa.
 * Os pacotes vêm do projeto angelauramc-openjdk-build (o mesmo Java usado pelo Amethyst/PojavLauncher).
 */
object JavaRuntimes {
    val available = listOf(8, 17, 21, 25)

    fun pick(required: Int): Int = when {
        required <= 8 -> 8
        required <= 17 -> 17
        required <= 21 -> 21
        else -> 25
    }

    fun arch(): String? {
        for (abi in Build.SUPPORTED_ABIS) {
            when (abi) {
                "arm64-v8a" -> return "arm64"
                "x86_64" -> return "x86_64"
            }
        }
        return null
    }

    private fun urls(major: Int, arch: String): List<String> = listOf(
        "https://github.com/pauloestefani/JavaWorld/releases/download/runtimes/jre$major-android-$arch.tar.xz",
        "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/download_jre$major/jre$major-android-$arch.tar.xz",
    )

    fun dir(paths: Paths, major: Int) = File(paths.runtimes, "jre$major")

    fun installed(paths: Paths, major: Int): JavaHome? {
        val home = dir(paths, major)
        if (!File(home, ".ok").exists()) return null
        val libs = home.walkTopDown().filter { it.isFile && it.name.endsWith(".so") }.toList()
        val jvm = libs.filter { it.name == "libjvm.so" }.let { l -> l.firstOrNull { it.parentFile?.name == "server" } ?: l.firstOrNull() }
        val jli = libs.firstOrNull { it.name == "libjli.so" }
        if (jvm == null || jli == null) return null
        return JavaHome(major, home, jli, jvm)
    }

    fun installedMajors(paths: Paths): List<Int> = available.filter { File(dir(paths, it), ".ok").exists() }

    fun sizeOnDisk(paths: Paths, major: Int): Long =
        dir(paths, major).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun remove(paths: Paths, major: Int) {
        dir(paths, major).deleteRecursively()
    }

    suspend fun ensure(paths: Paths, major: Int, report: (String, Float?) -> Unit): JavaHome {
        installed(paths, major)?.let { return it }
        val arch = arch() ?: throw IOException("Esse processador não é suportado (precisa ser 64 bits).")
        val archive = File(paths.downloads, "jre$major-$arch.tar.xz")
        var lastError: Throwable? = null
        if (!archive.exists()) {
            for (url in urls(major, arch)) {
                try {
                    report("Baixando o Java $major…", 0f)
                    Http.download(url, archive) { done, total ->
                        report("Baixando o Java $major… ${mb(done)}${if (total > 0) " de ${mb(total)}" else ""}",
                            if (total > 0) done.toFloat() / total else null)
                    }
                    lastError = null
                    break
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    lastError = e
                }
            }
        }
        if (!archive.exists()) throw IOException("Não consegui baixar o Java $major: ${lastError?.message}")

        report("Instalando o Java $major…", 0f)
        val target = dir(paths, major)
        val tmp = File(paths.runtimes, "jre$major.tmp")
        withContext(Dispatchers.IO) {
            tmp.deleteRecursively()
            target.deleteRecursively()
            val total = archive.length().toFloat()
            try {
                CountingStream(archive.inputStream()) { read -> report("Instalando o Java $major…", read / total) }.use { raw ->
                    XZInputStream(BufferedInputStream(raw, 1 shl 16)).use { xz ->
                        TarExtractor.extract(BufferedInputStream(xz, 1 shl 16), tmp, null)
                    }
                }
            } catch (e: Throwable) {
                tmp.deleteRecursively()
                archive.delete()
                throw IOException("O pacote do Java $major veio corrompido, tente de novo. (${e.message})", e)
            }
            if (!tmp.renameTo(target)) throw IOException("Não consegui instalar o Java $major")
            File(target, ".ok").writeText(System.currentTimeMillis().toString())
            archive.delete()
        }
        return installed(paths, major) ?: throw IOException("O Java $major foi instalado mas está incompleto")
    }

    private fun mb(bytes: Long) = "%.0f MB".format(bytes / 1048576.0)
}

private class CountingStream(input: InputStream, private val onRead: (Long) -> Unit) : FilterInputStream(input) {
    private var count = 0L
    private var lastReport = 0L

    override fun read(): Int {
        val r = super.read()
        if (r >= 0) bump(1)
        return r
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val r = super.read(b, off, len)
        if (r > 0) bump(r.toLong())
        return r
    }

    private fun bump(n: Long) {
        count += n
        val now = System.currentTimeMillis()
        if (now - lastReport > 250) {
            lastReport = now
            onRead(count)
        }
    }
}
