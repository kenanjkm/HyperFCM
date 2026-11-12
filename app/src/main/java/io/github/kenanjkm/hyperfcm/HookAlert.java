package io.github.kenanjkm.hyperfcm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.List;

import io.github.kenanjkm.hyperfcm.util.HookStatus;

/**
 * One notification when a hook group could not be installed (typically after a system update
 * renamed a class) or the boot guard disabled the hooks. Posted from the provider call a hooked
 * process makes when it reports its status; nothing is scheduled.
 */
final class HookAlert {
    private HookAlert() {}
    private static final String CHANNEL = "hook_status";

    static void update(Context context, HookStatus.Snapshot snapshot) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            // One notification per reporting process, so a healthy report never hides another's failure.
            final int ID = HookStatus.SOURCE_SYSTEM.equals(snapshot.source) ? 1 : 2;
            List<String> failed = snapshot.failed();
            if (failed.isEmpty()) {
                manager.cancel(ID);
                return;
            }
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    context.getString(R.string.hook_status_title), NotificationManager.IMPORTANCE_DEFAULT));
            Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent intent = PendingIntent.getActivity(context, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String text = String.join(", ", failed);
            manager.notify(ID, new Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_troubleshoot)
                    .setContentTitle(snapshot.safeMode ? context.getString(R.string.safe_mode_title)
                            : context.getString(R.string.hook_alert_title, failed.size()))
                    .setContentText(snapshot.safeMode ? context.getString(R.string.safe_mode_summary) : text)
                    .setStyle(new Notification.BigTextStyle().bigText(snapshot.safeMode
                            ? context.getString(R.string.safe_mode_summary)
                            : context.getString(R.string.hook_alert_text, text)))
                    .setContentIntent(intent)
                    .setAutoCancel(true)
                    .build());
        } catch (Throwable error) {
            android.util.Log.w("HyperFCM", "Hook alert unavailable", error);
        }
    }
}
