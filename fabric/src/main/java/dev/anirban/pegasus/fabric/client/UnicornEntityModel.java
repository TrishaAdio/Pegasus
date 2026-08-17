/*
 * Pegasus Java Edition — Created by Anirban <3
 *
 * Original model geometry, authored for this project.
 */
package dev.anirban.pegasus.fabric.client;

import dev.anirban.pegasus.fabric.entity.UnicornEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.util.math.MathHelper;

/** Original Unicorn model: the same body proportions as the Pegasus, with a horn and no wings. */
public class UnicornEntityModel extends EntityModel<UnicornEntity> {
    private final ModelPart root;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart frontLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart backLeftLeg;
    private final ModelPart backRightLeg;
    private final ModelPart tail;

    public UnicornEntityModel(ModelPart root) {
        this.root = root;
        this.neck = root.getChild("neck");
        this.head = this.neck.getChild("head");
        this.frontLeftLeg = root.getChild("front_left_leg");
        this.frontRightLeg = root.getChild("front_right_leg");
        this.backLeftLeg = root.getChild("back_left_leg");
        this.backRightLeg = root.getChild("back_right_leg");
        this.tail = root.getChild("tail");
    }

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
                        .uv(22, 52).cuboid(-3.0f, -7.0f, -2.0f, 2.0f, 2.0f, 1.0f, new Dilation(0.0f))
                        .uv(30, 52).cuboid(1.0f, -7.0f, -2.0f, 2.0f, 2.0f, 1.0f, new Dilation(0.0f))
                        // The horn: the Unicorn's defining feature.
                        .uv(38, 52).cuboid(-0.5f, -11.0f, -5.0f, 1.0f, 6.0f, 1.0f, new Dilation(0.0f)),
                ModelTransform.of(0.0f, -12.0f, -1.0f, 0.4f, 0.0f, 0.0f));

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
    public void setAngles(UnicornEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        this.head.yaw = MathHelper.clamp(headYaw, -60.0f, 60.0f) * 0.017453292f;
        this.head.pitch = MathHelper.clamp(headPitch, -40.0f, 40.0f) * 0.017453292f + 0.4f;

        float swing = MathHelper.cos(limbAngle * 0.7f) * 1.2f * limbDistance;
        float swingOffset = MathHelper.cos(limbAngle * 0.7f + MathHelper.PI) * 1.2f * limbDistance;
        this.frontLeftLeg.pitch = swing;
        this.frontRightLeg.pitch = swingOffset;
        this.backLeftLeg.pitch = swingOffset;
        this.backRightLeg.pitch = swing;

        this.tail.pitch = 0.5f + MathHelper.sin(animationProgress * 0.1f) * 0.12f;
        this.tail.yaw = MathHelper.sin(animationProgress * 0.07f) * 0.15f;
    }

    @Override
    public void render(net.minecraft.client.util.math.MatrixStack matrices,
                       net.minecraft.client.render.VertexConsumer vertices,
                       int light, int overlay, int colour) {
        this.root.render(matrices, vertices, light, overlay, colour);
    }
}
