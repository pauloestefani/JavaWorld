package io.github.pauloestefani.javaworld.server

import android.content.Context
import android.net.Uri
import io.github.pauloestefani.javaworld.data.PropertiesFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Exporta e importa o mundo em .zip (dá pra trazer um mundo do singleplayer). */
object WorldBackup {
    fun levelName(serverDir: File): String =
        PropertiesFile(File(serverDir, "server.properties")).get("level-name")?.takeIf { it.isNotBlank() } ?: "world"

    fun worldDirs(serverDir: File): List<File> {
        val name = levelName(serverDir)
        return listOf(name, "${name}_nether", "${name}_the_end").map { File(serverDir, it) }.filter { it.isDirectory }
    }

    fun hasWorld(serverDir: File) = File(serverDir, levelName(serverDir) + "/level.dat").exists()

    fun worldSize(serverDir: File): Long = worldDirs(serverDir).sumOf { d -> d.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    suspend fun export(ctx: Context, serverDir: File, uri: Uri) = withContext(Dispatchers.IO) {
        val dirs = worldDirs(serverDir)
        if (dirs.isEmpty()) throw IOException("Esse servidor ainda não tem mundo. Ligue ele uma vez primeiro.")
        val out = ctx.contentResolver.openOutputStream(uri) ?: throw IOException("Não consegui criar o arquivo")
        ZipOutputStream(out.buffered()).use { zip ->
            for (d in dirs) {
                d.walkTopDown().filter { it.isFile && it.name != "session.lock" }.forEach { f ->
                    val rel = f.relativeTo(serverDir).path.replace(File.separatorChar, '/')
                    zip.putNextEntry(ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Importa um .zip com um mundo (pasta com level.dat). Substitui o mundo atual. */
    suspend fun import(ctx: Context, serverDir: File, uri: Uri) = withContext(Dispatchers.IO) {
        val resolver = ctx.contentResolver
        // 1ª passada: achar onde está o level.dat dentro do zip
        var prefix: String? = null
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val n = e.name.replace('\\', '/')
                    if (!e.isDirectory && (n == "level.dat" || n.endsWith("/level.dat"))) {
                        val p = n.removeSuffix("level.dat")
                        if (prefix == null || p.length < prefix!!.length) prefix = p
                    }
                }
            }
        } ?: throw IOException("Não consegui abrir o arquivo")
        val base = prefix ?: throw IOException("Não achei um mundo de Minecraft (level.dat) dentro desse .zip")

        val name = levelName(serverDir)
        val target = File(serverDir, name)
        val tmp = File(serverDir, "$name.importando")
        tmp.deleteRecursively()
        val canon = tmp.canonicalPath
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val n = e.name.replace('\\', '/')
                    if (!n.startsWith(base) || e.isDirectory) continue
                    val rel = n.removePrefix(base)
                    if (rel.isEmpty() || rel == "session.lock") continue
                    val out = File(tmp, rel)
                    if (!out.canonicalPath.startsWith(canon + File.separator)) continue
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        listOf(name, "${name}_nether", "${name}_the_end").forEach { File(serverDir, it).deleteRecursively() }
        if (!tmp.renameTo(target)) throw IOException("Não consegui trocar o mundo")
    }

    fun deleteWorld(serverDir: File) {
        worldDirs(serverDir).forEach { it.deleteRecursively() }
    }
}
