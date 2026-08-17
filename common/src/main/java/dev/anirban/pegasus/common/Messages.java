/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Platform-neutral user-facing text with {@code {placeholder}} substitution.
 * Paper and Fabric both feed these strings into their own component APIs.
 */
public final class Messages {
    public static final String TAME_SUCCESS = "tame-success";
    public static final String TAME_FAILED = "tame-failed";
    public static final String ALREADY_OWNED = "already-owned";
    public static final String NOT_OWNER = "not-owner";
    public static final String NEEDS_SADDLE = "needs-saddle";
    public static final String OWNER_INFO = "owner-info";
    public static final String OWNER_NONE = "owner-none";
    public static final String TRANSFER_DONE = "transfer-done";
    public static final String OWNER_CLEARED = "owner-cleared";
    public static final String NO_PERMISSION = "no-permission";
    public static final String RELOADED = "reloaded";
    public static final String EGG_GIVEN = "egg-given";
    public static final String UNICORN_PREPARED = "unicorn-prepared";
    public static final String UNICORN_NOT_PREPARED = "unicorn-not-prepared";
    public static final String BREEDING_SUCCESS = "breeding-success";
    public static final String BREEDING_FAILED = "breeding-failed";

    private final Map<String, String> values;

    public Messages(Map<String, String> overrides) {
        Map<String, String> merged = new LinkedHashMap<>(defaults());
        if (overrides != null) {
            overrides.forEach((key, value) -> {
                if (key != null && value != null && !value.isBlank()) {
                    merged.put(key, value);
                }
            });
        }
        this.values = Map.copyOf(merged);
    }

    public static Map<String, String> defaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put(TAME_SUCCESS, "The Pegasus accepts you. You are now its owner.");
        defaults.put(TAME_FAILED, "The Pegasus is not convinced yet. Try again.");
        defaults.put(ALREADY_OWNED, "This Pegasus already belongs to {owner}.");
        defaults.put(NOT_OWNER, "Only {owner} may do that with this Pegasus.");
        defaults.put(NEEDS_SADDLE, "This Pegasus needs a saddle before it can be ridden.");
        defaults.put(OWNER_INFO, "Owner: {owner} ({uuid})");
        defaults.put(OWNER_NONE, "This Pegasus has no owner yet.");
        defaults.put(TRANSFER_DONE, "Ownership transferred to {owner}.");
        defaults.put(OWNER_CLEARED, "Ownership cleared. This Pegasus can be tamed again.");
        defaults.put(NO_PERMISSION, "You do not have permission to do that.");
        defaults.put(RELOADED, "Pegasus configuration reloaded.");
        defaults.put(EGG_GIVEN, "Gave a {variant} Pegasus spawn egg to {player}.");
        defaults.put(UNICORN_PREPARED, "The Unicorn absorbs the Nether Star's light.");
        defaults.put(UNICORN_NOT_PREPARED, "Nothing happens. This Unicorn is not ready.");
        defaults.put(BREEDING_SUCCESS, "A Pegasus foal is born!");
        defaults.put(BREEDING_FAILED, "The foal is an ordinary Unicorn this time.");
        return defaults;
    }

    public String get(String key) {
        return values.getOrDefault(Objects.requireNonNull(key, "key"), key);
    }

    /** Replaces {@code {name}} tokens; unmatched placeholders are left intact for visibility. */
    public String format(String key, Map<String, String> placeholders) {
        String message = get(key);
        if (placeholders == null || placeholders.isEmpty()) {
            return message;
        }
        StringBuilder builder = new StringBuilder(message);
        placeholders.forEach((name, value) -> {
            String token = "{" + name + "}";
            String replacement = value == null ? "" : value;
            int index;
            while ((index = builder.indexOf(token)) >= 0) {
                builder.replace(index, index + token.length(), replacement);
            }
        });
        return builder.toString();
    }

    public String format(String key, String placeholder, String value) {
        return format(key, Map.of(placeholder, value));
    }
}
