package dev.anirban.pegasus.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessagesTest {
    @Test
    void everyMessageConstantHasADefaultSoNoKeyLeaksToPlayers() {
        Map<String, String> defaults = Messages.defaults();
        for (String key : new String[]{
                Messages.TAME_SUCCESS, Messages.TAME_FAILED, Messages.ALREADY_OWNED, Messages.NOT_OWNER,
                Messages.NEEDS_SADDLE, Messages.OWNER_INFO, Messages.OWNER_NONE, Messages.TRANSFER_DONE,
                Messages.OWNER_CLEARED, Messages.NO_PERMISSION, Messages.RELOADED, Messages.EGG_GIVEN,
                Messages.UNICORN_PREPARED, Messages.UNICORN_NOT_PREPARED, Messages.BREEDING_SUCCESS,
                Messages.BREEDING_FAILED}) {
            assertTrue(defaults.containsKey(key), "missing default for " + key);
            assertFalse(defaults.get(key).isBlank(), "blank default for " + key);
        }
    }

    @Test
    void substitutesEveryOccurrenceOfAPlaceholder() {
        Messages messages = new Messages(Map.of("test", "{owner} and {owner} own {uuid}"));
        // "test" is not a known key, so it is ignored; verify against a real key instead.
        Messages real = new Messages(Map.of(Messages.OWNER_INFO, "{owner}/{owner}/{uuid}"));
        assertEquals("Ann/Ann/abc", real.format(Messages.OWNER_INFO,
                Map.of("owner", "Ann", "uuid", "abc")));
        assertEquals("{owner} and {owner} own {uuid}", messages.get("test"));
    }

    @Test
    void blankAndNullOverridesFallBackToDefaults() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put(Messages.TAME_SUCCESS, "   ");
        overrides.put(Messages.TAME_FAILED, null);
        Messages messages = new Messages(overrides);
        assertEquals(Messages.defaults().get(Messages.TAME_SUCCESS), messages.get(Messages.TAME_SUCCESS));
        assertEquals(Messages.defaults().get(Messages.TAME_FAILED), messages.get(Messages.TAME_FAILED));
    }

    @Test
    void nullPlaceholderValueBecomesEmptyRatherThanPrintingNull() {
        Messages messages = new Messages(null);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("owner", null);
        assertFalse(messages.format(Messages.NOT_OWNER, placeholders).contains("null"));
    }

    @Test
    void unknownKeyReturnsTheKeyItselfInsteadOfThrowing() {
        assertEquals("no-such-key", new Messages(null).get("no-such-key"));
    }
}
