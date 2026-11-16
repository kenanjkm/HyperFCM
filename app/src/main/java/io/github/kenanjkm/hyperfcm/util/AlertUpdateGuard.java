package io.github.kenanjkm.hyperfcm.util;

/**
 * Decides whether an update of a notification may cut off the sound or vibration that the same
 * notification started. Android stops the running alert when the app re-posts the notification
 * silently (for example WhatsApp adding its group summary a moment later), so the user hears only
 * a click. Only short, non-looping alerts are kept: a looping one (an incoming call, an insistent
 * alarm) must stop as soon as the app updates it, or it would keep ringing.
 */
public final class AlertUpdateGuard {
    private AlertUpdateGuard() {}

    /** Notification.FLAG_INSISTENT: the sound and vibration repeat until the notification changes. */
    public static final int FLAG_INSISTENT = 4;

    public static boolean isLooping(int notificationFlags) {
        return (notificationFlags & FLAG_INSISTENT) != 0;
    }

    /**
     * Whether stop requests are held back while this record is processed.
     * @param optionOn the user turned the option on
     * @param isUpdate the posted record replaces an existing notification with the same key
     * @param allowed  the package is on HyperFCM's allow list
     */
    public static boolean guardUpdate(boolean optionOn, boolean isUpdate, boolean allowed) {
        return optionOn && isUpdate && allowed;
    }

    /** Whether one stop request is skipped: only for a guarded update and a non-looping alert. */
    public static boolean keepRunningAlert(boolean guarding, boolean playingLooping) {
        return guarding && !playingLooping;
    }
}
