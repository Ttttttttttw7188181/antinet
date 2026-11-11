package com.antinet.vpn.ui.screens

import android.content.ClipboardManager
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
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.ui.InfoNote
import com.antinet.vpn.ui.MonospaceField
import com.antinet.vpn.ui.ScreenHeader

@Composable
fun ImportScreen(
    vm: AntiNetViewModel,
    initialText: String,
    onBack: () -> Unit,
    onScan: () -> Unit,
) {
    val context = LocalContext.current
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    fun readClipboard(): String {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return ""
        return clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "Импорт",
            subtitle = "Ссылки, конфигурации, QR",
            onBack = onBack,
        )

        InfoNote(
            text = "Поддерживаются vless, vmess, trojan, shadowsocks, socks и http. Можно вставить несколько ссылок или base64-список. Данные сохраняются только на телефоне.",
            icon = Icons.Rounded.FileDownload,
        )

        Spacer(Modifier.height(14.dp))
        MonospaceField(
            value = text,
            onValueChange = {
                text = it
                errors = emptyList()
            },
            label = "Ссылка или конфигурация",
            minLines = 9,
            modifier = Modifier.fillMaxWidth(),
            isError = errors.isNotEmpty(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val clip = readClipboard()
                    if (clip.isBlank()) {
                        vm.notify("Буфер обмена пуст")
                    } else {
                        text = clip
                        errors = emptyList()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(" Вставить")
            }
            OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(" QR-код")
            }
        }

        if (errors.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "Не всё удалось прочитать",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    errors.take(6).forEach { error ->
                        Text(
                            "• $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                if (text.isBlank()) {
                    vm.notify("Вставьте ссылку или конфигурацию")
                    return@Button
                }
                val outcome = vm.importText(text)
                if (outcome.added.isNotEmpty()) {
                    text = ""
                    onBack()
                } else {
                    errors = outcome.errors
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            enabled = text.isNotBlank(),
        ) {
            Text("Импортировать")
        }

        val looksLikeSubscription = text.trim().let {
            (it.startsWith("http://") || it.startsWith("https://")) && !it.contains("\n") && !it.contains(" ")
        }
        if (looksLikeSubscription) {
            TextButton(
                onClick = {
                    vm.addSubscription("", text.trim())
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Это ссылка подписки — добавить подписку")
            }
        }

        Text(
            "Импорт ничего не отправляет в сеть. Ссылки подписок загружаются только при обновлении.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
        )
    }
}
