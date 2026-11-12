package io.github.kenanjkm.hyperfcm.util;

import android.os.Build;

/** ROM information for the module app (system_server detects HyperOS by class presence instead). */
public final class RomInfo {
    private RomInfo() {}

    /** HyperOS version name such as "OS4.0.0.31", or an empty string on other ROMs. */
    public static String hyperOsVersion() {
        String name = property("ro.mi.os.version.incremental");
        return name.isEmpty() ? property("ro.mi.os.version.name") : name;
    }

    /** The only supported system: HyperOS 4 on Android 17. */
    public static boolean isSupported() {
        return isHyperOs() && hyperOsVersion().startsWith("OS4") && Build.VERSION.SDK_INT >= 37;
    }

    public static boolean isHyperOs() {
        return !property("ro.mi.os.version.name").isEmpty() || "Xiaomi".equalsIgnoreCase(Build.MANUFACTURER);
    }

    private static String property(String key) {
        try {
            Object value = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, key);
            return value instanceof String ? ((String) value).trim() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }
}
