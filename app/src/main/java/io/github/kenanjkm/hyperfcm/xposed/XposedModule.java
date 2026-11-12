package io.github.kenanjkm.hyperfcm.xposed;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import io.github.kenanjkm.hyperfcm.libxposed.XC_MethodHook;
import io.github.kenanjkm.hyperfcm.libxposed.XposedBridge;
import io.github.kenanjkm.hyperfcm.libxposed.XposedHelpers;
import io.github.kenanjkm.hyperfcm.util.ConfigSchema;
import io.github.kenanjkm.hyperfcm.util.ConfigSnapshot;
import io.github.kenanjkm.hyperfcm.util.DiagnosticLogger;
import io.github.kenanjkm.hyperfcm.util.FcmTrust;
import io.github.kenanjkm.hyperfcm.util.HookStatus;

/**
 * Shared state of one hooked process: context, configuration and the hook status report.
 * Background work here is limited to short-lived threads that use plain sleep; there are no
 * wakelocks, alarms or periodic jobs.
 */
public abstract class XposedModule {
    /** Process the hooks run in: "android" for system_server, otherwise the package name. */
    private static String processName = "UNKNOWN";
    /** Package name of the module app. Keep in sync with applicationId in app/build.gradle. */
    public static final String SELF_PACKAGE_NAME = "io.github.kenanjkm.hyperfcm";
    private static final String ACTION_UPDATE_CONFIG = SELF_PACKAGE_NAME + ".update.config";
    private static final String ACTION_STATUS_REQUEST = SELF_PACKAGE_NAME + ".status.request";
    private static final String CONFIG_PERMISSION = SELF_PACKAGE_NAME + ".permission.CONFIG";

    protected final ClassLoader classLoader;
    static final String TAG = "HyperFCM";
    private static volatile ConfigSnapshot config;
    private static boolean reloadRequested;

    @SuppressLint("StaticFieldLeak")
    protected static volatile Context context = null;
    private static final java.util.concurrent.CopyOnWriteArrayList<XposedModule> instances = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean receiversRegistered;
    public static volatile boolean isBootComplete = false;
    private static Thread loadConfigThread = null;
    private static volatile boolean bootInitThreadStarted = false;
    private static volatile boolean bootInitInvoked = false;
    private static volatile long lastStatusPublish;

    protected XposedModule(final ClassLoader classLoader) {
        this.classLoader = classLoader;
        instances.add(this);
        if (bootInitInvoked) {
            try {
                onCanReadConfig();
            } catch (Throwable e) {
                printLog(e.getMessage());
            }
        }
    }

    public static void setProcessName(String name) {
        processName = name;
    }

    /**
     * Hands the process context to the module. In system_server this is the system context taken
     * from ActivityThread; the hooks are installed before any Application exists there.
     */
    public static void attachContext(Context processContext) {
        if (context != null || processContext == null) {
            return;
        }
        context = processContext;
        startBootInitThread();
    }

    /**
     * Waits for the user to unlock, then loads the configuration. Early in boot the services the
     * check needs may not be registered yet, so it retries; ACTION_USER_UNLOCKED is the fallback.
     */
    private static void startBootInitThread() {
        if (bootInitThreadStarted) {
            return;
        }
        bootInitThreadStarted = true;
        Thread thread = new Thread(() -> {
            boolean receiverRegistered = false;
            while (true) {
                if (bootInitInvoked) {
                    return;
                }
                try {
                    UserManager userManager = context.getSystemService(UserManager.class);
                    if (userManager != null && userManager.isUserUnlocked()) {
                        callAllOnCanReadConfig();
                        // Registration may fail while services are starting. Keep polling
                        // until initialization actually succeeds, even after unlock.
                        if (bootInitInvoked) return;
                    }
                } catch (Throwable ignored) {
                }
                if (!receiverRegistered) {
                    try {
                        IntentFilter userUnlockIntentFilter = new IntentFilter();
                        userUnlockIntentFilter.addAction(Intent.ACTION_USER_UNLOCKED);
                        context.registerReceiver(unlockBroadcastReceive, userUnlockIntentFilter);
                        receiverRegistered = true;
                    } catch (Throwable ignored) {
                    }
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "hyperfcm-boot-init");
        thread.setDaemon(true);
        thread.start();
    }

    private static synchronized void callAllOnCanReadConfig() {
        if (bootInitInvoked) {
            return;
        }
        if (!initReceivers()) return;
        bootInitInvoked = true;
        // Load once right at unlock so the first push is not dropped for a missing configuration.
        onUpdateConfig();
        publishHookStatus(12);
        for (XposedModule instance : instances) {
            try {
                instance.onCanReadConfig();
            } catch (Throwable e) {
                printLog(e.getMessage());
            }
        }
    }

    protected void onCanReadConfig() throws Throwable {
    }

    /**
     * Hands the hook results and call counters to the module app, off the caller's thread.
     * The provider may not be reachable right at unlock, so the boot report retries a few times
     * with plain sleep (monotonic, never wakes the device). No alarm, no periodic work.
     */
    private static void publishHookStatus(int attempts) {
        Thread thread = new Thread(() -> {
            for (int attempt = 0; attempt < attempts; attempt++) {
                if (HookStatus.publish(context, SELF_PACKAGE_NAME + ".provider", processName)) {
                    return;
                }
                if (attempt + 1 == attempts) break;
                try { Thread.sleep(10_000L); } catch (InterruptedException e) { return; }
            }
            printLog("hook status publish failed");
        }, "hyperfcm-hook-status");
        thread.setDaemon(true);
        thread.start();
    }

    protected static boolean isConfigurationReady() { return config != null; }
    protected void onConfigLoaded(ConfigSnapshot previous, ConfigSnapshot current) throws Throwable { }
    private static void notifyConfigLoaded(ConfigSnapshot previous) {
        for (XposedModule instance : instances) {
            try { instance.onConfigLoaded(previous, config); }
            catch (Throwable error) { printLog("Config reconciliation failed: " + error); }
        }
    }

    protected static void printLog(String text) {
        printLog(text, false);
    }

    private static final DiagnosticLogger diagnosticLogger = new DiagnosticLogger(
            XposedBridge::log,
            error -> Log.w(TAG, "Diagnostic log failed; further logging warnings suppressed", error));

    protected static void printLog(String text, Boolean diagnostic) {
        diagnosticLogger.log("[" + processName + "] " + text, Boolean.TRUE.equals(diagnostic),
                SystemClock.elapsedRealtime());
    }

    protected void checkUserDeviceUnlockAndUpdateConfig() {
        if (context == null) {
            return;
        }
        try {
            UserManager userManager = context.getSystemService(UserManager.class);
            if (userManager != null && userManager.isUserUnlocked()) {
                onUpdateConfig();
            }
        } catch (Throwable e) {
            printLog("Config update failed: " + e.getMessage());
        }
    }

    private static final BroadcastReceiver unlockBroadcastReceive = new BroadcastReceiver() {
        public void onReceive(Context _context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_USER_UNLOCKED.equals(action)) {
                try {
                    context.unregisterReceiver(unlockBroadcastReceive);
                } catch (Throwable ignored) {
                }
                callAllOnCanReadConfig();
            }
        }
    };

    /** Packages the user chose to keep out of the freezer and the memory killer (empty until config loads). */
    protected static java.util.Set<String> protectedPackages() {
        ConfigSnapshot snapshot = config;
        return snapshot == null ? java.util.Collections.emptySet() : snapshot.protectedApps;
    }

    protected static boolean isDeliveryTargetAllowed(String packageName) {
        ConfigSnapshot snapshot = config;
        return snapshot != null && packageName != null && snapshot.allowList.contains(packageName);
    }

    protected boolean targetIsAllow(String packageName) {
        ConfigSnapshot snapshot = config;
        if (snapshot == null) checkUserDeviceUnlockAndUpdateConfig();
        return packageName != null && (SELF_PACKAGE_NAME.equals(packageName)
                || snapshot != null && snapshot.allowList.contains(packageName));
    }

    protected boolean getBooleanConfig(String key, boolean defaultValue) {
        ConfigSnapshot snapshot = config;
        if (snapshot == null) {
            checkUserDeviceUnlockAndUpdateConfig();
            return defaultValue;
        }
        return snapshot.options.getOrDefault(key, defaultValue);
    }

    protected static synchronized void onUpdateConfig() {
        reloadRequested = true;
        if (loadConfigThread != null) return;
        loadConfigThread = new Thread(() -> {
            while (true) {
                synchronized (XposedModule.class) {
                    if (!reloadRequested) {
                        loadConfigThread = null;
                        return;
                    }
                    reloadRequested = false;
                }
                ConfigSnapshot previous = config;
                try {
                    reloadLatestConfig();
                    isBootComplete = true;
                    notifyConfigLoaded(previous);
                } catch (Throwable e) {
                    printLog("Config reload failed: " + e.getMessage());
                }
            }
        }, "hyperfcm-config");
        loadConfigThread.start();
    }

    private static void reloadLatestConfig() throws Throwable {
        boolean loaded = false;
        Throwable failure = null;
        try { loadConfigFromRemotePreferences(); loaded = true; } catch (Throwable error) { failure = error; }
        try { loadConfigFromContentProvider(); loaded = true; } catch (Throwable error) { failure = error; }
        if (!loaded && failure != null) throw failure;
    }

    private static void loadConfigFromRemotePreferences() {
        SharedPreferences remotePreferences = XposedBridge.getRemotePreferences("config");
        if (remotePreferences == null) {
            throw new IllegalStateException("remote preferences unavailable");
        }
        java.util.Map<String, ?> values = remotePreferences.getAll();
        if (!Boolean.TRUE.equals(values.get("init"))) throw new IllegalStateException("Remote config not initialized");
        ConfigSnapshot snapshot = new ConfigSnapshot(values);
        if (config == null || snapshot.revision > config.revision) config = snapshot;
        printLog("[RemotePreferences] allowList size: " + snapshot.allowList.size());
    }

    @SuppressLint("Range")
    private static void loadConfigFromContentProvider() throws Throwable {
        if (context == null) {
            throw new IllegalStateException("context unavailable");
        }
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    Uri.parse("content://" + SELF_PACKAGE_NAME + ".provider/config"),
                    null, "all", null, null);
            if (cursor == null || cursor.getCount() == 0) {
                throw new IllegalStateException("provider returned no data");
            }
            Set<String> allowListTmp = new HashSet<>();
            Set<String> protectedTmp = new HashSet<>();
            boolean init = false;
            long revision = 0;
            HashMap<String, Object> values = new HashMap<>();
            cursor.moveToFirst();
            do {
                String key = cursor.getString(cursor.getColumnIndex("key"));
                String value = cursor.getString(cursor.getColumnIndex("value"));
                if ("allowList".equals(key)) allowListTmp.add(value);
                else if (ConfigSchema.KEY_PROTECTED_APPS.equals(key)) protectedTmp.add(value);
                else if ("revision".equals(key)) revision = Long.parseLong(value);
                else if ("init".equals(key)) init = "1".equals(value);
                else if (ConfigSchema.OPTIONS.contains(key))
                    values.put(key, "1".equals(value));
            } while (cursor.moveToNext());
            if (!init) throw new IllegalStateException("provider not initialized");
            values.put("allowList", allowListTmp);
            values.put(ConfigSchema.KEY_PROTECTED_APPS, protectedTmp);
            values.put("revision", revision);
            ConfigSnapshot snapshot = new ConfigSnapshot(values);
            if (config == null || snapshot.revision >= config.revision) config = snapshot;
            printLog("[ContentProvider] allowList size: " + allowListTmp.size());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /** Config reload and status request, both only from the module app (signature permission). */
    private static synchronized boolean initReceivers() {
        if (context == null) return false;
        if (!receiversRegistered) {
            try {
                IntentFilter filter = new IntentFilter(ACTION_UPDATE_CONFIG);
                filter.addAction(ACTION_STATUS_REQUEST);
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    public void onReceive(Context receiverContext, Intent intent) {
                        String action = intent == null ? null : intent.getAction();
                        if (ACTION_UPDATE_CONFIG.equals(action)) {
                            onUpdateConfig();
                        } else if (ACTION_STATUS_REQUEST.equals(action)) {
                            // The user opened or refreshed the status page; answer once, at most every 2 s.
                            long now = SystemClock.elapsedRealtime();
                            if (now - lastStatusPublish < 2000L) return;
                            lastStatusPublish = now;
                            publishHookStatus(1);
                        }
                    }
                };
                context.registerReceiver(receiver, filter, CONFIG_PERMISSION, null, Context.RECEIVER_EXPORTED);
                receiversRegistered = true;
            } catch (Throwable error) { printLog("Receiver registration will retry: " + error); }
        }
        return receiversRegistered;
    }

    private static final Set<String> warnings = java.util.concurrent.ConcurrentHashMap.newKeySet();
    protected static void logOnce(String message) {
        if (warnings.size() < 64 && warnings.add(message)) printLog(message);
    }

    protected boolean isFCMAction(String action) {
        return FcmTrust.isAction(action);
    }

    protected static boolean isGmsUid(int uid) {
        if (!isCurrentUserAppUid(uid) || context == null) return false;
        try {
            return FcmTrust.isGmsSender(uid, context.getPackageManager().getPackagesForUid(uid));
        } catch (RuntimeException error) {
            printLog("Cannot verify GMS UID: " + error.getMessage());
            return false;
        }
    }

    private static boolean isCurrentUserAppUid(int uid) {
        // Never attribute a work-profile sender to the personal-profile target.
        return uid >= 10000 && android.os.UserHandle.getUserHandleForUid(uid)
                .equals(android.os.Process.myUserHandle());
    }

    protected static String explicitTarget(Intent intent) {
        if (intent == null) return null;
        return FcmTrust.target(intent.getPackage(), intent.getComponent() == null
                ? null : intent.getComponent().getPackageName());
    }

    protected static int packageUid(String packageName) {
        if (packageName == null || context == null) return -1;
        try {
            return context.getPackageManager().getPackageUid(packageName, 0);
        } catch (Throwable error) {
            return -1;
        }
    }

    protected boolean trustedDelivery(Intent intent, String target, XC_MethodHook.MethodHookParam param) {
        // Require a coherent, explicit destination. Do not select an arbitrary allowlisted string argument.
        if (!isBootComplete || intent == null || target == null || !target.equals(explicitTarget(intent))
                || !targetIsAllow(target)) return false;
        try {
            // A queued broadcast is attributed by BroadcastRecord, never by ambient Binder identity.
            for (Object arg : param.args) {
                if (arg == null || !"com.android.server.am.BroadcastRecord".equals(arg.getClass().getName())) continue;
                return XposedHelpers.getObjectField(arg, "intent") == intent
                        && FcmTrust.RECEIVE.equals(intent.getAction())
                        && isGmsUid((Integer) XposedHelpers.getObjectField(arg, "callingUid"));
            }
            // Only the synchronous broadcast path may inherit the verified entry context.
            return FcmTrust.matches(intent.getAction(), target);
        } catch (Throwable error) {
            logOnce("Unsupported delivery attribution: " + param.method + ": " + error);
            return false;
        }
    }

    protected boolean isFCMIntent(Intent intent) {
        String action = intent.getAction();
        return isFCMAction(action);
    }

    protected static String getProcessName() {
        return processName;
    }
}
