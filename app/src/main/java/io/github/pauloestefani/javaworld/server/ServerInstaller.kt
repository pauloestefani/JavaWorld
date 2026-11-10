package io.github.pauloestefani.javaworld.server

import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.data.Loader
import io.github.pauloestefani.javaworld.data.ServerProfile
import io.github.pauloestefani.javaworld.net.Http
import io.github.pauloestefani.javaworld.net.VersionApi
import io.github.pauloestefani.javaworld.runtime.JavaHome
import io.github.pauloestefani.javaworld.runtime.JavaLauncher
import io.github.pauloestefani.javaworld.runtime.JavaRuntimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Baixa e instala os arquivos do servidor (Minecraft, Fabric, Forge, NeoForge). */
class ServerInstaller(private val app: App) {
    @Volatile var currentProcess: Process? = null
        private set

    suspend fun prepare(
        profile: ServerProfile,
        report: (String, Float?) -> Unit,
        log: (String) -> Unit,
    ): Pair<ServerProfile, JavaHome> {
        val dir = app.repo.dir(profile.id)
        dir.mkdirs()
        report("Conferindo a versão ${profile.mcVersion}…", null)
        val details = VersionApi.details(profile.mcVersion)
        val major = JavaRuntimes.pick(details.javaMajor)
        val java = JavaRuntimes.ensure(app.paths, major, report)

        if (profile.isInstalled && profile.javaMajor == major && launchFilesExist(dir, profile.launchArgs)) {
            return profile to java
        }

        val loaderVersion = profile.loaderVersion
            ?: VersionApi.latestLoaderVersion(profile.loader, profile.mcVersion)
        if (profile.loader != Loader.VANILLA && loaderVersion == null) {
            throw IOException("O ${profile.loader.label} ainda não tem versão para o Minecraft ${profile.mcVersion}")
        }

        val args: List<String> = when (profile.loader) {
            Loader.VANILLA -> {
                val url = details.serverUrl ?: throw IOException("A versão ${profile.mcVersion} não tem servidor oficial")
                val jar = File(dir, "server.jar")
                downloadWithReport("Baixando o Minecraft ${profile.mcVersion}", url, jar, report)
                if (!details.sha1.isNullOrBlank() && sha1(jar) != details.sha1) {
                    jar.delete()
                    throw IOException("O arquivo do Minecraft veio corrompido, tente de novo")
                }
                log("✔ Minecraft ${profile.mcVersion} baixado")
                listOf("-jar", "server.jar", "nogui")
            }
            Loader.FABRIC -> {
                val installer = VersionApi.fabricInstaller()
                val url = VersionApi.fabricServerJarUrl(profile.mcVersion, loaderVersion!!, installer)
                downloadWithReport("Baixando o Fabric $loaderVersion", url, File(dir, "fabric-server-launch.jar"), report)
                log("✔ Fabric $loaderVersion baixado (o Minecraft é baixado na primeira vez que ligar)")
                listOf("-jar", "fabric-server-launch.jar", "nogui")
            }
            Loader.FORGE, Loader.NEOFORGE -> {
                val url = if (profile.loader == Loader.FORGE) VersionApi.forgeInstallerUrl(profile.mcVersion, loaderVersion!!)
                else VersionApi.neoforgeInstallerUrl(loaderVersion!!)
                runModInstaller(profile, dir, java, url, report, log)
            }
        }

        val ready = profile.copy(loaderVersion = loaderVersion, javaMajor = major, launchArgs = args)
            .let { it.copy(installedKey = it.installKey) }
        app.repo.save(ready)
        return ready to java
    }

    private suspend fun runModInstaller(
        profile: ServerProfile,
        dir: File,
        java: JavaHome,
        url: String,
        report: (String, Float?) -> Unit,
        log: (String) -> Unit,
    ): List<String> = withContext(Dispatchers.IO) {
        val label = profile.loader.label
        val installer = File(dir, "installer.jar")
        downloadWithReport("Baixando o instalador do $label", url, installer, report)
        report("Instalando o $label… (pode levar alguns minutos)", null)
        log("▶ Rodando o instalador do $label ${profile.loaderVersion ?: ""}")
        val args = JavaLauncher.jvmArgs(java, minOf(profile.ramMb, 1024), dir, app.paths.tmp) +
            listOf("-jar", installer.path, "--installServer")
        val proc = JavaLauncher.process(app.paths, java, args, dir).start()
        currentProcess = proc
        try {
            proc.inputStream.bufferedReader().forEachLine { line ->
                if (!line.startsWith("[jw-pid]")) log(line)
            }
            val code = proc.waitFor()
            if (code != 0) throw IOException("O instalador do $label falhou (código $code). Veja o console.")
        } finally {
            currentProcess = null
            proc.destroy()
        }
        installer.delete()
        File(dir, "installer.jar.log").delete()
        log("✔ $label instalado")
        detectModLaunch(dir) ?: throw IOException("O $label foi instalado, mas não achei como iniciar ele")
    }

    fun cancel() {
        currentProcess?.destroy()
    }

    private suspend fun downloadWithReport(title: String, url: String, dest: File, report: (String, Float?) -> Unit) {
        report("$title…", 0f)
        Http.download(url, dest) { done, total ->
            report(
                "$title… ${mb(done)}${if (total > 0) " de ${mb(total)}" else ""}",
                if (total > 0) done.toFloat() / total else null
            )
        }
    }

    companion object {
        /** Descobre o comando de início do Forge/NeoForge a partir do run.sh que o instalador cria. */
        fun detectModLaunch(dir: File): List<String>? {
            val runSh = File(dir, "run.sh")
            if (runSh.exists()) {
                val line = runSh.readLines()
                    .map { it.trim() }
                    .filter { !it.startsWith("#") }
                    .map { it.removePrefix("exec ").trim() }
                    .lastOrNull { it.startsWith("java ") && !it.contains("--onlyCheckJava") }
                if (line != null) {
                    val tokens = line.removePrefix("java ").trim().split(Regex("\\s+"))
                        .map { it.trim('"') }
                        .filter { it.isNotBlank() && it != "\$@" && it != "||" }
                    if (tokens.isNotEmpty()) return tokens + "nogui"
                }
            }
            // Versões antigas do Forge (até 1.16) geram um .jar para rodar direto.
            val jar = dir.listFiles()?.filter { f ->
                f.isFile && f.name.endsWith(".jar") && !f.name.contains("installer") &&
                    (f.name.startsWith("forge-") || f.name.startsWith("neoforge-"))
            }?.maxByOrNull { it.length() }
            if (jar != null) return listOf("-jar", jar.name, "nogui")
            return null
        }

        fun launchFilesExist(dir: File, args: List<String>): Boolean {
            if (args.isEmpty()) return false
            for ((i, a) in args.withIndex()) {
                if (a.startsWith("@") && !File(dir, a.substring(1)).exists()) return false
                if (a == "-jar" && i + 1 < args.size && !File(dir, args[i + 1]).exists()) return false
            }
            return true
        }

        private fun mb(bytes: Long) = "%.1f MB".format(bytes / 1048576.0)

        fun sha1(f: File): String {
            val md = MessageDigest.getInstance("SHA-1")
            f.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    md.update(buf, 0, r)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
