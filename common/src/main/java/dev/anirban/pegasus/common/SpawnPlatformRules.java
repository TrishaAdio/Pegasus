/* Pegasus Java Edition — Created by Anirban <3 */
package dev.anirban.pegasus.common;

import java.util.Collection;
import java.util.Objects;

/** Validates a scanned, platform-specific set of blocks without importing server APIs. */
public final class SpawnPlatformRules {
    private SpawnPlatformRules() { }

    public record Settings(int minimumY, int minimumPlatformBlocks, double requiredBlockRatio) {
        public Settings {
            if (minimumY < -64 || minimumY > 320) throw new IllegalArgumentException("minimumY must be a world Y level");
            if (minimumPlatformBlocks < 1) throw new IllegalArgumentException("minimumPlatformBlocks must be positive");
            if (requiredBlockRatio <= 0 || requiredBlockRatio > 1) throw new IllegalArgumentException("requiredBlockRatio must be > 0 and <= 1");
        }
    }

    public record Result(boolean valid, String reason, int matchingBlocks, int scannedBlocks) { }

    public static Result validate(int candidateY, Collection<Boolean> isRequiredBlock, Settings settings) {
        Objects.requireNonNull(isRequiredBlock, "isRequiredBlock");
        Objects.requireNonNull(settings, "settings");
        if (candidateY <= settings.minimumY()) return new Result(false, "below-minimum-y", 0, 0);
        int scanned = isRequiredBlock.size();
        int matching = (int) isRequiredBlock.stream().filter(Boolean.TRUE::equals).count();
        if (matching < settings.minimumPlatformBlocks()) return new Result(false, "not-enough-required-blocks", matching, scanned);
        if (scanned == 0 || ((double) matching / scanned) < settings.requiredBlockRatio()) {
            return new Result(false, "platform-not-mostly-required-block", matching, scanned);
        }
        return new Result(true, "valid", matching, scanned);
    }
}
