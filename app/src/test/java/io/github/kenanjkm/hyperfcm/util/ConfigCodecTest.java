package io.github.kenanjkm.hyperfcm.util;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

/** Exercises real JSON types and missing keys at the codec boundary, not Map construction. */
public class ConfigCodecTest {
    private static ConfigSnapshot parse(String json) throws Exception {
        return ConfigCodec.fromJson(new JSONObject(json));
    }

    @Test public void parsesApplicationsTimestampAndExplicitOptions() throws Exception {
        ConfigSnapshot snapshot = parse("{\"allowList\":[\"com.example.first\",\"com.example.second\"],"
                + "\"revision\":1791444000000,\"disableAutoCleanNotification\":true,"
                + "\"includeIceBoxDisableApp\":true,\"keepGmsAwake\":false,"
                + "\"greezeGlobalPolicy\":true,\"sleepModeKeepMobileData\":true}");
        assertEquals(Set.of("com.example.first", "com.example.second"), snapshot.allowList);
        assertEquals(1791444000000L, snapshot.revision);
        assertTrue(snapshot.options.get("disableAutoCleanNotification"));
        assertTrue(snapshot.options.get("includeIceBoxDisableApp"));
        assertFalse(snapshot.options.get("keepGmsAwake"));
        assertTrue(snapshot.options.get("greezeGlobalPolicy"));
        assertTrue(snapshot.options.get("sleepModeKeepMobileData"));
    }

    @Test public void acceptsIntegerAndLongJsonRevisionBoundaries() throws Exception {
        for (long revision : new long[]{0, Integer.MAX_VALUE, (long) Integer.MAX_VALUE + 1, Long.MAX_VALUE}) {
            assertEquals(revision, parse("{\"allowList\":[],\"revision\":" + revision + "}").revision);
        }
    }

    @Test public void omittedKeysReachSnapshotDefaults() throws Exception {
        ConfigSnapshot snapshot = parse("{\"allowList\":[]}");
        assertEquals(0, snapshot.revision);
        assertTrue(snapshot.options.get("keepGmsAwake"));
        assertTrue(snapshot.options.get("autoAllowNewApps"));
        assertFalse(snapshot.options.get("greezeGlobalPolicy"));
        assertFalse(snapshot.options.get("sleepModeKeepMobileData"));
        assertFalse(snapshot.options.get("disableAutoCleanNotification"));
        assertFalse(snapshot.options.get("includeIceBoxDisableApp"));
    }

    @Test public void unknownKeysAreIgnored() throws Exception {
        ConfigSnapshot snapshot = parse("{\"allowList\":[],\"somethingElse\":false}");
        assertFalse(snapshot.options.containsKey("somethingElse"));
    }

    @Test public void deduplicatesRepeatedApplicationEntries() throws Exception {
        ConfigSnapshot snapshot = parse("{\"allowList\":[\"com.example.first\",\"com.example.first\","
                + "\"com.example.second\",\"com.example.first\"]}");
        assertEquals(Set.of("com.example.first", "com.example.second"), snapshot.allowList);
    }

    @Test public void rejectsMissingOrNonArrayAllowList() {
        assertThrows(JSONException.class, () -> parse("{}"));
        for (String value : new String[]{"null", "\"com.example.first\"", "{}", "true", "7"}) {
            assertThrows("allowList=" + value, JSONException.class,
                    () -> parse("{\"allowList\":" + value + "}"));
        }
    }

    @Test public void rejectsNonStringElementsInsteadOfCoercingOrSkippingThem() {
        for (String element : new String[]{"null", "7", "true", "{}", "[]"}) {
            assertThrows("element=" + element, IllegalArgumentException.class,
                    () -> parse("{\"allowList\":[\"com.example.first\"," + element + "]}"));
        }
    }

    @Test public void rejectsWrongOptionTypesIncludingJsonNull() {
        for (String key : ConfigSchema.OPTIONS) {
            for (String value : new String[]{"null", "\"true\"", "1", "[]", "{}"}) {
                assertThrows(key + "=" + value, IllegalArgumentException.class,
                        () -> parse("{\"allowList\":[],\"" + key + "\":" + value + "}"));
            }
        }
    }

    @Test public void rejectsWrongRevisionTypesIncludingJsonNull() {
        for (String revision : new String[]{"null", "\"123\"", "true", "[]", "{}"}) {
            assertInvalidRevision(revision);
        }
    }

    @Test public void rejectsNegativeIntegerRevision() {
        assertInvalidRevision("-1");
    }

    @Test public void rejectsFractionalRevisionRatherThanTruncatingIt() {
        assertInvalidRevision("1.25");
    }

    @Test public void rejectsNegativeFractionalRevisionRatherThanTruncatingItToZero() {
        assertInvalidRevision("-0.5");
    }

    @Test public void rejectsOverflowingRevisionRatherThanSaturatingItToLongMax() {
        assertInvalidRevision("9223372036854775808");
        assertInvalidRevision("1e100");
    }

    private static void assertInvalidRevision(String revision) {
        assertThrows("revision=" + revision, IllegalArgumentException.class,
                () -> parse("{\"allowList\":[],\"revision\":" + revision + "}"));
    }
}
