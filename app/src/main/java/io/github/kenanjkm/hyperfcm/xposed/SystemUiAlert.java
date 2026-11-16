package io.github.kenanjkm.hyperfcm.xposed;

import android.app.Notification;
import android.os.Handler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.kenanjkm.hyperfcm.libxposed.XC_MethodHook;
import io.github.kenanjkm.hyperfcm.libxposed.XposedBridge;
import io.github.kenanjkm.hyperfcm.libxposed.XposedHelpers;
import io.github.kenanjkm.hyperfcm.util.AlwaysAlertPolicy;
import io.github.kenanjkm.hyperfcm.util.ConfigSchema;

/**
 * Runs in HyperOS SystemUI. MiuiAlertManager#buzzBeepBlink picks the effects of a new notification
 * and sends them to system_server; for allowed apps the lock-screen and "unimportant" fold rules are
 * dropped (see {@link AlwaysAlertPolicy}). system_server still applies Do Not Disturb, the ringer
 * mode and every other mute rule. Called once per new notification on SystemUI's own path; nothing
 * is scheduled, so deep sleep is unaffected.
 */
public class SystemUiAlert extends XposedModule {
    private static final String MANAGER = "com.android.systemui.statusbar.notification.policy.MiuiAlertManager";

    public SystemUiAlert(ClassLoader classLoader) {
        super(classLoader);
        HookInstaller.install("Always alert (SystemUI)", this::startHook);
    }

    private static Field field(Class<?> clazz, String name) throws NoSuchFieldException {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(clazz.getName() + "#" + name);
    }

    protected void startHook() throws Throwable {
        Class<?> manager = XposedHelpers.findClass(MANAGER, classLoader);
        Class<?> entryClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.collection.NotificationEntry", classLoader);
        Class<?> sbnClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.ExpandedNotification", classLoader);
        // Resolve everything up front: if this SystemUI build differs, the group fails visibly and
        // SystemUI keeps its own behaviour.
        Method buzz = manager.getDeclaredMethod("buzzBeepBlink", entryClass);
        Field nm = field(manager, "mNm");
        Field bgHandler = field(manager, "mBgHandler");
        Field keyguard = field(manager, "mKeyguardStateController");
        Field settings = field(manager, "mSettingsManager");
        Field showing = field(keyguard.getType(), "mShowing");
        Field mirrorDnd = field(settings.getType(), "miuiMirrorDndModeEnabled");
        Field sbnField = field(entryClass, "mSbn");
        Field keyField = field(entryClass, "key");
        Field folded = field(sbnClass, "mIsFold");
        Field canVibrate = field(sbnClass, "mCanVibrate");
        Field canSound = field(sbnClass, "mCanSound");
        Field canLights = field(sbnClass, "mCanLights");
        Field targetPackage = field(sbnClass, "mPkgName");
        Method canShowOnKeyguard = sbnClass.getDeclaredMethod("canShowOnKeyguard");
        canShowOnKeyguard.setAccessible(true);

        XposedBridge.hookMethod(buzz, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!getBooleanConfig(ConfigSchema.KEY_ALWAYS_ALERT, false)) return;
                Object entry = param.args[0];
                Object sbn = sbnField.get(entry);
                if (!(sbn instanceof android.service.notification.StatusBarNotification)) return;
                android.service.notification.StatusBarNotification status =
                        (android.service.notification.StatusBarNotification) sbn;
                Notification notification = status.getNotification();
                boolean appAllowsAlert = notification == null
                        || notification.extras.getBoolean("miui.focus.enableAlert", true);
                Object keyguardController = keyguard.get(param.thisObject);
                Object settingsManager = settings.get(param.thisObject);
                boolean override = AlwaysAlertPolicy.override(true,
                        targetIsAllow(status.getPackageName())
                                || targetIsAllow((String) targetPackage.get(sbn)),
                        appAllowsAlert,
                        settingsManager != null && mirrorDnd.getBoolean(settingsManager),
                        folded.getBoolean(sbn),
                        keyguardController != null && showing.getBoolean(keyguardController),
                        (Boolean) canShowOnKeyguard.invoke(sbn));
                if (!override) return;

                int bits = AlwaysAlertPolicy.bits(canVibrate.getBoolean(sbn), canSound.getBoolean(sbn),
                        canLights.getBoolean(sbn));
                String key = (String) keyField.get(entry);
                Object notificationManager = nm.get(param.thisObject);
                Handler handler = (Handler) bgHandler.get(param.thisObject);
                if (key == null || notificationManager == null || handler == null) return;
                // Same call and thread SystemUI uses itself.
                handler.post(() -> {
                    try {
                        notificationManager.getClass().getMethod("buzzBeepBlinkForNotification", String.class, int.class)
                                .invoke(notificationManager, key, bits);
                    } catch (Throwable error) {
                        printLog("always alert: buzzBeepBlinkForNotification failed: " + error);
                    }
                });
                param.setResult(null);
            }
        });
    }
}
