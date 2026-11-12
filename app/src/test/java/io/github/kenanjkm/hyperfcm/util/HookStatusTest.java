package io.github.kenanjkm.hyperfcm.util;

import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class HookStatusTest {
    private static JSONObject hook(String name, boolean ok, String detail) throws Exception {
        return new JSONObject().put("name", name).put("ok", ok).put("detail", detail);
    }

    private static HookStatus.Snapshot snapshot(JSONObject... hooks) throws Exception {
        return new HookStatus.Snapshot(new JSONObject().put("time", 5L).put("bootTime", 2L)
                .put("hooks", new JSONArray(Arrays.asList(hooks))));
    }

    @Test public void parsesEntriesAndCountsSuccesses() throws Exception {
        HookStatus.Snapshot s = snapshot(hook("Broadcast entry", true, ""), hook("Auto-start", false, "x"));
        assertEquals(5L, s.time);
        assertEquals(2L, s.bootTime);
        assertEquals(HookStatus.SOURCE_SYSTEM, s.source);
        assertFalse(s.safeMode);
        assertEquals(2, s.hooks.size());
        assertEquals(1, s.okCount());
        assertEquals("Auto-start", s.hooks.get(1).name);
        assertFalse(s.hooks.get(1).ok);
        assertEquals("x", s.hooks.get(1).detail);
        assertEquals(Arrays.asList("Auto-start"), s.failed());
    }

    @Test public void groupsWithoutMethodHooksReportNoCallCount() throws Exception {
        HookStatus.Snapshot s = snapshot(hook("Install receiver", true, ""),
                hook("Greeze freeze", true, "").put("hooks", 4).put("calls", 12L).put("last", 99L));
        assertEquals(-1, s.hooks.get(0).calls);
        assertEquals(12, s.hooks.get(1).calls);
        assertEquals(99, s.hooks.get(1).last);
    }

    @Test public void missingHooksArrayIsEmpty() {
        HookStatus.Snapshot s = new HookStatus.Snapshot(new JSONObject());
        assertTrue(s.hooks.isEmpty());
        assertEquals(0, s.okCount());
        assertTrue(s.failed().isEmpty());
    }

    @Test public void countersFollowTheGroupBeingInstalled() throws Exception {
        assertNull(HookStatus.currentCounter());
        Runnable outer = HookStatus.enterGroup("outer-test");
        HookStatus.Counter first = HookStatus.currentCounter();
        Runnable inner = HookStatus.enterGroup("inner-test");
        HookStatus.Counter second = HookStatus.currentCounter();
        inner.run();
        assertSame(first, HookStatus.currentCounter());
        outer.run();
        assertNull(HookStatus.currentCounter());
        assertNotSame(first, second);
        second.hit(); second.hit();
        HookStatus.record("outer-test", true, "");
        HookStatus.record("inner-test", true, "");
        HookStatus.record("plain-test", false, "boom");
        HookStatus.Snapshot s = new HookStatus.Snapshot(HookStatus.toJson(HookStatus.SOURCE_POWERKEEPER, 10, 4));
        assertEquals(HookStatus.SOURCE_POWERKEEPER, s.source);
        long outerCalls = -2, innerCalls = -2, plainCalls = -2;
        for (HookStatus.Hook hook : s.hooks) {
            if (hook.name.equals("outer-test")) outerCalls = hook.calls;
            if (hook.name.equals("inner-test")) innerCalls = hook.calls;
            if (hook.name.equals("plain-test")) plainCalls = hook.calls;
        }
        assertEquals(0, outerCalls);
        assertEquals(2, innerCalls);
        assertEquals(-1, plainCalls);
    }

    @Test public void onlyKnownSourcesGetAFile() {
        assertEquals("hook-status.json", HookStatus.fileFor("android"));
        assertEquals("hook-status-powerkeeper.json", HookStatus.fileFor("com.miui.powerkeeper"));
        assertNull(HookStatus.fileFor("../evil"));
        assertNull(HookStatus.fileFor(null));
    }
}
