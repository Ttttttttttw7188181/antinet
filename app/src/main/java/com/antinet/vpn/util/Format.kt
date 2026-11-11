package com.antinet.vpn.util

import java.util.Locale
import kotlin.math.abs

object Format {
    fun bytes(value: Long): String {
        if (value < 1024) return "$value Б"
        val units = arrayOf("КБ", "МБ", "ГБ", "ТБ")
        var size = value.toDouble()
        var index = -1
        while (size >= 1024 && index < units.lastIndex) {
            size /= 1024.0
            index++
        }
        return if (size >= 100) {
            String.format(Locale.US, "%.0f %s", size, units[index])
        } else {
            String.format(Locale.US, "%.1f %s", size, units[index])
        }
    }

    fun speed(value: Long): String = "${bytes(value)}/с"

    fun duration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        val h = safe / 3600
        val m = (safe % 3600) / 60
        val s = safe % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    fun ping(ms: Long): String = when {
        ms < 0 -> "—"
        ms == 0L -> "0 мс"
        else -> "$ms мс"
    }

    fun relativeTime(timestamp: Long): String {
        if (timestamp <= 0L) return "ещё не обновлялась"
        val diff = abs(System.currentTimeMillis() - timestamp)
        val minutes = diff / 60_000
        return when {
            minutes < 1 -> "только что"
            minutes < 60 -> "$minutes мин назад"
            minutes < 1440 -> "${minutes / 60} ч назад"
            else -> "${minutes / 1440} дн назад"
        }
    }

    fun maskHost(host: String): String {
        if (host.length <= 4) return host
        return host.take(3) + "•".repeat((host.length - 3).coerceAtMost(8))
    }
}
