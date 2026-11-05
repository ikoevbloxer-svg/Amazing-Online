package ru.gorod.rusgorod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Форма ведомства поверх скина игрока (лицо и кисти рук остаются свои). */
public class UniformLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final PlayerModel<AbstractClientPlayer> model;

    public UniformLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.model = new PlayerModel<>(models.bakeLayer(ClientLayers.UNIFORM), false);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        ResourceLocation tex = ClientRanks.uniformFor(player);
        if (tex == null) return;
        getParentModel().copyPropertiesTo(model);
        model.setAllVisible(false);
        model.body.visible = true;
        model.rightArm.visible = true;
        model.leftArm.visible = true;
        model.rightLeg.visible = true;
        model.leftLeg.visible = true;
        model.hat.visible = true;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(tex));
        model.renderToBuffer(poseStack, vc, packedLight, LivingEntityRenderer.getOverlayCoords(player, 0.0F), 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
