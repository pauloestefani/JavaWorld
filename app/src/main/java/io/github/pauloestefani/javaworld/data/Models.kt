package io.github.pauloestefani.javaworld.data

import org.json.JSONArray
import org.json.JSONObject

enum class Loader(val id: String, val label: String, val modrinth: String?, val tagline: String) {
    VANILLA("vanilla", "Vanilla", null, "Minecraft puro, sem mods"),
    FABRIC("fabric", "Fabric", "fabric", "Leve e rápido, ótimo pra celular"),
    FORGE("forge", "Forge", "forge", "O clássico, com milhares de mods"),
    NEOFORGE("neoforge", "NeoForge", "neoforge", "A versão moderna do Forge");

    val supportsMods: Boolean get() = this != VANILLA

    companion object {
        fun of(id: String?): Loader = entries.firstOrNull { it.id == id } ?: VANILLA
    }
}

data class ServerProfile(
    val id: String,
    val name: String,
    val loader: Loader,
    val mcVersion: String,
    val loaderVersion: String?,
    val ramMb: Int,
    val port: Int,
    val createdAt: Long,
    val installedKey: String? = null,
    val javaMajor: Int = 0,
    val launchArgs: List<String> = emptyList(),
    val lastStarted: Long = 0,
) {
    val installKey: String get() = "${loader.id}:$mcVersion:${loaderVersion.orEmpty()}"
    val isInstalled: Boolean get() = installedKey == installKey && launchArgs.isNotEmpty()
    val typeLabel: String get() = "${loader.label} $mcVersion"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("loader", loader.id)
        put("mcVersion", mcVersion)
        put("loaderVersion", loaderVersion ?: JSONObject.NULL)
        put("ramMb", ramMb)
        put("port", port)
        put("createdAt", createdAt)
        put("installedKey", installedKey ?: JSONObject.NULL)
        put("javaMajor", javaMajor)
        put("launchArgs", JSONArray(launchArgs))
        put("lastStarted", lastStarted)
    }

    companion object {
        fun fromJson(o: JSONObject): ServerProfile {
            val args = o.optJSONArray("launchArgs")
            return ServerProfile(
                id = o.getString("id"),
                name = o.optString("name", "Servidor"),
                loader = Loader.of(o.optString("loader")),
                mcVersion = o.getString("mcVersion"),
                loaderVersion = o.optStringOrNull("loaderVersion"),
                ramMb = o.optInt("ramMb", 1024),
                port = o.optInt("port", 25565),
                createdAt = o.optLong("createdAt", 0),
                installedKey = o.optStringOrNull("installedKey"),
                javaMajor = o.optInt("javaMajor", 0),
                launchArgs = if (args == null) emptyList() else List(args.length()) { args.getString(it) },
                lastStarted = o.optLong("lastStarted", 0),
            )
        }
    }
}

fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
