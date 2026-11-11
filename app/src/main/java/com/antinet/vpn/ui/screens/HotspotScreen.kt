package com.antinet.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.data.EngineStatus
import com.antinet.vpn.engine.AntiNetVpnService
import com.antinet.vpn.hotspot.HotspotBridge
import com.antinet.vpn.shizuku.ShizukuBridge
import com.antinet.vpn.ui.AppCard
import com.antinet.vpn.ui.InfoNote
import com.antinet.vpn.ui.ScreenHeader
import com.antinet.vpn.ui.SectionTitle
import com.antinet.vpn.ui.StatusPill
import com.antinet.vpn.ui.SwitchRow

@Composable
fun HotspotScreen(vm: AntiNetViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val shizuku by ShizukuBridge.state.collectAsStateWithLifecycle()
    val hotspot by HotspotBridge.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        ShizukuBridge.init(context)
        ShizukuBridge.refresh(rebind = true)
    }

    val connected = engine.status == EngineStatus.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "Точка доступа",
            subtitle = "VPN для Wi-Fi клиентов",
            onBack = onBack,
        )

        InfoNote(
            text = "AntiNet создаёт отдельный туннель для точки доступа через Shizuku: тестовый TUN регистрируется как сеть раздачи, и трафик клиентов проходит через выбранное ядро. Root не нужен.",
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle("Раздача")
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.WifiTethering,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Туннель точки доступа", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            hotspot.starting -> "Запуск…"
                            hotspot.active -> "Активна${if (hotspot.iface.isNotBlank()) " · ${hotspot.iface}" else ""}"
                            !settings.hotspotEnabled -> "Выключена"
                            !connected -> "Включится вместе с VPN"
                            else -> "Ожидает запуска"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = if (hotspot.active) "Активна" else "Выкл",
                    active = hotspot.active,
                )
            }
            if (hotspot.error.isNotBlank()) {
                Text(
                    hotspot.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                icon = Icons.Rounded.CheckCircle,
                title = "Раздавать VPN",
                subtitle = "Поднимать туннель вместе с основным подключением",
                checked = settings.hotspotEnabled,
                onCheckedChange = { checked ->
                    vm.updateSettings { it.copy(hotspotEnabled = checked) }
                    if (connected) {
                        AntiNetVpnService.syncHotspot(context)
                    } else if (checked) {
                        vm.notify("Туннель раздачи запустится после подключения VPN")
                    }
                },
            )
            if (settings.hotspotEnabled && !connected) {
                Text(
                    "Сначала подключите VPN на главном экране.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Shizuku")
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Посредник shell", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            !shizuku.installed -> "Shizuku не установлен"
                            !shizuku.running -> "Shizuku не запущен"
                            !shizuku.permission -> "Нужно разрешение"
                            shizuku.connected -> "Готово"
                            else -> "Подключение…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(text = if (shizuku.ready) "Готово" else "Нет", active = shizuku.ready)
            }
            if (shizuku.error.isNotBlank()) {
                Text(
                    shizuku.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { ShizukuBridge.requestPermission() },
                    enabled = shizuku.installed && shizuku.running && !shizuku.permission,
                ) {
                    Text("Разрешить")
                }
                OutlinedButton(onClick = { ShizukuBridge.openShizuku(context) }) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Открыть")
                }
            }
            if (!shizuku.installed) {
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://shizuku.rikka.app/") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Установить Shizuku")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        InfoNote(
            text = "Раздача использует скрытые API Android (тестовая сеть и TetheringManager). Работа зависит от прошивки: на некоторых устройствах включение точки доступа может потребовать подтверждения, а политика оператора или производителя может блокировать тестовые сети.",
            icon = Icons.Rounded.WifiTethering,
            tone = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(24.dp))
    }
}
