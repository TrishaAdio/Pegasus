/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.common.animation;

/**
 * The ten required Pegasus animation states.
 *
 * <p>Ordering encodes priority: a higher {@link #priority()} wins when several states could apply,
 * which is what stops a hurt or death pose being overridden by locomotion.
 */
public enum AnimationState {
    IDLE("idle", 0, 0),
    WALK("walk", 1, 0),
    RUN("run", 2, 0),
    TAKEOFF("takeoff", 3, 400),
    FLY("fly", 4, 0),
    WING_FLAP("wing_flap", 5, 250),
    LANDING("landing", 6, 350),
    EAT("eat", 7, 700),
    HURT("hurt", 8, 400),
    DEATH("death", 9, 1600);

    private final String id;
    private final int priority;
    private final long minimumDurationMillis;

    AnimationState(String id, int priority, long minimumDurationMillis) {
        this.id = id;
        this.priority = priority;
        this.minimumDurationMillis = minimumDurationMillis;
    }

    public String id() {
        return id;
    }

    public int priority() {
        return priority;
    }

    /**
     * How long this state must stay on screen once entered. Transient poses such as takeoff and
     * hurt need a floor, otherwise a single tick of movement data can cut them off and the model
     * visibly snaps.
     */
    public long minimumDurationMillis() {
        return minimumDurationMillis;
    }

    public boolean airborne() {
        return this == TAKEOFF || this == FLY || this == WING_FLAP || this == LANDING;
    }
}
