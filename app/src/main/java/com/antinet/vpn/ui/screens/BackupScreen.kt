package com.antinet.vpn.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.ui.AppCard
import com.antinet.vpn.ui.ConfirmDialog
import com.antinet.vpn.ui.InfoNote
import com.antinet.vpn.ui.ScreenHeader
import com.antinet.vpn.ui.SectionTitle

@Composable
fun BackupScreen(vm: AntiNetViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    var pendingRaw by remember { mutableStateOf<String?>(null) }
    var showClear by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = vm.exportBackup()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(json)
                } ?: error("Не удалось открыть файл")
            }.onSuccess {
                vm.notify("Резервная копия сохранена")
            }.onFailure {
                vm.notify("Ошибка экспорта: ${it.message ?: "неизвестно"}")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (raw.isNullOrBlank()) {
                vm.notify("Не удалось прочитать файл")
            } else {
                pendingRaw = raw
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "Резервная копия",
            subtitle = "Экспорт и импорт",
            onBack = onBack,
        )

        InfoNote(
            text = "Файл содержит профили, подписки и настройки, включая пароли и ключи в открытом виде. Храните его в надёжном месте и не отправляйте в мессенджеры.",
            icon = Icons.Rounded.Warning,
            tone = MaterialTheme.colorScheme.tertiary,
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle("Экспорт")
        AppCard {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp).height(22.dp),
                )
                Column {
                    Text("${profiles.size} профилей · ${subscriptions.size} подписок", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "JSON-документ AntiNet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { exportLauncher.launch("antinet-backup.json") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.height(17.dp))
                    Text(" Сохранить")
                }
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("AntiNet backup", vm.exportBackup()))
                        vm.notify("Копия скопирована в буфер")
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.height(17.dp))
                    Text(" В буфер")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Импорт")
        AppCard {
            Text(
                "Выберите файл, сохранённый AntiNet. Можно добавить профили к текущим или полностью заменить данные.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.height(17.dp))
                Text("  Выбрать файл")
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Удаление")
        AppCard {
            Text(
                "Удаляет все профили, подписки и настройки на этом телефоне. Действие нельзя отменить.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { showClear = true },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.height(18.dp),
                )
                Text("  Удалить все данные", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    val raw = pendingRaw
    if (raw != null) {
        AlertDialog(
            onDismissRequest = { pendingRaw = null },
            title = { Text("Импорт копии") },
            text = {
                Text("Добавить профили к текущим или заменить всё содержимое?")
            },
            confirmButton = {
                Button(onClick = {
                    vm.importBackup(raw, merge = true)
                    pendingRaw = null
                }) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        vm.importBackup(raw, merge = false)
                        pendingRaw = null
                    }) {
                        Text("Заменить", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { pendingRaw = null }) {
                        Text("Отмена")
                    }
                }
            },
        )
    }

    if (showClear) {
        ConfirmDialog(
            title = "Удалить всё?",
            text = "Профили, подписки и настройки будут удалены без возможности восстановления.",
            confirmLabel = "Удалить всё",
            onConfirm = { vm.clearEverything() },
            onDismiss = { showClear = false },
        )
    }
}
