/*
 * Pegasus Java Edition — Created by Anirban <3
 * Clean-room implementation. No Bedrock add-on code or assets are included.
 */
package dev.anirban.pegasus.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Versioned, platform-neutral permanent ownership data. */
public record OwnershipRecord(UUID ownerId, String lastKnownName, int dataVersion) {
    public static final int CURRENT_VERSION = 1;

    public OwnershipRecord {
        Objects.requireNonNull(ownerId, "ownerId");
        lastKnownName = sanitizeName(lastKnownName);
        if (dataVersion < 1) dataVersion = CURRENT_VERSION;
    }

    public static OwnershipRecord of(UUID ownerId, String ownerName) {
        return new OwnershipRecord(ownerId, ownerName, CURRENT_VERSION);
    }

    public Map<String, String> serialize() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("version", Integer.toString(CURRENT_VERSION));
        data.put("owner-uuid", ownerId.toString());
        data.put("owner-name", lastKnownName);
        return data;
    }

    /** Parses current and legacy key spellings without allowing malformed UUIDs to escape. */
    public static Optional<OwnershipRecord> deserialize(Map<String, String> raw) {
        if (raw == null) return Optional.empty();
        String id = firstPresent(raw, "owner-uuid", "ownerUuid", "owner");
        if (id == null || id.isBlank()) return Optional.empty();
        try {
            int version = parsePositive(raw.get("version"), CURRENT_VERSION);
            return Optional.of(new OwnershipRecord(UUID.fromString(id.trim()),
                    firstPresent(raw, "owner-name", "ownerName", "lastKnownOwner"), version));
        } catch (IllegalArgumentException invalidUuid) {
            return Optional.empty();
        }
    }

    private static String firstPresent(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null) return value;
        }
        return "";
    }

    private static int parsePositive(String value, int fallback) {
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String sanitizeName(String name) {
        if (name == null) return "Unknown";
        String clean = name.strip();
        return clean.isEmpty() ? "Unknown" : clean.substring(0, Math.min(clean.length(), 64));
    }
}
