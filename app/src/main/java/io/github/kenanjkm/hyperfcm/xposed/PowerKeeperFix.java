package io.github.kenanjkm.hyperfcm.xposed;

import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;

import io.github.kenanjkm.hyperfcm.libxposed.XC_MethodHook;
import io.github.kenanjkm.hyperfcm.libxposed.XposedBridge;
import io.github.kenanjkm.hyperfcm.libxposed.XposedHelpers;
import io.github.kenanjkm.hyperfcm.util.ConfigSchema;

/**
 * HyperOS PowerKeeper (com.miui.powerkeeper) hooks.
 *
 * Sleep mode: PhoneSleepModeController enters sleep mode when it decides the user is asleep and
 * calls CommonAdapter.setDataEnabled(telephonyManager, false); on device this cut data from 00:42
 * to 06:45 and no FCM arrived. Only that one call is skipped, and only when it comes from the
 * sleep mode controller and the user enabled the option. Wi-Fi/Bluetooth/alarm restrictions of
 * sleep mode stay as they are.
 *
 * GMS DNS block: NetdExecutor.setGmsDnsBlockerState(uid, true) makes dnsproxyd refuse DNS for the
 * GMS UID when PowerKeeper decides Google is unreachable. PowerKeeper 4.2.00 ships the method
 * without a caller; the hook keeps it harmless on builds that do call it.
 *
 * Nothing here keeps the device awake (Doze is untouched).
 */
public class PowerKeeperFix extends XposedModule {
    private static final String COMMON_ADAPTER = "com.miui.powerkeeper.utils.CommonAdapter";
    private static final String NETD_EXECUTOR = "com.miui.powerkeeper.utils.NetdExecutor";

    public PowerKeeperFix(ClassLoader classLoader) {
        super(classLoader);
        HookInstaller.install("Sleep-mode mobile data", this::hookSleepModeData);
        HookInstaller.install("GMS DNS block", this::hookGmsDnsBlock);
    }

    private void hookSleepModeData() {
        Class<?> adapter = XposedHelpers.findClass(COMMON_ADAPTER, classLoader);
        Method method = XposedHelpers.findMethodExact(adapter, "setDataEnabled", TelephonyManager.class, boolean.class);
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!Boolean.FALSE.equals(param.args[1])
                        || !option(ConfigSchema.KEY_SLEEP_MODE_KEEP_DATA) || !calledBySleepMode()) return;
                printLog("PowerKeeper sleep mode: mobile data kept on");
                param.setResult(null);
            }
        });
    }

    // static synchronized void setGmsDnsBlockerState(int uid, boolean block)
    private void hookGmsDnsBlock() {
        Class<?> executor = XposedHelpers.findClass(NETD_EXECUTOR, classLoader);
        Method method = XposedHelpers.findMethodExact(executor, "setGmsDnsBlockerState", int.class, boolean.class);
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!Boolean.TRUE.equals(param.args[1]) || !option(ConfigSchema.KEY_KEEP_GMS_AWAKE)) return;
                logOnce("PowerKeeper GMS DNS block skipped");
                param.setResult(null);
            }
        });
    }

    /** Read live from the module's remote preferences, so a switch applies without a reboot. */
    private static boolean option(String key) {
        try {
            SharedPreferences prefs = XposedBridge.getRemotePreferences("config");
            return prefs != null && prefs.getBoolean(key, ConfigSchema.defaultValue(key));
        } catch (Throwable error) {
            return false;
        }
    }

    /** Only the phone/pad sleep-mode controllers; any other PowerKeeper caller is left alone. */
    private static boolean calledBySleepMode() {
        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            String name = frame.getClassName();
            if (name.startsWith("com.miui.powerkeeper.statemachine.PhoneSleepModeController")
                    || name.startsWith("com.miui.powerkeeper.statemachine.PadSleepModeController")) return true;
        }
        return false;
    }
}
