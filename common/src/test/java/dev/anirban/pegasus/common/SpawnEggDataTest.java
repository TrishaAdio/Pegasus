package dev.anirban.pegasus.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpawnEggDataTest {
    @Test
    void roundTripsThroughStoredData() {
        SpawnEggData egg = new SpawnEggData(PegasusVariant.BLUE_EYE);
        Map<String, String> stored = egg.serialize();
        assertEquals(PegasusVariant.BLUE_EYE, SpawnEggData.deserialize(stored).orElseThrow().variant());
        assertEquals("1", stored.get(SpawnEggData.VERSION_KEY));
    }

    @Test
    void acceptsHumanFriendlyVariantSpellings() {
        assertEquals(PegasusVariant.BLUE_EYE, SpawnEggData.parse("blue-eye").orElseThrow().variant());
        assertEquals(PegasusVariant.BLUE_EYE, SpawnEggData.parse("  BLUE_EYE ").orElseThrow().variant());
        assertEquals(PegasusVariant.CLASSIC, SpawnEggData.parse("classic").orElseThrow().variant());
    }

    @Test
    void rejectsUnknownOrHostileVariantDataWithoutThrowing() {
        assertTrue(SpawnEggData.parse("dragon").isEmpty());
        assertTrue(SpawnEggData.parse("").isEmpty());
        assertTrue(SpawnEggData.parse(null).isEmpty());
        assertTrue(SpawnEggData.parse("../../etc/passwd").isEmpty());
        assertTrue(SpawnEggData.deserialize(null).isEmpty());
        assertTrue(SpawnEggData.deserialize(Map.of()).isEmpty());
    }

    @Test
    void spawnPathAlwaysGetsAUsableVariant() {
        Map<String, String> corrupt = new HashMap<>();
        corrupt.put(SpawnEggData.VARIANT_KEY, "not-a-variant");
        assertEquals(PegasusVariant.CLASSIC, SpawnEggData.deserializeOrDefault(corrupt).variant());
        assertEquals(PegasusVariant.CLASSIC, SpawnEggData.deserializeOrDefault(null).variant());
    }

    @Test
    void nullVariantNormalisesInsteadOfCreatingBrokenItem() {
        assertEquals(PegasusVariant.CLASSIC, new SpawnEggData(null).variant());
    }
}
