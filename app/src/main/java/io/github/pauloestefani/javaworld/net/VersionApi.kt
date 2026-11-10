package io.github.pauloestefani.javaworld.net

import io.github.pauloestefani.javaworld.data.Loader
import org.json.JSONObject
import java.io.IOException

data class McVersion(val id: String, val type: String, val url: String, val releaseTime: String) {
    val isRelease: Boolean get() = type == "release"
}

data class VersionDetails(val serverUrl: String?, val sha1: String?, val javaMajor: Int)

/** Consulta as versões do Minecraft e dos carregadores de mods. */
object VersionApi {
    private const val MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val FABRIC = "https://meta.fabricmc.net/v2"
    private const val FORGE_PROMOS = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
    private const val NEOFORGE_VERSIONS =
        "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"

    @Volatile private var manifest: List<McVersion>? = null
    @Volatile private var fabricGames: Set<String>? = null
    @Volatile private var forgePromos: Map<String, String>? = null
    @Volatile private var neoVersions: List<String>? = null
    private val detailsCache = mutableMapOf<String, VersionDetails>()

    suspend fun versions(): List<McVersion> {
        manifest?.let { return it }
        val arr = Http.json(MANIFEST).getJSONArray("versions")
        val list = List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            McVersion(o.getString("id"), o.getString("type"), o.getString("url"), o.optString("releaseTime"))
        }
        manifest = list
        return list
    }

    suspend fun details(id: String): VersionDetails {
        synchronized(detailsCache) { detailsCache[id] }?.let { return it }
        val v = versions().firstOrNull { it.id == id } ?: throw IOException("Versão $id não encontrada")
        val o = Http.json(v.url)
        val server = o.optJSONObject("downloads")?.optJSONObject("server")
        val java = o.optJSONObject("javaVersion")?.optInt("majorVersion", 8) ?: 8
        val d = VersionDetails(server?.optString("url"), server?.optString("sha1"), java)
        synchronized(detailsCache) { detailsCache[id] = d }
        return d
    }

    /** Versões que o carregador suporta (null = todas as do Minecraft). */
    suspend fun supported(loader: Loader): Set<String>? = when (loader) {
        Loader.VANILLA -> null
        Loader.FABRIC -> fabricGameVersions()
        Loader.FORGE -> forgePromos().keys.map { it.substringBeforeLast('-') }.filter(::isAtLeast112).toSet()
        Loader.NEOFORGE -> neoforgeVersions().mapNotNull(::neoforgeMc).toSet()
    }

    suspend fun latestLoaderVersion(loader: Loader, mc: String): String? = when (loader) {
        Loader.VANILLA -> null
        Loader.FABRIC -> fabricLoader(mc)
        Loader.FORGE -> forgePromos()["$mc-recommended"] ?: forgePromos()["$mc-latest"]
        Loader.NEOFORGE -> {
            val all = neoforgeVersions().filter { neoforgeMc(it) == mc }
            all.lastOrNull { !it.contains("beta") && !it.contains("alpha") } ?: all.lastOrNull()
        }
    }

    // ---------- Fabric ----------
    private suspend fun fabricGameVersions(): Set<String> {
        fabricGames?.let { return it }
        val arr = Http.jsonArray("$FABRIC/versions/game")
        val set = (0 until arr.length()).map { arr.getJSONObject(it) }.map { it.getString("version") }.toSet()
        fabricGames = set
        return set
    }

    private suspend fun fabricLoader(mc: String): String? {
        val arr = Http.jsonArray("$FABRIC/versions/loader/${Http.enc(mc)}")
        if (arr.length() == 0) return null
        val all = (0 until arr.length()).map { arr.getJSONObject(it).getJSONObject("loader") }
        return (all.firstOrNull { it.optBoolean("stable") } ?: all.first()).getString("version")
    }

    suspend fun fabricInstaller(): String {
        val arr = Http.jsonArray("$FABRIC/versions/installer")
        val all = (0 until arr.length()).map { arr.getJSONObject(it) }
        return (all.firstOrNull { it.optBoolean("stable") } ?: all.first()).getString("version")
    }

    fun fabricServerJarUrl(mc: String, loader: String, installer: String) =
        "$FABRIC/versions/loader/${Http.enc(mc)}/$loader/$installer/server/jar"

    // ---------- Forge ----------
    private suspend fun forgePromos(): Map<String, String> {
        forgePromos?.let { return it }
        val promos = Http.json(FORGE_PROMOS).getJSONObject("promos")
        val map = promos.keys().asSequence().associateWith { promos.getString(it) }
        forgePromos = map
        return map
    }

    fun forgeInstallerUrl(mc: String, forge: String) =
        "https://maven.minecraftforge.net/net/minecraftforge/forge/$mc-$forge/forge-$mc-$forge-installer.jar"

    private fun isAtLeast112(v: String): Boolean {
        val p = v.split('.', '_', '-')
        val a = p.getOrNull(0)?.toIntOrNull() ?: return false
        if (a >= 2) return true // novo esquema de versões (26.x)
        val b = p.getOrNull(1)?.toIntOrNull() ?: return false
        return b >= 12
    }

    // ---------- NeoForge ----------
    private suspend fun neoforgeVersions(): List<String> {
        neoVersions?.let { return it }
        val arr = Http.json(NEOFORGE_VERSIONS).getJSONArray("versions")
        val list = List(arr.length()) { arr.getString(it) }
        neoVersions = list
        return list
    }

    fun neoforgeInstallerUrl(v: String) =
        "https://maven.neoforged.net/releases/net/neoforged/neoforge/$v/neoforge-$v-installer.jar"

    /** Descobre a versão do Minecraft a partir da versão do NeoForge. */
    fun neoforgeMc(v: String): String? {
        val p = v.substringBefore('-').split('.')
        val major = p.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = p.getOrNull(1)?.toIntOrNull() ?: return null
        if (major >= 25) {
            // Esquema novo: 26.3.0.102 -> 26.3 ; 26.1.2.5 -> 26.1.2
            if (p.size < 4) return null
            val patch = p[2].toIntOrNull() ?: return null
            return if (patch == 0) "$major.$minor" else "$major.$minor.$patch"
        }
        if (major < 20 || p.size != 3) return null
        if (major == 20 && minor < 2) return null
        return if (minor == 0) "1.$major" else "1.$major.$minor"
    }

    fun clearCache() {
        manifest = null; fabricGames = null; forgePromos = null; neoVersions = null
        synchronized(detailsCache) { detailsCache.clear() }
    }
}

fun JSONObject.optIntOrNull(key: String): Int? = if (has(key)) optInt(key) else null
