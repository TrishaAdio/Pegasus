/* Pegasus Java Edition — Created by Anirban <3 */
package dev.anirban.pegasus.common;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/** Pure rules for the documented Java Edition Unicorn-to-Pegasus breeding mechanic. */
public final class BreedingRules {
    private BreedingRules() { }

    public record Preparation(UUID unicornId, long preparedAtMillis) {
        public boolean activeAt(long nowMillis, Duration window) {
            return nowMillis >= preparedAtMillis && nowMillis - preparedAtMillis <= window.toMillis();
        }
    }

    public record Settings(Duration preparationWindow, int bothPreparedChance, int onePreparedChance) {
        public Settings {
            Objects.requireNonNull(preparationWindow, "preparationWindow");
            if (preparationWindow.isNegative() || preparationWindow.isZero()) {
                throw new IllegalArgumentException("Preparation window must be positive");
            }
            checkChance(bothPreparedChance, "bothPreparedChance");
            checkChance(onePreparedChance, "onePreparedChance");
        }
        private static void checkChance(int chance, String name) {
            if (chance < 0 || chance > 100) throw new IllegalArgumentException(name + " must be 0..100");
        }
    }

    public static int successChance(Preparation first, Preparation second, long nowMillis, Settings settings) {
        boolean a = first != null && first.activeAt(nowMillis, settings.preparationWindow());
        boolean b = second != null && second.activeAt(nowMillis, settings.preparationWindow());
        return a && b ? settings.bothPreparedChance() : (a || b ? settings.onePreparedChance() : 0);
    }

    /** randomPercent must be an inclusive percentage from 1 through 100. */
    public static boolean succeeds(int chancePercent, int randomPercent) {
        if (randomPercent < 1 || randomPercent > 100) throw new IllegalArgumentException("randomPercent must be 1..100");
        return randomPercent <= chancePercent;
    }
}
