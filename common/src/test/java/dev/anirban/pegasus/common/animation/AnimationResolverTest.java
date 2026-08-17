package dev.anirban.pegasus.common.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnimationResolverTest {
    private static AnimationResolver.Input ground(double speed) {
        return new AnimationResolver.Input(false, false, false, true, false, speed, 0, false);
    }

    private static AnimationResolver.Input flying(double vertical, boolean ascending) {
        return new AnimationResolver.Input(false, false, false, false, true, 0.3, vertical, ascending);
    }

    @Test
    void picksGroundLocomotionFromSpeed() {
        AnimationResolver resolver = new AnimationResolver();
        assertEquals(AnimationState.IDLE, resolver.resolve(ground(0), 0));
        assertEquals(AnimationState.WALK, resolver.resolve(ground(0.1), 1_000));
        assertEquals(AnimationState.RUN, resolver.resolve(ground(0.4), 2_000));
    }

    @Test
    void speedHysteresisPreventsWalkRunFlicker() {
        AnimationResolver resolver = new AnimationResolver();
        resolver.resolve(ground(0.4), 0);
        assertEquals(AnimationState.RUN, resolver.current());
        // Just under the enter threshold but above the exit threshold: must stay RUN, not flip.
        assertEquals(AnimationState.RUN, resolver.resolve(ground(0.19), 5_000));
        assertEquals(AnimationState.RUN, resolver.resolve(ground(0.18), 6_000));
        // Clearly slower: now it may drop.
        assertEquals(AnimationState.WALK, resolver.resolve(ground(0.05), 7_000));
    }

    @Test
    void transientStatesHoldForTheirMinimumDurationInsteadOfSnapping() {
        AnimationResolver resolver = new AnimationResolver();
        resolver.resolve(ground(0), 0);
        assertEquals(AnimationState.HURT, resolver.resolve(
                new AnimationResolver.Input(false, true, false, true, false, 0, 0, false), 1_000));
        // 100ms later the hurt pose must still be showing (minimum 400ms).
        assertEquals(AnimationState.HURT, resolver.resolve(ground(0), 1_100));
        // After the dwell time it may return to locomotion.
        assertEquals(AnimationState.IDLE, resolver.resolve(ground(0), 1_500));
    }

    @Test
    void deathAlwaysInterruptsImmediately() {
        AnimationResolver resolver = new AnimationResolver();
        resolver.resolve(new AnimationResolver.Input(false, true, false, true, false, 0, 0, false), 0);
        assertEquals(AnimationState.HURT, resolver.current());
        AnimationState state = resolver.resolve(
                new AnimationResolver.Input(true, true, false, true, false, 0, 0, false), 10);
        assertEquals(AnimationState.DEATH, state);
    }

    @Test
    void ascendingFromGroundEntersTakeoffThenSustainedFlight() {
        AnimationResolver resolver = new AnimationResolver();
        resolver.resolve(ground(0), 0);
        assertEquals(AnimationState.TAKEOFF, resolver.resolve(flying(0.4, true), 100));
        // Takeoff holds briefly, then transitions to a flap/fly cycle.
        assertEquals(AnimationState.TAKEOFF, resolver.resolve(flying(0.4, true), 300));
        assertEquals(AnimationState.WING_FLAP, resolver.resolve(flying(0.4, true), 600));
        assertEquals(AnimationState.FLY, resolver.resolve(flying(0.0, false), 1_200));
    }

    @Test
    void strongDescentReadsAsLandingAndTouchdownResolvesToLanding() {
        AnimationResolver resolver = new AnimationResolver();
        resolver.resolve(flying(0, false), 0);
        assertEquals(AnimationState.LANDING, resolver.resolve(flying(-0.6, false), 1_000));
        assertEquals(AnimationState.LANDING, resolver.resolve(ground(0), 2_000));
    }

    @Test
    void wingPhaseStaysNormalisedAndFasterWhenFlapping() {
        for (long time = 0; time < 5_000; time += 37) {
            double phase = AnimationResolver.wingPhase(time, AnimationState.FLY);
            assertTrue(phase >= 0 && phase < 1, "phase out of range: " + phase);
        }
        assertNotEquals(AnimationResolver.wingPhase(200, AnimationState.WING_FLAP),
                AnimationResolver.wingPhase(200, AnimationState.FLY));
    }
}
