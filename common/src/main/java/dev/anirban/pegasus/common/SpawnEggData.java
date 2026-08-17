/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.common;

import java.util.Map;
import java.util.Optional;

/**
 * Whitelisted custom-item payload.
 *
 * <p>Item data is attacker-controlled in practice (NBT/PDC can be edited or arrive from other
 * plugins), so parsing must never throw and must never accept an unknown variant. Callers get an
 * empty Optional and are expected to fall back to a safe default rather than spawning nothing.
 */
public record SpawnEggData(PegasusVariant variant) {
    public static final String VARIANT_KEY = "variant";
    public static final String VERSION_KEY = "version";
    public static final int CURRENT_VERSION = 1;

    public SpawnEggData {
        if (variant == null) {
            variant = PegasusVariant.CLASSIC;
        }
    }

    public Map<String, String> serialize() {
        return Map.of(VERSION_KEY, Integer.toString(CURRENT_VERSION), VARIANT_KEY, variant.id());
    }

    /** Safe parse of a raw variant string. */
    public static Optional<SpawnEggData> parse(String rawVariant) {
        return PegasusVariant.parse(rawVariant).map(SpawnEggData::new);
    }

    /** Safe parse of a stored map, tolerating missing keys and junk values. */
    public static Optional<SpawnEggData> deserialize(Map<String, String> raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return parse(raw.get(VARIANT_KEY));
    }

    /** Always yields a usable value; used on the spawn path so a bad egg still spawns a Pegasus. */
    public static SpawnEggData deserializeOrDefault(Map<String, String> raw) {
        return deserialize(raw).orElseGet(() -> new SpawnEggData(PegasusVariant.CLASSIC));
    }
}
