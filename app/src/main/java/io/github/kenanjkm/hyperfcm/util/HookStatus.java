package io.github.kenanjkm.hyperfcm.util;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Hook install results and call counters of one hooked process, handed to the module app.
 * Nothing here schedules work: a report is published once after boot and again only when the
 * app asks for it (the user opened or refreshed the status page).
 */
public final class HookStatus {
    private HookStatus() {}

    public static final String METHOD = "hookStatus";
    public static final String EXTRA_JSON = "json";
    public static final String SOURCE_SYSTEM = "android";
    public static final String SOURCE_POWERKEEPER = "com.miui.powerkeeper";

    /** File the provider stores a source's report in; null for an unknown source. */
    public static String fileFor(String source) {
        if (SOURCE_SYSTEM.equals(source)) return "hook-status.json";
        if (SOURCE_POWERKEEPER.equals(source)) return "hook-status-powerkeeper.json";
        return null;
    }

    /** Call counter of one group. Incremented on the hooked method's own thread: no locks, no I/O. */
    public static final class Counter {
        private final AtomicLong calls = new AtomicLong();
        private volatile long last;
        private volatile int hooks;

        public void hit() {
            calls.incrementAndGet();
            last = System.currentTimeMillis();
        }
        public void hookAdded() { hooks++; }
    }

    private static final class Entry {
        final String name; final boolean ok; final String detail;
        Entry(String name, boolean ok, String detail) { this.name = name; this.ok = ok; this.detail = detail; }
    }

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<String, Counter> COUNTERS = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> GROUP = new ThreadLocal<>();
    private static volatile boolean safeMode;

    public static void setSafeMode(boolean enabled) { safeMode = enabled; }

    /** Hooks registered until the returned action runs are counted under this group. */
    public static Runnable enterGroup(String name) {
        String previous = GROUP.get();
        GROUP.set(name);
        return () -> { if (previous == null) GROUP.remove(); else GROUP.set(previous); };
    }

    /** Counter of the group being installed on this thread, or null outside an install. */
    public static Counter currentCounter() {
        String name = GROUP.get();
        if (name == null) return null;
        Counter counter = COUNTERS.computeIfAbsent(name, key -> new Counter());
        counter.hookAdded();
        return counter;
    }

    /** Record one hook group. On a repeated name the newer result wins. */
    public static void record(String name, boolean ok, String detail) {
        if (name == null) return;
        ENTRIES.removeIf(entry -> entry.name.equals(name));
        ENTRIES.add(new Entry(name, ok, detail == null ? "" : detail));
    }

    public static JSONObject toJson(String source, long now, long bootTime) {
        JSONArray hooks = new JSONArray();
        for (Entry entry : ENTRIES) {
            JSONObject hook = new JSONObject();
            try {
                hook.put("name", entry.name).put("ok", entry.ok).put("detail", entry.detail);
                Counter counter = COUNTERS.get(entry.name);
                if (counter != null && counter.hooks > 0) {
                    hook.put("hooks", counter.hooks).put("calls", counter.calls.get()).put("last", counter.last);
                }
            } catch (Exception ignored) { }
            hooks.put(hook);
        }
        JSONObject json = new JSONObject();
        try {
            json.put("source", source).put("time", now).put("bootTime", bootTime)
                    .put("safeMode", safeMode).put("hooks", hooks);
        } catch (Exception ignored) { }
        return json;
    }

    /** Hooked process → module app, one Binder call via the config provider. */
    public static boolean publish(Context context, String authority, String source) {
        if (context == null || ENTRIES.isEmpty()) return false;
        try {
            long now = System.currentTimeMillis();
            Bundle extras = new Bundle();
            extras.putString(EXTRA_JSON, toJson(source, now, now - android.os.SystemClock.elapsedRealtime()).toString());
            Bundle result = context.getContentResolver().call(
                    Uri.parse("content://" + authority), METHOD, null, extras);
            return result != null && result.getBoolean("ok", false);
        } catch (Throwable error) {
            return false;
        }
    }

    /** One group as the UI shows it. {@code calls < 0}: the group registers no method hooks. */
    public static final class Hook {
        public final String name; public final boolean ok; public final String detail;
        public final long calls; public final long last;
        Hook(String name, boolean ok, String detail, long calls, long last) {
            this.name = name; this.ok = ok; this.detail = detail; this.calls = calls; this.last = last;
        }
    }

    /** Parsed view for the UI. */
    public static final class Snapshot {
        public final String source;
        public final long time;
        public final long bootTime;
        public final boolean safeMode;
        public final List<Hook> hooks = new ArrayList<>();

        public Snapshot(JSONObject json) {
            source = json.optString("source", SOURCE_SYSTEM);
            time = json.optLong("time", 0);
            bootTime = json.optLong("bootTime", 0);
            safeMode = json.optBoolean("safeMode", false);
            JSONArray array = json.optJSONArray("hooks");
            for (int i = 0; array != null && i < array.length(); i++) {
                JSONObject hook = array.optJSONObject(i);
                if (hook == null) continue;
                hooks.add(new Hook(hook.optString("name"), hook.optBoolean("ok"), hook.optString("detail"),
                        hook.has("calls") ? hook.optLong("calls", 0) : -1, hook.optLong("last", 0)));
            }
        }

        /** Names of the groups that could not be installed; these are worth an alert. */
        public List<String> failed() {
            List<String> failed = new ArrayList<>();
            for (Hook hook : hooks) if (!hook.ok) failed.add(hook.name);
            return failed;
        }

        public int okCount() {
            int n = 0;
            for (Hook hook : hooks) if (hook.ok) n++;
            return n;
        }
    }
}
