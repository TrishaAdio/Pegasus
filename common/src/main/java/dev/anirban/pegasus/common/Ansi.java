/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.common;

/**
 * Minimal ANSI helper.
 *
 * <p>Colour is always decorative: every message produced here keeps its full meaning as plain
 * text, and status symbols are ASCII-safe words/symbols rather than colour alone.
 */
public final class Ansi {
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    private final boolean enabled;

    public Ansi(boolean enabled) {
        this.enabled = enabled;
    }

    /** Honours NO_COLOR and non-TTY output so logs stay readable when piped to a file. */
    public static Ansi autoDetect() {
        boolean noColor = System.getenv("NO_COLOR") != null;
        boolean dumbTerminal = "dumb".equalsIgnoreCase(System.getenv("TERM"));
        return new Ansi(!noColor && !dumbTerminal);
    }

    public String success(String message) {
        return paint(GREEN, "[OK] " + message);
    }

    public String warn(String message) {
        return paint(YELLOW, "[WARN] " + message);
    }

    public String failure(String message) {
        return paint(RED, "[FAIL] " + message);
    }

    public String info(String message) {
        return paint(CYAN, "[..] " + message);
    }

    public String title(String message) {
        return enabled ? BOLD + CYAN + message + RESET : message;
    }

    public boolean enabled() {
        return enabled;
    }

    private String paint(String colour, String message) {
        return enabled ? colour + message + RESET : message;
    }
}
