package com.antinet.vpn.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.data.Profile
import com.antinet.vpn.ui.AppCard
import com.antinet.vpn.ui.ConfirmDialog
import com.antinet.vpn.ui.EmptyState
import com.antinet.vpn.ui.PingBadge
import com.antinet.vpn.ui.ProtocolBadge
import com.antinet.vpn.ui.ScreenHeader

private val Filters = listOf(
    "all" to "Все",
    "fav" to "Избранные",
    "subs" to "Подписки",
    "groups" to "Группы",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    vm: AntiNetViewModel,
    onNewProfile: () -> Unit,
    onEditProfile: (String) -> Unit,
    onImport: () -> Unit,
    onScan: () -> Unit,
    onSubscriptions: () -> Unit,
) {
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val pinging by vm.pinging.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var showAdd by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Profile?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filtered = remember(profiles, query, filter) {
        profiles.filter { profile ->
            val matchesQuery = query.isBlank() ||
                profile.name.contains(query, ignoreCase = true) ||
                profile.server.contains(query, ignoreCase = true) ||
                profile.endpoint.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                "fav" -> profile.favorite
                "subs" -> profile.subscriptionId.isNotBlank()
                "groups" -> profile.isGroup
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    fun copyProfile(profile: Profile) {
        if (profile.rawLink.isBlank()) {
            vm.notify("У профиля нет исходной ссылки для отправки")
            return
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("AntiNet", profile.rawLink))
        vm.notify("Ссылка скопирована")
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Серверы",
                subtitle = "${profiles.size} профилей · ${subscriptions.size} подписок",
                actions = {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "Импорт")
                    }
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Добавить")
                    }
                },
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Поиск по имени или адресу") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Filters.forEach { (key, label) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(label) },
                    )
                }
            }

            if (filtered.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Dns,
                    title = if (profiles.isEmpty()) "Пока нет серверов" else "Ничего не найдено",
                    text = if (profiles.isEmpty()) {
                        "Добавьте ссылку, подписку или настройте сервер вручную."
                    } else {
                        "Попробуйте изменить запрос или фильтр."
                    },
                    actionLabel = if (profiles.isEmpty()) "Добавить сервер" else null,
                    onAction = if (profiles.isEmpty()) ({ showAdd = true }) else null,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 104.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { profile ->
                        val selected = profile.id == settings.selectedProfileId
                        AppCard(
                            onClick = { vm.selectProfile(profile.id) },
                            contentPadding = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SelectionDot(selected)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            profile.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (profile.favorite) {
                                            Icon(
                                                Icons.Rounded.Star,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.padding(start = 4.dp).size(14.dp),
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ProtocolBadge(profile.protocol)
                                        Text(
                                            text = profile.endpoint,
                                            modifier = Modifier.padding(start = 8.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    val subName = subscriptions.firstOrNull { it.id == profile.subscriptionId }?.name
                                    if (!subName.isNullOrBlank() || profile.chainIds.isNotEmpty()) {
                                        Text(
                                            text = buildString {
                                                if (!subName.isNullOrBlank()) append(subName)
                                                if (profile.chainIds.isNotEmpty()) {
                                                    if (isNotEmpty()) append(" · ")
                                                    append("цепочка +${profile.chainIds.size}")
                                                }
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (profile.id in pinging) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 1.8.dp,
                                    )
                                } else {
                                    PingBadge(profile.lastPingMs)
                                }
                                IconButton(onClick = { vm.setFavorite(profile, !profile.favorite) }) {
                                    Icon(
                                        if (profile.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "Избранное",
                                        tint = if (profile.favorite) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Box {
                                    IconButton(onClick = { menuFor = profile.id }) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "Ещё")
                                    }
                                    DropdownMenu(
                                        expanded = menuFor == profile.id,
                                        onDismissRequest = { menuFor = null },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Проверить пинг") },
                                            leadingIcon = { Icon(Icons.Rounded.Speed, contentDescription = null) },
                                            onClick = {
                                                menuFor = null
                                                vm.ping(profile)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Изменить") },
                                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                            onClick = {
                                                menuFor = null
                                                onEditProfile(profile.id)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Дублировать") },
                                            leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                                            onClick = {
                                                menuFor = null
                                                vm.duplicateProfile(profile)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Поделиться ссылкой") },
                                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                            onClick = {
                                                menuFor = null
                                                copyProfile(profile)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            },
                                            onClick = {
                                                menuFor = null
                                                deleteTarget = profile
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Добавить сервер")
        }
    }

    if (showAdd) {
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text("Добавить сервер", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Ссылка, QR-код или ручная настройка",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                AddAction(Icons.Rounded.Add, "Новый профиль", "Настроить вручную") {
                    showAdd = false
                    onNewProfile()
                }
                AddAction(Icons.Rounded.ContentPaste, "Вставить из буфера", "Импорт конфигурации из буфера обмена") {
                    showAdd = false
                    vm.importClipboard(context)
                }
                AddAction(Icons.Rounded.QrCodeScanner, "Сканировать QR-код", "Камера телефона") {
                    showAdd = false
                    onScan()
                }
                AddAction(Icons.Rounded.Link, "Импорт из текста", "Вставить одну или несколько ссылок") {
                    showAdd = false
                    onImport()
                }
                AddAction(Icons.Rounded.Cloud, "Подписка", "Обновляемый список серверов") {
                    showAdd = false
                    onSubscriptions()
                }
            }
        }
    }

    val target = deleteTarget
    if (target != null) {
        ConfirmDialog(
            title = "Удалить сервер?",
            text = "«${target.name}» будет удалён. Подписки и другие профили останутся.",
            onConfirm = { vm.deleteProfile(target) },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(26.dp)
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
                contentDescription = "Выбран",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
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
}

@Composable
private fun AddAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
