package io.github.kenanjkm.hyperfcm.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Boot-loop guard. Every system_server start counts as an unfinished boot until the process has
 * stayed alive for a while. After {@link #LIMIT} unfinished boots in a row the module installs no
 * hooks ("safe mode") until the user re-enables them from the app, which changes the reset token.
 *
 * Nothing is scheduled: the state is read once at start and written at most twice per boot.
 */
public final class BootGuard {
    private BootGuard() {}

    public static final int LIMIT = 3;
    /** system_server can write here before the user unlocks. */
    public static final String FILE = "/data/system/hyperfcm-bootguard";

    public static final class State {
        public int unfinished;
        public boolean tripped;
        public long token;

        public State(int unfinished, boolean tripped, long token) {
            this.unfinished = unfinished;
            this.tripped = tripped;
            this.token = token;
        }

        public String encode() { return unfinished + " " + (tripped ? 1 : 0) + " " + token; }

        public static State decode(String text) {
            try {
                String[] parts = text.trim().split("\\s+");
                return new State(Math.max(0, Integer.parseInt(parts[0])), "1".equals(parts[1]), Long.parseLong(parts[2]));
            } catch (RuntimeException invalid) {
                return new State(0, false, 0);
            }
        }
    }

    /**
     * Advances the state for a new boot. Returns true when hooks must NOT be installed.
     * {@code resetToken} is the value the app last wrote; a change releases safe mode.
     */
    public static boolean enterBoot(State state, long resetToken) {
        if (state.tripped) {
            if (resetToken == state.token) return true;
            state.tripped = false;
            state.unfinished = 0;
        }
        state.token = resetToken;
        if (state.unfinished >= LIMIT) {
            state.tripped = true;
            return true;
        }
        state.unfinished++;
        return false;
    }

    /** The boot survived: forget the unfinished boots (safe mode itself is only left via the token). */
    public static void bootFinished(State state) { state.unfinished = 0; }

    public static State read(File file) {
        try {
            return State.decode(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (Throwable missing) {
            return new State(0, false, 0);
        }
    }

    public static boolean write(File file, State state) {
        try {
            Files.write(file.toPath(), state.encode().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
