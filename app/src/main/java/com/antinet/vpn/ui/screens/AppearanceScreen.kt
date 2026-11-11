package com.antinet.vpn.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.ui.AppCard
import com.antinet.vpn.ui.InfoNote
import com.antinet.vpn.ui.ScreenHeader
import com.antinet.vpn.ui.SectionTitle
import com.antinet.vpn.ui.SwitchRow
import com.antinet.vpn.ui.theme.AccentPalette
import com.antinet.vpn.ui.theme.AccentPalettes

@Composable
fun AppearanceScreen(vm: AntiNetViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val modes = listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "Оформление",
            subtitle = "Тема и цвет акцента",
            onBack = onBack,
        )

        SectionTitle("Тема")
        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { (key, label) ->
                    FilterChip(
                        selected = settings.themeMode == key,
                        onClick = { vm.updateSettings { it.copy(themeMode = key) } },
                        label = { Text(label) },
                        leadingIcon = if (settings.themeMode == key) {
                            {
                                Icon(
                                    Icons.Rounded.Contrast,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            Text(
                "Системная тема следует настройкам телефона.",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Цвет")
        AppCard(contentPadding = 8.dp) {
            SwitchRow(
                icon = Icons.Rounded.Palette,
                title = "Динамические цвета",
                subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "Подстраиваться под обои телефона"
                } else {
                    "Доступно на Android 12 и новее"
                },
                checked = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                onCheckedChange = { vm.updateSettings { it.copy(dynamicColor = it.dynamicColor.not()) } },
            )
        }

        Spacer(Modifier.height(12.dp))
        AppCard {
            Text(
                "Акцент",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Используется, когда динамические цвета выключены.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.padding(top = 14.dp)) {
                AccentPalettes.chunked(3).forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        row.forEach { palette ->
                            AccentChoice(
                                palette = palette,
                                selected = settings.accent == palette.id,
                                onClick = {
                                    vm.updateSettings {
                                        it.copy(accent = palette.id, dynamicColor = false)
                                    }
                                },
                            )
                        }
                    }
                    if (index != AccentPalettes.chunked(3).lastIndex) {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        InfoNote(
            text = "Оформление применяется сразу. Перезапуск VPN не требуется: тема не влияет на ядро.",
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AccentChoice(palette: AccentPalette, selected: Boolean, onClick: () -> Unit) {
    val color = Color.hsl(palette.hue, if (MaterialTheme.colorScheme.surface.luminanceCompat() < 0.5f) 0.62f else 0.58f, 0.52f)
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = color,
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Text(
            palette.title,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Color.luminanceCompat(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)
