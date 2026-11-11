package com.antinet.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.ui.AppCard
import com.antinet.vpn.ui.ChoiceField
import com.antinet.vpn.ui.InfoNote
import com.antinet.vpn.ui.ScreenHeader
import com.antinet.vpn.ui.SectionTitle
import com.antinet.vpn.ui.SwitchRow

@Composable
fun FragmentationScreen(vm: AntiNetViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var packets by remember(settings.fragmentPackets) { mutableStateOf(settings.fragmentPackets) }
    var length by remember(settings.fragmentLength) { mutableStateOf(settings.fragmentLength) }
    var delay by remember(settings.fragmentDelay) { mutableStateOf(settings.fragmentDelay) }
    var maxSplit by remember(settings.fragmentMaxSplit) { mutableStateOf(settings.fragmentMaxSplit) }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = "Фрагментация",
            subtitle = "Дробление TLS для обхода DPI",
            onBack = onBack,
        )

        AppCard(contentPadding = 8.dp) {
            SwitchRow(
                icon = Icons.Rounded.CallSplit,
                title = "Включить фрагментацию",
                subtitle = "Применяется к TLS и REALITY соединениям",
                checked = settings.fragmentEnabled,
                onCheckedChange = { checked ->
                    vm.updateSettings({ it.copy(fragmentEnabled = checked) }, reload = true)
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Параметры")
        AppCard {
            ChoiceField(
                label = "Пакеты",
                value = when (packets) {
                    "tlshello" -> "TLS ClientHello"
                    "1-3" -> "1–3 пакета"
                    "1-5" -> "1–5 пакетов"
                    else -> "Своё значение"
                },
                options = listOf(
                    "tlshello" to "TLS ClientHello",
                    "1-3" to "1–3 пакета",
                    "1-5" to "1–5 пакетов",
                ),
                onSelect = { packets = it },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = length,
                onValueChange = { length = it },
                label = { Text("Длина фрагмента") },
                placeholder = { Text("50-100") },
                supportingText = { Text("Байты на один фрагмент, например 50-100") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = delay,
                onValueChange = { delay = it },
                label = { Text("Задержка") },
                placeholder = { Text("10-20") },
                supportingText = { Text("Миллисекунды между фрагментами") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = maxSplit,
                onValueChange = { maxSplit = it.filter { ch -> ch.isDigit() }.take(3) },
                label = { Text("Максимум фрагментов") },
                placeholder = { Text("10") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (packets.startsWith("tlshello")) {
                Text(
                    "Для REALITY значение tlshello автоматически заменяется на 1-3.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (error.isNotEmpty()) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = {
                    val lengthOk = Regex("^\\d{1,4}(-\\d{1,4})?$").matches(length.trim())
                    val delayOk = Regex("^\\d{1,4}(-\\d{1,4})?$").matches(delay.trim())
                    val split = maxSplit.toIntOrNull()
                    error = when {
                        !lengthOk -> "Длина: число или диапазон, например 50-100"
                        !delayOk -> "Задержка: число или диапазон, например 10-20"
                        split == null || split !in 1..100 -> "Максимум фрагментов: от 1 до 100"
                        packets.isBlank() -> "Укажите пакеты"
                        else -> ""
                    }
                    if (error.isEmpty()) {
                        vm.updateSettings({
                            it.copy(
                                fragmentPackets = packets,
                                fragmentLength = length.trim(),
                                fragmentDelay = delay.trim(),
                                fragmentMaxSplit = maxSplit,
                            )
                        }, reload = true)
                        vm.notify("Фрагментация применена")
                    }
                }) {
                    Text("Применить")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        InfoNote(
            text = "Фрагментация делит первые TLS-пакеты на части, чтобы фильтр DPI не увидел полный ClientHello. Это не ускоряет соединение и может мешать некоторым серверам — выключите её, если сайты перестали открываться.",
            icon = Icons.Rounded.Speed,
        )
        Spacer(Modifier.height(12.dp))
        InfoNote(
            text = "Значения по умолчанию: TLS ClientHello, 50-100 байт, 10-20 мс, 10 фрагментов. UDP-трафик получает маскировочный шум отдельно.",
            icon = Icons.Rounded.Timer,
            tone = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(24.dp))
    }
}
