package io.github.kenanjkm.hyperfcm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Log.i("HyperFCM", "Boot completed, notify hooked processes to reload config");
        // Tell the hooked processes to reload the configuration after boot.
        try {
            context.sendBroadcast(new Intent("io.github.kenanjkm.hyperfcm.update.config"));
        } catch (Throwable e) {
            Log.e("HyperFCM", "send update config broadcast failed: " + e.getMessage());
        }
    }
}
