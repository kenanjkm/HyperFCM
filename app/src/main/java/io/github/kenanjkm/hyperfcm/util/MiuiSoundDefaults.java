package io.github.kenanjkm.hyperfcm.util;

/**
 * HyperOS China SystemUI (NotificationSettingsManager#canSound / #canVibrate) answers "no sound"
 * and "no vibration" for every channel the user never touched, unless the app is on Xiaomi's cloud
 * whitelist (USE_WHITE_LISTS = !IS_INTERNATIONAL_BUILD). Apps such as Duolingo, Gmail or Claude are
 * therefore silent by default. For allowed apps the default becomes "on"; a switch the user turned
 * off in the app's notification settings (app level or channel level) still wins.
 */
public final class MiuiSoundDefaults {
    private MiuiSoundDefaults() {}

    /** Preferences file SystemUI keeps the per-app and per-channel switches in. */
    public static final String PREFS = "app_notification";

    /** Key of the app-level switch, as NotificationSettingsManager.Prefs builds it. */
    public static String appKey(String pkg, String suffix) {
        return pkg + "_" + suffix;
    }

    /** Key of the channel switch, or null when SystemUI treats the channel as the app level. */
    public static String channelKey(String pkg, String channel, String suffix) {
        if (channel == null || channel.isEmpty() || "miscellaneous".equals(channel)) return null;
        return pkg + "_" + channel + "_" + suffix;
    }

    /**
     * @param systemAnswer what SystemUI decided
     * @param appSwitch    the user's app-level switch, or null if never set
     * @param channelSwitch the user's channel switch, or null if never set
     */
    public static boolean allow(boolean optionOn, boolean allowed, boolean systemAnswer,
                                Boolean appSwitch, Boolean channelSwitch) {
        if (systemAnswer || !optionOn || !allowed) return systemAnswer;
        if (Boolean.FALSE.equals(appSwitch) || Boolean.FALSE.equals(channelSwitch)) return false;
        return true;
    }
}
