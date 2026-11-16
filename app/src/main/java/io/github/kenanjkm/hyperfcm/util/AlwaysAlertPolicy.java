package io.github.kenanjkm.hyperfcm.util;

/**
 * HyperOS SystemUI decides which effects a new notification may use and hands them to
 * system_server as bits (MiuiAlertManager#buzzBeepBlink → buzzBeepBlinkForNotification). Two of its
 * rules silence a notification for reasons unrelated to sound:
 *  - the app may not show on the lock screen, so while locked it gets neither sound nor vibration;
 *  - MIUI folded the notification as "unimportant", so it gets no effects at all.
 * For allowed apps this policy drops those two rules. Everything else stays: the user's per-app and
 * per-channel sound, vibration and light switches, the app asking for no alert (focus notifications),
 * mirror DND, and every check system_server still makes afterwards (Do Not Disturb, silent and vibrate
 * ringer modes, silent channels, silent updates, grouping, the "recently noisy" limit).
 */
public final class AlwaysAlertPolicy {
    private AlwaysAlertPolicy() {}

    public static final int BIT_VIBRATE = 1;
    public static final int BIT_SOUND = 2;
    public static final int BIT_LIGHTS = 4;

    /** The effects the user's own notification settings allow, ignoring lock screen and folding. */
    public static int bits(boolean canVibrate, boolean canSound, boolean canLights) {
        return (canVibrate ? BIT_VIBRATE : 0) | (canSound ? BIT_SOUND : 0) | (canLights ? BIT_LIGHTS : 0);
    }

    /**
     * Whether SystemUI's own decision is replaced. Only when the option is on, the app is allowed,
     * the app did not ask for no alert, mirror DND is off and one of the two dropped rules applies;
     * otherwise SystemUI runs unchanged.
     */
    public static boolean override(boolean optionOn, boolean allowed, boolean appAllowsAlert,
                                   boolean mirrorDnd, boolean folded, boolean keyguardShowing,
                                   boolean canShowOnKeyguard) {
        if (!optionOn || !allowed || !appAllowsAlert || mirrorDnd) return false;
        return folded || (keyguardShowing && !canShowOnKeyguard);
    }
}
