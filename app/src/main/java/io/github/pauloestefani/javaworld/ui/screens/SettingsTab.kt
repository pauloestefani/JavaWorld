package io.github.pauloestefani.javaworld.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.data.Loader
import io.github.pauloestefani.javaworld.data.PropertiesFile
import io.github.pauloestefani.javaworld.data.ServerProfile
import io.github.pauloestefani.javaworld.net.McVersion
import io.github.pauloestefani.javaworld.net.VersionApi
import io.github.pauloestefani.javaworld.server.WorldBackup
import io.github.pauloestefani.javaworld.ui.Fmt
import io.github.pauloestefani.javaworld.ui.components.ConfirmDialog
import io.github.pauloestefani.javaworld.ui.components.SectionCard
import io.github.pauloestefani.javaworld.ui.components.SelectField
import io.github.pauloestefani.javaworld.ui.theme.JW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private class PropsState(file: File) {
    val props = PropertiesFile(file)
    var motd by mutableStateOf(props.get("motd", "A Minecraft Server"))
    var gamemode by mutableStateOf(props.get("gamemode", "survival"))
    var difficulty by mutableStateOf(props.get("difficulty", "normal"))
    var online by mutableStateOf(props.get("online-mode", "true") == "true")
    var pvp by mutableStateOf(props.get("pvp", "true") == "true")
    var whitelist by mutableStateOf(props.get("white-list", "false") == "true")
    var flight by mutableStateOf(props.get("allow-flight", "false") == "true")
    var commandBlocks by mutableStateOf(props.get("enable-command-block", "false") == "true")
    var maxPlayers by mutableIntStateOf(props.get("max-players", "8").toIntOrNull() ?: 8)
    var view by mutableIntStateOf(props.get("view-distance", "8").toIntOrNull() ?: 8)
    var sim by mutableIntStateOf(props.get("simulation-distance", "6").toIntOrNull() ?: 6)
    var seed by mutableStateOf(props.get("level-seed", ""))

    fun save() {
        props.set("motd", motd)
        props.set("gamemode", gamemode)
        props.set("difficulty", difficulty)
        props.set("online-mode", online.toString())
        props.set("enforce-secure-profile", online.toString())
        props.set("pvp", pvp.toString())
        props.set("white-list", whitelist.toString())
        props.set("enforce-whitelist", whitelist.toString())
        props.set("allow-flight", flight.toString())
        props.set("enable-command-block", commandBlocks.toString())
        props.set("max-players", maxPlayers.toString())
        props.set("view-distance", view.toString())
        props.set("simulation-distance", sim.toString())
        props.set("level-seed", seed)
        props.save()
    }
}

@Composable
fun SettingsTab(profile: ServerProfile) {
    val app = App.instance
    val ctx = LocalContext.current
    val dir = app.repo.dir(profile.id)
    val run by app.servers.info.collectAsState()
    val running = run.serverId == profile.id && run.status.active
    val st = remember(profile.id) { PropsState(File(dir, "server.properties")) }
    var ram by remember(profile.id) { mutableIntStateOf(profile.ramMb) }
    var version by remember(profile.id) { mutableStateOf(profile.mcVersion) }
    var versions by remember { mutableStateOf<List<McVersion>>(emptyList()) }
    var pickVersion by remember { mutableStateOf(false) }
    var confirmVersion by remember { mutableStateOf<String?>(null) }
    var confirmResetWorld by remember { mutableStateOf(false) }
    var worldSize by remember { mutableStateOf<Long?>(null) }
    val maxRam = remember { app.monitor.maxRamMb() }

    LaunchedEffect(profile.id) {
        worldSize = withContext(Dispatchers.IO) { WorldBackup.worldSize(dir) }
        runCatching {
            val supported = VersionApi.supported(profile.loader)
            versions = VersionApi.versions().filter { it.isRelease && (supported == null || it.id in supported) }
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (running) {
            Text("O servidor está ligado: as mudanças valem quando reiniciar.", color = JW.Gold, style = MaterialTheme.typography.bodySmall)
        }

        SectionCard(title = "Jogo", icon = Icons.Rounded.SportsEsports) {
            OutlinedTextField(st.motd, { st.motd = it.take(59) }, label = { Text("Mensagem na lista de servidores (MOTD)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
            SelectField("Modo de jogo", st.gamemode, gamemodes, { st.gamemode = it })
            SelectField("Dificuldade", st.difficulty, difficulties, { st.difficulty = it })
            Toggle("PvP (jogadores se atacarem)", st.pvp) { st.pvp = it }
            Toggle("Permitir voar (evita expulsar quem usa mods de voo)", st.flight) { st.flight = it }
            Toggle("Blocos de comando", st.commandBlocks) { st.commandBlocks = it }
            LabeledSlider("Máximo de jogadores", st.maxPlayers, 1..30) { st.maxPlayers = it }
        }

        SectionCard(title = "Desempenho", icon = Icons.Rounded.Visibility) {
            LabeledSlider("Distância de visão (chunks)", st.view, 3..16) { st.view = it }
            LabeledSlider("Distância de simulação (chunks)", st.sim, 3..12) { st.sim = it }
            Text("No celular, 6 a 8 de visão e 4 a 6 de simulação costumam rodar bem.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(title = "Segurança", icon = Icons.Rounded.Security) {
            Toggle("Só contas originais (online-mode)", st.online) { st.online = it }
            if (!st.online) {
                Text("Modo pirata: qualquer um pode entrar usando qualquer nome. Ative a lista de permissão para controlar quem entra.",
                    color = JW.Gold, style = MaterialTheme.typography.bodySmall)
            }
            Toggle("Lista de permissão (whitelist)", st.whitelist) { st.whitelist = it }
            if (st.whitelist) {
                Text("Adicione jogadores pelo console: whitelist add NomeDoJogador",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionCard(title = "Memória RAM", icon = Icons.Rounded.Memory) {
            Text(Fmt.ram(ram), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Slider(
                value = ram.toFloat(),
                onValueChange = { ram = ((it / 256).toInt() * 256).coerceIn(512, maxRam) },
                valueRange = 512f..maxRam.toFloat().coerceAtLeast(768f),
            )
            Text("Recomendado: ${Fmt.ram(app.monitor.recommendedRamMb())}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Button(
            onClick = {
                st.save()
                if (ram != profile.ramMb) app.repo.update(profile.id) { it.copy(ramMb = ram) }
                toast(ctx, "Ajustes salvos")
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
        ) { Text("Salvar ajustes") }

        SectionCard(title = "Versão", icon = Icons.Rounded.Update) {
            Text("${profile.typeLabel}${profile.loaderVersion?.let { " (${profile.loader.label} $it)" } ?: ""}",
                style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { pickVersion = true }, enabled = !running && versions.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Trocar a versão do Minecraft")
            }
            Text("Dá pra atualizar um mundo para uma versão mais nova, mas não voltar para uma mais antiga. Exporte o mundo antes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(title = "Mundo", icon = Icons.Rounded.Public) {
            Text("Tamanho do mundo: ${worldSize?.let { Fmt.bytes(it) } ?: "…"}", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(st.seed, { st.seed = it.trim() }, label = { Text("Semente (seed) do mundo") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
            Text("A semente só vale quando um mundo novo é criado.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { confirmResetWorld = true }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Apagar o mundo e gerar um novo", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (pickVersion) {
        VersionPickerDialog(versions, version, onPick = { pickVersion = false; if (it != profile.mcVersion) confirmVersion = it },
            onDismiss = { pickVersion = false })
    }
    confirmVersion?.let { v ->
        ConfirmDialog(
            title = "Trocar para $v?",
            text = "Os arquivos do ${profile.loader.label} $v serão baixados na próxima vez que ligar." +
                if (profile.loader != Loader.VANILLA) " Seus mods também precisam ter versão para $v." else "",
            confirm = "Trocar",
            onConfirm = {
                version = v
                app.repo.update(profile.id) { it.copy(mcVersion = v, loaderVersion = null, installedKey = null, launchArgs = emptyList()) }
            },
            onDismiss = { confirmVersion = null },
        )
    }
    if (confirmResetWorld) {
        ConfirmDialog(
            title = "Apagar o mundo?",
            text = "Todo o mundo deste servidor (construções, inventários) será apagado. Na próxima vez que ligar, um mundo novo será gerado.",
            confirm = "Apagar", danger = true,
            onConfirm = { st.save(); WorldBackup.deleteWorld(dir); worldSize = 0; toast(ctx, "Mundo apagado") },
            onDismiss = { confirmResetWorld = false },
        )
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}
