package io.github.kenanjkm.hyperfcm.xposed;

import io.github.kenanjkm.hyperfcm.libxposed.XposedBridge;
import io.github.kenanjkm.hyperfcm.util.HookStatus;

/** Installs one hook group: a failure is recorded and never reaches the caller or other groups. */
public final class HookInstaller {
    private HookInstaller() {}

    public interface Action { void run() throws Throwable; }

    public static void install(String name, Action action) {
        Runnable leave = HookStatus.enterGroup(name);
        try {
            action.run();
            HookStatus.record(name, true, "");
        } catch (Throwable error) {
            XposedBridge.log("hook error " + name + ": " + error);
            HookStatus.record(name, false, String.valueOf(error));
        } finally {
            leave.run();
        }
    }
}
