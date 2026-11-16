package io.github.kenanjkm.hyperfcm.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AlertUpdateGuardTest {
    @Test
    public void guardsOnlyUpdatesOfAllowedAppsWithOptionOn() {
        assertTrue(AlertUpdateGuard.guardUpdate(true, true, true));
        assertFalse(AlertUpdateGuard.guardUpdate(false, true, true));
        assertFalse(AlertUpdateGuard.guardUpdate(true, false, true));
        assertFalse(AlertUpdateGuard.guardUpdate(true, true, false));
    }

    @Test
    public void loopingAlertsAlwaysStop() {
        assertTrue(AlertUpdateGuard.keepRunningAlert(true, false));
        assertFalse(AlertUpdateGuard.keepRunningAlert(true, true));
        assertFalse(AlertUpdateGuard.keepRunningAlert(false, false));
    }

    @Test
    public void insistentFlagMeansLooping() {
        assertTrue(AlertUpdateGuard.isLooping(AlertUpdateGuard.FLAG_INSISTENT));
        assertTrue(AlertUpdateGuard.isLooping(AlertUpdateGuard.FLAG_INSISTENT | 0x40));
        assertFalse(AlertUpdateGuard.isLooping(0x10 | 0x8));
    }

    @Test
    public void optionIsOffByDefault() {
        assertTrue(ConfigSchema.OPTIONS.contains(ConfigSchema.KEY_KEEP_ALERT_ON_UPDATE));
        assertFalse(ConfigSchema.defaultValue(ConfigSchema.KEY_KEEP_ALERT_ON_UPDATE));
    }
}
