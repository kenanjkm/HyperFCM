package io.github.kenanjkm.hyperfcm.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class BootGuardTest {
    @Test public void normalBootsNeverTrip() {
        BootGuard.State state = new BootGuard.State(0, false, 0);
        for (int i = 0; i < 20; i++) {
            assertFalse(BootGuard.enterBoot(state, 0));
            BootGuard.bootFinished(state);
        }
        assertEquals(0, state.unfinished);
    }

    @Test public void tripsAfterLimitUnfinishedBootsAndStaysTripped() {
        BootGuard.State state = new BootGuard.State(0, false, 0);
        for (int i = 0; i < BootGuard.LIMIT; i++) assertFalse(BootGuard.enterBoot(state, 7));
        assertTrue(BootGuard.enterBoot(state, 7));
        assertTrue(state.tripped);
        // A boot that survives in safe mode must not silently re-enable the hooks.
        BootGuard.bootFinished(state);
        assertTrue(BootGuard.enterBoot(state, 7));
    }

    @Test public void newTokenReleasesSafeModeAndCountsAgain() {
        BootGuard.State state = new BootGuard.State(BootGuard.LIMIT, true, 7);
        assertFalse(BootGuard.enterBoot(state, 8));
        assertFalse(state.tripped);
        assertEquals(1, state.unfinished);
        assertEquals(8, state.token);
    }

    @Test public void oneFinishedBootResetsTheCount() {
        BootGuard.State state = new BootGuard.State(0, false, 0);
        assertFalse(BootGuard.enterBoot(state, 0));
        assertFalse(BootGuard.enterBoot(state, 0));
        BootGuard.bootFinished(state);
        for (int i = 0; i < BootGuard.LIMIT; i++) assertFalse(BootGuard.enterBoot(state, 0));
    }

    @Test public void stateRoundTripsAndCorruptFilesStartClean() {
        BootGuard.State decoded = BootGuard.State.decode(new BootGuard.State(2, true, 123456789012L).encode());
        assertEquals(2, decoded.unfinished);
        assertTrue(decoded.tripped);
        assertEquals(123456789012L, decoded.token);
        for (String bad : new String[]{"", "x", "1 1", "a b c", "-5 0 0"}) {
            BootGuard.State state = BootGuard.State.decode(bad);
            assertFalse(bad, state.tripped);
            assertEquals(bad, 0, state.unfinished);
        }
    }
}
