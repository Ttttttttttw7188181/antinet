package com.antinet.vpn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.AntiNetViewModel
import com.antinet.vpn.ui.screens.AppearanceScreen
import com.antinet.vpn.ui.screens.BackupScreen
import com.antinet.vpn.ui.screens.CoreScreen
import com.antinet.vpn.ui.screens.FragmentationScreen
import com.antinet.vpn.ui.screens.HomeScreen
import com.antinet.vpn.ui.screens.HotspotScreen
import com.antinet.vpn.ui.screens.ImportScreen
import com.antinet.vpn.ui.screens.PerAppScreen
import com.antinet.vpn.ui.screens.QrScannerScreen
import com.antinet.vpn.ui.screens.RoutingScreen
import com.antinet.vpn.ui.screens.ServerEditorScreen
import com.antinet.vpn.ui.screens.ServersScreen
import com.antinet.vpn.ui.screens.SettingsScreen
import com.antinet.vpn.ui.screens.StatsScreen
import com.antinet.vpn.ui.screens.SubscriptionsScreen
import com.antinet.vpn.ui.screens.AboutScreen

enum class Route {
    HOME,
    SERVERS,
    STATS,
    SETTINGS,
    SUBSCRIPTIONS,
    SERVER_EDIT,
    IMPORT,
    SCAN_QR,
    APPEARANCE,
    CORE,
    ROUTING,
    FRAGMENTATION,
    HOTSPOT,
    PER_APP,
    BACKUP,
    ABOUT,
}

data class NavEntry(val route: Route, val arg: String = "")

private const val SEPARATOR = "\u001F"

private val NavSaver = listSaver<SnapshotStateList<NavEntry>, String>(
    save = { stack -> stack.map { "${it.route.name}$SEPARATOR${it.arg}" } },
    restore = { saved ->
        saved.map { raw ->
            val parts = raw.split(SEPARATOR, limit = 2)
            NavEntry(
                route = runCatching { Route.valueOf(parts[0]) }.getOrDefault(Route.HOME),
                arg = parts.getOrElse(1) { "" },
            )
        }.toMutableStateList()
    },
)

@Composable
fun AntiNetApp(vm: AntiNetViewModel) {
    val backStack = rememberSaveable(saver = NavSaver) { mutableStateListOf(NavEntry(Route.HOME)) }
    val snackbarHostState = remember { SnackbarHostState() }
    val current = backStack.lastOrNull() ?: NavEntry(Route.HOME)

    fun go(route: Route) {
        backStack.add(NavEntry(route))
    }

    fun goWithArg(route: Route, arg: String) {
        backStack.add(NavEntry(route, arg))
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun resetTo(route: Route) {
        backStack.clear()
        backStack.add(NavEntry(route))
    }

    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = backStack.size > 1 || current.route != Route.HOME) {
        if (backStack.size > 1) pop() else resetTo(Route.HOME)
    }

    val showBottomBar = current.route == Route.HOME ||
        current.route == Route.SERVERS ||
        current.route == Route.STATS ||
        current.route == Route.SETTINGS

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                AntiNetBottomBar(
                    current = current.route,
                    onSelect = { route -> if (route != current.route) resetTo(route) },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                },
                label = "antinet-screen",
            ) { entry ->
                when (entry.route) {
                    Route.HOME -> HomeScreen(
                        vm = vm,
                        onServers = { resetTo(Route.SERVERS) },
                        onStats = { resetTo(Route.STATS) },
                        onSettings = { resetTo(Route.SETTINGS) },
                        onHotspot = { go(Route.HOTSPOT) },
                        onRouting = { go(Route.ROUTING) },
                        onCore = { go(Route.CORE) },
                        onFragmentation = { go(Route.FRAGMENTATION) },
                    )

                    Route.SERVERS -> ServersScreen(
                        vm = vm,
                        onNewProfile = { goWithArg(Route.SERVER_EDIT, "") },
                        onEditProfile = { id -> goWithArg(Route.SERVER_EDIT, id) },
                        onImport = { go(Route.IMPORT) },
                        onScan = { go(Route.SCAN_QR) },
                        onSubscriptions = { go(Route.SUBSCRIPTIONS) },
                    )

                    Route.STATS -> StatsScreen(vm = vm)

                    Route.SETTINGS -> SettingsScreen(
                        vm = vm,
                        onOpenSubscriptions = { go(Route.SUBSCRIPTIONS) },
                        onOpenAppearance = { go(Route.APPEARANCE) },
                        onOpenCore = { go(Route.CORE) },
                        onOpenRouting = { go(Route.ROUTING) },
                        onOpenFragmentation = { go(Route.FRAGMENTATION) },
                        onOpenHotspot = { go(Route.HOTSPOT) },
                        onOpenPerApp = { go(Route.PER_APP) },
                        onOpenBackup = { go(Route.BACKUP) },
                        onOpenAbout = { go(Route.ABOUT) },
                    )

                    Route.SUBSCRIPTIONS -> SubscriptionsScreen(vm = vm, onBack = { pop() })

                    Route.SERVER_EDIT -> ServerEditorScreen(
                        vm = vm,
                        profileId = entry.arg.ifBlank { null },
                        onBack = { pop() },
                    )

                    Route.IMPORT -> ImportScreen(
                        vm = vm,
                        initialText = entry.arg,
                        onBack = { pop() },
                        onScan = { go(Route.SCAN_QR) },
                    )

                    Route.SCAN_QR -> QrScannerScreen(
                        onBack = { pop() },
                        onResult = { text ->
                            backStack[backStack.lastIndex] = NavEntry(Route.IMPORT, text)
                        },
                    )

                    Route.APPEARANCE -> AppearanceScreen(vm = vm, onBack = { pop() })
                    Route.CORE -> CoreScreen(vm = vm, onBack = { pop() })
                    Route.ROUTING -> RoutingScreen(vm = vm, onBack = { pop() })
                    Route.FRAGMENTATION -> FragmentationScreen(vm = vm, onBack = { pop() })
                    Route.HOTSPOT -> HotspotScreen(vm = vm, onBack = { pop() })
                    Route.PER_APP -> PerAppScreen(vm = vm, onBack = { pop() })
                    Route.BACKUP -> BackupScreen(vm = vm, onBack = { pop() })
                    Route.ABOUT -> AboutScreen(onBack = { pop() })
                }
            }
        }
    }
}

private data class TabSpec(val route: Route, val title: String, val icon: ImageVector)

private val Tabs = listOf(
    TabSpec(Route.HOME, "Главная", Icons.Rounded.Home),
    TabSpec(Route.SERVERS, "Серверы", Icons.Rounded.Dns),
    TabSpec(Route.STATS, "Статистика", Icons.Rounded.Insights),
    TabSpec(Route.SETTINGS, "Настройки", Icons.Rounded.Settings),
)

@Composable
private fun AntiNetBottomBar(current: Route, onSelect: (Route) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { onSelect(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.title) },
                label = { Text(tab.title) },
            )
        }
    }
}
