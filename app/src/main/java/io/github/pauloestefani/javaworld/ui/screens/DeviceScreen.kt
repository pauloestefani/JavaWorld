package io.github.pauloestefani.javaworld.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.pauloestefani.javaworld.App
import io.github.pauloestefani.javaworld.monitor.CoreSample
import io.github.pauloestefani.javaworld.runtime.JavaRuntimes
import io.github.pauloestefani.javaworld.server.Status
import io.github.pauloestefani.javaworld.ui.Fmt
import io.github.pauloestefani.javaworld.ui.components.ConfirmDialog
import io.github.pauloestefani.javaworld.ui.components.InfoRow
import io.github.pauloestefani.javaworld.ui.components.RingGauge
import io.github.pauloestefani.javaworld.ui.components.SectionCard
import io.github.pauloestefani.javaworld.ui.components.Sparkline
import io.github.pauloestefani.javaworld.ui.components.UsageBar
import io.github.pauloestefani.javaworld.ui.theme.JW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DeviceScreen() {
    val app = App.instance
    val info = app.monitor.info
    val sample by app.monitor.samples.collectAsState()
    val run by app.servers.info.collectAsState()
    var javaSizes by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var refreshJava by remember { mutableIntStateOf(0) }
    var removeJava by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(refreshJava) {
        javaSizes = withContext(Dispatchers.IO) {
            JavaRuntimes.installedMajors(app.paths).associateWith { JavaRuntimes.sizeOnDisk(app.paths, it) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Seu celular", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 20.dp))
        Text("O que tem dentro e quanto o servidor está usando agora.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionCard(title = info.model, icon = Icons.Rounded.PhoneAndroid) {
            InfoRow("Sistema", info.android)
            InfoRow("Arquitetura", info.abi)
        }

        val s = sample
        SectionCard(title = "Processador", icon = Icons.Rounded.DeveloperBoard) {
            Text(info.socName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            if (info.socDetail.isNotBlank() && info.socDetail != info.socName) {
                Text(info.socDetail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            InfoRow("Núcleos", "${info.cores}")
            InfoRow("Configuração", info.clusters)
            if (s != null) {
                Text("Frequência de cada núcleo agora", style = MaterialTheme.typography.labelLarge)
                CoreBars(s.cores)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Carga estimada (pela frequência)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Fmt.pct(s.cpuFreqLoad), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Sparkline(s.historyCpu, JW.Grass)
                if (s.cpuFreqLoad == null) {
                    Text("Seu Android não deixa ler a frequência dos núcleos.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionCard(title = "Memória RAM", icon = Icons.Rounded.Memory) {
            if (s != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Fmt.bytes(s.ramUsed), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("usados de ${Fmt.bytes(s.ramTotal)}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp))
                }
                UsageBar(s.ramFraction, if (s.ramFraction > 0.88f) JW.Redstone else JW.Diamond)
                Sparkline(s.historyRam, JW.Diamond)
                InfoRow("Livre", Fmt.bytes(s.ramAvail))
                if (s.lowMemory) Text("Memória quase cheia! Feche outros apps.", color = JW.Redstone, style = MaterialTheme.typography.bodySmall)
            } else {
                InfoRow("Total", Fmt.bytes(info.totalRam))
            }
            InfoRow("Recomendado para o servidor", Fmt.ram(app.monitor.recommendedRamMb()))
        }

        SectionCard(title = "Servidor agora", icon = Icons.Rounded.Dns) {
            val active = run.status.active && run.pid > 0
            if (!active || s == null) {
                Text(if (run.status == Status.PREPARING) "Preparando o servidor…" else "Nenhum servidor ligado.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RingGauge(s.serverCpu, Fmt.pct(s.serverCpu), "CPU (todos os núcleos)", JW.Grass)
                    RingGauge(
                        s.serverRss?.let { it.toFloat() / s.ramTotal }, s.serverRss?.let { Fmt.bytes(it) } ?: "—",
                        "RAM usada", JW.Diamond,
                    )
                }
                Sparkline(s.historyServerCpu, JW.Grass)
                InfoRow("Threads do Java", s.serverThreads?.toString() ?: "—")
                InfoRow("Limite de RAM configurado", Fmt.ram(run.ramMb))
            }
        }

        SectionCard(title = "Bateria", icon = Icons.Rounded.BatteryChargingFull) {
            if (s != null) {
                InfoRow("Carga", s.batteryPct?.let { "$it%" } ?: "—")
                InfoRow(
                    "Temperatura", s.batteryTemp?.let { "%.1f °C".format(it) } ?: "—",
                    valueColor = if ((s.batteryTemp ?: 0f) >= 43f) JW.Redstone else MaterialTheme.colorScheme.onSurface,
                )
                InfoRow("Na tomada", if (s.charging) "Sim" else "Não")
            }
            Text("Dica: deixe o celular carregando e fora da capinha enquanto o servidor estiver ligado.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(title = "Armazenamento", icon = Icons.Rounded.SdStorage) {
            if (s != null && s.storageTotal > 0) {
                InfoRow("Livre", "${Fmt.bytes(s.storageFree)} de ${Fmt.bytes(s.storageTotal)}")
                UsageBar(1f - s.storageFree.toFloat() / s.storageTotal, JW.Gold)
            }
        }

        SectionCard(title = "Java instalado", icon = Icons.Rounded.Coffee) {
            if (javaSizes.isEmpty()) {
                Text("Nenhum ainda. O Java certo é baixado sozinho quando você liga um servidor.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            javaSizes.forEach { (major, size) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Java $major", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(Fmt.bytes(size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { removeJava = major }, enabled = !run.status.active) { Text("Remover") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    removeJava?.let { m ->
        ConfirmDialog(
            title = "Remover o Java $m?",
            text = "Libera espaço. Se algum servidor precisar dele, ele é baixado de novo automaticamente.",
            confirm = "Remover",
            onConfirm = { JavaRuntimes.remove(app.paths, m); refreshJava++ },
            onDismiss = { removeJava = null },
        )
    }
}

@Composable
private fun CoreBars(cores: List<CoreSample>) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            if (cores.isEmpty()) return@Canvas
            val gap = 6.dp.toPx()
            val w = (size.width - gap * (cores.size - 1)) / cores.size
            cores.forEachIndexed { i, c ->
                val x = i * (w + gap)
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(6.dp.toPx()))
                val frac = if (c.curKhz != null && c.maxKhz != null && c.maxKhz > 0) (c.curKhz.toFloat() / c.maxKhz).coerceIn(0f, 1f) else 0f
                val h = size.height * frac
                val color = when {
                    frac > 0.85f -> JW.Redstone
                    frac > 0.6f -> JW.Gold
                    else -> JW.Grass
                }
                drawRoundRect(color, Offset(x, size.height - h), Size(w, h), CornerRadius(6.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            cores.forEach { c ->
                Text(
                    Fmt.ghz(c.curKhz).replace(" GHz", ""),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
