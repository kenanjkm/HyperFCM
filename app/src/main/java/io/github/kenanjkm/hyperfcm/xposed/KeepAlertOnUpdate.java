package io.github.kenanjkm.hyperfcm.xposed;

import java.lang.reflect.Method;

import io.github.kenanjkm.hyperfcm.libxposed.XC_MethodHook;
import io.github.kenanjkm.hyperfcm.libxposed.XposedBridge;
import io.github.kenanjkm.hyperfcm.libxposed.XposedHelpers;
import io.github.kenanjkm.hyperfcm.util.AlertUpdateGuard;
import io.github.kenanjkm.hyperfcm.util.ConfigSchema;

/**
 * Lets a short notification sound or vibration finish when the app updates the notification.
 *
 * NotificationAttentionHelper#buzzBeepBlinkLocked stops the running sound (clearSoundLocked) and
 * vibration (clearVibrateLocked) when the record that started them is re-posted without an alert
 * of its own, e.g. an update flagged "only alert once". The stop calls are skipped only while such
 * an update of an allowed app is processed and only if the running alert does not loop, so calls
 * and insistent alarms still stop at once. All hooks run on system_server's notification thread
 * under mNotificationLock; nothing is scheduled, so deep sleep is unaffected.
 */
public class KeepAlertOnUpdate extends XposedModule {
    private static final String HELPER = "com.android.server.notification.NotificationAttentionHelper";
    private static final ThreadLocal<Boolean> KEEP = new ThreadLocal<>();
    private static volatile boolean soundLooping;
    private static volatile boolean vibrationLooping;

    public KeepAlertOnUpdate(ClassLoader classLoader) {
        super(classLoader);
        HookInstaller.install("Keep alert on update", this::startHook);
    }

    private static Method method(Class<?> clazz, String name, int params) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (name.equals(m.getName()) && m.getParameterCount() == params) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodError(HELPER + "#" + name);
    }

    private static int flagsOf(Object record) {
        Object sbn = XposedHelpers.callMethod(record, "getSbn");
        Object notification = XposedHelpers.callMethod(sbn, "getNotification");
        return (int) XposedHelpers.getObjectField(notification, "flags");
    }

    private static String packageOf(Object record) {
        return (String) XposedHelpers.callMethod(XposedHelpers.callMethod(record, "getSbn"), "getPackageName");
    }

    protected void startHook() {
        Class<?> helper = XposedHelpers.findClass(HELPER, classLoader);
        // Resolve every target first: without the play hooks the looping state is unknown, so the
        // group installs all hooks or none.
        Method buzz = method(helper, "buzzBeepBlinkLocked", 2);
        Method playSound = method(helper, "playSound", 2);
        Method playVibration = method(helper, "playVibration", 3);
        Method clearSound = method(helper, "clearSoundLocked", 0);
        Method clearVibrate = method(helper, "clearVibrateLocked", 0);
        // The play and clear methods are short; ART may inline them into buzzBeepBlinkLocked,
        // where their hooks would never fire.
        if (!XposedBridge.deoptimize(buzz)) printLog("buzzBeepBlinkLocked not deoptimized");

        XposedBridge.hookMethod(playSound, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(param.getResult())) {
                    soundLooping = AlertUpdateGuard.isLooping(flagsOf(param.args[0]));
                }
            }
        });
        XposedBridge.hookMethod(playVibration, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(param.getResult())) {
                    vibrationLooping = AlertUpdateGuard.isLooping(flagsOf(param.args[0]));
                }
            }
        });
        XposedBridge.hookMethod(buzz, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                KEEP.remove();
                if (!isBootComplete || !getBooleanConfig(ConfigSchema.KEY_KEEP_ALERT_ON_UPDATE, false)) return;
                Object record = param.args[0];
                boolean isUpdate = Boolean.TRUE.equals(XposedHelpers.getObjectField(record, "isUpdate"));
                if (AlertUpdateGuard.guardUpdate(true, isUpdate, isUpdate && targetIsAllow(packageOf(record)))) {
                    KEEP.set(Boolean.TRUE);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                KEEP.remove();
            }
        });
        XposedBridge.hookMethod(clearSound, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (KEEP.get() != null && AlertUpdateGuard.keepRunningAlert(true, soundLooping)) {
                    param.setResult(null);
                }
            }
        });
        XposedBridge.hookMethod(clearVibrate, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (KEEP.get() != null && AlertUpdateGuard.keepRunningAlert(true, vibrationLooping)) {
                    param.setResult(null);
                }
            }
        });
    }
}
