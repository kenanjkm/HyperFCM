package io.github.kenanjkm.hyperfcm.xposed;

import android.service.notification.NotificationListenerService;

import java.lang.reflect.Method;
import io.github.kenanjkm.hyperfcm.libxposed.XC_MethodHook;
import io.github.kenanjkm.hyperfcm.libxposed.XposedBridge;
import io.github.kenanjkm.hyperfcm.libxposed.XposedHelpers;

public class KeepNotification extends XposedModule{

    public KeepNotification(ClassLoader classLoader) {
        super(classLoader);
        HookInstaller.install("Keep notifications", this::startHook);
    }
    
    protected void startHook() throws NoSuchMethodError, XposedHelpers.ClassNotFoundError {
        Class<?> clazz = XposedHelpers.findClass("com.android.server.notification.NotificationManagerService",classLoader);
        int hooks = 0;
        for (Method targetMethod : clazz.getDeclaredMethods()) {
            if (!"cancelAllNotificationsInt".equals(targetMethod.getName())) continue;
            String[] types = java.util.Arrays.stream(targetMethod.getParameterTypes())
                    .map(Class::getName).toArray(String[]::new);
            int finalReason_args_index = io.github.kenanjkm.hyperfcm.util.NotificationSignature.reasonIndex(
                    targetMethod.getReturnType().getName(), types);
            final int finalPkg_args_index = 2;
            if (finalReason_args_index < 0) {
                logOnce("Unsupported notification signature: " + targetMethod);
                continue;
            }
            hooks++;
            XposedBridge.hookMethod(targetMethod,new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if(!isBootComplete){
                        return;
                    }
                    if (param.args.length <= Math.max(finalPkg_args_index, finalReason_args_index)
                            || !(param.args[finalPkg_args_index] instanceof String)
                            || !(param.args[finalReason_args_index] instanceof Integer)) {
                        printLog("Unsupported cancelAllNotificationsInt arguments");
                        return;
                    }
                    if(getBooleanConfig("disableAutoCleanNotification",false) && targetIsAllow((String) param.args[finalPkg_args_index])){
                        int reason = (int)param.args[finalReason_args_index];
                        if(reason == NotificationListenerService.REASON_PACKAGE_CHANGED){
                            param.setResult(null);
                        }
                    }
                }
            });
        }
        if (hooks == 0) throw new NoSuchMethodError("NotificationManagerService#cancelAllNotificationsInt");
    }
}
