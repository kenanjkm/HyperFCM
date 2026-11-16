package io.github.kenanjkm.hyperfcm.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MiuiSoundDefaultsTest {
    @Test
    public void keysMatchSystemUi() {
        assertEquals("com.duolingo_sound", MiuiSoundDefaults.appKey("com.duolingo", "sound"));
        assertEquals("com.duolingo_practice_vibrate",
                MiuiSoundDefaults.channelKey("com.duolingo", "practice", "vibrate"));
        assertNull(MiuiSoundDefaults.channelKey("com.duolingo", "miscellaneous", "sound"));
        assertNull(MiuiSoundDefaults.channelKey("com.duolingo", "", "sound"));
    }

    @Test
    public void unsetSwitchesBecomeOnForAllowedApps() {
        assertTrue(MiuiSoundDefaults.allow(true, true, false, null, null));
        assertTrue(MiuiSoundDefaults.allow(true, true, false, true, null));
    }

    @Test
    public void userTurnedOffStaysOff() {
        assertFalse(MiuiSoundDefaults.allow(true, true, false, false, null));
        assertFalse(MiuiSoundDefaults.allow(true, true, false, true, false));
    }

    @Test
    public void otherwiseSystemAnswerStands() {
        assertFalse(MiuiSoundDefaults.allow(false, true, false, null, null));
        assertFalse(MiuiSoundDefaults.allow(true, false, false, null, null));
        assertTrue(MiuiSoundDefaults.allow(false, false, true, false, false));
    }
}
