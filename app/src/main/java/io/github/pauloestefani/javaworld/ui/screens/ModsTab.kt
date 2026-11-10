package io.github.pauloestefani.javaworld.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.data.Loader
import io.github.pauloestefani.javaworld.data.ServerProfile
import io.github.pauloestefani.javaworld.mods.InstalledMod
import io.github.pauloestefani.javaworld.mods.ModManager
import io.github.pauloestefani.javaworld.net.VersionApi
import io.github.pauloestefani.javaworld.ui.Fmt
import io.github.pauloestefani.javaworld.ui.components.ConfirmDialog
import io.github.pauloestefani.javaworld.ui.components.EmptyState
import io.github.pauloestefani.javaworld.ui.components.SectionCard
import io.github.pauloestefani.javaworld.ui.theme.JW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ModsTab(profile: ServerProfile, onSearch: () -> Unit) {
    if (profile.loader == Loader.VANILLA) {
        ConvertToModded(profile)
        return
    }
    val app = App.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mm = remember(profile.id) { ModManager(app.repo.dir(profile.id)) }
    var refresh by remember { mutableIntStateOf(0) }
    var mods by remember { mutableStateOf<List<InstalledMod>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<InstalledMod?>(null) }
    val run by app.servers.info.collectAsState()
    val running = run.serverId == profile.id && run.status.active

    LaunchedEffect(refresh) { mods = withContext(Dispatchers.IO) { mm.list() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            var ok = 0
            for (u in uris) {
                runCatching { mm.importFromUri(ctx, u) }.onSuccess { ok++ }.onFailure { toast(ctx, it.message ?: "Erro") }
            }
            if (ok > 0) toast(ctx, "$ok mod(s) adicionado(s)")
            refresh++
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionCard(title = "Adicionar mods", icon = Icons.Rounded.Extension) {
                Text(
                    "Mods de ${profile.loader.label} para o Minecraft ${profile.mcVersion}. Quem for jogar precisa ter os mesmos mods (menos os que são só de servidor).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(8.dp)); Text("Buscar no Modrinth")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Arquivo .jar")
                    }
                    FilledTonalButton(
                        enabled = busy == null,
                        onClick = {
                            busy = "Instalando o pacote de desempenho…"
                            scope.launch {
                                val loader = profile.loader.modrinth ?: return@launch
                                val installed = mutableListOf<String>()
                                for (slug in ModManager.performancePack(loader)) {
                                    runCatching { mm.installFromModrinth(slug, profile) { busy = it } }
                                        .onSuccess { installed += it }
                                }
                                busy = null
                                refresh++
                                toast(ctx, if (installed.isEmpty()) "Nada novo para instalar nessa versão" else "Instalados: ${installed.size} mods de desempenho")
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Bolt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Desempenho")
                    }
                }
                busy?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (running) {
                    Text("Mudanças nos mods valem quando reiniciar o servidor.", color = JW.Gold, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text("Instalados (${mods.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        }
        if (mods.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.Extension, "Nenhum mod ainda", "Busque no Modrinth ou adicione um arquivo .jar do celular.")
            }
        }
        items(mods, key = { it.file.name }) { m ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${Fmt.bytes(m.size)}${if (!m.enabled) " • desativado" else ""}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = m.enabled, onCheckedChange = { mm.setEnabled(m, it); refresh++ })
                    IconButton(onClick = { toDelete = m }) { Icon(Icons.Rounded.Delete, "Remover", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    toDelete?.let { m ->
        ConfirmDialog(
            title = "Remover ${m.displayName}?", text = "O arquivo do mod será apagado deste servidor.",
            confirm = "Remover", danger = true,
            onConfirm = { mm.delete(m); refresh++ }, onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun ConvertToModded(profile: ServerProfile) {
    val app = App.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf<Loader?>(null) }
    var confirm by remember { mutableStateOf<Pair<Loader, String>?>(null) }
    val run by app.servers.info.collectAsState()
    val running = run.serverId == profile.id && run.status.active

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(title = "Este servidor é Vanilla", icon = Icons.Rounded.SwapHoriz) {
            Text(
                "Para usar mods, converta para um carregador de mods. O seu mundo continua o mesmo, só passa a aceitar mods.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (running) Text("Desligue o servidor antes de converter.", color = JW.Gold, style = MaterialTheme.typography.bodySmall)
            listOf(Loader.FABRIC, Loader.FORGE, Loader.NEOFORGE).forEach { l ->
                OutlinedButton(
                    enabled = !running && checking == null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        checking = l
                        scope.launch {
                            val v = runCatching { VersionApi.latestLoaderVersion(l, profile.mcVersion) }.getOrNull()
                            checking = null
                            if (v == null) toast(ctx, "O ${l.label} não tem versão para o Minecraft ${profile.mcVersion}")
                            else confirm = l to v
                        }
                    },
                ) {
                    if (checking == l) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Converter para ${l.label} — ${l.tagline.lowercase()}")
                }
            }
            Text(
                "Dica: Fabric é o mais leve e o melhor para celular.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    confirm?.let { (l, v) ->
        ConfirmDialog(
            title = "Converter para ${l.label}?",
            text = "O servidor vai usar ${l.label} $v. Na próxima vez que ligar, os arquivos novos serão baixados. Recomendo exportar o mundo antes, por segurança.",
            confirm = "Converter",
            onConfirm = {
                app.repo.update(profile.id) { it.copy(loader = l, loaderVersion = v, installedKey = null, launchArgs = emptyList()) }
            },
            onDismiss = { confirm = null },
        )
    }
}
