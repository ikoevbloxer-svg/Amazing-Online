package ru.gorod.rusgorod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Погоны — объёмные пластины на плечах, привязанные к рукам модели.
 * Текстура 128×64 (масштаб 8×): верхняя грань 32×24 пикселя с просветами, звёздами, лычками.
 */
public class InsigniaLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private final ModelPart right;
    private final ModelPart left;
    private final Function<T, ResourceLocation> texture;

    public InsigniaLayer(RenderLayerParent<T, M> parent, EntityModelSet models, Function<T, ResourceLocation> texture) {
        super(parent);
        ModelPart root = models.bakeLayer(ClientLayers.EPAULETTE);
        this.right = root.getChild("right");
        this.left = root.getChild("left");
        this.texture = texture;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.0F, -2.75F, -1.5F, 4.0F, 0.75F, 3.0F), PartPose.ZERO);
        root.addOrReplaceChild("left", CubeListBuilder.create().texOffs(0, 0).mirror()
                .addBox(-1.0F, -2.75F, -1.5F, 4.0F, 0.75F, 3.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 8);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        ResourceLocation tex = texture.apply(entity);
        if (tex == null) return;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(tex));
        poseStack.pushPose();
        getParentModel().rightArm.translateAndRotate(poseStack);
        right.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        poseStack.pushPose();
        getParentModel().leftArm.translateAndRotate(poseStack);
        left.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
