package com.antinet.vpn.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SubscriptionManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    data class Result(
        val profiles: List<Profile> = emptyList(),
        val error: String = "",
    )

    suspend fun fetch(url: String): Result = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext Result(error = "Пустая ссылка подписки")
        val request = Request.Builder()
            .url(url.trim())
            .header("User-Agent", "AntiNet/1.2.0 (Android)")
            .header("Accept", "*/*")
            .header("Cache-Control", "no-cache")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result(error = "HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext Result(error = "Пустой ответ")
                if (body.trimStart().startsWith("proxies:") || body.trimStart().startsWith("proxy-groups:")) {
                    return@withContext Result(error = "Формат Clash YAML пока не поддерживается")
                }
                val outcome = ShareLinkParser.parseMany(body)
                if (outcome.added.isEmpty()) {
                    return@withContext Result(error = outcome.errors.firstOrNull() ?: "В подписке нет профилей")
                }
                Result(outcome.added)
            }
        }.getOrElse { error ->
            Result(error = error.message?.take(160) ?: error.javaClass.simpleName)
        }
    }

    suspend fun update(subscription: Subscription): Result {
        val result = fetch(subscription.url)
        if (result.profiles.isNotEmpty()) {
            val incoming = result.profiles.map { it.copy(subscriptionId = subscription.id) }
            val count = AntiNetStore.addProfiles(incoming, replaceSubscriptionId = subscription.id)
            AntiNetStore.markSubscriptionUpdated(subscription.id, count)
        }
        return result
    }
}
