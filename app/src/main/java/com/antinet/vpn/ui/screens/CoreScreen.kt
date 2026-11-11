package com.antinet.vpn.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.data.CoreType
import com.antinet.vpn.data.EngineStatus
import com.antinet.vpn.engine.AntiNetVpnService
import com.antinet.vpn.ui.AppCard
import com.antinet.vpn.ui.ChoiceField
import com.antinet.vpn.ui.InfoNote
import com.antinet.vpn.ui.ScreenHeader
import com.antinet.vpn.ui.SectionTitle
import com.antinet.vpn.ui.SwitchRow

@Composable
fun CoreScreen(vm: AntiNetViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "Ядро",
            subtitle = "Движок подключения",
            onBack = onBack,
        )

        SectionTitle("Движок")
        AppCard(contentPadding = 6.dp) {
            CoreRow(
                icon = Icons.Rounded.Bolt,
                title = "Xray",
                subtitle = "Фрагментация, свои JSON-правила, mKCP",
                selected = settings.coreType == CoreType.XRAY,
                onSelect = {
                    vm.updateSettings({ it.copy(coreType = CoreType.XRAY) }, reload = true)
                },
            )
            CoreRow(
                icon = Icons.Rounded.Science,
                title = "sing-box",
                subtitle = "Гибкий DNS, rule-set, современные транспорты",
                selected = settings.coreType == CoreType.SING_BOX,
                onSelect = {
                    vm.updateSettings({ it.copy(coreType = CoreType.SING_BOX) }, reload = true)
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        InfoNote(
            text = "Ядро применяется сразу при активном подключении: AntiNet перезапускает туннель с новой конфигурацией. Профили, группы и цепочки общие для обоих движков.",
            icon = Icons.Rounded.Layers,
        )

        if (settings.coreType == CoreType.SING_BOX) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Настройки sing-box")
            AppCard {
                ChoiceField(
                    label = "Уровень логов",
                    value = settings.singBoxLogLevel,
                    options = listOf(
                        "error" to "error",
                        "warn" to "warn",
                        "info" to "info",
                        "debug" to "debug",
                    ),
                    onSelect = { level ->
                        vm.updateSettings { it.copy(singBoxLogLevel = level) }
                    },
                )
                Spacer(Modifier.height(8.dp))
                ChoiceField(
                    label = "DNS-стратегия",
                    value = settings.singBoxDomainStrategy,
                    options = listOf(
                        "prefer_ipv4" to "Сначала IPv4",
                        "prefer_ipv6" to "Сначала IPv6",
                        "ipv4_only" to "Только IPv4",
                        "ipv6_only" to "Только IPv6",
                    ),
                    onSelect = { strategy ->
                        vm.updateSettings { it.copy(singBoxDomainStrategy = strategy) }
                    },
                )
                Spacer(Modifier.height(8.dp))
                ChoiceField(
                    label = "Стек TUN",
                    value = settings.singBoxStack,
                    options = listOf(
                        "gvisor" to "gVisor",
                        "system" to "System",
                        "mixed" to "Mixed",
                    ),
                    onSelect = { stack ->
                        vm.updateSettings { it.copy(singBoxStack = stack) }
                    },
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    icon = Icons.Rounded.Language,
                    title = "Sniffing",
                    subtitle = "Определять домен по первому пакету",
                    checked = settings.singBoxSniff,
                    onCheckedChange = { checked ->
                        vm.updateSettings { it.copy(singBoxSniff = checked) }
                    },
                )
                Text(
                    "Параметры применяются при следующем подключении.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Настройки Xray")
            AppCard {
                InfoNote(
                    text = "Фрагментация настраивается в разделе «Фрагментация», свои правила — в «Маршрутизации». Mux работает для обоих ядер.",
                    icon = Icons.Rounded.Dns,
                )
            }
        }

        if (engine.status == EngineStatus.CONNECTED) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { AntiNetVpnService.reload(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.SwapVert, contentDescription = null)
                Text(" Перезапустить туннель")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(18.dp),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
