package com.antinet.vpn.ui.screens

import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.engine.AntiNetVpnService
import com.antinet.vpn.ui.AppCard
import com.antinet.vpn.ui.InfoNote
import com.antinet.vpn.ui.ScreenHeader
import com.antinet.vpn.ui.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledApp(val packageName: String, val label: String)

@Composable
fun PerAppScreen(vm: AntiNetViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()

    var enabled by remember(settings.perAppEnabled) { mutableStateOf(settings.perAppEnabled) }
    var bypass by remember(settings.perAppBypass) { mutableStateOf(settings.perAppBypass) }
    var selected by remember(settings.perAppPackages) { mutableStateOf(settings.perAppPackages.toSet()) }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context) }
        loading = false
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Приложения",
            subtitle = "Выборочный VPN",
            onBack = onBack,
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
            AppCard(contentPadding = 8.dp) {
                SwitchRow(
                    icon = Icons.Rounded.Apps,
                    title = "Выборочный VPN",
                    subtitle = "Использовать туннель только для выбранных приложений",
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
                if (enabled) {
                    SwitchRow(
                        icon = Icons.Rounded.Apps,
                        title = "Режим исключения",
                        subtitle = "Выбранные приложения идут напрямую, остальные — через VPN",
                        checked = bypass,
                        onCheckedChange = { bypass = it },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Поиск приложения") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Выбрано: ${selected.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        vm.updateSettings {
                            it.copy(
                                perAppEnabled = enabled,
                                perAppBypass = bypass,
                                perAppPackages = selected.toList(),
                            )
                        }
                        if (engine.active) {
                            AntiNetVpnService.stop(context)
                            vm.notify("Настройка сохранена. Подключите VPN заново.")
                        } else {
                            vm.notify("Настройка сохранена")
                        }
                    },
                ) {
                    Text("Применить")
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 24.dp,
                ),
            ) {
                if (!enabled) {
                    item {
                        InfoNote(
                            text = "Выборочный режим выключен: VPN работает для всех приложений, кроме самого AntiNet.",
                        )
                    }
                }
                item {
                    Text(
                        "${filtered.size} приложений",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                selected = if (checked) selected - app.packageName else selected + app.packageName
                            }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(avatarColor(app.packageName).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                app.label.take(1).uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = avatarColor(app.packageName),
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { value ->
                                selected = if (value) selected + app.packageName else selected - app.packageName
                            },
                            enabled = enabled,
                        )
                    }
                }
            }
        }
    }
}

private fun loadApps(context: Context): List<InstalledApp> {
    val manager = context.packageManager
    @Suppress("DEPRECATION")
    val infos = manager.getInstalledApplications(PackageManager.GET_META_DATA)
    return infos.asSequence()
        .filter { it.packageName != context.packageName }
        .map { InstalledApp(it.packageName, it.loadLabel(manager).toString()) }
        .sortedBy { it.label.lowercase() }
        .toList()
}

private fun avatarColor(packageName: String): Color {
    val hue = ((packageName.hashCode() and 0x7fffffff) % 360).toFloat()
    return Color.hsl(hue, 0.45f, 0.62f)
}
