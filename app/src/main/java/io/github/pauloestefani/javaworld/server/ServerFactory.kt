package io.github.pauloestefani.javaworld.server

import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.data.Loader
import io.github.pauloestefani.javaworld.data.PropertiesFile
import io.github.pauloestefani.javaworld.data.ServerProfile
import java.io.File

data class InitialSettings(
    val motd: String = "Servidor do JavaWorld",
    val gamemode: String = "survival",
    val difficulty: String = "normal",
    val onlineMode: Boolean = true,
    val maxPlayers: Int = 8,
    val viewDistance: Int = 8,
    val simulationDistance: Int = 6,
    val seed: String = "",
)

object ServerFactory {
    /** Porta livre: 25565 se nenhum outro servidor usa, senão a próxima. */
    fun freePort(app: App): Int {
        val used = app.repo.servers.value.map { it.port }.toSet()
        var p = 25565
        while (p in used) p++
        return p
    }

    fun create(
        app: App,
        name: String,
        loader: Loader,
        mcVersion: String,
        loaderVersion: String?,
        ramMb: Int,
        settings: InitialSettings,
    ): ServerProfile {
        val profile = ServerProfile(
            id = app.repo.newId(),
            name = name.trim().ifBlank { "Meu servidor" },
            loader = loader,
            mcVersion = mcVersion,
            loaderVersion = loaderVersion,
            ramMb = ramMb,
            port = 25565,
            createdAt = System.currentTimeMillis(),
        )
        app.repo.save(profile)
        val dir = app.repo.dir(profile.id)
        PropertiesFile(File(dir, "server.properties")).apply {
            set("motd", settings.motd)
            set("gamemode", settings.gamemode)
            set("difficulty", settings.difficulty)
            set("online-mode", settings.onlineMode.toString())
            set("max-players", settings.maxPlayers.toString())
            set("view-distance", settings.viewDistance.toString())
            set("simulation-distance", settings.simulationDistance.toString())
            set("server-port", profile.port.toString())
            set("level-seed", settings.seed)
            set("enforce-secure-profile", settings.onlineMode.toString())
            set("spawn-protection", "0")
            save()
        }
        return profile
    }
}
