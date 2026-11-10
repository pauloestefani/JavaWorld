@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.pauloestefani.javaworld.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.data.ServerProfile
import io.github.pauloestefani.javaworld.net.NetAddress
import io.github.pauloestefani.javaworld.net.NetInfo
import io.github.pauloestefani.javaworld.server.Status
import io.github.pauloestefani.javaworld.server.WorldBackup
import io.github.pauloestefani.javaworld.tunnel.TunnelStatus
import io.github.pauloestefani.javaworld.ui.Fmt
import io.github.pauloestefani.javaworld.ui.components.ConfirmDialog
import io.github.pauloestefani.javaworld.ui.components.CopyField
import io.github.pauloestefani.javaworld.ui.components.PowerButton
import io.github.pauloestefani.javaworld.ui.components.RingGauge
import io.github.pauloestefani.javaworld.ui.components.SectionCard
import io.github.pauloestefani.javaworld.ui.components.Sparkline
import io.github.pauloestefani.javaworld.ui.components.StatusPill
import io.github.pauloestefani.javaworld.ui.components.Tag
import io.github.pauloestefani.javaworld.ui.components.UsageBar
import io.github.pauloestefani.javaworld.ui.theme.JW
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val tabs = listOf(
    "Painel" to Icons.Rounded.Dashboard,
    "Console" to Icons.Rounded.Terminal,
    "Mods" to Icons.Rounded.Extension,
    "Ajustes" to Icons.Rounded.Tune,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerDetailScreen(
    id: String,
    onBack: () -> Unit,
    onSearchMods: () -> Unit,
    onOpenTunnel: () -> Unit,
    initialTab: Int = 0,
) {
    val app = App.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by app.repo.servers.collectAsState()
    val profile = servers.firstOrNull { it.id == id }
    var tab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    val run by app.servers.info.collectAsState()

    if (profile == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val running = run.serverId == id && run.status.active
    val dir = app.repo.dir(id)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            runCatching { WorldBackup.export(ctx, dir, uri) }
                .onSuccess { toast(ctx, "Mundo exportado!") }
                .onFailure { toast(ctx, it.message ?: "Erro ao exportar") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(profile.name, maxLines = 1, style = MaterialTheme.typography.titleLarge)
                            Text(
                                profile.typeLabel, style = MaterialTheme.typography.labelMedium,
                                color = loaderColor(profile.loader),
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar") } },
                    actions = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Exportar mundo (.zip)") },
                                    leadingIcon = { Icon(Icons.Rounded.FileDownload, null) },
                                    onClick = {
                                        menu = false
                                        exportLauncher.launch("${profile.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}-mundo.zip")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Importar mundo (.zip)") },
                                    leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
                                    enabled = !running,
                                    onClick = { menu = false; importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Excluir servidor", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    enabled = !running,
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    tabs.forEachIndexed { i, (label, icon) ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = { Text(label) },
                            icon = { Icon(icon, null, Modifier.size(20.dp)) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            when (tab) {
                0 -> DashboardTab(profile, onOpenTunnel = onOpenTunnel, onConsole = { tab = 1 })
                1 -> ConsoleTab(profile)
                2 -> ModsTab(profile, onSearch = onSearchMods)
                3 -> SettingsTab(profile)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Excluir \"${profile.name}\"?",
            text = "O mundo, os mods e as configurações desse servidor serão apagados do celular. Isso não dá pra desfazer. Se quiser guardar o mundo, exporte antes.",
            confirm = "Excluir",
            danger = true,
            onConfirm = { app.repo.delete(id) },
            onDismiss = { confirmDelete = false },
        )
    }
    confirmImport?.let { uri ->
        ConfirmDialog(
            title = "Importar mundo?",
            text = "O mundo atual desse servidor será substituído pelo do arquivo. Se quiser guardar o atual, exporte antes.",
            confirm = "Importar",
            onConfirm = {
                scope.launch {
                    runCatching { WorldBackup.import(ctx, dir, uri) }
                        .onSuccess { toast(ctx, "Mundo importado! Ligue o servidor para jogar nele.") }
                        .onFailure { toast(ctx, it.message ?: "Erro ao importar") }
                }
            },
            onDismiss = { confirmImport = null },
        )
    }
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardTab(profile: ServerProfile, onOpenTunnel: () -> Unit, onConsole: () -> Unit) {
    val app = App.instance
    val ctx = LocalContext.current
    val run by app.servers.info.collectAsState()
    val sample by app.monitor.samples.collectAsState()
    val tunnelState by app.tunnel.state.collectAsState()
    val tunnelSettings by app.tunnel.settings.collectAsState()
    var addresses by remember { mutableStateOf<List<NetAddress>>(emptyList()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ignoringBattery by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        while (true) {
            addresses = NetInfo.addresses()
            ignoringBattery = pm.isIgnoringBatteryOptimizations(ctx.packageName)
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }

    val mine = run.serverId == profile.id
    val status = if (mine) run.status else Status.STOPPED
    val otherRunning = !mine && run.status.active
    val otherName = run.serverId?.let { app.repo.get(it)?.name }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Botão principal
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusPill(status)
                PowerButton(status, onClick = {
                    when {
                        otherRunning -> toast(ctx, "Desligue o servidor \"$otherName\" primeiro")
                        status.active -> app.servers.stop()
                        else -> app.servers.start(profile.id)
                    }
                })
                val subtitle = when (status) {
                    Status.STOPPED -> "Toque para ligar"
                    Status.RUNNING -> "Online há ${Fmt.duration(now - run.startedAt)} • toque para desligar"
                    Status.STOPPING -> "Salvando o mundo… toque de novo para forçar"
                    Status.CRASHED -> "Toque para tentar de novo"
                    else -> run.message ?: status.label
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (status == Status.PREPARING || status == Status.STARTING) {
                    val p = run.progress
                    if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (status == Status.STARTING) {
                        Text("A primeira vez demora mais: o mundo está sendo gerado.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                if (mine && status == Status.CRASHED && run.error != null) {
                    Text(run.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = onConsole) { Text("Ver o console") }
                }
                if (otherRunning) {
                    Text("O servidor \"$otherName\" está ligado. Só dá pra rodar um por vez.",
                        color = JW.Gold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        }

        if (!ignoringBattery) {
            SectionCard(title = "Não deixe o Android desligar o servidor", icon = Icons.Rounded.BatteryAlert) {
                Text(
                    "Com a economia de bateria ligada, o Android pode fechar o servidor quando a tela apagar. Libere o JavaWorld:",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                        )
                    }
                }) { Text("Liberar") }
            }
        }

        // Como entrar
        SectionCard(title = "Como seus amigos entram", icon = Icons.Rounded.Link) {
            Text(
                "No Minecraft: Multijogador → Adicionar servidor → cole um destes endereços.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val portSuffix = if (profile.port != 25565) ":${profile.port}" else ""
            if (tunnelSettings.isConfigured) {
                val label = when (tunnelState.status) {
                    TunnelStatus.ONLINE -> "Pela internet (túnel no ar)"
                    TunnelStatus.CONNECTING -> "Pela internet (conectando…)"
                    TunnelStatus.ERROR -> "Pela internet (túnel com problema)"
                    TunnelStatus.OFF -> "Pela internet (liga junto com o servidor)"
                }
                CopyField(label, tunnelSettings.publicAddress, tunnelState.message,
                    accent = if (tunnelState.status == TunnelStatus.ERROR) JW.Redstone else JW.Diamond)
            } else {
                OutlinedButton(onClick = onOpenTunnel, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Public, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Configurar acesso pela internet")
                }
            }
            addresses.forEach { a -> CopyField(a.label, a.address + portSuffix, a.hint) }
            if (addresses.isEmpty() && !tunnelSettings.isConfigured) {
                Text("Conecte num Wi-Fi para amigos na mesma rede entrarem.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Você mesmo, jogando neste celular? Use: localhost$portSuffix", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Jogadores
        if (mine && status == Status.RUNNING) {
            SectionCard(
                title = "Jogando agora", icon = Icons.Rounded.People,
                trailing = { Tag("${run.players.size}", JW.Grass) },
            ) {
                if (run.players.isEmpty()) {
                    Text("Ninguém por enquanto.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        run.players.forEach { p ->
                            AssistChip(onClick = {}, label = { Text(p) }, leadingIcon = { Icon(Icons.Rounded.Person, null, Modifier.size(18.dp)) })
                        }
                    }
                }
            }
        }

        // Uso
        SectionCard(title = "Uso do celular", icon = Icons.Rounded.Insights) {
            val s = sample
            val rss = if (mine) s?.serverRss else null
            val ramFrac = if (rss != null && profile.ramMb > 0) rss.toFloat() / (profile.ramMb * 1048576f) else null
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RingGauge(
                    fraction = if (mine) s?.serverCpu else null,
                    value = if (mine && s?.serverCpu != null) Fmt.pct(s.serverCpu) else "—",
                    label = "CPU do servidor", color = JW.Grass,
                )
                RingGauge(
                    fraction = ramFrac?.coerceIn(0f, 1f),
                    value = if (rss != null) Fmt.bytes(rss) else "—",
                    label = "RAM do servidor", color = JW.Diamond,
                )
            }
            if (s != null) {
                if (mine && s.serverCpu != null) Sparkline(s.historyServerCpu, JW.Grass)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text("RAM do celular", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${Fmt.bytes(s.ramUsed)} de ${Fmt.bytes(s.ramTotal)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    UsageBar(s.ramFraction, if (s.ramFraction > 0.88f) JW.Redstone else JW.Diamond)
                }
                Row(Modifier.fillMaxWidth()) {
                    s.batteryTemp?.let {
                        Text(
                            "Bateria: ${"%.0f".format(it)}°C${if (s.charging) " • carregando" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it >= 43f) JW.Redstone else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if ((s.batteryTemp ?: 0f) >= 43f) {
                    Text("O celular está quente. Tire a capinha e deixe num lugar ventilado.", color = JW.Redstone,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
