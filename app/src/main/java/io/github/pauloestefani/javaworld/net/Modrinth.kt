package io.github.pauloestefani.javaworld.net

import org.json.JSONArray

data class ModHit(
    val projectId: String,
    val slug: String,
    val title: String,
    val description: String,
    val iconUrl: String?,
    val downloads: Long,
    val author: String,
)

data class ModFileInfo(
    val projectId: String,
    val versionId: String,
    val versionNumber: String,
    val url: String,
    val filename: String,
    val size: Long,
    val requiredDeps: List<String>,
)

/** Busca e baixa mods do Modrinth (modrinth.com). */
object Modrinth {
    private const val API = "https://api.modrinth.com/v2"

    suspend fun search(query: String, loader: String, mc: String, offset: Int = 0, limit: Int = 20): List<ModHit> {
        val facets = JSONArray().apply {
            put(JSONArray().put("project_type:mod"))
            put(JSONArray().put("categories:$loader"))
            put(JSONArray().put("versions:$mc"))
            put(JSONArray().put("server_side:required").put("server_side:optional"))
        }
        val index = if (query.isBlank()) "downloads" else "relevance"
        val url = "$API/search?query=${Http.enc(query)}&limit=$limit&offset=$offset&index=$index&facets=${Http.enc(facets.toString())}"
        val hits = Http.json(url).getJSONArray("hits")
        return List(hits.length()) { i ->
            val o = hits.getJSONObject(i)
            ModHit(
                projectId = o.getString("project_id"),
                slug = o.optString("slug"),
                title = o.optString("title"),
                description = o.optString("description"),
                iconUrl = o.optString("icon_url").takeIf { it.isNotBlank() },
                downloads = o.optLong("downloads"),
                author = o.optString("author"),
            )
        }
    }

    /** A melhor versão do mod para esse carregador e versão do Minecraft. */
    suspend fun bestFile(projectIdOrSlug: String, loader: String, mc: String): ModFileInfo? {
        val loaders = JSONArray().put(loader).toString()
        val games = JSONArray().put(mc).toString()
        val url = "$API/project/${Http.enc(projectIdOrSlug)}/version?loaders=${Http.enc(loaders)}&game_versions=${Http.enc(games)}"
        val arr = Http.jsonArray(url)
        if (arr.length() == 0) return null
        val versions = List(arr.length()) { arr.getJSONObject(it) }
        val v = versions.firstOrNull { it.optString("version_type") == "release" } ?: versions.first()
        val files = v.getJSONArray("files")
        if (files.length() == 0) return null
        val all = List(files.length()) { files.getJSONObject(it) }
        val f = all.firstOrNull { it.optBoolean("primary") } ?: all.first()
        val deps = v.optJSONArray("dependencies")
        val required = mutableListOf<String>()
        if (deps != null) {
            for (i in 0 until deps.length()) {
                val d = deps.getJSONObject(i)
                val pid = d.optString("project_id")
                if (d.optString("dependency_type") == "required" && pid.isNotBlank() && pid != "null") required += pid
            }
        }
        return ModFileInfo(
            projectId = v.getString("project_id"),
            versionId = v.getString("id"),
            versionNumber = v.optString("version_number"),
            url = f.getString("url"),
            filename = f.getString("filename"),
            size = f.optLong("size"),
            requiredDeps = required,
        )
    }

    suspend fun projectTitle(id: String): String =
        runCatching { Http.json("$API/project/${Http.enc(id)}").optString("title", id) }.getOrDefault(id)
}
