package io.github.pauloestefani.javaworld.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.server.Status
import io.github.pauloestefani.javaworld.tunnel.TunnelStatus
import io.github.pauloestefani.javaworld.ui.components.ConfirmDialog
import io.github.pauloestefani.javaworld.ui.components.CopyField
import io.github.pauloestefani.javaworld.ui.components.SectionCard
import io.github.pauloestefani.javaworld.ui.components.StatusPill
import io.github.pauloestefani.javaworld.ui.theme.JW

private const val GUIDE_URL = "https://github.com/pauloestefani/JavaWorld/blob/main/tunel/README.md"

@Composable
fun TunnelScreen() {
    val app = App.instance
    val ctx = LocalContext.current
    val settings by app.tunnel.settings.collectAsState()
    val state by app.tunnel.state.collectAsState()
    val log by app.tunnel.log.collectAsState()
    val run by app.servers.info.collectAsState()

    var host by remember(settings) { mutableStateOf(settings.host) }
    var controlPort by remember(settings) { mutableStateOf(settings.controlPort.toString()) }
    var token by remember(settings) { mutableStateOf(settings.token) }
    var remotePort by remember(settings) { mutableStateOf(settings.remotePort.toString()) }
    var showLog by remember { mutableStateOf(false) }
    var confirmNewToken by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.tunnel.ensureToken() }

    val changed = host.trim() != settings.host || controlPort != settings.controlPort.toString() ||
        token.trim() != settings.token || remotePort != settings.remotePort.toString()

    fun save() {
        app.tunnel.save(
            settings.copy(
                host = host.trim(),
                controlPort = controlPort.toIntOrNull() ?: 7000,
                token = token.trim(),
                remotePort = remotePort.toIntOrNull() ?: 25565,
            )
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Túnel", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 20.dp))
        Text(
            "Para amigos de fora da sua casa entrarem. Você usa uma máquina sua, grátis, na Oracle Cloud em São Paulo: nada de site de terceiro e ping baixo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val pillStatus = when (state.status) {
            TunnelStatus.ONLINE -> Status.RUNNING
            TunnelStatus.CONNECTING -> Status.STARTING
            TunnelStatus.ERROR -> Status.CRASHED
            TunnelStatus.OFF -> Status.STOPPED
        }
        SectionCard(title = "Status", icon = Icons.Rounded.Public, trailing = { StatusPill(pillStatus, label = state.status.label) }) {
            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (settings.isConfigured) {
                CopyField("Endereço para os amigos", settings.publicAddress, accent = JW.Diamond)
                val serverOn = run.status.active
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (app.tunnel.isRunning || state.status == TunnelStatus.CONNECTING || state.status == TunnelStatus.ONLINE) {
                        OutlinedButton(onClick = { app.tunnel.stop() }) { Text("Desligar túnel") }
                    } else {
                        Button(
                            onClick = {
                                val port = run.serverId?.let { app.repo.get(it)?.port } ?: 25565
                                app.tunnel.start(port)
                            },
                        ) { Text(if (serverOn) "Ligar túnel" else "Testar conexão") }
                    }
                    TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Esconder registro" else "Ver registro") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ligar junto com o servidor", modifier = Modifier.weight(1f))
                    Switch(checked = settings.autoStart, onCheckedChange = { app.tunnel.save(settings.copy(autoStart = it)) })
                }
            } else {
                Text("Ainda não configurado. Siga os passos abaixo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showLog) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.medium)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    if (log.isEmpty()) Text("Sem mensagens ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    log.takeLast(120).forEach { Text(it, fontFamily = JW.Mono, fontSize = 11.sp, lineHeight = 14.sp) }
                }
            }
        }

        SectionCard(title = "1. Crie a máquina na Oracle", icon = Icons.Rounded.Cloud) {
            Text(
                "Na Oracle Cloud (plano Always Free), crie uma instância Ubuntu na região São Paulo. " +
                    "Em \"Opções avançadas → Gerenciamento → Script de inicialização (cloud-init)\", cole o script abaixo. " +
                    "Ele instala e configura o túnel sozinho.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.token.isNotBlank()) {
                CopyField("Script de inicialização (cloud-init)", app.tunnel.cloudInitScript().trim(), accent = JW.Gold)
                Text("Já tem a máquina? Rode isto nela por SSH:", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                CopyField("Comando por SSH", app.tunnel.sshCommand(), accent = JW.Gold)
            }
            Text(
                "Não esqueça: na Oracle, libere as portas TCP ${settings.controlPort} e ${settings.remotePort} na \"Security List\" da rede.",
                style = MaterialTheme.typography.bodySmall, color = JW.Gold,
            )
            OutlinedButton(onClick = {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GUIDE_URL))) }
            }) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, null)
                Spacer(Modifier.width(8.dp))
                Text("Guia passo a passo")
            }
        }

        SectionCard(title = "2. Conecte o app na máquina", icon = Icons.Rounded.Settings) {
            OutlinedTextField(
                value = host, onValueChange = { host = it.trim() },
                label = { Text("IP público da máquina (ex: 129.151.32.10)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = token, onValueChange = { token = it.trim() },
                label = { Text("Token (senha do túnel)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                leadingIcon = { Icon(Icons.Rounded.Key, null) },
                trailingIcon = {
                    IconButton(onClick = { if (settings.token.isBlank()) token = app.tunnel.newToken() else confirmNewToken = true }) {
                        Icon(Icons.Rounded.Refresh, "Gerar outro")
                    }
                },
            )
            Text("O token já vai dentro do script. Se trocar, rode o script de novo na máquina.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = controlPort, onValueChange = { controlPort = it.filter(Char::isDigit).take(5) },
                    label = { Text("Porta do túnel") }, singleLine = true, modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = remotePort, onValueChange = { remotePort = it.filter(Char::isDigit).take(5) },
                    label = { Text("Porta pública") }, singleLine = true, modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Button(onClick = { save() }, enabled = changed, modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.medium) {
                Text("Salvar")
            }
        }

        SectionCard(title = "Como funciona", icon = Icons.Rounded.Terminal) {
            Text(
                "Sua operadora não deixa ninguém de fora conectar direto no seu celular. " +
                    "Então o celular abre uma conexão até a sua máquina na Oracle (usando o frp, que é código aberto), " +
                    "e ela repassa os jogadores para o seu servidor. O Minecraft continua rodando no celular.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Celular  ⇄  Oracle São Paulo  ⇄  Amigos", fontWeight = FontWeight.Bold, fontFamily = JW.Mono,
                modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmNewToken) {
        ConfirmDialog(
            title = "Gerar outro token?",
            text = "A máquina na Oracle vai continuar com o token antigo até você rodar o script de novo nela.",
            confirm = "Gerar",
            onConfirm = {
                val t = app.tunnel.newToken()
                token = t
                app.tunnel.save(settings.copy(token = t))
            },
            onDismiss = { confirmNewToken = false },
        )
    }
}
