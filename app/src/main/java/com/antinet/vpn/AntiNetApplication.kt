package com.antinet.vpn

import android.app.Application
import com.antinet.vpn.data.AntiNetStore
import com.antinet.vpn.engine.XrayCore
import com.antinet.vpn.shizuku.ShizukuBridge
import java.io.File

class AntiNetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Shizuku creates the user-service process with the app's Application class.
        // It must not load the proxy cores or touch Shizuku itself there.
        if (isShizukuUserServiceProcess()) return
        AntiNetStore.init(this)
        runCatching { XrayCore.init(this) }
        ShizukuBridge.init(this)
    }

    private fun isShizukuUserServiceProcess(): Boolean = runCatching {
        // The kernel keeps the full argv, separated by NULs: the process name is
        // only the first component.
        File("/proc/self/cmdline").readText()
            .substringBefore('\u0000')
            .endsWith(":shizuku")
    }.getOrDefault(false)
}
