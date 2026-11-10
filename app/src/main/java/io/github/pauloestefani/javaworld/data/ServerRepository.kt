package io.github.pauloestefani.javaworld.data

import io.github.pauloestefani.javaworld.Paths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ServerRepository(private val paths: Paths) {
    private val _servers = MutableStateFlow<List<ServerProfile>>(emptyList())
    val servers: StateFlow<List<ServerProfile>> = _servers.asStateFlow()

    init {
        reload()
    }

    @Synchronized
    fun reload() {
        val list = paths.servers.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val f = File(dir, PROFILE)
                if (!f.exists()) null else runCatching { ServerProfile.fromJson(JSONObject(f.readText())) }.getOrNull()
            }
            ?.sortedByDescending { it.createdAt }
            .orEmpty()
        _servers.value = list
    }

    fun get(id: String): ServerProfile? = _servers.value.firstOrNull { it.id == id }

    fun dir(id: String): File = paths.serverDir(id)

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(10)

    @Synchronized
    fun save(profile: ServerProfile) {
        val dir = dir(profile.id)
        dir.mkdirs()
        val tmp = File(dir, "$PROFILE.tmp")
        tmp.writeText(profile.toJson().toString(2))
        val target = File(dir, PROFILE)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        val others = _servers.value.filter { it.id != profile.id }
        _servers.value = (others + profile).sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun update(id: String, transform: (ServerProfile) -> ServerProfile): ServerProfile? {
        val current = get(id) ?: return null
        val next = transform(current)
        save(next)
        return next
    }

    @Synchronized
    fun delete(id: String) {
        dir(id).deleteRecursively()
        _servers.value = _servers.value.filter { it.id != id }
    }

    companion object {
        const val PROFILE = "javaworld.json"
    }
}
