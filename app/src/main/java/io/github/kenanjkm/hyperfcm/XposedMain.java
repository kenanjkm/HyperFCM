package io.github.kenanjkm.hyperfcm;

import android.content.Context;

import java.io.File;

import io.github.kenanjkm.hyperfcm.libxposed.XposedBridge;
import io.github.kenanjkm.hyperfcm.libxposed.XposedHelpers;
import io.github.kenanjkm.hyperfcm.util.BootGuard;
import io.github.kenanjkm.hyperfcm.util.HookStatus;
import io.github.kenanjkm.hyperfcm.xposed.BroadcastFix;
import io.github.kenanjkm.hyperfcm.xposed.HookInstaller;
import io.github.kenanjkm.hyperfcm.xposed.HyperOsFix;
import io.github.kenanjkm.hyperfcm.xposed.KeepNotification;
import io.github.kenanjkm.hyperfcm.xposed.NewAppInstallReceiver;
import io.github.kenanjkm.hyperfcm.xposed.PowerKeeperFix;
import io.github.kenanjkm.hyperfcm.xposed.XposedModule;

import io.github.libxposed.api.XposedModuleInterface;

public class XposedMain extends io.github.libxposed.api.XposedModule {
    /** A boot counts as finished once system_server has stayed alive this long. */
    private static final long BOOT_FINISHED_AFTER_MS = 120_000L;
    private boolean powerKeeperHooksInitialized;

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        XposedBridge.init(this);
        XposedModule.setProcessName(HookStatus.SOURCE_SYSTEM);
        ClassLoader classLoader = param.getClassLoader();

        if (bootGuardBlocksHooks()) {
            XposedBridge.log("boot guard: " + BootGuard.LIMIT + " unfinished boots in a row, hooks are NOT installed");
            HookStatus.setSafeMode(true);
            HookStatus.record("Boot guard", false, "safe mode: hooks disabled after repeated failed boots");
        } else {
            HookInstaller.install("Boot guard", () -> { });
            new BroadcastFix(classLoader);
            new KeepNotification(classLoader);
            HookInstaller.install("Install receiver", () -> new NewAppInstallReceiver(classLoader));
            if (XposedHelpers.findClassIfExists("com.miui.server.greeze.GreezeManagerService", classLoader) == null) {
                HookStatus.record("HyperOS", false, "Greeze not found: this ROM is not HyperOS");
            } else {
                new HyperOsFix(classLoader);
            }
        }
        // The system Application was attached long before this callback; take the system context directly.
        attachSystemContext(classLoader);
    }

    @Override
    public synchronized void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        XposedBridge.init(this);
        if (!HookStatus.SOURCE_POWERKEEPER.equals(param.getPackageName()) || powerKeeperHooksInitialized) return;
        powerKeeperHooksInitialized = true;
        XposedModule.setProcessName(HookStatus.SOURCE_POWERKEEPER);
        if (BootGuard.read(new File(BootGuard.FILE)).tripped) {
            XposedBridge.log("boot guard: safe mode, PowerKeeper hooks are NOT installed");
            return;
        }
        new PowerKeeperFix(param.getClassLoader());
        attachApplicationContext();
    }

    /**
     * Counts this start as an unfinished boot and clears the count once the process has survived
     * for a while. Any failure here leaves the hooks enabled: the guard must never be the reason
     * the module does not work.
     */
    private boolean bootGuardBlocksHooks() {
        try {
            File file = new File(BootGuard.FILE);
            BootGuard.State state = BootGuard.read(file);
            long token = 0;
            try {
                android.content.SharedPreferences prefs = XposedBridge.getRemotePreferences("guard");
                if (prefs != null) token = prefs.getLong("reset", 0);
            } catch (Throwable error) {
                // Keep the stored token: an unreadable preference must not release safe mode by accident.
                token = state.token;
            }
            boolean blocked = BootGuard.enterBoot(state, token);
            if (!BootGuard.write(file, state)) {
                XposedBridge.log("boot guard: state file not writable, guard inactive");
                return false;
            }
            Thread thread = new Thread(() -> {
                try { Thread.sleep(BOOT_FINISHED_AFTER_MS); } catch (InterruptedException e) { return; }
                BootGuard.State current = BootGuard.read(file);
                BootGuard.bootFinished(current);
                BootGuard.write(file, current);
            }, "hyperfcm-boot-guard");
            thread.setDaemon(true);
            thread.start();
            return blocked;
        } catch (Throwable error) {
            XposedBridge.log("boot guard unavailable: " + error);
            return false;
        }
    }

    private void attachSystemContext(ClassLoader classLoader) {
        try {
            Class<?> activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", classLoader);
            Object activityThread = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread");
            Object systemContext = XposedHelpers.callMethod(activityThread, "getSystemContext");
            if (systemContext instanceof Context) {
                XposedModule.attachContext((Context) systemContext);
            } else {
                XposedBridge.log("system context unavailable: getSystemContext returned null");
            }
        } catch (Throwable e) {
            XposedBridge.log("system context unavailable: " + e.getMessage());
        }
    }

    /** PowerKeeper: its Application may not exist yet when the package is ready; wait for it briefly. */
    private void attachApplicationContext() {
        Thread thread = new Thread(() -> {
            for (int attempt = 0; attempt < 30; attempt++) {
                try {
                    Object application = Class.forName("android.app.ActivityThread")
                            .getMethod("currentApplication").invoke(null);
                    if (application instanceof Context) {
                        XposedModule.attachContext((Context) application);
                        return;
                    }
                } catch (Throwable error) {
                    XposedBridge.log("application context unavailable: " + error);
                    return;
                }
                try { Thread.sleep(2000L); } catch (InterruptedException e) { return; }
            }
        }, "hyperfcm-context");
        thread.setDaemon(true);
        thread.start();
    }
}
