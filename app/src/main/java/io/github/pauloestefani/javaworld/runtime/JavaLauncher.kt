package io.github.pauloestefani.javaworld.runtime

import io.github.pauloestefani.javaworld.Paths
import java.io.File
import java.util.TimeZone

/** Monta o processo do Java usando o launcher nativo que vem dentro do APK. */
object JavaLauncher {
    fun jvmArgs(java: JavaHome, ramMb: Int, workDir: File, tmp: File): List<String> = buildList {
        add("-Xms${minOf(ramMb, 512)}M")
        add("-Xmx${ramMb}M")
        add("-XX:-UsePerfData")
        add("-XX:ActiveProcessorCount=${Runtime.getRuntime().availableProcessors().coerceAtLeast(1)}")
        add("-XX:+UseG1GC")
        add("-XX:MaxGCPauseMillis=200")
        add("-XX:+DisableExplicitGC")
        add("-Djava.home=${java.home.path}")
        add("-Djava.io.tmpdir=${tmp.path}")
        add("-Duser.home=${workDir.path}")
        add("-Duser.timezone=${TimeZone.getDefault().id}")
        add("-Djava.awt.headless=true")
        add("-Dfile.encoding=UTF-8")
        add("-Dstdout.encoding=UTF-8")
        add("-Dos.name=Linux")
        add("-Dlog4j2.formatMsgNoLookups=true")
    }

    fun process(paths: Paths, java: JavaHome, args: List<String>, workDir: File): ProcessBuilder {
        val pb = ProcessBuilder(listOf(paths.launcher.path) + expandArgFiles(args, workDir))
            .directory(workDir)
            .redirectErrorStream(true)
        val env = pb.environment()
        listOf("CLASSPATH", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "LD_PRELOAD").forEach { env.remove(it) }
        env["JAVA_HOME"] = java.home.path
        env["JW_LIBJLI"] = java.libJli.path
        env["JW_LIBJVM"] = java.libJvm.path
        env["LD_LIBRARY_PATH"] = java.ldLibraryPath(paths.nativeDir)
        // Garante que o Java use o libc++ que vem no app, e não um do sistema/fabricante.
        val cxx = File(paths.nativeDir, "libc++_shared.so")
        if (cxx.exists()) env["JW_PRELOAD"] = cxx.path
        env["HOME"] = workDir.path
        env["TMPDIR"] = paths.tmp.path
        env["TERM"] = "dumb"
        return pb
    }

    /**
     * O comando "java" normal expande os arquivos @argumentos (usados pelo Forge/NeoForge)
     * antes de iniciar a JVM. Como chamamos a JVM direto, fazemos isso aqui.
     */
    fun expandArgFiles(args: List<String>, workDir: File): List<String> = args.flatMap { a ->
        when {
            a.startsWith("@@") -> listOf(a.substring(1))
            a.startsWith("@") && a.length > 1 -> {
                val f = File(a.substring(1)).let { if (it.isAbsolute) it else File(workDir, a.substring(1)) }
                if (f.isFile) parseArgFile(f.readText()) else listOf(a)
            }
            else -> listOf(a)
        }
    }

    /** Lê um arquivo de argumentos no formato do Java (espaços, aspas e comentários com #). */
    fun parseArgFile(text: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inToken = false
        var quote: Char? = null
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quote != null -> {
                    if (c == quote) quote = null
                    else if (c == '\\' && i + 1 < text.length) { i++; cur.append(text[i]) }
                    else cur.append(c)
                }
                c == '"' || c == '\'' -> { quote = c; inToken = true }
                c == '#' && !inToken -> { while (i < text.length && text[i] != '\n') i++ }
                c.isWhitespace() -> {
                    if (inToken) { out += cur.toString(); cur.clear(); inToken = false }
                }
                else -> { cur.append(c); inToken = true }
            }
            i++
        }
        if (inToken) out += cur.toString()
        return out
    }
}
