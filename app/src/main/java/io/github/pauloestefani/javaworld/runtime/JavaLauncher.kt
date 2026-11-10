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
        val pb = ProcessBuilder(listOf(paths.launcher.path) + args)
            .directory(workDir)
            .redirectErrorStream(true)
        val env = pb.environment()
        listOf("CLASSPATH", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "LD_PRELOAD").forEach { env.remove(it) }
        env["JAVA_HOME"] = java.home.path
        env["JW_LIBJLI"] = java.libJli.path
        env["JW_LIBJVM"] = java.libJvm.path
        env["LD_LIBRARY_PATH"] = java.ldLibraryPath + ":" + paths.nativeDir.path
        env["HOME"] = workDir.path
        env["TMPDIR"] = paths.tmp.path
        env["TERM"] = "dumb"
        return pb
    }
}
