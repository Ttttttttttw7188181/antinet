package com.antinet.vpn.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.data.EngineStatus
import com.antinet.vpn.data.Profile
import com.antinet.vpn.data.RoutingPresets
import com.antinet.vpn.engine.AntiNetVpnService
import com.antinet.vpn.ui.AppCard
import com.antinet.vpn.ui.PingBadge
import com.antinet.vpn.ui.ProtocolBadge
import com.antinet.vpn.ui.SectionTitle
import com.antinet.vpn.ui.StatusPill
import com.antinet.vpn.util.Format

@Composable
fun HomeScreen(
    vm: AntiNetViewModel,
    onServers: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onHotspot: () -> Unit,
    onRouting: () -> Unit,
    onCore: () -> Unit,
    onFragmentation: () -> Unit,
) {
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val connected = engine.status == EngineStatus.CONNECTED
    val starting = engine.status == EngineStatus.STARTING
    val stopping = engine.status == EngineStatus.STOPPING

    val selected = profiles.firstOrNull { it.id == engine.profileId }
        ?: profiles.firstOrNull { it.id == settings.selectedProfileId }
        ?: profiles.firstOrNull()

    var pendingProfileId by remember { mutableStateOf("") }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingProfileId
        pendingProfileId = ""
        if (result.resultCode == Activity.RESULT_OK && id.isNotBlank()) {
            AntiNetVpnService.start(context, id)
        } else if (result.resultCode != Activity.RESULT_OK) {
            vm.notify("Без системного разрешения VPN не запустить")
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val id = pendingProfileId
        if (id.isBlank()) return@rememberLauncherForActivityResult
        val intent = AntiNetVpnService.prepare(context)
        if (intent != null) vpnPermission.launch(intent) else AntiNetVpnService.start(context, id)
    }

    val connect: () -> Unit = {
        val profile = selected
        if (profile == null) {
            vm.notify("Сначала добавьте сервер")
            onServers()
        } else {
            pendingProfileId = profile.id
            val needsNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsNotifications) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val intent = AntiNetVpnService.prepare(context)
                if (intent != null) vpnPermission.launch(intent) else AntiNetVpnService.start(context, profile.id)
            }
        }
    }

    val routingTitle = RoutingPresets.firstOrNull { it.id == settings.routingMode }?.title ?: "Свои правила"
    val quickServers = remember(profiles, selected?.id) {
        buildList {
            selected?.let { add(it) }
            profiles.filter { it.id != selected?.id }
                .sortedWith(compareByDescending<Profile> { it.favorite }.thenBy { it.name })
                .take(4)
                .forEach { add(it) }
        }.distinctBy { it.id }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            HomeTopBar(
                connected = connected,
                starting = starting,
                statusError = engine.status == EngineStatus.ERROR,
                onSettings = onSettings,
            )
            Spacer(Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                ),
            ) {
                Box(
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                        ),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusPill(
                                text = when {
                                    connected -> "Защищено"
                                    starting || stopping -> "Подключение"
                                    engine.status == EngineStatus.ERROR -> "Ошибка"
                                    else -> "Отключено"
                                },
                                active = connected,
                            )
                            Spacer(Modifier.weight(1f))
                            if (connected) PingBadge(engine.pingMs)
                        }

                        ConnectOrb(
                            active = connected,
                            starting = starting || stopping,
                            onClick = {
                                when {
                                    starting || stopping -> Unit
                                    connected -> AntiNetVpnService.stop(context)
                                    else -> connect()
                                }
                            },
                        )

                        Text(
                            text = when {
                                connected -> "Соединение защищено"
                                starting -> "Запускаю ядро…"
                                stopping -> "Отключаю…"
                                engine.status == EngineStatus.ERROR -> "Не удалось подключиться"
                                else -> "Нажмите, чтобы подключиться"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        if (selected != null) {
                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = selected.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(8.dp))
                                ProtocolBadge(selected.protocol)
                            }
                            Text(
                                text = selected.endpoint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(
                                text = "Добавьте первый сервер или подписку",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Button(onClick = onServers, modifier = Modifier.padding(top = 10.dp)) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Добавить сервер")
                            }
                        }

                        if (connected) {
                            Row(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "  ${Format.duration(engine.sessionSeconds)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "  ·  ${Format.bytes(engine.down)} получено",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (engine.status == EngineStatus.ERROR && engine.error.isNotBlank()) {
                            Text(
                                text = engine.error,
                                modifier = Modifier.padding(top = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }

                        if (!connected && !starting) {
                            Text(
                                text = "Если на телефоне уже включён другой VPN, он будет отключён.",
                                modifier = Modifier.padding(top = 12.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpeedTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.ArrowDownward,
                    label = "Скачивание",
                    value = Format.speed(engine.downSpeed),
                    tint = MaterialTheme.colorScheme.primary,
                )
                SpeedTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.ArrowUpward,
                    label = "Отправка",
                    value = Format.speed(engine.upSpeed),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(Modifier.height(14.dp))
            AppCard(onClick = onStats) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SessionStat(Modifier.weight(1f), "Сессия", Format.duration(engine.sessionSeconds))
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    )
                    SessionStat(Modifier.weight(1f), "Получено", Format.bytes(engine.down))
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    )
                    SessionStat(Modifier.weight(1f), "Отправлено", Format.bytes(engine.up))
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("Сервер")
            AppCard(contentPadding = 6.dp) {
                if (quickServers.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Rounded.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            "Пока нет серверов",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        TextButton(onClick = onServers) { Text("Добавить") }
                    }
                } else {
                    quickServers.forEachIndexed { index, profile ->
                        HomeServerRow(
                            profile = profile,
                            selected = profile.id == selected?.id,
                            onSelect = {
                                if (profile.id != selected?.id) vm.selectProfile(profile.id)
                            },
                        )
                        if (index != quickServers.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                modifier = Modifier.padding(start = 52.dp, end = 14.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            )
                        }
                    }
                    TextButton(
                        onClick = onServers,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (profiles.size > quickServers.size) "Все серверы (${profiles.size})" else "Управление серверами")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("Быстрые настройки")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Waves,
                    title = "Маршрутизация",
                    value = routingTitle,
                    onClick = onRouting,
                )
                if (settings.coreType == com.antinet.vpn.data.CoreType.SING_BOX) {
                    QuickTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Speed,
                        title = "Ядро",
                        value = "sing-box",
                        onClick = onCore,
                    )
                } else {
                    QuickTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Speed,
                        title = "Фрагментация",
                        value = if (settings.fragmentEnabled) "Включена" else "Выключена",
                        onClick = onFragmentation,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Waves,
                    title = "Точка доступа",
                    value = if (settings.hotspotEnabled) "Вкл" else "Выкл",
                    onClick = onHotspot,
                )
            }
            Spacer(Modifier.height(12.dp))
            AppCard(contentPadding = 8.dp) {
                com.antinet.vpn.ui.SwitchRow(
                    icon = Icons.Rounded.WifiTethering,
                    title = "Раздача VPN",
                    subtitle = "Хотспот вместе с VPN через Shizuku",
                    checked = settings.hotspotEnabled,
                    onCheckedChange = { checked ->
                        vm.updateSettings { it.copy(hotspotEnabled = checked) }
                        if (connected) AntiNetVpnService.syncHotspot(context)
                        if (checked) onHotspot()
                    },
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "AntiNet · ${if (settings.coreType == com.antinet.vpn.data.CoreType.SING_BOX) "sing-box" else "Xray"}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HomeTopBar(
    connected: Boolean,
    starting: Boolean,
    statusError: Boolean,
    onSettings: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Waves,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                "AntiNet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    connected -> "VPN активен"
                    starting -> "Подключение…"
                    statusError -> "Требуется внимание"
                    else -> "VPN выключен"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Настройки")
        }
    }
}

@Composable
private fun ConnectOrb(active: Boolean, starting: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .size(190.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            if (active) {
                drawCircle(
                    color = primary.copy(alpha = (1f - pulse) * 0.22f),
                    radius = radius * (0.66f + 0.34f * pulse),
                    center = center,
                )
                drawCircle(
                    color = tertiary.copy(alpha = (1f - pulse) * 0.12f),
                    radius = radius * (0.55f + 0.2f * pulse),
                    center = center,
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.22f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier.size(126.dp),
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.linearGradient(listOf(primary, tertiary)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (starting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(38.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp,
                    )
                } else {
                    Icon(
                        Icons.Rounded.PowerSettingsNew,
                        contentDescription = if (active) "Отключить" else "Подключить",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(46.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
) {
    Surface(
        modifier = modifier.height(96.dp),
        shape = MaterialTheme.shapes.large,
        color = tint.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Text(
                    label,
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SessionStat(modifier: Modifier, label: String, value: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeServerRow(profile: Profile, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(17.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                profile.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                profile.endpoint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PingBadge(profile.lastPingMs)
        Icon(
            if (selected) Icons.Rounded.Check else Icons.Rounded.Add,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 10.dp).size(18.dp),
        )
    }
}

@Composable
private fun QuickTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier, onClick = onClick, contentPadding = 14.dp) {
        com.antinet.vpn.ui.IconBadge(icon = icon)
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
