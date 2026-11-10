package io.github.pauloestefani.javaworld.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.data.ServerProfile
import io.github.pauloestefani.javaworld.server.LogKind
import io.github.pauloestefani.javaworld.server.LogLevel
import io.github.pauloestefani.javaworld.server.Status
import io.github.pauloestefani.javaworld.ui.theme.JW
import kotlinx.coroutines.launch

private data class Quick(val label: String, val command: String?, val askName: Boolean = false)

private val quick = listOf(
    Quick("Quem está online", "list"),
    Quick("Salvar mundo", "save-all"),
    Quick("Virar dia", "time set day"),
    Quick("Tirar chuva", "weather clear"),
    Quick("Dar OP…", "op", askName = true),
    Quick("Tirar OP…", "deop", askName = true),
    Quick("Expulsar…", "kick", askName = true),
)

@Composable
fun ConsoleTab(profile: ServerProfile) {
    val app = App.instance
    val run by app.servers.info.collectAsState()
    val allLines by app.servers.log.collectAsState()
    val mine = run.serverId == profile.id
    val lines = if (mine) allLines else emptyList()
    val canSend = mine && (run.status == Status.RUNNING || run.status == Status.STARTING)
    var input by remember { mutableStateOf("") }
    var ask by remember { mutableStateOf<Quick?>(null) }
    val listState = rememberLazyListState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
        }
    }
    // Segue o fim do log, a não ser que a pessoa role pra cima para ler.
    var follow by remember { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.isScrollInProgress, atBottom, autoScrolling) }.collect { (scrolling, bottom, auto) ->
            if (auto) return@collect
            if (scrolling && !bottom) follow = false
            else if (!scrolling && bottom) follow = true
        }
    }
    LaunchedEffect(lines.size, follow) {
        if (lines.isNotEmpty() && follow) {
            autoScrolling = true
            listState.scrollToItem(lines.size - 1)
            autoScrolling = false
        }
    }

    fun send(cmd: String) {
        if (cmd.isBlank()) return
        app.servers.send(cmd)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
        ) {
            if (lines.isEmpty()) {
                Text(
                    if (mine) "Esperando o servidor…" else "O console aparece aqui quando o servidor estiver ligado.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(10.dp)) {
                items(lines, key = { it.id }) { l ->
                    val color = when {
                        l.kind == LogKind.COMMAND -> JW.Diamond
                        l.kind == LogKind.SYSTEM && l.level == LogLevel.ERROR -> JW.Redstone
                        l.kind == LogKind.SYSTEM -> JW.Grass
                        l.level == LogLevel.ERROR -> JW.Redstone
                        l.level == LogLevel.WARN -> JW.Gold
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
                    }
                    Text(l.text, color = color, fontFamily = JW.Mono, fontSize = 11.5.sp, lineHeight = 15.sp)
                }
            }
            if (!follow && lines.isNotEmpty()) {
                SmallFloatingActionButton(
                    onClick = { follow = true; scope.launch { listState.scrollToItem(lines.size - 1) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                ) { Icon(Icons.Rounded.KeyboardDoubleArrowDown, "Ir para o fim") }
            }
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                enabled = lines.isNotEmpty(),
                onClick = {
                    val text = lines.takeLast(400).joinToString("\n") { it.text }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Console do ${profile.name} (${profile.typeLabel})")
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    ctx.startActivity(Intent.createChooser(send, "Compartilhar o console"))
                },
                label = { Text("Compartilhar") },
                leadingIcon = { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)) },
            )
            quick.forEach { q ->
                AssistChip(
                    enabled = canSend,
                    onClick = { if (q.askName) ask = q else q.command?.let { send(it) } },
                    label = { Text(q.label) },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                enabled = canSend,
                singleLine = true,
                placeholder = { Text(if (canSend) "Digite um comando (ex: gamemode creative Steve)" else "Ligue o servidor para usar comandos") },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input); input = "" }),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JW.Mono),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { send(input); input = "" }, enabled = canSend && input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Enviar")
            }
        }
    }

    ask?.let { q ->
        var name by remember(q) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(q.label.removeSuffix("…")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nome do jogador no Minecraft:")
                    OutlinedTextField(value = name, onValueChange = { name = it.trim() }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { send("${q.command} $name"); ask = null }) { Text("Enviar") }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("Cancelar") } },
        )
    }
}
