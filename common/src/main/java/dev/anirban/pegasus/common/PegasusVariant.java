/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.common;

import java.util.Locale;
import java.util.Optional;

/** Supported original Pegasus appearances. BLUE_EYE is the documented foal/eye variant. */
public enum PegasusVariant {
    CLASSIC("classic", "Pegasus"),
    BLUE_EYE("blue_eye", "Blue-Eyed Pegasus");

    private final String id;
    private final String displayName;

    PegasusVariant(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Never throws: unknown or hostile input resolves to empty so callers can fall back safely. */
    public static Optional<PegasusVariant> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalised = raw.strip().toLowerCase(Locale.ROOT).replace('-', '_');
        for (PegasusVariant variant : values()) {
            if (variant.id.equals(normalised)) {
                return Optional.of(variant);
            }
        }
        return Optional.empty();
    }

    public static PegasusVariant parseOrDefault(String raw) {
        return parse(raw).orElse(CLASSIC);
    }
}
