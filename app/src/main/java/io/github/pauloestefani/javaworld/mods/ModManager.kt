package io.github.pauloestefani.javaworld.mods

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.pauloestefani.javaworld.data.ServerProfile
import io.github.pauloestefani.javaworld.net.Http
import io.github.pauloestefani.javaworld.net.Modrinth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class InstalledMod(val file: File, val enabled: Boolean, val projectId: String?) {
    val displayName: String get() = file.name.removeSuffix(".disabled").removeSuffix(".jar")
    val size: Long get() = file.length()
}

/** Cuida da pasta mods/ de um servidor. */
class ModManager(private val serverDir: File) {
    val modsDir = File(serverDir, "mods")
    private val indexFile = File(modsDir, ".javaworld-mods.json")

    private fun readIndex(): JSONObject = runCatching { JSONObject(indexFile.readText()) }.getOrElse { JSONObject() }

    private fun writeIndex(o: JSONObject) {
        modsDir.mkdirs()
        indexFile.writeText(o.toString(2))
    }

    fun list(): List<InstalledMod> {
        val index = readIndex()
        val byFile = index.keys().asSequence().associateBy { index.getString(it) }
        return modsDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".jar") || it.name.endsWith(".jar.disabled")) }
            ?.map { f ->
                val base = f.name.removeSuffix(".disabled")
                InstalledMod(f, !f.name.endsWith(".disabled"), byFile[base])
            }
            ?.sortedBy { it.displayName.lowercase() }
            .orEmpty()
    }

    fun installedProjects(): Set<String> {
        val index = readIndex()
        return index.keys().asSequence().filter { File(modsDir, index.getString(it)).exists() || File(modsDir, index.getString(it) + ".disabled").exists() }.toSet()
    }

    fun setEnabled(mod: InstalledMod, enabled: Boolean) {
        if (mod.enabled == enabled) return
        val target = if (enabled) File(modsDir, mod.file.name.removeSuffix(".disabled")) else File(modsDir, mod.file.name + ".disabled")
        mod.file.renameTo(target)
    }

    fun delete(mod: InstalledMod) {
        mod.file.delete()
        val index = readIndex()
        val base = mod.file.name.removeSuffix(".disabled")
        index.keys().asSequence().toList().filter { index.getString(it) == base }.forEach { index.remove(it) }
        writeIndex(index)
    }

    suspend fun importFromUri(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = ctx.contentResolver
        var name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "mod-${System.currentTimeMillis()}.jar"
        name = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (!name.endsWith(".jar")) throw IOException("\"$name\" não é um arquivo .jar de mod")
        modsDir.mkdirs()
        val dest = File(modsDir, name)
        resolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            ?: throw IOException("Não consegui abrir o arquivo")
        name
    }

    /**
     * Instala um mod do Modrinth e as dependências obrigatórias dele.
     * Devolve os nomes dos arquivos instalados.
     */
    suspend fun installFromModrinth(
        projectId: String,
        profile: ServerProfile,
        onStatus: (String) -> Unit = {},
    ): List<String> {
        val loader = profile.loader.modrinth ?: throw IOException("Servidor Vanilla não aceita mods")
        val done = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val already = installedProjects()

        suspend fun install(id: String, isDependency: Boolean) {
            if (!visited.add(id)) return
            if (id in already) return
            val file = Modrinth.bestFile(id, loader, profile.mcVersion)
            if (file == null) {
                if (isDependency) {
                    val title = Modrinth.projectTitle(id)
                    throw IOException("A dependência \"$title\" não tem versão para ${profile.loader.label} ${profile.mcVersion}")
                }
                throw IOException("Esse mod não tem versão para ${profile.loader.label} ${profile.mcVersion}")
            }
            if (file.projectId in already) return
            onStatus("Baixando ${file.filename}…")
            Http.download(file.url, File(modsDir, file.filename))
            val index = readIndex()
            index.put(file.projectId, file.filename)
            writeIndex(index)
            done += file.filename
            for (dep in file.requiredDeps) install(dep, true)
        }

        modsDir.mkdirs()
        install(projectId, false)
        return done
    }

    companion object {
        /** Mods que deixam o servidor mais leve, por carregador. */
        fun performancePack(loader: String): List<String> = when (loader) {
            "fabric" -> listOf("lithium", "ferrite-core", "krypton", "modernfix")
            "neoforge" -> listOf("lithium", "ferrite-core", "modernfix")
            "forge" -> listOf("ferrite-core", "modernfix")
            else -> emptyList()
        }
    }
}
