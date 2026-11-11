package com.antinet.vpn.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.data.CoreType
import com.antinet.vpn.data.RoutingPresets
import com.antinet.vpn.ui.AppCard
import com.antinet.vpn.ui.ChoiceField
import com.antinet.vpn.ui.InfoNote
import com.antinet.vpn.ui.MonospaceField
import com.antinet.vpn.ui.ScreenHeader
import com.antinet.vpn.ui.SectionTitle
import com.antinet.vpn.ui.SwitchRow
import org.json.JSONArray

@Composable
fun RoutingScreen(vm: AntiNetViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var mtu by remember(settings.mtu) { mutableFloatStateOf(settings.mtu.toFloat().coerceIn(1200f, 9000f)) }
    var muxCount by remember(settings.muxConcurrency) {
        mutableFloatStateOf(settings.muxConcurrency.toFloat().coerceIn(1f, 32f))
    }
    var rulesText by remember(settings.customRules) { mutableStateOf(settings.customRules) }
    var rulesError by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "Маршрутизация",
            subtitle = "Что идёт через прокси",
            onBack = onBack,
        )

        SectionTitle("Режим")
        AppCard(contentPadding = 6.dp) {
            RoutingPresets.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.updateSettings { it.copy(routingMode = preset.id) }
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.routingMode == preset.id,
                        onClick = { vm.updateSettings { it.copy(routingMode = preset.id) } },
                    )
                    Column(Modifier.padding(start = 6.dp)) {
                        Text(preset.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            preset.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        AppCard(contentPadding = 8.dp) {
            SwitchRow(
                icon = Icons.Rounded.Lan,
                title = "Локальная сеть напрямую",
                subtitle = "Принтеры, роутер, устройства в Wi-Fi",
                checked = settings.bypassLan,
                onCheckedChange = { checked ->
                    vm.updateSettings { it.copy(bypassLan = checked) }
                },
            )
            SwitchRow(
                icon = Icons.Rounded.Block,
                title = "Блокировать рекламу",
                subtitle = if (settings.coreType == CoreType.SING_BOX) {
                    "Rule-set geosite-category-ads-all"
                } else {
                    "Правило geosite:category-ads-all"
                },
                checked = settings.blockAds,
                onCheckedChange = { checked ->
                    vm.updateSettings { it.copy(blockAds = checked) }
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("DNS и сеть")
        AppCard {
            ChoiceField(
                label = "DNS",
                value = when (settings.dnsMode) {
                    "google" -> "Google 8.8.8.8"
                    "system" -> "Системный"
                    else -> "Cloudflare 1.1.1.1"
                },
                options = listOf(
                    "cloudflare" to "Cloudflare 1.1.1.1",
                    "google" to "Google 8.8.8.8",
                    "system" to "Системный",
                ),
                onSelect = { mode ->
                    vm.updateSettings { it.copy(dnsMode = mode) }
                },
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                icon = Icons.Rounded.Dns,
                title = "IPv6",
                subtitle = "Добавить IPv6-адрес и маршрут в TUN",
                checked = settings.ipv6,
                onCheckedChange = { checked ->
                    vm.updateSettings { it.copy(ipv6 = checked) }
                },
            )
            Text(
                "DNS и IPv6 применяются при следующем подключении.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Тонкая настройка")
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MTU", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "${mtu.toInt()} байт",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = mtu,
                onValueChange = { mtu = it },
                valueRange = 1200f..9000f,
                steps = 77,
                onValueChangeFinished = {
                    vm.updateSettings({ it.copy(mtu = mtu.toInt()) }, reload = true)
                },
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                icon = Icons.Rounded.Layers,
                title = "Mux",
                subtitle = "Несколько соединений через один поток (если сервер поддерживает)",
                checked = settings.mux,
                onCheckedChange = { checked ->
                    vm.updateSettings { it.copy(mux = checked) }
                },
            )
            if (settings.mux) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Потоков: $muxCount",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = muxCount,
                    onValueChange = { muxCount = it },
                    valueRange = 1f..32f,
                    steps = 30,
                    onValueChangeFinished = {
                        vm.updateSettings({ it.copy(muxConcurrency = muxCount.toInt()) }, reload = true)
                    },
                )
            }
        }

        if (settings.coreType == CoreType.XRAY) {
        Spacer(Modifier.height(16.dp))
        SectionTitle("Свои правила")
        AppCard {
            Text(
                "JSON-массив правил Xray. Добавляется после встроенных правил.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            MonospaceField(
                value = rulesText,
                onValueChange = {
                    rulesText = it
                    rulesError = ""
                },
                label = "customRules",
                minLines = 6,
                isError = rulesError.isNotEmpty(),
            )
            if (rulesError.isNotEmpty()) {
                Text(
                    rulesError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    rulesText = ""
                    rulesError = ""
                    vm.updateSettings({ it.copy(customRules = "") }, reload = true)
                }) {
                    Text("Очистить")
                }
                Button(onClick = {
                    val trimmed = rulesText.trim()
                    if (trimmed.isEmpty()) {
                        rulesError = ""
                        vm.updateSettings({ it.copy(customRules = "") }, reload = true)
                    } else {
                        val parsed = runCatching { JSONArray(trimmed) }.getOrNull()
                        if (parsed == null) {
                            rulesError = "Некорректный JSON"
                        } else {
                            rulesError = ""
                            vm.updateSettings({ it.copy(customRules = trimmed) }, reload = true)
                        }
                    }
                }) {
                    Text("Применить")
                }
            }
        }
        }

        Spacer(Modifier.height(16.dp))
        InfoNote(
            text = if (settings.coreType == CoreType.SING_BOX) {
                "Для режимов «Обход России» и «Обход Ирана» sing-box загружает бинарные rule-set с SagerNet при первом подключении. Нужен доступ в интернет."
            } else {
                "Для режимов «Обход России» и «Обход Ирана» приложению нужны файлы geoip.dat и geosite.dat рядом с ядром. Без них правила geosite/geoip будут пропущены."
            },
            icon = Icons.Rounded.Route,
        )
        Spacer(Modifier.height(24.dp))
    }
}
