@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.pauloestefani.javaworld.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.data.Loader
import io.github.pauloestefani.javaworld.data.ServerProfile
import io.github.pauloestefani.javaworld.server.RunInfo
import io.github.pauloestefani.javaworld.server.Status
import io.github.pauloestefani.javaworld.ui.Fmt
import io.github.pauloestefani.javaworld.ui.components.EmptyState
import io.github.pauloestefani.javaworld.ui.components.StatusPill
import io.github.pauloestefani.javaworld.ui.components.Tag
import io.github.pauloestefani.javaworld.ui.theme.JW

fun loaderColor(l: Loader): Color = when (l) {
    Loader.VANILLA -> JW.Grass
    Loader.FABRIC -> JW.Gold
    Loader.FORGE -> JW.Diamond
    Loader.NEOFORGE -> JW.Amethyst
}

@Composable
fun ServersScreen(onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val app = App.instance
    val servers by app.repo.servers.collectAsState()
    val run by app.servers.info.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (servers.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Novo servidor") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header() }
            if (servers.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.ViewInAr,
                        title = "Nenhum servidor ainda",
                        text = "Crie um mundo, escolha a versão do Minecraft e ligue. Tudo roda aqui no seu celular.",
                    ) {
                        Button(onClick = onCreate) {
                            Icon(Icons.Rounded.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Criar meu primeiro servidor")
                        }
                    }
                }
            }
            items(servers, key = { it.id }) { s ->
                ServerCard(s, run, onClick = { onOpen(s.id) })
            }
        }
    }
}

@Composable
private fun Header() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(JW.Grass, JW.GrassDeep))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Grass, null, tint = Color(0xFF06210A)) }
                Spacer(Modifier.width(12.dp))
                Text("JavaWorld", style = MaterialTheme.typography.headlineLarge)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Seu servidor de Minecraft Java, rodando direto do celular.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerCard(s: ServerProfile, run: RunInfo, onClick: () -> Unit) {
    val mine = run.serverId == s.id
    val status = if (mine) run.status else Status.STOPPED
    val accent = loaderColor(s.loader)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (s.loader == Loader.VANILLA) Icons.Rounded.ViewInAr else Icons.Rounded.Extension,
                    null, tint = accent, modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(s.typeLabel, accent)
                    Tag(Fmt.ram(s.ramMb), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (mine && status.active) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(status)
                        if (status == Status.RUNNING) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.People, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text("${run.players.size}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (status == Status.PREPARING) {
                        val p = run.progress
                        if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                } else if (mine && status == Status.CRASHED) {
                    StatusPill(status)
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
