@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.pauloestefani.javaworld.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.mods.ModManager
import io.github.pauloestefani.javaworld.net.ModHit
import io.github.pauloestefani.javaworld.net.Modrinth
import io.github.pauloestefani.javaworld.ui.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class InstallState { IDLE, BUSY, DONE }

@Composable
fun ModSearchScreen(id: String, onBack: () -> Unit) {
    val app = App.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by app.repo.servers.collectAsState()
    val profile = servers.firstOrNull { it.id == id }
    if (profile == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val loader = profile.loader.modrinth
    val mm = remember(id) { ModManager(app.repo.dir(id)) }
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ModHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    val states = remember { mutableStateMapOf<String, InstallState>() }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) { installed = withContext(Dispatchers.IO) { mm.installedProjects() } }

    LaunchedEffect(submitted, page) {
        if (loader == null) return@LaunchedEffect
        if (page == 0) delay(150)
        loading = true
        error = null
        try {
            val r = Modrinth.search(submitted, loader, profile.mcVersion, offset = page * 20)
            results = if (page == 0) r else results + r
            hasMore = r.size == 20
        } catch (e: Exception) {
            error = "Não consegui buscar os mods. Confira a internet."
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Buscar mods")
                        Text("${profile.loader.label} • ${profile.mcVersion}", style = MaterialTheme.typography.labelMedium,
                            color = loaderColor(profile.loader))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Nome do mod (ex: lithium, create, jei)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { page = 0; submitted = query.trim() }),
                )
            }
            item {
                Text(
                    if (submitted.isBlank()) "Mais baixados que funcionam no servidor" else "Resultados para \"$submitted\"",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
            items(results, key = { it.projectId }) { hit ->
                val st = states[hit.projectId] ?: if (hit.projectId in installed) InstallState.DONE else InstallState.IDLE
                ModCard(hit, st) {
                    states[hit.projectId] = InstallState.BUSY
                    scope.launch {
                        runCatching { mm.installFromModrinth(hit.projectId, profile) }
                            .onSuccess { files ->
                                states[hit.projectId] = InstallState.DONE
                                installed = installed + hit.projectId
                                toast(ctx, if (files.size > 1) "Instalado com ${files.size - 1} dependência(s)" else "${hit.title} instalado!")
                            }
                            .onFailure {
                                states[hit.projectId] = InstallState.IDLE
                                toast(ctx, it.message ?: "Erro ao instalar")
                            }
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.size(28.dp))
                        hasMore && results.isNotEmpty() -> TextButton(onClick = { page++ }) { Text("Carregar mais") }
                        results.isEmpty() && error == null -> Text("Nenhum mod encontrado para essa versão.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModCard(hit: ModHit, state: InstallState, onInstall: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (hit.iconUrl != null) {
                    AsyncImage(model = hit.iconUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.Extension, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(hit.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(hit.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${Fmt.count(hit.downloads)} downloads • ${hit.author}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            when (state) {
                InstallState.DONE -> Icon(Icons.Rounded.CheckCircle, "Instalado", tint = MaterialTheme.colorScheme.primary)
                InstallState.BUSY -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                InstallState.IDLE -> FilledTonalButton(onClick = onInstall, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                }
            }
        }
    }
}
