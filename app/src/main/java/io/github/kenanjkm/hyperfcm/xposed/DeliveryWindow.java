package io.github.kenanjkm.hyperfcm.xposed;

import android.os.SystemClock;

import io.github.kenanjkm.hyperfcm.util.FcmDeliveryWindow;

/**
 * The 20 s window in which a target that just received a trusted FCM broadcast may finish its
 * work (start its service, stay unfrozen). It only expires; nothing is scheduled to close it.
 */
final class DeliveryWindow {
    private DeliveryWindow() {}

    private static final FcmDeliveryWindow WINDOWS = new FcmDeliveryWindow();

    /** Opens the window for the target and returns its UID, or -1 when the package is unknown. */
    static int begin(String target) {
        int uid = XposedModule.packageUid(target);
        if (uid < 0) return -1;
        WINDOWS.begin(uid, target, SystemClock.elapsedRealtime());
        XposedModule.printLog("FCM delivery window: pkg=" + target + ", uid=" + uid, true);
        return uid;
    }

    /** True while the package has an open window; avoids a package-manager lookup on hot paths. */
    static boolean isOpen(String packageName) {
        return WINDOWS.containsPackage(packageName, SystemClock.elapsedRealtime());
    }

    /** Package that owns the active window of this UID, or null. */
    static String packageFor(int uid) {
        return WINDOWS.packageName(uid, SystemClock.elapsedRealtime());
    }
}
