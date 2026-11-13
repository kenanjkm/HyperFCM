package io.github.kenanjkm.hyperfcm

import io.github.kenanjkm.hyperfcm.util.ConfigSchema.*
import io.github.kenanjkm.hyperfcm.util.ConfigSnapshot
import io.github.kenanjkm.hyperfcm.util.ConfigCodec
import io.github.kenanjkm.hyperfcm.util.ConfigFile
import io.github.kenanjkm.hyperfcm.util.ConfigSaveQueue
import io.github.kenanjkm.hyperfcm.util.ConfigPersistence
import io.github.kenanjkm.hyperfcm.util.FcmApps
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.kenanjkm.hyperfcm.ui.HyperFcmApp
import io.github.kenanjkm.hyperfcm.ui.HyperFcmTheme
import io.github.kenanjkm.hyperfcm.util.HookStatus
import io.github.kenanjkm.hyperfcm.util.IceboxUtils
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "HyperFCM-ui"
private const val ACTION_UPDATE_CONFIG = "io.github.kenanjkm.hyperfcm.update.config"
private const val ACTION_STATUS_REQUEST = "io.github.kenanjkm.hyperfcm.status.request"
private const val CONFIG_PERMISSION = "io.github.kenanjkm.hyperfcm.permission.CONFIG"
private const val APP_LIST_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"
private const val REQUEST_NOTIFICATIONS = 2
private const val REQUEST_APP_LIST = 3
private const val PROJECT_URL = "https://github.com/kenanjkm/HyperFCM"

data class ModuleConfig(
    val allowedPackages: Set<String> = emptySet(),
    val disableAutoCleanNotification: Boolean = false,
    val includeIceBoxDisabledApps: Boolean = false,
    val keepGmsAwake: Boolean = defaultValue(KEY_KEEP_GMS_AWAKE),
    val autoAllowNewApps: Boolean = defaultValue(KEY_AUTO_ALLOW_NEW_APPS),
    val sleepModeKeepMobileData: Boolean = defaultValue(KEY_SLEEP_MODE_KEEP_DATA),
    val greezeGlobalPolicy: Boolean = defaultValue(KEY_GREEZE_GLOBAL_POLICY),
    val installEvents: Set<String> = emptySet(),
    val protectedApps: Set<String> = emptySet()
)

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap
)

enum class ConfigSource {
    REMOTE,
    LOCAL,
    DEFAULT
}

/** One hook group as reported by a hooked process. [calls] is negative when the group has no method hooks. */
data class HookEntry(val name: String, val ok: Boolean, val detail: String, val calls: Long, val last: Long)

/**
 * Hook results of the current (or last reported) boot: system_server's groups followed by
 * PowerKeeper's. Null until system_server reports for the first time.
 */
data class HookReport(val time: Long, val bootTime: Long, val safeMode: Boolean, val hooks: List<HookEntry>) {
    val okCount: Int get() = hooks.count { it.ok }

    /** True when the report belongs to the running boot (bootTime matches within tolerance). */
    val currentBoot: Boolean
        get() = kotlin.math.abs(bootTime - (System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime())) < 60_000L

    /**
     * Groups on paths the system runs constantly. No call at all several minutes after boot means
     * the hook is installed but never reached (for example inlined away by a system update).
     */
    fun silent(): List<HookEntry> =
        if (!currentBoot || time - bootTime < 10 * 60_000L) emptyList()
        else hooks.filter { it.ok && it.calls == 0L && it.name in ALWAYS_BUSY }

    private companion object {
        val ALWAYS_BUSY = setOf("Broadcast entry", "Broadcast dispatch", "Greeze receiver", "Greeze freeze")
    }
}

data class ModuleUiState(
    val config: ModuleConfig = ModuleConfig(),
    val configSource: ConfigSource = ConfigSource.DEFAULT,
    val xposedConnected: Boolean = false,
    val appsLoading: Boolean = true,
    val appsLoadFailed: Boolean = false,
    val hookReport: HookReport? = null
)

/** Optimistic configuration survives Activity recreation while the save queue drains. */
private object ConfigMemory : XposedServiceHelper.OnServiceListener {
    var latestRevision = 0L
    var editGeneration = 0L
    var initialized = false
    val connection = MutableStateFlow<XposedService?>(null)
    private var listenerRegistered = false
    val state = androidx.compose.runtime.mutableStateOf(ModuleUiState())

    fun bindXposedService() {
        if (listenerRegistered) return
        try {
            XposedServiceHelper.registerListener(this)
            listenerRegistered = true
        } catch (error: Throwable) {
            Log.e(TAG, "Could not connect to the Xposed service", error)
        }
    }

    override fun onServiceBind(service: XposedService) {
        connection.value = service
    }

    override fun onServiceDied(service: XposedService) {
        connection.compareAndSet(service, null)
    }
}

class MainActivity : ComponentActivity() {
    private val configRefreshQueued = AtomicBoolean()
    private val configObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { loadConfigFromAvailableSource() }
    }
    private val hookStatusObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { loadHookReport() }
    }
    private val xposedService: XposedService?
        get() = ConfigMemory.connection.value
    private var latestRevision: Long
        get() = ConfigMemory.latestRevision
        set(value) { ConfigMemory.latestRevision = value }
    private var editGeneration: Long
        get() = ConfigMemory.editGeneration
        set(value) { ConfigMemory.editGeneration = value }
    private val uiState = ConfigMemory.state
    /** The half-save warning is shown once per app session, not on every toggle. */
    private var partialSaveWarned = false
    private var installedApps = androidx.compose.runtime.mutableStateOf<List<InstalledApp>>(emptyList())
    private var appsLoadingJob: Job? = null
    private var launcherIconHidden = androidx.compose.runtime.mutableStateOf(false)
    /** Bumped when the app-list permission changes, so open lists reload. */
    private val appListGeneration = androidx.compose.runtime.mutableIntStateOf(0)
    private val launcherAliasComponent by lazy {
        ComponentName(packageName, "$packageName.LauncherAlias")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcherIconHidden.value = packageManager.getComponentEnabledSetting(launcherAliasComponent) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val darkMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ) { darkMode },
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ) { darkMode }
        )
        window.isNavigationBarContrastEnforced = false
        if (!ConfigMemory.initialized) {
            loadConfigFromLocalFile()
            ConfigMemory.initialized = true
        }
        initializeDefaultConfigIfNeeded()
        contentResolver.registerContentObserver(Uri.parse("content://$packageName.provider/config"), false, configObserver)
        contentResolver.registerContentObserver(Uri.parse("content://$packageName.provider/hookStatus"), false, hookStatusObserver)
        loadHookReport()
        // One dialog at a time: a second requestPermissions while one is showing is dropped.
        requestNextPermission(0)
        ConfigMemory.bindXposedService()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ConfigMemory.connection.collect { service ->
                    uiState.value = uiState.value.copy(xposedConnected = service != null)
                    loadConfigFromAvailableSource()
                }
            }
        }

        setContent {
            HyperFcmTheme {
                HyperFcmApp(
                    state = uiState.value,
                    apps = installedApps.value,
                    versionName = BuildConfig.VERSION_NAME,
                    launcherIconHidden = launcherIconHidden.value,
                    onConfigChange = ::updateConfig,
                    onLauncherIconHiddenChange = ::setLauncherIconHidden,
                    onOpenDiagnostics = ::openGmsDiagnostics,
                    onOpenProject = ::openProjectPage,
                    onReloadApps = ::loadInstalledApps,
                    appListGeneration = appListGeneration.intValue,
                    onRefreshStatus = ::requestHookStatus,
                    onLeaveSafeMode = ::leaveSafeMode
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launcherIconHidden.value = packageManager.getComponentEnabledSetting(launcherAliasComponent) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        loadInstalledApps()
        requestHookStatus()
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(configObserver)
        contentResolver.unregisterContentObserver(hookStatusObserver)
        super.onDestroy()
    }

    /** Reads the reports the hooked processes handed to ConfigProvider; absent until the first boot with the module. */
    private fun loadHookReport() {
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                runCatching {
                    fun read(source: String): HookStatus.Snapshot? {
                        val file = java.io.File(applicationContext.filesDir, HookStatus.fileFor(source) ?: return null)
                        return if (file.exists()) HookStatus.Snapshot(JSONObject(file.readText())) else null
                    }
                    val system = read(HookStatus.SOURCE_SYSTEM) ?: return@runCatching null
                    // PowerKeeper's groups only count when they come from the same boot.
                    val powerKeeper = read(HookStatus.SOURCE_POWERKEEPER)
                        ?.takeIf { kotlin.math.abs(it.bootTime - system.bootTime) < 60_000L }
                    val hooks = (system.hooks + powerKeeper?.hooks.orEmpty())
                        .map { HookEntry(it.name, it.ok, it.detail, it.calls, it.last) }
                    HookReport(system.time, system.bootTime, system.safeMode, hooks)
                }.getOrNull()
            }
            uiState.value = uiState.value.copy(hookReport = report)
        }
    }

    /**
     * Asks the hooked processes for fresh call counters. One broadcast when the user opens or
     * refreshes the page; they answer through ConfigProvider and the observer reloads the report.
     */
    private fun requestHookStatus() {
        try {
            sendBroadcast(Intent(ACTION_STATUS_REQUEST), CONFIG_PERMISSION)
        } catch (error: Throwable) {
            Log.w(TAG, "Status request unavailable", error)
        }
    }

    /** Releases the boot guard: system_server sees the new token at the next boot and installs the hooks again. */
    private fun leaveSafeMode() {
        ConfigSaveQueue.submit {
            val saved = runCatching {
                xposedService?.getRemotePreferences("guard")?.edit()
                    ?.putLong("reset", System.currentTimeMillis())?.commit() == true
            }.getOrDefault(false)
            runOnUiThread {
                Toast.makeText(this, getString(if (saved) R.string.safe_mode_released else R.string.toast_save_failed),
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLauncherIconHidden(hidden: Boolean) {
        try {
            packageManager.setComponentEnabledSetting(
                launcherAliasComponent,
                if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            launcherIconHidden.value = hidden
        } catch (error: Throwable) {
            Toast.makeText(this, getString(R.string.toast_icon_failed), Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Could not change launcher icon visibility", error)
        }
    }

    /**
     * HyperOS hides other apps from the installed-app list until the user grants its own
     * "read installed apps" permission. Without it both app lists come back incomplete.
     */
    /** Asks the first still-needed permission after [step]; its result asks the next one. */
    private fun requestNextPermission(step: Int) {
        if (step <= 0 && requestAppListPermissionIfNeeded()) return
        if (step <= 1 && requestNotificationPermissionIfNeeded()) return
        if (step <= 2 && uiState.value.config.includeIceBoxDisabledApps) requestIceBoxPermissionIfNeeded()
    }

    private fun requestAppListPermissionIfNeeded(): Boolean = try {
        val info = runCatching { packageManager.getPermissionInfo(APP_LIST_PERMISSION, 0) }.getOrNull()
        if (info == null || info.protection != android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
            || checkSelfPermission(APP_LIST_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            false
        } else {
            requestPermissions(arrayOf(APP_LIST_PERMISSION), REQUEST_APP_LIST)
            true
        }
    } catch (error: Throwable) {
        Log.w(TAG, "App list permission request unavailable", error)
        false
    }

    @Deprecated("Activity result API is not used for these one-off system dialogs")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_APP_LIST -> {
                appListGeneration.value++
                loadInstalledApps(force = true)
                requestNextPermission(1)
            }
            REQUEST_NOTIFICATIONS -> {
                // Marked only once the dialog was really answered, so a dropped request is retried.
                if (grantResults.isNotEmpty()) {
                    getSharedPreferences("ui", MODE_PRIVATE).edit()
                        .putBoolean("notificationPermissionAsked", true).apply()
                }
                requestNextPermission(2)
            }
        }
    }

    /** Only for the one-off alert when a hook group fails after boot; asked once, never nagged. */
    private fun requestNotificationPermissionIfNeeded(): Boolean = try {
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            || prefs.getBoolean("notificationPermissionAsked", false)) {
            false
        } else {
            requestPermissions(arrayOf(permission), REQUEST_NOTIFICATIONS)
            true
        }
    } catch (error: Throwable) {
        Log.w(TAG, "Notification permission request unavailable", error)
        false
    }

    private fun requestIceBoxPermissionIfNeeded() {
        try {
            if (ContextCompat.checkSelfPermission(this, IceboxUtils.SDK_PERMISSION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(IceboxUtils.SDK_PERMISSION),
                    IceboxUtils.REQUEST_CODE
                )
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Icebox permission request unavailable", error)
        }
    }

    private fun initializeDefaultConfigIfNeeded() {
        val appContext = applicationContext
        // Queue before service synchronization and user edits. Revision zero allows
        // any newer remote configuration to replace the initial defaults.
        ConfigSaveQueue.submit {
            try {
                if (ConfigFile.initializeIfMissing(appContext, ModuleConfig().toJson()
                        .put("revision", 0L).put("bootstrapDefaults", true))) {
                    appContext.sendBroadcast(Intent(ACTION_UPDATE_CONFIG))
                    runOnUiThread {
                        if (latestRevision == 0L && uiState.value.configSource == ConfigSource.DEFAULT) {
                            uiState.value = uiState.value.copy(configSource = ConfigSource.LOCAL)
                        }
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Could not initialize default configuration", error)
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.toast_default_save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadConfigFromAvailableSource() {
        if (!configRefreshQueued.compareAndSet(false, true)) return
        val generation = editGeneration
        ConfigSaveQueue.submit {
            // Coalesce waiting reads, but allow a new refresh once this read starts.
            configRefreshQueued.set(false)
            try {
                // Read when this queued task actually executes, after earlier edits finish.
                val persisted = runCatching { ConfigFile.read(applicationContext) }.getOrNull()
                val localRevision = persisted?.optLong("revision", 0) ?: -1L
                val preferences = xposedService?.getRemotePreferences("config")
                if (preferences != null && preferences.getBoolean("init", false)
                    && (preferences.getLong("revision", 0) > localRevision
                        // Older releases did not write a revision. Preserve those
                        // remote settings over a freshly bootstrapped default file.
                        || persisted?.optBoolean("bootstrapDefaults", false) == true
                            && preferences.getLong("revision", 0) == localRevision)) {
                    val remoteSnapshot = ConfigSnapshot(preferences.all)
                    val remote = configFromSnapshot(remoteSnapshot)
                    val revision = remoteSnapshot.revision
                    val localSaved = runCatching {
                        ConfigFile.write(applicationContext, remote.toJson().put("revision", revision))
                    }.onFailure { Log.w(TAG, "Local sync unavailable; using remote configuration", it) }.isSuccess
                    runOnUiThread {
                        if (generation == editGeneration) {
                            latestRevision = maxOf(latestRevision, revision)
                            uiState.value = uiState.value.copy(config = remote,
                                configSource = if (localSaved) ConfigSource.LOCAL else ConfigSource.REMOTE)
                        }
                    }
                } else if (persisted != null) {
                    val localSnapshot = ConfigCodec.fromJson(persisted)
                    val local = configFromSnapshot(localSnapshot)
                    saveRemote(localSnapshot)
                    runOnUiThread {
                        if (generation == editGeneration) {
                            latestRevision = maxOf(latestRevision, localRevision)
                            uiState.value = uiState.value.copy(config = local, configSource = ConfigSource.LOCAL)
                        }
                    }
                }
                applicationContext.sendBroadcast(Intent(ACTION_UPDATE_CONFIG))
            } catch (error: Throwable) { Log.w(TAG, "Config synchronization unavailable", error) }
        }
    }

    private fun configFromSnapshot(snapshot: ConfigSnapshot) = ModuleConfig(
        allowedPackages = snapshot.allowList.toSet(),
        disableAutoCleanNotification = snapshot.options.getValue(KEY_DISABLE_AUTO_CLEAN_NOTIFICATION),
        includeIceBoxDisabledApps = snapshot.options.getValue(KEY_INCLUDE_ICEBOX_DISABLED_APP),
        keepGmsAwake = snapshot.options.getValue(KEY_KEEP_GMS_AWAKE),
        autoAllowNewApps = snapshot.options.getValue(KEY_AUTO_ALLOW_NEW_APPS),
        sleepModeKeepMobileData = snapshot.options.getValue(KEY_SLEEP_MODE_KEEP_DATA),
        greezeGlobalPolicy = snapshot.options.getValue(KEY_GREEZE_GLOBAL_POLICY),
        installEvents = snapshot.installEvents.toSet(),
        protectedApps = snapshot.protectedApps.toSet()
    )

    private fun configFromJson(json: JSONObject) = configFromSnapshot(ConfigCodec.fromJson(json))

    private fun loadConfigFromLocalFile() {
        try {
            val json = ConfigFile.read(this)
            val config = configFromJson(json)
            latestRevision = json.optLong("revision", 0)
            uiState.value = uiState.value.copy(config = config, configSource = ConfigSource.LOCAL)
        } catch (error: Throwable) {
            uiState.value = uiState.value.copy(configSource = ConfigSource.DEFAULT)
            Log.i(TAG, "Using default configuration", error)
        }
    }

    private fun updateConfig(requested: ModuleConfig) {
        val previous = uiState.value.config
        val config = requested
        if (config.includeIceBoxDisabledApps && !uiState.value.config.includeIceBoxDisabledApps) requestIceBoxPermissionIfNeeded()
        editGeneration++
        val generation = editGeneration
        latestRevision = maxOf(latestRevision + 1, System.currentTimeMillis())
        val revision = latestRevision
        uiState.value = uiState.value.copy(config = config)
        ConfigSaveQueue.submit {
            val result = runCatching {
                ConfigPersistence.save(
                    object : ConfigPersistence.Store {
                        override fun read(): JSONObject = ConfigFile.read(applicationContext)
                        override fun write(json: JSONObject) { ConfigFile.write(applicationContext, json) }
                    },
                    object : ConfigPersistence.Store {
                        override fun read(): JSONObject? {
                            val values = xposedService?.getRemotePreferences("config")?.all ?: return null
                            if (values["init"] != true) return null
                            val snapshot = ConfigSnapshot(values)
                            return configFromSnapshot(snapshot).toJson().put("revision", snapshot.revision)
                        }
                        override fun write(json: JSONObject) {
                            if (!saveRemote(ConfigCodec.fromJson(json))) {
                                throw java.io.IOException("Remote configuration save unavailable")
                            }
                        }
                    }, previous.toJson(), config.toJson(), revision, System.currentTimeMillis())
            }.onFailure { Log.e(TAG, "Could not prepare configuration save", it) }.getOrNull()
            result?.localFailure?.let { Log.w(TAG, "Local configuration save failed", it) }
            // Some readers use only one store (PowerKeeper and early boot: remote; install and
            // Icebox handling: local), so a half save is reported instead of passing silently.
            if (result != null && result.localSaved != result.remoteSaved && !partialSaveWarned) {
                partialSaveWarned = true
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.toast_save_partial), Toast.LENGTH_LONG).show()
                }
            }
            if (result == null || !result.localSaved && !result.remoteSaved) {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.toast_save_failed), Toast.LENGTH_SHORT).show()
                    if (generation == editGeneration) {
                        uiState.value = uiState.value.copy(config = previous)
                        loadConfigFromAvailableSource()
                    }
                }
            } else {
                val saved = result.config
                val persistedConfig = configFromJson(saved)
                val persistedRevision = saved.getLong("revision")
                runOnUiThread {
                    latestRevision = maxOf(latestRevision, persistedRevision)
                    if (generation == editGeneration) {
                        uiState.value = uiState.value.copy(config = persistedConfig,
                            configSource = if (result.localSaved) ConfigSource.LOCAL else ConfigSource.REMOTE)
                    }
                }
                applicationContext.sendBroadcast(Intent(ACTION_UPDATE_CONFIG))
            }
        }
    }

    private fun saveRemote(snapshot: ConfigSnapshot): Boolean = try {
        val preferences = xposedService?.getRemotePreferences("config")
        when {
            preferences == null -> false
            snapshot.matchesPreferences(preferences.all) -> true
            else -> {
                val editor = preferences.edit()
                    .putBoolean("init", true)
                    .putLong("revision", snapshot.revision)
                    .putStringSet("allowList", snapshot.allowList.toMutableSet())
                    .putStringSet(KEY_INSTALL_EVENTS, snapshot.installEvents.toMutableSet())
                    .putStringSet(KEY_PROTECTED_APPS, snapshot.protectedApps.toMutableSet())
                RETIRED_KEYS.forEach(editor::remove)
                snapshot.options.forEach { (key, enabled) -> editor.putBoolean(key, enabled) }
                editor.commit()
            }
        }
    } catch (error: Throwable) {
        Log.w(TAG, "Remote save unavailable; local configuration is retained", error)
        false
    }

    private fun ModuleConfig.toJson() = JSONObject()
        .put("allowList", JSONArray(allowedPackages.sorted()))
        .put(KEY_INSTALL_EVENTS, JSONArray(installEvents.toList()))
        .put(KEY_PROTECTED_APPS, JSONArray(protectedApps.sorted()))
        .put(KEY_AUTO_ALLOW_NEW_APPS, autoAllowNewApps)
        .put(KEY_SLEEP_MODE_KEEP_DATA, sleepModeKeepMobileData)
        .put(KEY_GREEZE_GLOBAL_POLICY, greezeGlobalPolicy)
        .put(KEY_DISABLE_AUTO_CLEAN_NOTIFICATION, disableAutoCleanNotification)
        .put(KEY_INCLUDE_ICEBOX_DISABLED_APP, includeIceBoxDisabledApps)
        .put(KEY_KEEP_GMS_AWAKE, keepGmsAwake)

    private fun loadInstalledApps() = loadInstalledApps(force = false)

    private fun loadInstalledApps(force: Boolean) {
        if (appsLoadingJob?.isActive == true) {
            if (!force) return
            appsLoadingJob?.cancel()
        }
        uiState.value = uiState.value.copy(appsLoading = true, appsLoadFailed = false)
        appsLoadingJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { queryInstalledApps() }
            }
            result.onSuccess { apps ->
                installedApps.value = apps
                uiState.value = uiState.value.copy(appsLoading = false, appsLoadFailed = false)
            }.onFailure { error ->
                uiState.value = uiState.value.copy(appsLoading = false, appsLoadFailed = true)
                Log.e(TAG, "Could not read installed applications", error)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun queryInstalledApps(): List<InstalledApp> {
        val flags = PackageManager.GET_RECEIVERS or
            PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_UNINSTALLED_PACKAGES
        val collator = Collator.getInstance(Locale.getDefault())
        return packageManager.getInstalledPackages(flags).mapNotNull { packageInfo: PackageInfo ->
            val receivers = packageInfo.receivers ?: return@mapNotNull null
            if (!receivers.any(::isFcmReceiver)) return@mapNotNull null
            val applicationInfo = packageInfo.applicationInfo ?: return@mapNotNull null
            try {
                InstalledApp(
                    packageName = packageInfo.packageName,
                    label = applicationInfo.loadLabel(packageManager).toString(),
                    icon = drawableToBitmap(applicationInfo.loadIcon(packageManager))
                )
            } catch (error: Throwable) {
                Log.w(TAG, "Skipping ${packageInfo.packageName}", error)
                null
            }
        }.sortedWith { first, second -> collator.compare(first.label, second.label) }
    }

    private fun isFcmReceiver(receiver: ActivityInfo): Boolean =
        FcmApps.isReceiverName(receiver.name)

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val size = (96 * resources.displayMetrics.density / 2f).toInt().coerceIn(72, 144)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun openGmsDiagnostics() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(
                "com.google.android.gms",
                "com.google.android.gms.gcm.GcmDiagnostics"
            )
            setPackage("com.google.android.gms")
        }
        try {
            startActivity(intent)
        } catch (error: Throwable) {
            Toast.makeText(this, getString(R.string.toast_diag_failed), Toast.LENGTH_SHORT).show()
            Log.w(TAG, "GMS diagnostics activity unavailable", error)
        }
    }

    private fun openProjectPage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
        } catch (error: Throwable) {
            Toast.makeText(this, getString(R.string.toast_no_browser), Toast.LENGTH_SHORT).show()
        }
    }
}
