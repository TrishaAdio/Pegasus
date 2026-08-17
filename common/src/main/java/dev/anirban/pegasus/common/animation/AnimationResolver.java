/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.common.animation;

/**
 * Decides which animation to show from plain movement facts.
 *
 * <p>Kept free of any server/client API so the same transition rules drive the Paper display-entity
 * frames and the Fabric model, and so the anti-flicker behaviour is unit-testable.
 *
 * <p>Two mechanisms prevent the visual snapping that naive per-tick switching causes:
 * <ul>
 *   <li>a minimum dwell time per state ({@link AnimationState#minimumDurationMillis()}), and</li>
 *   <li>speed hysteresis, so a Pegasus hovering exactly on the walk/run boundary does not
 *       oscillate between two states every tick.</li>
 * </ul>
 */
public final class AnimationResolver {
    /** Blocks-per-tick thresholds; separate enter/exit values give the hysteresis band. */
    private static final double WALK_ENTER = 0.03;
    private static final double WALK_EXIT = 0.015;
    private static final double RUN_ENTER = 0.22;
    private static final double RUN_EXIT = 0.17;

    private AnimationState current = AnimationState.IDLE;
    private long stateChangedAt = Long.MIN_VALUE;

    /** Immutable snapshot of everything needed to pick a state. */
    public record Input(boolean dead, boolean hurt, boolean eating, boolean onGround,
                        boolean airborne, double horizontalSpeed, double verticalSpeed,
                        boolean ascending) { }

    public AnimationState current() {
        return current;
    }

    public long stateChangedAt() {
        return stateChangedAt;
    }

    /**
     * Advances the state machine.
     *
     * @return the state that should be rendered now
     */
    public AnimationState resolve(Input input, long nowMillis) {
        AnimationState desired = desiredFor(input);

        if (stateChangedAt == Long.MIN_VALUE) {
            current = desired;
            stateChangedAt = nowMillis;
            return current;
        }
        if (desired == current) {
            return current;
        }

        // Death always interrupts immediately; every other state must serve its minimum dwell time
        // so transient poses cannot be cut off after a single tick.
        long elapsed = nowMillis - stateChangedAt;
        if (desired != AnimationState.DEATH && elapsed < current.minimumDurationMillis()) {
            return current;
        }

        current = desired;
        stateChangedAt = nowMillis;
        return current;
    }

    private AnimationState desiredFor(Input input) {
        if (input.dead()) {
            return AnimationState.DEATH;
        }
        if (input.hurt()) {
            return AnimationState.HURT;
        }
        if (input.eating()) {
            return AnimationState.EAT;
        }
        if (input.airborne() && !input.onGround()) {
            // A strong downward vector while still airborne reads as an approach to land.
            if (input.verticalSpeed() < -0.35) {
                return AnimationState.LANDING;
            }
            // Takeoff is only entered when leaving the ground. Once already airborne the state must
            // progress into the flap/glide cycle, otherwise a sustained climb would hold the takeoff
            // pose forever.
            if (!current.airborne() && input.ascending()) {
                return AnimationState.TAKEOFF;
            }
            return input.ascending() ? AnimationState.WING_FLAP : AnimationState.FLY;
        }
        if (current.airborne() && input.onGround()) {
            return AnimationState.LANDING;
        }
        return groundLocomotion(input.horizontalSpeed());
    }

    /** Applies hysteresis so boundary speeds cannot flip-flop between states. */
    private AnimationState groundLocomotion(double speed) {
        return switch (current) {
            case RUN -> speed >= RUN_EXIT ? AnimationState.RUN
                    : (speed >= WALK_EXIT ? AnimationState.WALK : AnimationState.IDLE);
            case WALK -> speed >= RUN_ENTER ? AnimationState.RUN
                    : (speed >= WALK_EXIT ? AnimationState.WALK : AnimationState.IDLE);
            default -> speed >= RUN_ENTER ? AnimationState.RUN
                    : (speed >= WALK_ENTER ? AnimationState.WALK : AnimationState.IDLE);
        };
    }

    /** Wing beat phase in [0,1); used for continuous wing motion rather than discrete frames. */
    public static double wingPhase(long nowMillis, AnimationState state) {
        double periodMillis = switch (state) {
            case TAKEOFF, WING_FLAP -> 420.0;
            case FLY -> 700.0;
            case LANDING -> 520.0;
            default -> 2600.0;
        };
        double phase = (nowMillis % (long) periodMillis) / periodMillis;
        return phase < 0 ? phase + 1 : phase;
    }
}
