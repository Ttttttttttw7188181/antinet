package com.antinet.vpn.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.data.EngineStatus
import com.antinet.vpn.data.Profile
import com.antinet.vpn.data.RoutingPresets
import com.antinet.vpn.ui.AppCard
import com.antinet.vpn.ui.InfoNote
import com.antinet.vpn.ui.KeyValueRow
import com.antinet.vpn.ui.PingBadge
import com.antinet.vpn.ui.ProtocolBadge
import com.antinet.vpn.ui.ScreenHeader
import com.antinet.vpn.ui.SectionTitle
import com.antinet.vpn.ui.SpeedGraph
import com.antinet.vpn.ui.StatusPill
import com.antinet.vpn.util.Format

@Composable
fun StatsScreen(vm: AntiNetViewModel) {
    val engine by vm.engine.collectAsStateWithLifecycle()
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val selected = profiles.firstOrNull { it.id == engine.profileId }
        ?: profiles.firstOrNull { it.id == settings.selectedProfileId }
    val connected = engine.status == EngineStatus.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "Статистика",
            subtitle = when {
                connected -> "Сессия активна"
                engine.status == EngineStatus.STARTING -> "Подключение"
                else -> "Нет активной сессии"
            },
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        ) {
            Box(
                Modifier.background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f),
                        ),
                    ),
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            text = if (connected) "Подключено" else "Ожидание",
                            active = connected,
                        )
                        Spacer(Modifier.weight(1f))
                        if (selected != null) PingBadge(engine.pingMs)
                    }
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = Format.duration(engine.sessionSeconds),
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        "длительность сессии",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (selected != null) {
                        Row(
                            modifier = Modifier.padding(top = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                selected.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(8.dp))
                            ProtocolBadge(selected.protocol)
                        }
                        Text(
                            selected.endpoint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier.padding(top = 18.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetricTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.ArrowDownward,
                            label = "Получено",
                            value = Format.bytes(engine.down),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        MetricTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.ArrowUpward,
                            label = "Отправлено",
                            value = Format.bytes(engine.up),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Insights,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Скорость",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "↓ ${Format.speed(engine.downSpeed)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "↑ ${Format.speed(engine.upSpeed)}",
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (traffic.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "График появится после подключения",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SpeedGraph(
                    points = traffic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(top = 14.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LegendDot(MaterialTheme.colorScheme.primary, "Скачивание")
                    Spacer(Modifier.width(14.dp))
                    LegendDot(MaterialTheme.colorScheme.tertiary, "Отправка")
                    Spacer(Modifier.weight(1f))
                    Text(
                        "последние ${traffic.size} с",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Соединение")
        AppCard {
            KeyValueRow("Сервер", selected?.name ?: "Не выбран")
            KeyValueRow("Пинг", Format.ping(engine.pingMs))
            KeyValueRow(
                "Маршрутизация",
                RoutingPresets.firstOrNull { it.id == settings.routingMode }?.title ?: "Свои правила",
            )
            KeyValueRow(
                "Ядро",
                if (settings.coreType == com.antinet.vpn.data.CoreType.SING_BOX) "sing-box" else "Xray",
            )
            KeyValueRow(
                "Фрагментация",
                if (settings.fragmentEnabled) "Включена · ${settings.fragmentPackets}" else "Выключена",
            )
            KeyValueRow("MTU", settings.mtu.toString())
            KeyValueRow("IPv6", if (settings.ipv6) "Включён" else "Выключен")
            KeyValueRow("Mux", if (settings.mux) "Включён · ${settings.muxConcurrency}" else "Выключен")
        }

        if (selected != null && selected.isGroup) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Участники группы")
            AppCard {
                val members = selected.groupIds.mapNotNull { id -> profiles.firstOrNull { it.id == id } }
                if (members.isEmpty()) {
                    Text(
                        "В группе нет доступных серверов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    members.forEach { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    member.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    member.endpoint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            PingBadge(member.lastPingMs)
                        }
                    }
                    Button(
                        onClick = { vm.ping(selected) },
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Проверить всех")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        InfoNote(
            text = "Счётчики приходят от ядра Xray и сбрасываются каждую секунду. В сессии показана суммарная скорость, график — последние 90 секунд.",
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
                Text(
                    label,
                    modifier = Modifier.padding(start = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                )
            }
            Text(
                value,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            label,
            modifier = Modifier.padding(start = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
