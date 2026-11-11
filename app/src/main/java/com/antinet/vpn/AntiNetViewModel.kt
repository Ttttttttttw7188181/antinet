package com.antinet.vpn

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antinet.vpn.data.AppSettings
import com.antinet.vpn.data.AntiNetStore
import com.antinet.vpn.data.ImportOutcome
import com.antinet.vpn.data.Profile
import com.antinet.vpn.data.ShareLinkParser
import com.antinet.vpn.data.Subscription
import com.antinet.vpn.data.SubscriptionManager
import com.antinet.vpn.engine.AntiNetRuntime
import com.antinet.vpn.engine.AntiNetVpnService
import com.antinet.vpn.data.CoreType
import com.antinet.vpn.engine.SingBoxCore
import com.antinet.vpn.engine.XrayConfigBuilder
import com.antinet.vpn.engine.XrayCore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AntiNetViewModel(application: Application) : AndroidViewModel(application) {
    val profiles: StateFlow<List<Profile>> = AntiNetStore.profiles
    val subscriptions: StateFlow<List<Subscription>> = AntiNetStore.subscriptions
    val settings: StateFlow<AppSettings> = AntiNetStore.settings
    val engine = AntiNetRuntime.engine
    val traffic = AntiNetRuntime.traffic

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _pinging = MutableStateFlow<Set<String>>(emptySet())
    val pinging: StateFlow<Set<String>> = _pinging.asStateFlow()

    init {
        viewModelScope.launch { autoUpdateSubscriptions() }
    }

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    fun importText(raw: String): ImportOutcome {
        val outcome = ShareLinkParser.parseMany(raw)
        if (outcome.added.isNotEmpty()) {
            val added = AntiNetStore.addProfiles(outcome.added)
            if (AntiNetStore.settingsSnapshot().selectedProfileId.isBlank()) {
                AntiNetStore.profiles.value.firstOrNull()?.let { AntiNetStore.selectProfile(it.id) }
            }
            notify(if (added > 0) "Добавлено профилей: $added" else "Такие профили уже есть")
        } else {
            notify(outcome.errors.firstOrNull() ?: "Не удалось распознать профиль")
        }
        return outcome
    }

    fun importClipboard(context: Context): ImportOutcome? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isBlank()) {
            notify("Буфер обмена пуст")
            return null
        }
        return importText(text)
    }

    fun saveProfile(profile: Profile) {
        val exists = AntiNetStore.profile(profile.id) != null
        if (exists) {
            AntiNetStore.updateProfile(profile)
        } else {
            AntiNetStore.addProfiles(listOf(profile))
        }
        if (AntiNetStore.settingsSnapshot().selectedProfileId.isBlank()) {
            AntiNetStore.selectProfile(profile.id)
        }
        reloadIfActive()
        notify(if (exists) "Сервер обновлён" else "Сервер добавлен")
    }

    fun duplicateProfile(profile: Profile) {
        val copy = profile.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${profile.name} (копия)",
            createdAt = System.currentTimeMillis(),
        )
        AntiNetStore.addProfiles(listOf(copy))
        notify("Создана копия")
    }

    fun deleteProfile(profile: Profile) {
        AntiNetStore.deleteProfile(profile.id)
        if (AntiNetRuntime.engine.value.profileId == profile.id) {
            AntiNetVpnService.stop(getApplication())
        }
        notify("Сервер удалён")
    }

    fun selectProfile(id: String) {
        AntiNetStore.selectProfile(id)
        reloadIfActive()
    }

    fun setFavorite(profile: Profile, favorite: Boolean) {
        AntiNetStore.setFavorite(profile.id, favorite)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings, reload: Boolean = false) {
        AntiNetStore.updateSettings(transform)
        if (reload) reloadIfActive()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = updateSettings(transform, reload = false)

    fun addSubscription(name: String, url: String) {
        val subscription = AntiNetStore.addSubscription(name, url)
        viewModelScope.launch {
            _busy.value = true
            notify("Обновляю подписку…")
            val result = SubscriptionManager.update(subscription)
            _busy.value = false
            if (result.profiles.isEmpty()) {
                notify("Подписка: ${result.error}")
            } else {
                notify("Загружено профилей: ${result.profiles.size}")
                if (AntiNetStore.settingsSnapshot().selectedProfileId.isBlank()) {
                    AntiNetStore.profiles.value.firstOrNull()?.let { AntiNetStore.selectProfile(it.id) }
                }
            }
        }
    }

    fun updateSubscription(subscription: Subscription) {
        viewModelScope.launch {
            _busy.value = true
            val result = SubscriptionManager.update(subscription)
            _busy.value = false
            if (result.profiles.isEmpty()) {
                notify("Подписка: ${result.error}")
            } else {
                notify("Обновлено профилей: ${result.profiles.size}")
            }
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        AntiNetStore.deleteSubscription(subscription.id)
        notify("Подписка удалена")
    }

    fun updateAllSubscriptions() {
        viewModelScope.launch {
            _busy.value = true
            var updated = 0
            AntiNetStore.subscriptions.value.forEach { subscription ->
                val result = SubscriptionManager.update(subscription)
                if (result.profiles.isNotEmpty()) updated += result.profiles.size
            }
            AntiNetStore.updateSettings { it.copy(lastSubCheck = System.currentTimeMillis()) }
            _busy.value = false
            notify(if (updated > 0) "Обновлено профилей: $updated" else "Подписки не изменились")
        }
    }

    fun ping(profile: Profile) {
        if (profile.id in _pinging.value) return
        _pinging.value = _pinging.value + profile.id
        viewModelScope.launch {
            val context = getApplication<Application>()
            val settings = AntiNetStore.settingsSnapshot()
            val all = AntiNetStore.profiles.value
            val targets = if (profile.isGroup) {
                profile.groupIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
            } else {
                listOf(profile)
            }
            var best = Long.MAX_VALUE
            targets.forEach { target ->
                val result = when (settings.coreType) {
                    CoreType.XRAY -> {
                        val config = runCatching {
                            XrayConfigBuilder(settings, all).buildPing(target.id)
                        }.getOrNull()
                        if (config == null) -1L else XrayCore.measureDelay(context, config, settings.delayUrl)
                    }
                    CoreType.SING_BOX -> SingBoxCore.measureDelay(target.server, target.port)
                }
                AntiNetStore.setPing(target.id, result)
                if (result >= 0) best = minOf(best, result)
            }
            _pinging.value = _pinging.value - profile.id
            if (best != Long.MAX_VALUE) {
                AntiNetStore.setPing(profile.id, best)
                notify("${profile.name}: $best мс")
            } else {
                AntiNetStore.setPing(profile.id, -1)
                notify("${profile.name}: нет ответа")
            }
        }
    }

    fun exportBackup(): String = AntiNetStore.exportJson()

    fun importBackup(raw: String, merge: Boolean) {
        val count = AntiNetStore.importJson(raw, merge)
        if (count < 0) {
            notify("Это не резервная копия AntiNet")
        } else {
            notify("Импортировано профилей: $count")
        }
    }

    fun clearEverything() {
        AntiNetVpnService.stop(getApplication())
        AntiNetStore.clearAll()
        notify("Все данные удалены")
    }

    fun reloadIfActive() {
        if (AntiNetRuntime.engine.value.active) {
            AntiNetVpnService.reload(getApplication())
        }
    }

    fun updateEnginePing(ms: Long) {
        AntiNetRuntime.ping(ms)
    }

    private suspend fun autoUpdateSubscriptions() {
        val settings = AntiNetStore.settingsSnapshot()
        if (!settings.autoUpdateSubscriptions) return
        val subscriptions = AntiNetStore.subscriptions.value
        if (subscriptions.isEmpty()) return
        val interval = settings.subscriptionIntervalHours * 3_600_000L
        if (System.currentTimeMillis() - settings.lastSubCheck < interval) return
        subscriptions.forEach { SubscriptionManager.update(it) }
        AntiNetStore.updateSettings { it.copy(lastSubCheck = System.currentTimeMillis()) }
    }
}
