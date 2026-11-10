@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.pauloestefani.javaworld.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.data.Loader
import io.github.pauloestefani.javaworld.net.McVersion
import io.github.pauloestefani.javaworld.net.VersionApi
import io.github.pauloestefani.javaworld.server.InitialSettings
import io.github.pauloestefani.javaworld.server.ServerFactory
import io.github.pauloestefani.javaworld.ui.Fmt
import io.github.pauloestefani.javaworld.ui.components.Choice
import io.github.pauloestefani.javaworld.ui.components.SectionCard
import io.github.pauloestefani.javaworld.ui.components.SelectField
import kotlinx.coroutines.launch

val gamemodes = listOf(
    Choice("survival", "Sobrevivência"),
    Choice("creative", "Criativo"),
    Choice("adventure", "Aventura"),
    Choice("spectator", "Espectador"),
)

val difficulties = listOf(
    Choice("peaceful", "Pacífico"),
    Choice("easy", "Fácil"),
    Choice("normal", "Normal"),
    Choice("hard", "Difícil"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateServerScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val app = App.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("Meu mundo") }
    var loader by remember { mutableStateOf(Loader.VANILLA) }
    var versions by remember { mutableStateOf<List<McVersion>>(emptyList()) }
    var version by remember { mutableStateOf<String?>(null) }
    var loadingVersions by remember { mutableStateOf(true) }
    var versionsError by remember { mutableStateOf<String?>(null) }
    var showSnapshots by remember { mutableStateOf(false) }
    var pickVersion by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    val maxRam = remember { app.monitor.maxRamMb() }
    var ram by remember { mutableIntStateOf(app.monitor.recommendedRamMb().coerceAtMost(maxRam)) }
    var gamemode by remember { mutableStateOf("survival") }
    var difficulty by remember { mutableStateOf("normal") }
    var premiumOnly by remember { mutableStateOf(true) }
    var eula by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loader, showSnapshots, reload) {
        loadingVersions = true
        versionsError = null
        try {
            val all = VersionApi.versions()
            val supported = VersionApi.supported(loader)
            val list = all.filter { (showSnapshots && loader == Loader.VANILLA) || it.isRelease }
                .filter { supported == null || it.id in supported }
            versions = list
            if (version == null || list.none { it.id == version }) version = list.firstOrNull()?.id
        } catch (e: Exception) {
            versionsError = "Não consegui carregar as versões. Confira a internet. (${e.message})"
        }
        loadingVersions = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novo servidor") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) },
                label = { Text("Nome do servidor") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            )

            Text("Tipo de servidor", style = MaterialTheme.typography.titleMedium)
            Loader.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { l ->
                        LoaderOption(l, selected = l == loader, modifier = Modifier.weight(1f)) { loader = l }
                    }
                }
            }
            if (loader == Loader.VANILLA) {
                Text(
                    "Dá pra adicionar mods depois: é só converter pra Fabric ou Forge na aba Mods, o mundo continua o mesmo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Versão do Minecraft", icon = Icons.Rounded.ViewInAr) {
                when {
                    loadingVersions -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Carregando versões…")
                    }
                    versionsError != null -> Column {
                        Text(versionsError!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { VersionApi.clearCache(); reload++ }) { Text("Tentar de novo") }
                    }
                    else -> {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { pickVersion = true }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(version ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                                val v = versions.firstOrNull { it.id == version }
                                Text(
                                    if (v == versions.firstOrNull()) "Mais recente" else if (v?.isRelease == false) "Snapshot (teste)" else "Versão estável",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Rounded.KeyboardArrowDown, null)
                        }
                        if (loader == Loader.VANILLA) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Mostrar snapshots", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = showSnapshots, onCheckedChange = { showSnapshots = it })
                            }
                        }
                    }
                }
            }

            SectionCard(title = "Memória RAM do servidor", icon = Icons.Rounded.Memory) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Fmt.ram(ram), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "de ${Fmt.bytes(app.monitor.info.totalRam)} do celular",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Slider(
                    value = ram.toFloat(),
                    onValueChange = { ram = ((it / 256).toInt() * 256).coerceIn(512, maxRam) },
                    valueRange = 512f..maxRam.toFloat().coerceAtLeast(768f),
                )
                Text(
                    "Recomendado pro seu celular: ${Fmt.ram(app.monitor.recommendedRamMb())}. " +
                        "Com mods, mais RAM ajuda; mas deixe sobrar pro Android não fechar o app.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Jogo", icon = Icons.Rounded.SportsEsports) {
                SelectField("Modo de jogo", gamemode, gamemodes, { gamemode = it })
                SelectField("Dificuldade", difficulty, difficulties, { difficulty = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Só contas originais", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (premiumOnly) "Só entra quem tem Minecraft original (mais seguro)."
                            else "Qualquer um entra, até sem conta original (modo pirata). Cuidado: qualquer pessoa pode usar o nome de outra.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = premiumOnly, onCheckedChange = { premiumOnly = it })
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = eula, onCheckedChange = { eula = it })
                    Column(Modifier.weight(1f)) {
                        Text("Concordo com o EULA do Minecraft", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Ler o EULA (aka.ms/MinecraftEULA)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aka.ms/MinecraftEULA")))
                            },
                        )
                    }
                }
            }

            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)

            Button(
                onClick = {
                    val v = version ?: return@Button
                    creating = true
                    error = null
                    scope.launch {
                        try {
                            val lv = VersionApi.latestLoaderVersion(loader, v)
                            if (loader != Loader.VANILLA && lv == null) {
                                error = "O ${loader.label} ainda não tem versão para o Minecraft $v"
                            } else {
                                val p = ServerFactory.create(
                                    app, name, loader, v, lv, ram,
                                    InitialSettings(
                                        motd = name.ifBlank { "Servidor do JavaWorld" },
                                        gamemode = gamemode, difficulty = difficulty, onlineMode = premiumOnly,
                                    ),
                                )
                                onCreated(p.id)
                            }
                        } catch (e: Exception) {
                            error = "Não consegui criar: ${e.message}"
                        }
                        creating = false
                    }
                },
                enabled = eula && version != null && !creating,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                if (creating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Criar servidor", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (pickVersion) {
        VersionPickerDialog(versions, version, onPick = { version = it; pickVersion = false }, onDismiss = { pickVersion = false })
    }
}

@Composable
private fun LoaderOption(l: Loader, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = loaderColor(l)
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (selected) BorderStroke(2.dp, color) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp).heightIn(min = 92.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (l == Loader.VANILLA) Icons.Rounded.ViewInAr else Icons.Rounded.Extension, null, tint = color)
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(l.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(l.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun VersionPickerDialog(versions: List<McVersion>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolha a versão") },
        text = {
            Column {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it }, singleLine = true,
                    placeholder = { Text("Buscar (ex: 1.20)") }, modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(8.dp))
                val shown = versions.filter { filter.isBlank() || it.id.contains(filter.trim()) }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(shown, key = { it.id }) { v ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(v.id) }
                                .background(if (v.id == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(v.id, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                if (v.isRelease) v.releaseTime.take(4) else "snapshot",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
