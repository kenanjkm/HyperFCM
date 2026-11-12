package io.github.kenanjkm.hyperfcm;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import io.github.kenanjkm.hyperfcm.util.ConfigFile;
import io.github.kenanjkm.hyperfcm.util.ConfigSchema;
import io.github.kenanjkm.hyperfcm.util.ConfigCodec;
import io.github.kenanjkm.hyperfcm.util.ConfigSnapshot;
import io.github.kenanjkm.hyperfcm.util.IceboxUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/** Only the module itself and system-UID processes (system_server, PowerKeeper) can read configuration. */
public class ConfigProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private void requireReader() {
        int uid = Binder.getCallingUid();
        if (uid != Process.myUid() && uid != Process.SYSTEM_UID)
            throw new SecurityException("Configuration reader is not authorized");
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        requireReader();
        MatrixCursor data = new MatrixCursor(new String[]{"key", "value"});
        if (!"/config".equals(uri.getPath())) return data;
        try {
            ConfigSnapshot snapshot =
                    ConfigCodec.fromJson(ConfigFile.read(getContext()));
            data.addRow(new Object[]{"init", "1"});
            data.addRow(new Object[]{"revision", Long.toString(snapshot.revision)});
            for (String key : ConfigSchema.OPTIONS)
                data.addRow(new Object[]{key, snapshot.options.get(key) ? "1" : "0"});
            for (String name : snapshot.allowList) data.addRow(new Object[]{"allowList", name});
            for (String name : snapshot.protectedApps) data.addRow(new Object[]{ConfigSchema.KEY_PROTECTED_APPS, name});
        } catch (Exception error) { android.util.Log.w("HyperFCM", "Config file unavailable", error); }
        return data;
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("newAppInstalled".equals(method)) {
            return io.github.kenanjkm.hyperfcm.util.NewAppInstallStore.handle(getContext(), arg, extras);
        }
        requireReader();
        if (io.github.kenanjkm.hyperfcm.util.HookStatus.METHOD.equals(method)) {
            Bundle result = new Bundle();
            if (Binder.getCallingUid() != Process.SYSTEM_UID || extras == null) throw new SecurityException("Invalid hook status source");
            String json = extras.getString(io.github.kenanjkm.hyperfcm.util.HookStatus.EXTRA_JSON);
            if (json == null || json.length() > 64 * 1024) return result;
            long identity = Binder.clearCallingIdentity();
            try {
                JSONObject parsed = new JSONObject(json); // validate before writing
                io.github.kenanjkm.hyperfcm.util.HookStatus.Snapshot snapshot =
                        new io.github.kenanjkm.hyperfcm.util.HookStatus.Snapshot(parsed);
                String name = io.github.kenanjkm.hyperfcm.util.HookStatus.fileFor(snapshot.source);
                if (name == null) return result;
                java.io.File file = new java.io.File(getContext().getFilesDir(), name);
                java.nio.file.Files.write(file.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                getContext().getContentResolver().notifyChange(Uri.parse("content://" + getContext().getPackageName() + ".provider/hookStatus"), null);
                result.putBoolean("ok", true);
                HookAlert.update(getContext(), snapshot);
            } catch (Exception error) { android.util.Log.w("HyperFCM", "Hook status write failed", error); }
            finally { Binder.restoreCallingIdentity(identity); }
            return result;
        }
        if ("activateFrozenApp".equals(method)) {
            if (Binder.getCallingUid() != Process.SYSTEM_UID || arg == null) throw new SecurityException("Invalid activation request");
            Bundle result = new Bundle();
            long deadline = extras == null ? 0 : extras.getLong("deadline", 0);
            long now = android.os.SystemClock.elapsedRealtime();
            if (!io.github.kenanjkm.hyperfcm.util.ActivationDeadline.isValid(deadline, now)) return result;
            long identity = Binder.clearCallingIdentity();
            try {
                JSONObject config = ConfigFile.read(getContext());
                if (!config.optBoolean("includeIceBoxDisableApp", false)) return result;
                JSONArray allowed = config.getJSONArray("allowList");
                boolean permitted = false;
                for (int i = 0; i < allowed.length(); i++) if (arg.equals(allowed.getString(i))) permitted = true;
                if (!permitted) return result;
                IceboxUtils.activeAppBeforeDeadline(getContext(), arg, deadline);
                result.putBoolean("enabled", IceboxUtils.isAppEnabled(getContext(), arg));
            } catch (Exception error) { android.util.Log.w("HyperFCM", "Frozen app activation failed", error); }
            finally { Binder.restoreCallingIdentity(identity); }
            return result;
        }
        return super.call(method, arg, extras);
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
