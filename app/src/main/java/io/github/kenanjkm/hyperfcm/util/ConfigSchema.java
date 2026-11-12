package io.github.kenanjkm.hyperfcm.util;

import java.util.List;

/** Persisted names and defaults shared by UI, provider and hooked processes. */
public final class ConfigSchema {
    private ConfigSchema() {}
    /** Keep an app's notifications when the system force-stops it. */
    public static final String KEY_DISABLE_AUTO_CLEAN_NOTIFICATION = "disableAutoCleanNotification";
    public static final String KEY_INCLUDE_ICEBOX_DISABLED_APP = "includeIceBoxDisableApp";
    /** Keep Google Play services out of the Greeze freezer and its network limit (needed for FCM). */
    public static final String KEY_KEEP_GMS_AWAKE = "keepGmsAwake";
    public static final String KEY_AUTO_ALLOW_NEW_APPS = "autoAllowNewApps";
    /** Keep mobile data on while PowerKeeper sleep mode is active (rest of sleep mode unchanged). */
    public static final String KEY_SLEEP_MODE_KEEP_DATA = "sleepModeKeepMobileData";
    /** Run the Greeze freezer with its international (global ROM) policy instead of the CN one. Needs a reboot. */
    public static final String KEY_GREEZE_GLOBAL_POLICY = "greezeGlobalPolicy";
    public static final String KEY_INSTALL_EVENTS = "installEvents";
    /** Packages the user chose to keep out of the freezer and the memory killer (costs battery). */
    public static final String KEY_PROTECTED_APPS = "protectedApps";
    /** Keys of earlier schema versions; removed from the store when it is next written. */
    public static final List<String> RETIRED_KEYS = List.of();
    public static final List<String> OPTIONS = List.of(KEY_DISABLE_AUTO_CLEAN_NOTIFICATION,
            KEY_INCLUDE_ICEBOX_DISABLED_APP, KEY_KEEP_GMS_AWAKE, KEY_AUTO_ALLOW_NEW_APPS,
            KEY_SLEEP_MODE_KEEP_DATA, KEY_GREEZE_GLOBAL_POLICY);
    public static boolean defaultValue(String key) {
        return KEY_KEEP_GMS_AWAKE.equals(key) || KEY_AUTO_ALLOW_NEW_APPS.equals(key);
    }
}
