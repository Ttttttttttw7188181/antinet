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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.data.Subscription
import com.antinet.vpn.ui.AppCard
import com.antinet.vpn.ui.ConfirmDialog
import com.antinet.vpn.ui.EmptyState
import com.antinet.vpn.ui.InfoNote
import com.antinet.vpn.ui.ScreenHeader
import com.antinet.vpn.util.Format

@Composable
fun SubscriptionsScreen(vm: AntiNetViewModel, onBack: () -> Unit) {
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Subscription?>(null) }
    var deleteTarget by remember { mutableStateOf<Subscription?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Подписки",
            subtitle = "${subscriptions.size} источников",
            onBack = onBack,
            actions = {
                IconButton(onClick = { vm.updateAllSubscriptions() }, enabled = subscriptions.isNotEmpty()) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Обновить все")
                }
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Добавить")
                }
            },
        )

        if (subscriptions.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Cloud,
                title = "Подписок пока нет",
                text = "Подписка — это ссылка, по которой приходят серверы. AntiNet обновит список вручную или по расписанию.",
                actionLabel = "Добавить подписку",
                onAction = { showAdd = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    InfoNote(
                        text = "Автообновление проверяет подписки при запуске приложения не чаще выбранного интервала. Серверы, пришедшие из подписки, заменяются целиком.",
                    )
                }
                items(subscriptions, key = { it.id }) { subscription ->
                    val count = profiles.count { it.subscriptionId == subscription.id }
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    subscription.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    subscription.host.ifBlank { subscription.url },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Switch(
                                checked = subscription.autoUpdate,
                                onCheckedChange = { checked ->
                                    vm.updateSubscription(subscription.copy(autoUpdate = checked))
                                },
                            )
                        }
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$count серверов · ${Format.relativeTime(subscription.updatedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { editTarget = subscription }) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = "Изменить",
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            IconButton(onClick = { deleteTarget = subscription }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { vm.updateSubscription(subscription) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("  Обновить")
                        }
                    }
                }
            }
        }
    }

    if (showAdd || editTarget != null) {
        val target = editTarget
        SubscriptionDialog(
            title = if (target == null) "Новая подписка" else "Изменить подписку",
            initialName = target?.name.orEmpty(),
            initialUrl = target?.url.orEmpty(),
            onDismiss = {
                showAdd = false
                editTarget = null
            },
            onSave = { name, url ->
                if (target == null) {
                    vm.addSubscription(name, url)
                } else {
                    val updated = target.copy(name = name, url = url)
                    vm.updateSubscription(updated)
                }
                showAdd = false
                editTarget = null
            },
        )
    }

    val target = deleteTarget
    if (target != null) {
        ConfirmDialog(
            title = "Удалить подписку?",
            text = "«${target.name}» и её серверы будут удалены. Остальные профили останутся.",
            onConfirm = { vm.deleteSubscription(target) },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun SubscriptionDialog(
    title: String,
    initialName: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    placeholder = { Text("Например, основной") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    label = { Text("Ссылка подписки") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), url.trim()) },
                enabled = url.isNotBlank(),
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
