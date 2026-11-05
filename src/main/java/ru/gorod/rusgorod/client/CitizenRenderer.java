package ru.gorod.rusgorod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import ru.gorod.rusgorod.entity.CitizenEntity;

/** Рендер NPC моделью игрока со скином по фракции/профессии, позами и погонами по званию. */
public class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, PlayerModel<CitizenEntity>> {

    public CitizenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CitizenModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
        this.addLayer(new InsigniaLayer<>(this, ctx.getModelSet(), ClientRanks::insigniaForNpc));
    }

    @Override
    public ResourceLocation getTextureLocation(CitizenEntity e) {
        return ClientRanks.rl("textures/entity/" + e.skinKey() + ".png");
    }
}
