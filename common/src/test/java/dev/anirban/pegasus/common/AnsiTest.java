package dev.anirban.pegasus.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnsiTest {
    private static final String ESCAPE = "\u001B";

    @Test
    void colourIsNeverTheOnlySignalOfStatus() {
        // Requirement: a reader with no colour support must still understand every line.
        Ansi plain = new Ansi(false);
        assertEquals("[OK] done", plain.success("done"));
        assertEquals("[WARN] careful", plain.warn("careful"));
        assertEquals("[FAIL] broken", plain.failure("broken"));
        assertEquals("[..] working", plain.info("working"));
        assertEquals("title", plain.title("title"));
    }

    @Test
    void disabledAnsiEmitsNoEscapeSequences() {
        Ansi plain = new Ansi(false);
        for (String line : List.of(plain.success("a"), plain.warn("b"), plain.failure("c"),
                plain.info("d"), plain.title("e"))) {
            assertFalse(line.contains(ESCAPE), "unexpected escape sequence in: " + line);
        }
    }

    @Test
    void enabledAnsiStillContainsThePlainTextMarkerAndMessage() {
        Ansi coloured = new Ansi(true);
        String line = coloured.failure("disk full");
        assertTrue(line.contains(ESCAPE));
        // Even coloured, the textual marker and the message survive intact.
        assertTrue(line.contains("[FAIL]"));
        assertTrue(line.contains("disk full"));
    }

    @Test
    void startupReportRendersEveryLineAndDetectsFailure() {
        StartupReport report = new StartupReport()
                .ok("Configuration loaded and validated")
                .ok("Ownership storage ready")
                .warn("something is odd")
                .info("resource pack optional");
        assertFalse(report.hasFailure());

        List<String> emitted = new ArrayList<>();
        report.emit(new Ansi(false), emitted::add);

        // Title plus one line per entry.
        assertEquals(5, emitted.size());
        assertTrue(emitted.get(0).contains("Created by Anirban <3"));
        assertTrue(emitted.get(1).startsWith("[OK]"));
        assertTrue(emitted.get(3).startsWith("[WARN]"));
        assertTrue(emitted.get(4).startsWith("[..]"));
    }

    @Test
    void startupReportFlagsFailures() {
        StartupReport report = new StartupReport().ok("fine").fail("command registration failed");
        assertTrue(report.hasFailure());
        List<String> emitted = new ArrayList<>();
        report.emit(new Ansi(false), emitted::add);
        assertTrue(emitted.stream().anyMatch(line -> line.startsWith("[FAIL]")));
    }

    @Test
    void brandingCreditIsExactAndPresentInTheStartupTitle() {
        assertEquals("Created by Anirban <3", Branding.CREDIT);
        assertTrue(Branding.STARTUP_TITLE.contains("Created by Anirban <3"));
        assertTrue(Branding.STARTUP_TITLE.startsWith("Pegasus 1.21"));
    }
}
