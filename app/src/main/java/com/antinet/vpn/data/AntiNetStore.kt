package com.antinet.vpn.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Single source of truth for profiles, subscriptions and preferences.
 *
 * The storage is intentionally small and boring: one JSON document per collection in
 * private SharedPreferences. No profile ever leaves the device unless the user exports it.
 */
object AntiNetStore {
    private const val PREFS = "antinet_store"
    private const val KEY_PROFILES = "profiles_v1"
    private const val KEY_SUBSCRIPTIONS = "subscriptions_v1"
    private const val KEY_SETTINGS = "settings_v1"
    private const val SCHEMA = 1

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var prefs: SharedPreferences

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _profiles.value = readList(KEY_PROFILES, object : TypeToken<List<Profile>>() {}.type)
        _subscriptions.value = readList(KEY_SUBSCRIPTIONS, object : TypeToken<List<Subscription>>() {}.type)
        _settings.value = readSettings()
    }

    fun settingsSnapshot(): AppSettings = _settings.value

    fun selectedProfile(): Profile? {
        val id = _settings.value.selectedProfileId
        return _profiles.value.firstOrNull { it.id == id }
    }

    fun profile(id: String): Profile? = _profiles.value.firstOrNull { it.id == id }

    fun addProfiles(incoming: List<Profile>, replaceSubscriptionId: String? = null): Int {
        val current = _profiles.value.toMutableList()
        if (!replaceSubscriptionId.isNullOrBlank()) {
            current.removeAll { it.subscriptionId == replaceSubscriptionId }
        }
        var added = 0
        incoming.forEach { candidate ->
            val duplicate = current.any { existing ->
                (existing.rawLink.isNotBlank() && existing.rawLink == candidate.rawLink) ||
                    (existing.protocol == candidate.protocol &&
                        existing.server.equals(candidate.server, ignoreCase = true) &&
                        existing.port == candidate.port &&
                        existing.password == candidate.password &&
                        existing.network == candidate.network &&
                        existing.path == candidate.path)
            }
            if (!duplicate) {
                current.add(candidate)
                added++
            }
        }
        _profiles.value = current
        write(KEY_PROFILES, current)
        if (_settings.value.selectedProfileId.isBlank() && current.isNotEmpty()) {
            updateSettings { it.copy(selectedProfileId = current.first().id) }
        }
        if (replaceSubscriptionId != null) {
            _subscriptions.value = _subscriptions.value.map {
                if (it.id == replaceSubscriptionId) it.copy(count = added, updatedAt = System.currentTimeMillis()) else it
            }
            write(KEY_SUBSCRIPTIONS, _subscriptions.value)
        }
        return added
    }

    fun updateProfile(profile: Profile) {
        val current = _profiles.value.map { if (it.id == profile.id) profile else it }
        _profiles.value = current
        write(KEY_PROFILES, current)
    }

    fun deleteProfile(id: String) {
        val current = _profiles.value
            .filterNot { it.id == id }
            .map { profile ->
                profile.copy(
                    groupIds = profile.groupIds.filterNot { it == id },
                    chainIds = profile.chainIds.filterNot { it == id },
                )
            }
        _profiles.value = current
        write(KEY_PROFILES, current)
        if (_settings.value.selectedProfileId == id) {
            updateSettings { it.copy(selectedProfileId = current.firstOrNull()?.id.orEmpty()) }
        }
    }

    fun setFavorite(id: String, favorite: Boolean) {
        profile(id)?.let { updateProfile(it.copy(favorite = favorite)) }
    }

    fun setPing(id: String, pingMs: Long) {
        profile(id)?.let { updateProfile(it.copy(lastPingMs = pingMs)) }
    }

    fun selectProfile(id: String) {
        updateSettings { it.copy(selectedProfileId = id) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value).normalized()
        _settings.value = next
        write(KEY_SETTINGS, next)
    }

    fun addSubscription(name: String, url: String): Subscription {
        val subscription = Subscription(
            name = name.ifBlank { runCatching { java.net.URI(url).host }.getOrDefault("Подписка") },
            url = url.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        val current = _subscriptions.value + subscription
        _subscriptions.value = current
        write(KEY_SUBSCRIPTIONS, current)
        return subscription
    }

    fun updateSubscription(subscription: Subscription) {
        val current = _subscriptions.value.map { if (it.id == subscription.id) subscription else it }
        _subscriptions.value = current
        write(KEY_SUBSCRIPTIONS, current)
    }

    fun deleteSubscription(id: String) {
        val removed = _subscriptions.value.firstOrNull { it.id == id }
        _subscriptions.value = _subscriptions.value.filterNot { it.id == id }
        write(KEY_SUBSCRIPTIONS, _subscriptions.value)
        if (removed != null) {
            val remaining = _profiles.value.filterNot { it.subscriptionId == id }
            if (remaining.size != _profiles.value.size) {
                _profiles.value = remaining
                write(KEY_PROFILES, remaining)
                if (_settings.value.selectedProfileId.isBlank() || remaining.none { it.id == _settings.value.selectedProfileId }) {
                    updateSettings { it.copy(selectedProfileId = remaining.firstOrNull()?.id.orEmpty()) }
                }
            }
        }
    }

    fun markSubscriptionUpdated(id: String, count: Int) {
        val current = _subscriptions.value.map {
            if (it.id == id) it.copy(updatedAt = System.currentTimeMillis(), count = count) else it
        }
        _subscriptions.value = current
        write(KEY_SUBSCRIPTIONS, current)
    }

    fun exportJson(): String {
        val root = com.google.gson.JsonObject()
        root.addProperty("app", "AntiNet")
        root.addProperty("schema", SCHEMA)
        root.add("profiles", gson.toJsonTree(_profiles.value))
        root.add("subscriptions", gson.toJsonTree(_subscriptions.value))
        root.add("settings", gson.toJsonTree(_settings.value.copy(selectedProfileId = _settings.value.selectedProfileId)))
        return gson.toJson(root)
    }

    /** Returns the number of imported profiles, or -1 when the document is not an AntiNet backup. */
    fun importJson(raw: String, merge: Boolean = true): Int {
        return runCatching {
            val root = JsonParser.parseString(raw).asJsonObject
            val profilesType = object : TypeToken<List<Profile>>() {}.type
            val subsType = object : TypeToken<List<Subscription>>() {}.type
            val importedProfiles: List<Profile> = gson.fromJson(root.get("profiles"), profilesType) ?: emptyList()
            val importedSubs: List<Subscription> = gson.fromJson(root.get("subscriptions"), subsType) ?: emptyList()
            val importedSettings: AppSettings? = root.get("settings")?.let {
                gson.fromJson(it, AppSettings::class.java)
            }
            val base = if (merge) _profiles.value else emptyList()
            val known = base.map { it.id }.toMutableSet()
            val normalized = importedProfiles.map { profile ->
                if (profile.id.isBlank() || profile.id in known) {
                    profile.copy(id = UUID.randomUUID().toString())
                } else {
                    known.add(profile.id)
                    profile
                }
            }
            val merged = base + normalized
            _profiles.value = merged
            write(KEY_PROFILES, merged)

            val subBase = if (merge) _subscriptions.value else emptyList()
            val subIds = subBase.map { it.id }.toMutableSet()
            val subMerged = subBase + importedSubs.map {
                if (it.id.isBlank() || it.id in subIds) it.copy(id = UUID.randomUUID().toString()) else it
            }
            _subscriptions.value = subMerged
            write(KEY_SUBSCRIPTIONS, subMerged)

            if (importedSettings != null && !merge) {
                _settings.value = importedSettings.normalized()
                write(KEY_SETTINGS, _settings.value)
            }
            if (_settings.value.selectedProfileId.isBlank()) {
                updateSettings { it.copy(selectedProfileId = merged.firstOrNull()?.id.orEmpty()) }
            }
            normalized.size
        }.getOrDefault(-1)
    }

    fun clearAll() {
        _profiles.value = emptyList()
        _subscriptions.value = emptyList()
        _settings.value = AppSettings()
        write(KEY_PROFILES, emptyList<Profile>())
        write(KEY_SUBSCRIPTIONS, emptyList<Subscription>())
        write(KEY_SETTINGS, _settings.value)
    }

    private fun readSettings(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching { gson.fromJson(raw, AppSettings::class.java) }
            .getOrNull()
            ?.takeIf { it.schema == SCHEMA }
            ?.normalized()
            ?: AppSettings()
    }

    private fun <T> readList(key: String, type: java.lang.reflect.Type): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<T>>(raw, type) }.getOrNull() ?: emptyList()
    }

    private fun write(key: String, value: Any) {
        prefs.edit().putString(key, gson.toJson(value)).apply()
    }
}
