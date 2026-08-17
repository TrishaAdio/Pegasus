/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.common;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds the concise startup banner shared by both platforms.
 *
 * <p>Deliberately bounded: one title line plus one line per registered subsystem. Detailed
 * diagnostics belong to debug mode, keeping normal boot output free of console spam.
 */
public final class StartupReport {
    public enum Status { SUCCESS, WARNING, FAILURE, INFO }

    public record Line(Status status, String text) { }

    private final List<Line> lines = new ArrayList<>();

    public StartupReport ok(String text) {
        lines.add(new Line(Status.SUCCESS, text));
        return this;
    }

    public StartupReport warn(String text) {
        lines.add(new Line(Status.WARNING, text));
        return this;
    }

    public StartupReport fail(String text) {
        lines.add(new Line(Status.FAILURE, text));
        return this;
    }

    public StartupReport info(String text) {
        lines.add(new Line(Status.INFO, text));
        return this;
    }

    public List<Line> lines() {
        return List.copyOf(lines);
    }

    public boolean hasFailure() {
        return lines.stream().anyMatch(line -> line.status() == Status.FAILURE);
    }

    /** Renders the banner through the supplied logger sink using ANSI colour when available. */
    public void emit(Ansi ansi, Consumer<String> sink) {
        sink.accept(ansi.title(Branding.STARTUP_TITLE));
        for (Line line : lines) {
            sink.accept(switch (line.status()) {
                case SUCCESS -> ansi.success(line.text());
                case WARNING -> ansi.warn(line.text());
                case FAILURE -> ansi.failure(line.text());
                case INFO -> ansi.info(line.text());
            });
        }
    }
}
