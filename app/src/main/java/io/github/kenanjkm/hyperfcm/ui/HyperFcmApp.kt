package io.github.kenanjkm.hyperfcm.ui

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.kenanjkm.hyperfcm.HookEntry
import io.github.kenanjkm.hyperfcm.HookReport
import io.github.kenanjkm.hyperfcm.InstalledApp
import io.github.kenanjkm.hyperfcm.ModuleConfig
import io.github.kenanjkm.hyperfcm.ModuleUiState
import io.github.kenanjkm.hyperfcm.R
import io.github.kenanjkm.hyperfcm.util.RomInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HyperFcmTheme(content: @Composable () -> Unit) {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(controller = controller, content = content)
}

private enum class MainTab(@StringRes val label: Int) {
    HOME(R.string.tab_home),
    APPS(R.string.tab_apps),
    SETTINGS(R.string.tab_settings)
}

@Composable
fun HyperFcmApp(
    state: ModuleUiState,
    apps: List<InstalledApp>,
    versionName: String,
    launcherIconHidden: Boolean,
    onConfigChange: (ModuleConfig) -> Unit,
    onLauncherIconHiddenChange: (Boolean) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenProject: () -> Unit,
    onReloadApps: () -> Unit,
    onRefreshStatus: () -> Unit,
    onLeaveSafeMode: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { MainTab.entries.size })
    val scope = rememberCoroutineScope()
    val selected = pagerState.targetPage
    val appListSession = remember(pagerState.settledPage) { AppListSession() }
    val onAppConfigChange: (ModuleConfig) -> Unit = {
        appListSession.onUserEdit()
        onConfigChange(it)
    }
    val goTo: (MainTab) -> Unit = { tab -> scope.launch { pagerState.animateScrollToPage(tab.ordinal) } }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab.ordinal,
                        onClick = { goTo(tab) },
                        icon = when (tab) {
                            MainTab.HOME -> MiuixIcons.Home
                            MainTab.APPS -> MiuixIcons.ListView
                            MainTab.SETTINGS -> MiuixIcons.Settings
                        },
                        label = stringResource(tab.label)
                    )
                }
            }
        }
    ) { outerPadding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val bottom = outerPadding.calculateBottomPadding()
            when (MainTab.entries[page]) {
                MainTab.HOME -> HomePage(
                    bottomPadding = bottom,
                    state = state,
                    apps = apps,
                    versionName = versionName,
                    onOpenApps = { goTo(MainTab.APPS) },
                    onOpenSettings = { goTo(MainTab.SETTINGS) },
                    onOpenDiagnostics = onOpenDiagnostics,
                    onOpenProject = onOpenProject,
                    onRefreshStatus = onRefreshStatus,
                    onLeaveSafeMode = onLeaveSafeMode
                )
                MainTab.APPS -> AppsPage(
                    bottomPadding = bottom,
                    session = appListSession,
                    state = state,
                    apps = apps,
                    onConfigChange = onAppConfigChange,
                    onReloadApps = onReloadApps
                )
                MainTab.SETTINGS -> SettingsPage(
                    bottomPadding = bottom,
                    state = state,
                    launcherIconHidden = launcherIconHidden,
                    onConfigChange = onConfigChange,
                    onLauncherIconHiddenChange = onLauncherIconHiddenChange
                )
            }
        }
    }
}

/** One page: collapsing miuix top bar over a lazy list with the HyperOS overscroll feel. */
@Composable
private fun Page(
    title: String,
    bottomPadding: androidx.compose.ui.unit.Dp,
    subtitle: String = "",
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val scrollBehavior: ScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                subtitle = subtitle,
                actions = actions,
                scrollBehavior = scrollBehavior,
                bottomContent = { header?.invoke() }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottomPadding + 16.dp
            ),
            content = content
        )
    }
}

private val CardModifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)

// ---------------------------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomePage(
    bottomPadding: androidx.compose.ui.unit.Dp,
    state: ModuleUiState,
    apps: List<InstalledApp>,
    versionName: String,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenProject: () -> Unit,
    onRefreshStatus: () -> Unit,
    onLeaveSafeMode: () -> Unit
) {
    val config = state.config
    val allowedCount = apps.count { it.packageName in config.allowedPackages }
    val hyperOs = remember { RomInfo.isHyperOs() }
    val report = state.hookReport

    Page(
        title = stringResource(R.string.app_name),
        subtitle = stringResource(R.string.app_subtitle),
        bottomPadding = bottomPadding,
        actions = {
            IconButton(onClick = onRefreshStatus, modifier = Modifier.padding(end = 12.dp)) {
                Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.refresh))
            }
        }
    ) {
        item(key = "status") {
            StatusCard(connected = state.xposedConnected, safeMode = report?.safeMode == true, versionName = versionName)
        }
        if (report?.safeMode == true) item(key = "safeMode") {
            Card(modifier = CardModifier) {
                BasicComponent(
                    title = stringResource(R.string.safe_mode_title),
                    summary = stringResource(R.string.safe_mode_summary)
                )
                TextButton(
                    text = stringResource(R.string.safe_mode_leave),
                    onClick = onLeaveSafeMode,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
        if (!hyperOs) item(key = "rom") {
            Card(modifier = CardModifier) {
                BasicComponent(
                    title = stringResource(R.string.not_hyperos_title),
                    summary = stringResource(R.string.not_hyperos_summary)
                )
            }
        }
        item(key = "hooks") {
            SmallTitle(stringResource(R.string.hook_status_title))
            HookStatusCard(report)
        }
        item(key = "overview") {
            SmallTitle(stringResource(R.string.home_overview))
            Card(modifier = CardModifier) {
                ArrowPreference(
                    title = stringResource(R.string.home_fcm_wake_title),
                    summary = when {
                        state.appsLoading && apps.isEmpty() -> stringResource(R.string.apps_loading)
                        state.appsLoadFailed && apps.isEmpty() -> stringResource(R.string.apps_load_failed)
                        else -> stringResource(R.string.home_apps_allowed, allowedCount, apps.size)
                    },
                    onClick = onOpenApps
                )
                ArrowPreference(
                    title = stringResource(R.string.keep_gms_title),
                    summary = stringResource(if (config.keepGmsAwake) R.string.state_on else R.string.state_off),
                    onClick = onOpenSettings
                )
                ArrowPreference(
                    title = stringResource(R.string.protected_title),
                    summary = if (config.protectedApps.isEmpty()) stringResource(R.string.state_off)
                        else stringResource(R.string.protected_summary_some, config.protectedApps.size),
                    onClick = onOpenSettings
                )
            }
        }
        item(key = "tools") {
            SmallTitle(stringResource(R.string.home_tools))
            Card(modifier = CardModifier) {
                ArrowPreference(
                    title = stringResource(R.string.home_diagnostics),
                    summary = stringResource(R.string.home_diagnostics_summary),
                    onClick = onOpenDiagnostics
                )
                ArrowPreference(
                    title = stringResource(R.string.home_about),
                    summary = "github.com/kenanjkm/HyperFCM",
                    onClick = onOpenProject
                )
            }
        }
        item(key = "system") {
            SmallTitle(stringResource(R.string.home_device))
            SystemInfoCard()
        }
    }
}

@Composable
private fun StatusCard(connected: Boolean, safeMode: Boolean, versionName: String) {
    val ok = connected && !safeMode
    val container = when {
        safeMode -> MiuixTheme.colorScheme.error
        connected -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.secondaryContainer
    }
    val content = if (connected || safeMode) Color.White else MiuixTheme.colorScheme.onSecondaryContainer
    Card(
        modifier = CardModifier.padding(top = 12.dp),
        colors = CardDefaults.defaultColors(color = container, contentColor = content)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = if (ok) MiuixIcons.Basic.Check else MiuixIcons.Basic.Close,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = content
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(when {
                        safeMode -> R.string.status_safe_mode
                        connected -> R.string.status_active
                        else -> R.string.status_inactive
                    }),
                    color = content,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.version_format, versionName),
                    color = content.copy(alpha = 0.8f),
                    style = MiuixTheme.textStyles.body2
                )
                if (!connected && !safeMode) {
                    Text(
                        text = stringResource(R.string.status_inactive_hint),
                        color = content.copy(alpha = 0.8f),
                        style = MiuixTheme.textStyles.body2
                    )
                }
            }
        }
    }
}

/** Which hook groups the hooked processes installed, and how often each has been called since boot. */
@Composable
private fun HookStatusCard(report: HookReport?) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hooks = report?.hooks.orEmpty()
    val okCount = hooks.count { it.ok }
    val silent = report?.silent().orEmpty()
    val summary = when {
        report == null -> stringResource(R.string.hook_status_none)
        report.safeMode -> stringResource(R.string.safe_mode_summary)
        !report.currentBoot -> stringResource(R.string.hook_status_stale, okCount, hooks.size)
        okCount != hooks.size -> stringResource(R.string.hook_status_partial, okCount, hooks.size)
        silent.isNotEmpty() -> stringResource(R.string.hook_status_silent, silent.joinToString { it.name })
        else -> stringResource(R.string.hook_status_all_ok, hooks.size)
    }
    Card(modifier = CardModifier) {
        ArrowPreference(
            title = when {
                report == null -> stringResource(R.string.hook_status_waiting)
                okCount == hooks.size && silent.isEmpty() && report.currentBoot -> stringResource(R.string.hook_status_good)
                else -> stringResource(R.string.hook_status_attention)
            },
            summary = summary,
            onClick = { if (report != null) expanded = !expanded }
        )
        AnimatedVisibility(visible = expanded && report != null) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                hooks.forEach { hook -> HookRow(hook, quiet = hook in silent) }
                Text(
                    text = stringResource(R.string.hook_status_calls_hint),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

@Composable
private fun HookRow(hook: HookEntry, quiet: Boolean) {
    val bad = !hook.ok || quiet
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = if (hook.ok) MiuixIcons.Basic.Check else MiuixIcons.Basic.Close,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (bad) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(hook.name, style = MiuixTheme.textStyles.body1)
            val detail = when {
                !hook.ok -> hook.detail
                hook.calls < 0 -> ""
                hook.calls == 0L -> stringResource(R.string.hook_calls_none)
                else -> stringResource(R.string.hook_calls, hook.calls, formatTime(hook.last))
            }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (bad) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(millis))

@Composable
private fun SystemInfoCard() {
    val unknown = stringResource(R.string.unknown)
    val info = remember(unknown) {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val device = if (model.startsWith(manufacturer, ignoreCase = true)) model
            else listOf(manufacturer, model).filter(String::isNotBlank).joinToString(" ")
        Triple(
            device.ifBlank { unknown },
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            RomInfo.hyperOsVersion().ifBlank { unknown }
        )
    }
    Card(modifier = CardModifier) {
        BasicComponent(title = stringResource(R.string.sysinfo_device), summary = info.first)
        BasicComponent(title = stringResource(R.string.sysinfo_os), summary = info.second)
        BasicComponent(title = "HyperOS", summary = info.third)
    }
}

// ---------------------------------------------------------------------------------------------
// Apps
// ---------------------------------------------------------------------------------------------

@Composable
private fun AppsPage(
    bottomPadding: androidx.compose.ui.unit.Dp,
    session: AppListSession,
    state: ModuleUiState,
    apps: List<InstalledApp>,
    onConfigChange: (ModuleConfig) -> Unit,
    onReloadApps: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    val allowed = state.config.allowedPackages
    val allowedCount = apps.count { it.packageName in allowed }
    val snapshots = session.snapshots(allowed, filter)
    val ordered = remember(apps, snapshots.order) { snapshots.order.grouped(apps, InstalledApp::packageName) }
    val visible = remember(ordered, query, filter, snapshots.filter) {
        ordered.filter { snapshots.filter.includes(it.packageName, filter) }
            .filter { app ->
                query.isBlank() || app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            }
    }

    Page(
        title = stringResource(R.string.tab_apps),
        subtitle = stringResource(R.string.apps_enabled_count, allowedCount, apps.size),
        bottomPadding = bottomPadding,
        actions = {
            IconButton(
                onClick = {
                    val detected = apps.mapTo(mutableSetOf(), InstalledApp::packageName)
                    onConfigChange(state.config.copy(allowedPackages = allowed + detected))
                },
                enabled = apps.isNotEmpty(),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Icon(MiuixIcons.SelectAll, contentDescription = stringResource(R.string.select_all))
            }
        },
        header = {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.search_hint),
                    useLabelAsPlaceholder = true,
                    singleLine = true
                )
                TabRow(
                    tabs = AppFilter.entries.map { stringResource(it.title) },
                    selectedTabIndex = filter.ordinal,
                    onTabSelected = { filter = AppFilter.entries[it] }
                )
            }
        }
    ) {
        when {
            state.appsLoading && apps.isEmpty() -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    InfiniteProgressIndicator()
                }
            }
            state.appsLoadFailed && apps.isEmpty() -> item(key = "failed") {
                EmptyState(
                    title = stringResource(R.string.fcm_apps_load_failed_title),
                    message = stringResource(R.string.fcm_apps_load_failed_msg),
                    onReload = onReloadApps
                )
            }
            visible.isEmpty() -> item(key = "empty") {
                EmptyState(
                    title = stringResource(when {
                        query.isNotBlank() -> R.string.empty_search_title
                        filter == AppFilter.ALLOWED -> R.string.empty_allowed_title
                        filter == AppFilter.DISABLED -> R.string.empty_disabled_title
                        else -> R.string.empty_all_title
                    }),
                    message = stringResource(when {
                        query.isNotBlank() -> R.string.empty_search_msg
                        filter == AppFilter.ALLOWED -> R.string.empty_allowed_msg
                        filter == AppFilter.DISABLED -> R.string.empty_disabled_msg
                        else -> R.string.empty_all_msg
                    }),
                    onReload = if (query.isBlank() && filter == AppFilter.ALL) onReloadApps else null
                )
            }
            else -> items(visible, key = { it.packageName }) { app ->
                val isAllowed = app.packageName in allowed
                val toggle = {
                    val next = if (isAllowed) allowed - app.packageName else allowed + app.packageName
                    onConfigChange(state.config.copy(allowedPackages = next))
                }
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
                    BasicComponent(
                        title = app.label,
                        summary = app.packageName,
                        startAction = { AppIcon(app, 44) },
                        endActions = { Switch(checked = isAllowed, onCheckedChange = { toggle() }) },
                        onClick = toggle
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: InstalledApp, sizeDp: Int) {
    Image(
        bitmap = app.icon.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.padding(end = 12.dp).size(sizeDp.dp).clip(RoundedCornerShape((sizeDp / 4).dp))
    )
}

@Composable
private fun EmptyState(title: String, message: String, onReload: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = MiuixTheme.textStyles.title4)
        Text(message, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (onReload != null) TextButton(text = stringResource(R.string.reload_list), onClick = onReload)
    }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

@Composable
private fun SettingsPage(
    bottomPadding: androidx.compose.ui.unit.Dp,
    state: ModuleUiState,
    launcherIconHidden: Boolean,
    onConfigChange: (ModuleConfig) -> Unit,
    onLauncherIconHiddenChange: (Boolean) -> Unit
) {
    val config = state.config
    var showHideIcon by rememberSaveable { mutableStateOf(false) }
    var showProtected by rememberSaveable { mutableStateOf(false) }

    Page(title = stringResource(R.string.tab_settings), bottomPadding = bottomPadding) {
        item(key = "google") {
            SmallTitle(stringResource(R.string.settings_google_group))
            Card(modifier = CardModifier) {
                SwitchPreference(
                    title = stringResource(R.string.keep_gms_title),
                    summary = stringResource(R.string.keep_gms_summary),
                    checked = config.keepGmsAwake,
                    onCheckedChange = { onConfigChange(config.copy(keepGmsAwake = it)) }
                )
            }
        }
        item(key = "wake") {
            SmallTitle(stringResource(R.string.wake_group))
            Card(modifier = CardModifier) {
                SwitchPreference(
                    title = stringResource(R.string.auto_allow_title),
                    summary = stringResource(R.string.auto_allow_summary),
                    checked = config.autoAllowNewApps,
                    onCheckedChange = { onConfigChange(config.copy(autoAllowNewApps = it)) }
                )
                SwitchPreference(
                    title = stringResource(R.string.keep_notif_title),
                    summary = stringResource(R.string.keep_notif_summary),
                    checked = config.disableAutoCleanNotification,
                    onCheckedChange = { onConfigChange(config.copy(disableAutoCleanNotification = it)) }
                )
                SwitchPreference(
                    title = stringResource(R.string.icebox_title),
                    summary = stringResource(R.string.icebox_summary),
                    checked = config.includeIceBoxDisabledApps,
                    onCheckedChange = { onConfigChange(config.copy(includeIceBoxDisabledApps = it)) }
                )
            }
        }
        item(key = "greeze") {
            SmallTitle(stringResource(R.string.greeze_group))
            Card(modifier = CardModifier) {
                SwitchPreference(
                    title = stringResource(R.string.greeze_global_title),
                    summary = stringResource(R.string.greeze_global_summary),
                    checked = config.greezeGlobalPolicy,
                    onCheckedChange = { onConfigChange(config.copy(greezeGlobalPolicy = it)) }
                )
                ArrowPreference(
                    title = stringResource(R.string.protected_title),
                    summary = if (config.protectedApps.isEmpty()) stringResource(R.string.protected_summary_none)
                        else stringResource(R.string.protected_summary_some, config.protectedApps.size),
                    onClick = { showProtected = true }
                )
            }
        }
        item(key = "night") {
            SmallTitle(stringResource(R.string.night_group))
            Card(modifier = CardModifier) {
                SwitchPreference(
                    title = stringResource(R.string.sleep_keep_data_title),
                    summary = stringResource(R.string.sleep_keep_data_summary),
                    checked = config.sleepModeKeepMobileData,
                    onCheckedChange = { onConfigChange(config.copy(sleepModeKeepMobileData = it)) }
                )
            }
        }
        item(key = "other") {
            SmallTitle(stringResource(R.string.other_group))
            Card(modifier = CardModifier) {
                SwitchPreference(
                    title = stringResource(R.string.hide_icon_setting_title),
                    summary = stringResource(R.string.hide_icon_setting_summary),
                    checked = launcherIconHidden,
                    onCheckedChange = { hidden ->
                        if (hidden) showHideIcon = true else onLauncherIconHiddenChange(false)
                    }
                )
            }
        }
    }

    OverlayDialog(
        show = showHideIcon,
        title = stringResource(R.string.hide_icon_title),
        summary = stringResource(R.string.hide_icon_text),
        onDismissRequest = { showHideIcon = false }
    ) {
        DialogButtons(
            confirm = stringResource(R.string.hide),
            onCancel = { showHideIcon = false },
            onConfirm = {
                showHideIcon = false
                onLauncherIconHiddenChange(true)
            }
        )
    }

    ProtectedAppsDialog(
        show = showProtected,
        selected = config.protectedApps,
        onConfirm = { chosen ->
            showProtected = false
            onConfigChange(config.copy(protectedApps = chosen))
        },
        onDismiss = { showProtected = false }
    )
}

@Composable
private fun DialogButtons(confirm: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(text = stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(20.dp))
        TextButton(
            text = confirm,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }
}

/** Pick apps that HyperOS must neither freeze nor kill in the background. Costs battery; opt-in per app. */
@Composable
private fun ProtectedAppsDialog(
    show: Boolean,
    selected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.protected_title),
        summary = stringResource(R.string.protected_warning),
        onDismissRequest = onDismiss
    ) {
        var chosen by remember(selected) { mutableStateOf(selected) }
        // Every app with a launcher icon, not only FCM apps: apps with their own push channel
        // or long background work are exactly the ones that may need protection.
        val context = LocalContext.current
        var loaded by remember { mutableStateOf<List<InstalledApp>?>(null) }
        var query by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            loaded = withContext(Dispatchers.IO) { loadLaunchableApps(context, selected) }
        }
        val apps = loaded.orEmpty().filter {
            query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.search_hint),
                useLabelAsPlaceholder = true,
                singleLine = true
            )
            when {
                loaded == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    InfiniteProgressIndicator()
                }
                apps.isEmpty() -> Text(
                    stringResource(R.string.protected_empty),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        val checked = app.packageName in chosen
                        val toggle = { chosen = if (checked) chosen - app.packageName else chosen + app.packageName }
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { toggle() }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(app, 36)
                            Text(app.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Checkbox(
                                state = if (checked) ToggleableState.On else ToggleableState.Off,
                                onClick = { toggle() }
                            )
                        }
                    }
                }
            }
            DialogButtons(
                confirm = stringResource(R.string.ok),
                onCancel = onDismiss,
                onConfirm = { onConfirm(chosen) }
            )
        }
    }
}

/** Launchable apps (not this module), protected ones first, then by label. Runs off the main thread. */
private fun loadLaunchableApps(context: android.content.Context, selected: Set<String>): List<InstalledApp> {
    val pm = context.packageManager
    val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN)
        .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    val collator = java.text.Collator.getInstance(java.util.Locale.getDefault())
    val size = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    return pm.queryIntentActivities(launcher, 0)
        .mapNotNull { it.activityInfo?.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .mapNotNull { info ->
            runCatching {
                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    icon = info.loadIcon(pm).toBitmap(size, size)
                )
            }.getOrNull()
        }
        .sortedWith(compareBy<InstalledApp> { it.packageName !in selected }.thenComparator { a, b -> collator.compare(a.label, b.label) })
}
