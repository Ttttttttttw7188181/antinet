package com.antinet.vpn.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
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
import com.antinet.vpn.data.GroupStrategy
import com.antinet.vpn.data.Profile
import com.antinet.vpn.data.ProxyProtocol
import com.antinet.vpn.ui.AppCard
import com.antinet.vpn.ui.CheckRow
import com.antinet.vpn.ui.ChoiceField
import com.antinet.vpn.ui.InfoNote
import com.antinet.vpn.ui.MonospaceField
import com.antinet.vpn.ui.ScreenHeader
import com.antinet.vpn.ui.SectionTitle
import com.antinet.vpn.ui.SwitchRow

private val Protocols = listOf(
    ProxyProtocol.VLESS to "VLESS",
    ProxyProtocol.VMESS to "VMess",
    ProxyProtocol.TROJAN to "Trojan",
    ProxyProtocol.SHADOWSOCKS to "Shadowsocks",
    ProxyProtocol.SOCKS to "SOCKS",
    ProxyProtocol.HTTP to "HTTP",
    ProxyProtocol.CUSTOM to "Свой outbound (JSON)",
    ProxyProtocol.GROUP to "Группа (распределение)",
)

private val Networks = listOf(
    "tcp" to "TCP",
    "ws" to "WebSocket",
    "grpc" to "gRPC",
    "httpupgrade" to "HTTPUpgrade",
    "xhttp" to "XHTTP",
)

private val Securities = listOf("none" to "Нет", "tls" to "TLS", "reality" to "Reality")

private val Strategies = listOf(
    GroupStrategy.LEAST_PING to "Наименьший пинг",
    GroupStrategy.LEAST_LOAD to "Наименьшая нагрузка",
    GroupStrategy.RANDOM to "Случайно",
)

@Composable
fun ServerEditorScreen(vm: AntiNetViewModel, profileId: String?, onBack: () -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val original = remember(profileId) { profileId?.let { id -> profiles.firstOrNull { it.id == id } } ?: Profile() }
    var draft by remember(profileId) { mutableStateOf(original) }
    var portText by remember(profileId) { mutableStateOf(original.port.toString()) }
    var error by remember { mutableStateOf("") }
    val others = profiles.filter { it.id != draft.id && !it.isGroup }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(
            title = if (profileId == null) "Новый сервер" else "Сервер",
            subtitle = draft.protocolLabel,
            onBack = onBack,
        )

        AppCard {
            TextInput("Название", draft.name) { draft = draft.copy(name = it) }
            Spacer(Modifier.height(8.dp))
            ChoiceField(
                label = "Протокол",
                value = Protocols.firstOrNull { it.first == draft.protocol }?.second.orEmpty(),
                options = Protocols.map { it.first.name to it.second },
                onSelect = { draft = draft.copy(protocol = ProxyProtocol.valueOf(it)) },
            )
        }

        when (draft.protocol) {
            ProxyProtocol.GROUP -> GroupSection(
                draft = draft,
                candidates = others,
                onChange = { draft = it },
            )
            ProxyProtocol.CUSTOM -> {
                Spacer(Modifier.height(16.dp))
                SectionTitle("Outbound Xray")
                AppCard {
                    MonospaceField(
                        value = draft.customOutbound,
                        onValueChange = { draft = draft.copy(customOutbound = it) },
                        label = "JSON",
                    )
                }
            }
            else -> {
                Spacer(Modifier.height(16.dp))
                SectionTitle("Подключение")
                AppCard {
                    TextInput("Адрес", draft.server) { draft = draft.copy(server = it.trim()) }
                    Spacer(Modifier.height(8.dp))
                    TextInput("Порт", portText, KeyboardType.Number) { value ->
                        portText = value.filter { it.isDigit() }.take(5)
                    }
                    Spacer(Modifier.height(8.dp))
                    CredentialFields(draft) { draft = it }
                }

                if (draft.protocol in setOf(ProxyProtocol.VLESS, ProxyProtocol.VMESS, ProxyProtocol.TROJAN)) {
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("Транспорт")
                    AppCard {
                        ChoiceField(
                            label = "Сеть",
                            value = Networks.firstOrNull { it.first == draft.network }?.second ?: draft.network,
                            options = Networks,
                            onSelect = { draft = draft.copy(network = it) },
                        )
                        if (draft.network != "tcp") {
                            Spacer(Modifier.height(8.dp))
                            if (draft.network == "grpc") {
                                TextInput("Service name", draft.serviceName) { draft = draft.copy(serviceName = it) }
                            } else {
                                TextInput("Host", draft.host) { draft = draft.copy(host = it.trim()) }
                                Spacer(Modifier.height(8.dp))
                                TextInput("Путь", draft.path) { draft = draft.copy(path = it) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        ChoiceField(
                            label = "Безопасность",
                            value = Securities.firstOrNull { it.first == draft.security }?.second ?: draft.security,
                            options = Securities,
                            onSelect = { draft = draft.copy(security = it) },
                        )
                        if (draft.security != "none") {
                            Spacer(Modifier.height(8.dp))
                            TextInput("SNI", draft.sni) { draft = draft.copy(sni = it.trim()) }
                            Spacer(Modifier.height(8.dp))
                            TextInput("Fingerprint", draft.fingerprint) { draft = draft.copy(fingerprint = it.trim()) }
                        }
                        if (draft.security == "tls") {
                            Spacer(Modifier.height(8.dp))
                            TextInput("ALPN", draft.alpn) { draft = draft.copy(alpn = it.trim()) }
                            SwitchRow(
                                icon = Icons.Rounded.Lock,
                                title = "Разрешить небезопасный TLS",
                                subtitle = "Не проверять сертификат сервера",
                                checked = draft.allowInsecure,
                                onCheckedChange = { draft = draft.copy(allowInsecure = it) },
                            )
                        }
                        if (draft.security == "reality") {
                            Spacer(Modifier.height(8.dp))
                            TextInput("Public key", draft.publicKey) { draft = draft.copy(publicKey = it.trim()) }
                            Spacer(Modifier.height(8.dp))
                            TextInput("Short ID", draft.shortId) { draft = draft.copy(shortId = it.trim()) }
                            Spacer(Modifier.height(8.dp))
                            TextInput("SpiderX", draft.spiderX) { draft = draft.copy(spiderX = it) }
                        }
                    }
                }

                ChainSection(draft = draft, candidates = others, onChange = { draft = it })
            }
        }

        if (error.isNotEmpty()) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val port = portText.toIntOrNull()
                val candidate = draft.copy(port = port ?: 0)
                error = validate(candidate)
                if (error.isEmpty()) {
                    vm.saveProfile(candidate.copy(name = candidate.name.ifBlank { candidate.endpoint }))
                    onBack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CredentialFields(draft: Profile, onChange: (Profile) -> Unit) {
    when (draft.protocol) {
        ProxyProtocol.VLESS -> {
            TextInput("UUID", draft.password) { onChange(draft.copy(password = it.trim())) }
            Spacer(Modifier.height(8.dp))
            TextInput("Flow", draft.flow) { onChange(draft.copy(flow = it.trim())) }
        }
        ProxyProtocol.VMESS -> {
            TextInput("UUID", draft.password) { onChange(draft.copy(password = it.trim())) }
            Spacer(Modifier.height(8.dp))
            TextInput("Шифрование", draft.method) { onChange(draft.copy(method = it.trim())) }
        }
        ProxyProtocol.TROJAN -> TextInput("Пароль", draft.password) { onChange(draft.copy(password = it)) }
        ProxyProtocol.SHADOWSOCKS -> {
            TextInput("Метод", draft.method) { onChange(draft.copy(method = it.trim())) }
            Spacer(Modifier.height(8.dp))
            TextInput("Пароль", draft.password) { onChange(draft.copy(password = it)) }
        }
        ProxyProtocol.SOCKS, ProxyProtocol.HTTP -> {
            TextInput("Логин (необязательно)", draft.username) { onChange(draft.copy(username = it)) }
            Spacer(Modifier.height(8.dp))
            TextInput("Пароль (необязательно)", draft.password) { onChange(draft.copy(password = it)) }
        }
        else -> Unit
    }
}

@Composable
private fun GroupSection(draft: Profile, candidates: List<Profile>, onChange: (Profile) -> Unit) {
    Spacer(Modifier.height(16.dp))
    SectionTitle("Распределение по серверам")
    AppCard {
        ChoiceField(
            label = "Стратегия",
            value = Strategies.firstOrNull { it.first == draft.groupStrategy }?.second.orEmpty(),
            options = Strategies.map { it.first.name to it.second },
            onSelect = { onChange(draft.copy(groupStrategy = GroupStrategy.valueOf(it))) },
        )
        Spacer(Modifier.height(8.dp))
        if (candidates.isEmpty()) {
            Text("Сначала добавьте обычные серверы", style = MaterialTheme.typography.bodySmall)
        }
        candidates.forEach { profile ->
            CheckRow(
                checked = profile.id in draft.groupIds,
                title = profile.name,
                subtitle = "${profile.protocolLabel} · ${profile.endpoint}",
                onCheckedChange = { checked ->
                    val ids = if (checked) draft.groupIds + profile.id else draft.groupIds - profile.id
                    onChange(draft.copy(groupIds = ids.distinct()))
                },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    InfoNote(text = "Xray сам выбирает сервер из группы по пингу или нагрузке и переключается, если сервер перестал отвечать.")
}

@Composable
private fun ChainSection(draft: Profile, candidates: List<Profile>, onChange: (Profile) -> Unit) {
    if (candidates.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    SectionTitle("Цепочка")
    AppCard {
        Text(
            "Трафик к этому серверу пойдёт через отмеченные серверы по порядку выбора",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        candidates.forEach { profile ->
            val position = draft.chainIds.indexOf(profile.id)
            CheckRow(
                checked = position >= 0,
                title = if (position >= 0) "${position + 1}. ${profile.name}" else profile.name,
                subtitle = "${profile.protocolLabel} · ${profile.endpoint}",
                onCheckedChange = { checked ->
                    val ids = if (checked) draft.chainIds + profile.id else draft.chainIds - profile.id
                    onChange(draft.copy(chainIds = ids.distinct()))
                },
            )
        }
    }
}

@Composable
private fun TextInput(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun validate(profile: Profile): String = when (profile.protocol) {
    ProxyProtocol.GROUP -> if (profile.groupIds.size < 2) "Отметьте хотя бы два сервера" else ""
    ProxyProtocol.CUSTOM -> runCatching { org.json.JSONObject(profile.customOutbound); "" }
        .getOrDefault("Некорректный JSON")
    else -> when {
        profile.server.isBlank() -> "Укажите адрес"
        profile.port !in 1..65535 -> "Порт от 1 до 65535"
        profile.protocol in setOf(ProxyProtocol.VLESS, ProxyProtocol.VMESS, ProxyProtocol.TROJAN, ProxyProtocol.SHADOWSOCKS) &&
            profile.password.isBlank() -> "Укажите UUID или пароль"
        profile.security == "reality" && profile.publicKey.isBlank() -> "Для Reality нужен public key"
        else -> ""
    }
}
