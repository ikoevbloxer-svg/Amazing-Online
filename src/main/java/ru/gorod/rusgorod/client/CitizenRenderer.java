package ru.gorod.rusgorod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import ru.gorod.rusgorod.RusGorod;
import ru.gorod.rusgorod.entity.CitizenEntity;

import java.util.HashMap;
import java.util.Map;

/** Рендер NPC моделью игрока со скином по фракции (форма полиции, камуфляж, костюмы и т.д.). */
public class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, PlayerModel<CitizenEntity>> {
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    public CitizenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(CitizenEntity e) {
        String name = e.getFaction().texture(e.getVariant());
        return CACHE.computeIfAbsent(name, n -> new ResourceLocation(RusGorod.MODID, "textures/entity/" + n + ".png"));
    }
}
