package com.antinet.vpn.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.antinet.vpn.BuildConfig
import com.antinet.vpn.ui.AppCard
import com.antinet.vpn.ui.InfoNote
import com.antinet.vpn.ui.KeyValueRow
import com.antinet.vpn.ui.ScreenHeader
import com.antinet.vpn.ui.SectionTitle
import com.antinet.vpn.ui.SettingDivider
import com.antinet.vpn.ui.SettingRow
import com.antinet.vpn.ui.SettingsGroup

private data class Credit(
    val name: String,
    val author: String,
    val license: String,
    val role: String,
    val url: String,
)

private val Credits = listOf(
    Credit("Xray-core", "XTLS (RPRX и участники)", "MPL-2.0", "Сетевое ядро Xray", "https://github.com/XTLS/Xray-core"),
    Credit("AndroidLibXrayLite", "2dust", "LGPL-3.0", "Сборка Xray для Android (libv2ray.aar)", "https://github.com/2dust/AndroidLibXrayLite"),
    Credit("sing-box", "SagerNet", "GPL-3.0", "Сетевое ядро sing-box", "https://github.com/SagerNet/sing-box"),
    Credit("libbox-android", "clever-vpn", "GPL-3.0", "Сборка sing-box для Android (libbox.aar)", "https://github.com/clever-vpn/sing-box-for-android"),
    Credit("Shizuku", "RikkaApps", "Apache-2.0", "Shell-доступ без root для раздачи VPN", "https://github.com/RikkaApps/Shizuku"),
    Credit("HiddenApiBypass", "LSPosed", "Apache-2.0", "Доступ к скрытым API тестовой сети", "https://github.com/LSPosed/AndroidHiddenApiBypass"),
    Credit("Jetpack Compose и Material 3", "Google / AOSP", "Apache-2.0", "Интерфейс и Material You", "https://developer.android.com/jetpack/compose"),
    Credit("OkHttp", "Square", "Apache-2.0", "Загрузка подписок", "https://github.com/square/okhttp"),
    Credit("Gson", "Google", "Apache-2.0", "Хранение настроек", "https://github.com/google/gson"),
    Credit("ZXing", "ZXing authors", "Apache-2.0", "Распознавание QR", "https://github.com/zxing/zxing"),
    Credit("CameraX", "Google / AOSP", "Apache-2.0", "Камера для QR", "https://developer.android.com/training/camerax"),
    Credit("Geo-данные (geoip/geosite)", "Loyalsoldier, SagerNet", "GPL-3.0 / CC-BY-SA-4.0", "Правила маршрутизации", "https://github.com/Loyalsoldier/v2ray-rules-dat"),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(title = "О приложении", subtitle = "Авторы и лицензии", onBack = onBack)

        AppCard {
            Text("AntiNet", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Клиент Xray и sing-box для Android в стиле Material You",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            KeyValueRow("Версия", BuildConfig.VERSION_NAME)
            KeyValueRow("Ядра", "Xray · sing-box")
            KeyValueRow("Автор", "Narezany")
            KeyValueRow("Лицензия", "GPL-3.0")
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Авторы и благодарности")
        SettingsGroup {
            Credits.forEachIndexed { index, credit ->
                SettingRow(
                    icon = Icons.Rounded.Code,
                    title = credit.name,
                    subtitle = "${credit.author} · ${credit.license}\n${credit.role}",
                    onClick = { runCatching { uriHandler.openUri(credit.url) } },
                )
                if (index != Credits.lastIndex) SettingDivider()
            }
        }

        Spacer(Modifier.height(16.dp))
        InfoNote(
            text = "Shizuku — отдельное приложение своего автора. AntiNet не содержит его кода и обращается к нему через публичный API. Раздача VPN использует скрытые API Android (тестовая сеть и TetheringManager) в процессе Shizuku.",
            icon = Icons.Rounded.Info,
        )
        Spacer(Modifier.height(12.dp))
        InfoNote(
            text = "Дизайн вдохновлён клиентом INCY. Название, логотип и графика INCY не используются.",
            icon = Icons.Rounded.Favorite,
            tone = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(12.dp))
        SettingsGroup {
            SettingRow(
                icon = Icons.Rounded.Code,
                title = "Исходный код AntiNet",
                subtitle = "github.com/Ttttttttttw7188181/antinet",
                onClick = { runCatching { uriHandler.openUri("https://github.com/Ttttttttttw7188181/antinet") } },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
