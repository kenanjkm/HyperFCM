package io.github.kenanjkm.hyperfcm.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AlwaysAlertPolicyTest {
    @Test
    public void bitsFollowTheUsersOwnSwitches() {
        assertEquals(7, AlwaysAlertPolicy.bits(true, true, true));
        assertEquals(AlwaysAlertPolicy.BIT_SOUND, AlwaysAlertPolicy.bits(false, true, false));
        assertEquals(0, AlwaysAlertPolicy.bits(false, false, false));
    }

    @Test
    public void overridesLockScreenAndFolding() {
        assertTrue(AlwaysAlertPolicy.override(true, true, true, false, false, true, false));
        assertTrue(AlwaysAlertPolicy.override(true, true, true, false, true, false, true));
    }

    @Test
    public void leavesOtherCasesToSystemUi() {
        // Nothing to drop: unlocked, or the app may show on the lock screen, and not folded.
        assertFalse(AlwaysAlertPolicy.override(true, true, true, false, false, false, false));
        assertFalse(AlwaysAlertPolicy.override(true, true, true, false, false, true, true));
        // Option off, app not allowed, app asked for no alert, mirror DND.
        assertFalse(AlwaysAlertPolicy.override(false, true, true, false, true, true, false));
        assertFalse(AlwaysAlertPolicy.override(true, false, true, false, true, true, false));
        assertFalse(AlwaysAlertPolicy.override(true, true, false, false, true, true, false));
        assertFalse(AlwaysAlertPolicy.override(true, true, true, true, true, true, false));
    }

    @Test
    public void optionIsOffByDefault() {
        assertTrue(ConfigSchema.OPTIONS.contains(ConfigSchema.KEY_ALWAYS_ALERT));
        assertFalse(ConfigSchema.defaultValue(ConfigSchema.KEY_ALWAYS_ALERT));
    }
}
