/*
 * Pegasus Java Edition — Created by Anirban <3
 *
 * Original model geometry, authored for this project.
 */
package dev.anirban.pegasus.fabric.client;

import dev.anirban.pegasus.common.animation.AnimationResolver;
import dev.anirban.pegasus.common.animation.AnimationState;
import dev.anirban.pegasus.fabric.entity.PegasusEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * Original winged-horse model with a full animation set.
 *
 * <p>Animation is driven by {@link AnimationResolver}'s shared state machine plus a continuous wing
 * phase. Every pose is computed from smooth trigonometric functions and blended toward its target
 * with {@link #approach}, which is what avoids the visible snapping that direct angle assignment
 * causes when a state changes mid-motion.
 */
public class PegasusEntityModel extends EntityModel<PegasusEntity> {
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart neck;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart leftWingTip;
    private final ModelPart rightWingTip;
    private final ModelPart frontLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart backLeftLeg;
    private final ModelPart backRightLeg;
    private final ModelPart tail;

    /** Smoothed wing angles so a state change eases in rather than jumping. */
    private float smoothedWingFlap;
    private float smoothedWingSpread;
    private float smoothedBodyPitch;

    public PegasusEntityModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = root.getChild("neck");
        this.head = this.neck.getChild("head");
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
        this.leftWingTip = this.leftWing.getChild("left_wing_tip");
        this.rightWingTip = this.rightWing.getChild("right_wing_tip");
        this.frontLeftLeg = root.getChild("front_left_leg");
        this.frontRightLeg = root.getChild("front_right_leg");
        this.backLeftLeg = root.getChild("back_left_leg");
        this.backRightLeg = root.getChild("back_right_leg");
        this.tail = root.getChild("tail");
    }

    /** Builds the original geometry. Texture is 128x64. */
    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        root.addChild("body",
                ModelPartBuilder.create().uv(0, 0).cuboid(-5.0f, -8.0f, -10.0f, 10.0f, 10.0f, 22.0f,
                        new Dilation(0.0f)),
                ModelTransform.pivot(0.0f, 11.0f, -1.0f));

        ModelPartData neck = root.addChild("neck",
                ModelPartBuilder.create().uv(0, 35).cuboid(-2.5f, -14.0f, -3.0f, 5.0f, 15.0f, 5.0f,
                        new Dilation(0.0f)),
                ModelTransform.of(0.0f, 4.0f, -9.0f, -0.6f, 0.0f, 0.0f));

        neck.addChild("head",
                ModelPartBuilder.create().uv(22, 35).cuboid(-3.0f, -5.0f, -8.0f, 6.0f, 6.0f, 10.0f,
                                new Dilation(0.0f))
                        // Ears
                        .uv(22, 52).cuboid(-3.0f, -7.0f, -2.0f, 2.0f, 2.0f, 1.0f, new Dilation(0.0f))
                        .uv(30, 52).cuboid(1.0f, -7.0f, -2.0f, 2.0f, 2.0f, 1.0f, new Dilation(0.0f)),
                ModelTransform.of(0.0f, -12.0f, -1.0f, 0.4f, 0.0f, 0.0f));

        // Wings are two-segment so the tip can trail the main wing for a natural beat.
        ModelPartData leftWing = root.addChild("left_wing",
                ModelPartBuilder.create().uv(58, 0).cuboid(0.0f, -1.0f, -6.0f, 18.0f, 2.0f, 14.0f,
                        new Dilation(0.0f)),
                ModelTransform.pivot(5.0f, 3.0f, -3.0f));
        leftWing.addChild("left_wing_tip",
                ModelPartBuilder.create().uv(58, 18).cuboid(0.0f, -1.0f, -5.0f, 16.0f, 1.0f, 12.0f,
                        new Dilation(0.0f)),
                ModelTransform.pivot(18.0f, 0.0f, 0.0f));

        ModelPartData rightWing = root.addChild("right_wing",
                ModelPartBuilder.create().uv(58, 0).mirrored()
                        .cuboid(-18.0f, -1.0f, -6.0f, 18.0f, 2.0f, 14.0f, new Dilation(0.0f)),
                ModelTransform.pivot(-5.0f, 3.0f, -3.0f));
        rightWing.addChild("right_wing_tip",
                ModelPartBuilder.create().uv(58, 18).mirrored()
                        .cuboid(-16.0f, -1.0f, -5.0f, 16.0f, 1.0f, 12.0f, new Dilation(0.0f)),
                ModelTransform.pivot(-18.0f, 0.0f, 0.0f));

        ModelPartBuilder leg = ModelPartBuilder.create().uv(0, 56)
                .cuboid(-1.5f, 0.0f, -1.5f, 3.0f, 12.0f, 3.0f, new Dilation(0.0f));
        root.addChild("front_left_leg", leg, ModelTransform.pivot(3.0f, 12.0f, -7.0f));
        root.addChild("front_right_leg", leg, ModelTransform.pivot(-3.0f, 12.0f, -7.0f));
        root.addChild("back_left_leg", leg, ModelTransform.pivot(3.5f, 12.0f, 8.0f));
        root.addChild("back_right_leg", leg, ModelTransform.pivot(-3.5f, 12.0f, 8.0f));

        root.addChild("tail",
                ModelPartBuilder.create().uv(44, 52).cuboid(-1.5f, 0.0f, 0.0f, 3.0f, 12.0f, 3.0f,
                        new Dilation(0.0f)),
                ModelTransform.of(0.0f, 4.0f, 11.0f, 0.5f, 0.0f, 0.0f));

        return TexturedModelData.of(modelData, 128, 64);
    }

    @Override
    public void setAngles(PegasusEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        AnimationState state = entity.animationState();
        long now = System.currentTimeMillis();
        float wingPhase = (float) AnimationResolver.wingPhase(now, state);
        float idleBreath = MathHelper.sin(animationProgress * 0.06f) * 0.04f;

        // Head follows the look direction, clamped so it cannot invert.
        this.head.yaw = MathHelper.clamp(headYaw, -60.0f, 60.0f) * 0.017453292f;
        this.head.pitch = MathHelper.clamp(headPitch, -40.0f, 40.0f) * 0.017453292f + 0.4f;

        float targetFlap;
        float targetSpread;
        float targetPitch;
        boolean legsTucked = false;

        switch (state) {
            case TAKEOFF, WING_FLAP -> {
                targetFlap = MathHelper.sin(wingPhase * MathHelper.TAU) * 1.05f;
                targetSpread = 0.15f;
                targetPitch = -0.25f;
                legsTucked = true;
            }
            case FLY -> {
                targetFlap = MathHelper.sin(wingPhase * MathHelper.TAU) * 0.55f;
                targetSpread = 0.05f;
                targetPitch = -0.08f;
                legsTucked = true;
            }
            case LANDING -> {
                targetFlap = MathHelper.sin(wingPhase * MathHelper.TAU) * 0.85f + 0.25f;
                targetSpread = 0.35f;
                targetPitch = 0.18f;
            }
            case DEATH -> {
                targetFlap = 0.9f;
                targetSpread = 0.6f;
                targetPitch = 0.6f;
            }
            case HURT -> {
                targetFlap = 0.4f;
                targetSpread = 0.3f;
                targetPitch = 0.12f;
            }
            case EAT -> {
                targetFlap = -0.05f;
                targetSpread = 0.0f;
                targetPitch = 0.0f;
                this.head.pitch = 0.95f; // Lower the muzzle toward the food.
            }
            case RUN -> {
                targetFlap = MathHelper.sin(wingPhase * MathHelper.TAU) * 0.18f - 0.05f;
                targetSpread = 0.0f;
                targetPitch = -0.05f;
            }
            default -> {
                // IDLE and WALK: wings folded against the body with a subtle breathing motion.
                targetFlap = -0.05f + idleBreath;
                targetSpread = 0.0f;
                targetPitch = 0.0f;
            }
        }

        // Ease toward the target so state changes blend instead of snapping.
        this.smoothedWingFlap = approach(this.smoothedWingFlap, targetFlap, 0.35f);
        this.smoothedWingSpread = approach(this.smoothedWingSpread, targetSpread, 0.3f);
        this.smoothedBodyPitch = approach(this.smoothedBodyPitch, targetPitch, 0.2f);

        this.leftWing.roll = -this.smoothedWingFlap;
        this.rightWing.roll = this.smoothedWingFlap;
        this.leftWing.yaw = -this.smoothedWingSpread;
        this.rightWing.yaw = this.smoothedWingSpread;
        // The tip lags the main wing, which reads as a flexible membrane rather than a rigid plank.
        this.leftWingTip.roll = -this.smoothedWingFlap * 0.55f;
        this.rightWingTip.roll = this.smoothedWingFlap * 0.55f;

        this.body.pitch = this.smoothedBodyPitch;
        this.neck.pitch = -0.6f + this.smoothedBodyPitch * 0.5f;

        if (state == AnimationState.DEATH) {
            this.root.roll = 1.35f;
        } else {
            this.root.roll = approach(this.root.roll, 0.0f, 0.25f);
        }

        // Gait: amplitude scales with movement so walk and run share one continuous cycle.
        float gait = state == AnimationState.RUN ? 1.5f : 1.0f;
        float swing = MathHelper.cos(limbAngle * 0.7f * gait) * 1.2f * limbDistance;
        float swingOffset = MathHelper.cos(limbAngle * 0.7f * gait + MathHelper.PI) * 1.2f * limbDistance;

        if (legsTucked) {
            // Tuck the legs while airborne instead of letting them keep walking in mid-air.
            this.frontLeftLeg.pitch = approach(this.frontLeftLeg.pitch, -0.9f, 0.2f);
            this.frontRightLeg.pitch = approach(this.frontRightLeg.pitch, -0.9f, 0.2f);
            this.backLeftLeg.pitch = approach(this.backLeftLeg.pitch, 0.7f, 0.2f);
            this.backRightLeg.pitch = approach(this.backRightLeg.pitch, 0.7f, 0.2f);
        } else {
            this.frontLeftLeg.pitch = swing;
            this.frontRightLeg.pitch = swingOffset;
            this.backLeftLeg.pitch = swingOffset;
            this.backRightLeg.pitch = swing;
        }

        this.tail.pitch = 0.5f + MathHelper.sin(animationProgress * 0.1f) * 0.12f;
        this.tail.yaw = MathHelper.sin(animationProgress * 0.07f) * 0.15f;
    }

    /** Frame-rate independent easing toward a target angle. */
    private static float approach(float current, float target, float rate) {
        return current + (target - current) * MathHelper.clamp(rate, 0.0f, 1.0f);
    }

    @Override
    public void render(net.minecraft.client.util.math.MatrixStack matrices,
                       net.minecraft.client.render.VertexConsumer vertices,
                       int light, int overlay, int colour) {
        this.root.render(matrices, vertices, light, overlay, colour);
    }
}
