package com.antinet.vpn.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.BuildConfig
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.data.CoreType
import com.antinet.vpn.data.EngineStatus
import com.antinet.vpn.data.RoutingPresets
import com.antinet.vpn.engine.AntiNetVpnService
import com.antinet.vpn.ui.ScreenHeader
import com.antinet.vpn.ui.SectionTitle
import com.antinet.vpn.ui.SettingDivider
import com.antinet.vpn.ui.SettingRow
import com.antinet.vpn.ui.SettingsGroup
import com.antinet.vpn.ui.theme.AccentPalettes
import com.antinet.vpn.util.Format

@Composable
fun SettingsScreen(
    vm: AntiNetViewModel,
    onOpenSubscriptions: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenCore: () -> Unit,
    onOpenRouting: () -> Unit,
    onOpenFragmentation: () -> Unit,
    onOpenHotspot: () -> Unit,
    onOpenPerApp: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()

    val routingTitle = RoutingPresets.firstOrNull { it.id == settings.routingMode }?.title ?: "Свои правила"
    val themeTitle = when (settings.themeMode) {
        "light" -> "Светлая"
        "dark" -> "Тёмная"
        else -> "Системная"
    }
    val accentTitle = AccentPalettes.firstOrNull { it.id == settings.accent }?.title ?: "Океан"
    val coreTitle = when (settings.coreType) {
        CoreType.XRAY -> "Xray"
        CoreType.SING_BOX -> "sing-box"
    }
    val perAppTitle = when {
        !settings.perAppEnabled -> "Все приложения"
        settings.perAppBypass -> "Кроме ${settings.perAppPackages.size}"
        else -> "Только ${settings.perAppPackages.size}"
    }
    val lastUpdate = subscriptions.maxOfOrNull { it.updatedAt } ?: 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "Настройки",
            subtitle = "${profiles.size} профилей · AntiNet ${BuildConfig.VERSION_NAME}",
        )

        SectionTitle("Соединение")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Memory,
                title = "Ядро",
                subtitle = "Движок подключения к серверам",
                value = coreTitle,
                onClick = onOpenCore,
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Rounded.Route,
                title = "Маршрутизация",
                subtitle = "Какие адреса идут напрямую",
                value = routingTitle,
                onClick = onOpenRouting,
            )
            if (settings.coreType == CoreType.XRAY) {
                SettingDivider()
                SettingRow(
                    icon = Icons.Rounded.CallSplit,
                    title = "Фрагментация",
                    subtitle = "Дробление TLS ClientHello",
                    value = if (settings.fragmentEnabled) "Вкл" else "Выкл",
                    onClick = onOpenFragmentation,
                )
            }
            SettingDivider()
            SettingRow(
                icon = Icons.Rounded.WifiTethering,
                title = "Точка доступа",
                subtitle = "Раздача VPN через Shizuku",
                value = "Shizuku",
                onClick = onOpenHotspot,
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Rounded.Apps,
                title = "Приложения",
                subtitle = "Какие приложения используют VPN",
                value = perAppTitle,
                onClick = onOpenPerApp,
            )
            if (engine.status == EngineStatus.CONNECTED || engine.status == EngineStatus.STARTING) {
                SettingDivider()
                SettingRow(
                    icon = Icons.Rounded.PowerSettingsNew,
                    title = "Отключить VPN",
                    subtitle = "Завершить текущую сессию",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { AntiNetVpnService.stop(context) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Подписки")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Cloud,
                title = "Подписки",
                subtitle = if (subscriptions.isEmpty()) "Пока не добавлены" else "Обновлено ${Format.relativeTime(lastUpdate)}",
                value = subscriptions.size.toString(),
                onClick = onOpenSubscriptions,
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Rounded.Refresh,
                title = "Обновить все подписки",
                subtitle = "Загрузить свежий список серверов",
                onClick = { vm.updateAllSubscriptions() },
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Интерфейс")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Palette,
                title = "Оформление",
                subtitle = "Тема, динамические цвета, акцент",
                value = "$themeTitle · $accentTitle",
                onClick = onOpenAppearance,
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Данные")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Backup,
                title = "Резервная копия",
                subtitle = "Экспорт и импорт профилей",
                onClick = onOpenBackup,
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("О приложении")
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Info,
                title = "Об AntiNet и авторах",
                subtitle = "Лицензии и благодарности",
                value = BuildConfig.VERSION_NAME,
                onClick = onOpenAbout,
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "AntiNet — независимый клиент. Ядра Xray и sing-box используются по открытым лицензиям.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}
