package com.antinet.vpn.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.antinet.vpn.MainActivity
import com.antinet.vpn.R
import com.antinet.vpn.data.AntiNetStore
import com.antinet.vpn.data.AppSettings
import com.antinet.vpn.data.CoreType
import com.antinet.vpn.data.EngineStatus
import com.antinet.vpn.hotspot.HotspotBridge
import com.antinet.vpn.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

/**
 * Owns the Android TUN interface, the selected core and the optional tethering
 * tunnel. All state is kept in [AntiNetRuntime] in the same process as the UI, so
 * counters and speed update without a custom binder.
 */
class AntiNetVpnService : VpnService() {
    companion object {
        private const val CHANNEL_ID = "antinet_vpn"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_START = "com.antinet.vpn.action.START"
        private const val ACTION_STOP = "com.antinet.vpn.action.STOP"
        private const val ACTION_RELOAD = "com.antinet.vpn.action.RELOAD"
        private const val ACTION_HOTSPOT = "com.antinet.vpn.action.HOTSPOT"
        private const val HOTSPOT_MTU = 1500

        /** Applies the current hotspotEnabled setting to a running session. */
        fun syncHotspot(context: Context) {
            val intent = Intent(context, AntiNetVpnService::class.java).setAction(ACTION_HOTSPOT)
            runCatching { context.startService(intent) }
        }

        fun start(context: Context, profileId: String) {
            AntiNetStore.selectProfile(profileId)
            val intent = Intent(context, AntiNetVpnService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AntiNetVpnService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }

        fun reload(context: Context) {
            val intent = Intent(context, AntiNetVpnService::class.java).setAction(ACTION_RELOAD)
            runCatching { context.startService(intent) }
        }

        fun prepare(context: Context): Intent? = VpnService.prepare(context)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tun: ParcelFileDescriptor? = null
    private var engine: TunnelEngine? = null
    private var currentCore: CoreType? = null
    private var trafficJob: Job? = null
    private var reloadJob: Job? = null
    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var running = false
    private var stopping = false
    private var hotspotStarted = false
    private var hotspotStarting = false
    private var hotspotEngine: TunnelEngine? = null
    private var hotspotTun: ParcelFileDescriptor? = null
    private var hotspotGeneration = 0

    override fun onCreate() {
        super.onCreate()
        AntiNetStore.init(this)
        SingBoxEngine.install(this) { fd -> protect(fd) }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                reload()
                return START_STICKY
            }
            ACTION_HOTSPOT -> {
                syncHotspot()
                return if (running) START_STICKY else START_NOT_STICKY
            }
            else -> startSession()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopEverything()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopHotspot()
        trafficJob?.cancel()
        reloadJob?.cancel()
        unregisterNetwork()
        runCatching { tun?.close() }
        tun = null
        if (running) {
            running = false
            val active = engine
            engine = null
            thread(name = "antinet-core-stop") {
                runBlocking { runCatching { active?.stop() } }
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun startSession() {
        if (running || stopping) return
        val settings = AntiNetStore.settingsSnapshot()
        val profile = AntiNetStore.selectedProfile()
        if (profile == null) {
            AntiNetRuntime.failed("Сначала добавьте сервер")
            stopSelf()
            return
        }
        AntiNetRuntime.starting(profile.id, profile.name)
        startForegroundCompat(buildNotification(profile.name, "", ""))

        if (prepare(this) != null) {
            failAndStop("Нет разрешения VPN")
            return
        }

        val config = runCatching { buildConfig(settings, profile.id, hotspot = false) }.getOrElse {
            failAndStop(it.message ?: "Не удалось собрать конфигурацию")
            return
        }

        val fd = runCatching { TunFactory.establish(this, settings) }.getOrElse {
            failAndStop(it.message ?: "Не удалось создать VPN-интерфейс")
            return
        }
        tun = fd
        val newEngine = createEngine(settings, reportTraffic = true)

        scope.launch {
            runCatching {
                newEngine.start(applicationContext, config, fd.fd)
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    if (stopping) {
                        runCatching { newEngine.stop() }
                        return@withContext
                    }
                    engine = newEngine
                    currentCore = settings.coreType
                    running = true
                    AntiNetRuntime.connected(profile.id, profile.name, profile.lastPingMs)
                    startTrafficLoop()
                    registerNetwork(settings.failoverEnabled)
                    if (settings.hotspotEnabled) startHotspot()
                    updateNotification()
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    failAndStop(error.message ?: "${newEngine.label} не запустился")
                }
            }
        }
    }

    private fun buildConfig(settings: AppSettings, profileId: String, hotspot: Boolean): String {
        val profiles = AntiNetStore.profiles.value
        return when (settings.coreType) {
            // The tethering test network always uses a 1500-byte MTU, so the
            // hotspot core must not assume the user's main-tunnel MTU.
            CoreType.XRAY -> XrayConfigBuilder(
                if (hotspot) settings.copy(mtu = HOTSPOT_MTU) else settings,
                profiles,
            ).build(profileId).json
            CoreType.SING_BOX -> SingBoxConfigBuilder(settings, profiles).build(
                profileId,
                tunAddress = if (hotspot) {
                    "${TunFactory.HOTSPOT_ADDRESS_V4}/${TunFactory.HOTSPOT_PREFIX_V4}"
                } else {
                    "${TunFactory.ADDRESS_V4}/${TunFactory.PREFIX_V4}"
                },
                tunMtu = if (hotspot) HOTSPOT_MTU else settings.mtu,
                cacheFile = !hotspot,
            ).json
        }
    }

    private fun createEngine(settings: AppSettings, reportTraffic: Boolean): TunnelEngine =
        when (settings.coreType) {
            CoreType.XRAY -> XrayEngine()
            CoreType.SING_BOX -> SingBoxEngine(reportTraffic)
        }

    private fun startTrafficLoop() {
        trafficJob?.cancel()
        trafficJob = scope.launch {
            var tick = 0
            while (isActive) {
                delay(1_000)
                val traffic = engine?.traffic() ?: TunnelEngine.Traffic()
                AntiNetRuntime.updateTraffic(
                    up = traffic.up,
                    down = traffic.down,
                    upSpeed = traffic.up,
                    downSpeed = traffic.down,
                )
                if (++tick % 5 == 0) {
                    withContext(Dispatchers.Main) { updateNotification() }
                }
            }
        }
    }

    private fun registerNetwork(failover: Boolean) {
        if (networkCallback != null) return
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        connectivity = manager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runCatching { setUnderlyingNetworks(arrayOf(network)) }
                if (failover) scheduleReload()
            }

            override fun onLost(network: Network) {
                runCatching { setUnderlyingNetworks(null) }
                if (failover) scheduleReload()
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        networkCallback = callback
    }

    private fun unregisterNetwork() {
        val callback = networkCallback ?: return
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
        networkCallback = null
        connectivity = null
    }

    private fun scheduleReload() {
        if (stopping || !running) return
        reloadJob?.cancel()
        reloadJob = scope.launch {
            delay(2_500)
            withContext(Dispatchers.Main) { reload() }
        }
    }

    private fun reload() {
        if (!running || stopping) return
        val settings = AntiNetStore.settingsSnapshot()
        val profile = AntiNetStore.selectedProfile() ?: return
        if (tun == null) return
        val config = runCatching { buildConfig(settings, profile.id, hotspot = false) }.getOrElse {
            failAndStop(it.message ?: "Не удалось пересобрать конфигурацию")
            return
        }
        scope.launch {
            val previous = engine
            val next = createEngine(settings, reportTraffic = true)
            runCatching {
                previous?.stop()
                // A new core (and a changed MTU) needs a fresh interface: some cores
                // close the descriptor they were given when they stop.
                val fresh = TunFactory.establish(this@AntiNetVpnService, settings)
                runCatching { tun?.close() }
                tun = fresh
                next.start(applicationContext, config, fresh.fd)
            }.onSuccess {
                engine = next
                currentCore = settings.coreType
                AntiNetRuntime.refreshed(profile.id, profile.name, profile.lastPingMs)
                if (settings.hotspotEnabled) {
                    stopHotspotNow()
                    delay(600)
                    startHotspot()
                } else {
                    stopHotspotNow()
                }
                withContext(Dispatchers.Main) { updateNotification() }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    failAndStop(it.message ?: "Не удалось перезапустить ${next.label}")
                }
            }
        }
    }

    private fun startHotspot() {
        if (hotspotStarted || hotspotStarting || !running) return
        hotspotStarting = true
        val generation = ++hotspotGeneration
        val settings = AntiNetStore.settingsSnapshot()
        val profile = AntiNetStore.selectedProfile()
        if (profile == null) {
            hotspotStarting = false
            return
        }
        scope.launch {
            HotspotBridge.start(
                applicationContext,
                settings.mtu,
                settings.ipv6,
                dnsFor(settings),
            ).onSuccess { session ->
                if (generation != hotspotGeneration) {
                    runCatching { session.fd.close() }
                    runCatching { HotspotBridge.stop() }
                    hotspotStarting = false
                    return@onSuccess
                }
                hotspotTun = session.fd
                val config = runCatching { buildConfig(settings, profile.id, hotspot = true) }.getOrElse {
                    HotspotBridge.failed(it.message ?: "Не удалось собрать конфигурацию раздачи")
                    runCatching { session.fd.close() }
                    hotspotTun = null
                    hotspotStarting = false
                    return@onSuccess
                }
                val hotspotCore = createEngine(settings, reportTraffic = false)
                runCatching { hotspotCore.start(applicationContext, config, session.fd.fd) }
                    .onSuccess {
                        if (generation != hotspotGeneration) {
                            runCatching { hotspotCore.stop() }
                            runCatching { session.fd.close() }
                            hotspotTun = null
                            hotspotStarting = false
                            return@onSuccess
                        }
                        hotspotEngine = hotspotCore
                        hotspotStarted = true
                    }
                    .onFailure {
                        val message = it.message ?: "${hotspotCore.label} не запустился для раздачи"
                        runCatching { session.fd.close() }
                        hotspotTun = null
                        scope.launch {
                            HotspotBridge.stop()
                            HotspotBridge.failed(message)
                        }
                    }
                hotspotStarting = false
            }.onFailure {
                hotspotStarting = false
            }
        }
    }

    private fun stopHotspot() {
        hotspotGeneration++
        if (!hotspotStarted && hotspotEngine == null && hotspotTun == null) return
        hotspotStarted = false
        hotspotStarting = false
        val activeEngine = hotspotEngine
        val fd = hotspotTun
        hotspotEngine = null
        hotspotTun = null
        thread(name = "antinet-hotspot-stop") {
            runBlocking {
                runCatching { activeEngine?.stop() }
                runCatching { fd?.close() }
                HotspotBridge.stop()
            }
        }
    }

    /** Blocking stop used when the hotspot must be restarted right away. */
    private suspend fun stopHotspotNow() {
        hotspotGeneration++
        if (!hotspotStarted && hotspotEngine == null && hotspotTun == null) return
        hotspotStarted = false
        hotspotStarting = false
        val activeEngine = hotspotEngine
        val fd = hotspotTun
        hotspotEngine = null
        hotspotTun = null
        withContext(Dispatchers.IO) {
            runCatching { activeEngine?.stop() }
            runCatching { fd?.close() }
            HotspotBridge.stop()
        }
    }

    private fun syncHotspot() {
        if (!running || stopping) return
        if (AntiNetStore.settingsSnapshot().hotspotEnabled) startHotspot() else stopHotspot()
    }

    private fun dnsFor(settings: AppSettings): String = when (settings.dnsMode) {
        "google" -> "8.8.8.8"
        else -> "1.1.1.1"
    }

    private fun stopEverything() {
        if (stopping) return
        stopping = true
        stopHotspot()
        AntiNetRuntime.stopping()
        trafficJob?.cancel()
        trafficJob = null
        reloadJob?.cancel()
        reloadJob = null
        unregisterNetwork()
        running = false
        runCatching { tun?.close() }
        tun = null
        val active = engine
        engine = null
        currentCore = null
        thread(name = "antinet-core-stop") {
            runBlocking { runCatching { active?.stop() } }
        }
        AntiNetRuntime.stopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        cancelNotification()
        stopSelf()
    }

    private fun failAndStop(message: String) {
        stopHotspot()
        AntiNetRuntime.failed(message)
        running = false
        runCatching { tun?.close() }
        tun = null
        trafficJob?.cancel()
        trafficJob = null
        unregisterNetwork()
        val active = engine
        engine = null
        thread(name = "antinet-core-stop") {
            runBlocking { runCatching { active?.stop() } }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        cancelNotification()
        stopSelf()
    }

    private fun updateNotification() {
        val state = AntiNetRuntime.engine.value
        val notification = buildNotification(
            state.profileName.ifBlank { "AntiNet" },
            Format.speed(state.downSpeed),
            Format.speed(state.upSpeed),
        )
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(name: String, down: String, up: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, AntiNetVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (down.isBlank() && up.isBlank()) {
            "Защищённое соединение активно"
        } else {
            "↓ $down   ↑ $up"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_antinet)
            .setContentTitle(name)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Отключить", stop)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        }.onFailure {
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN-соединение",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Состояние и скорость AntiNet"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun cancelNotification() {
        runCatching { getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID) }
    }
}
