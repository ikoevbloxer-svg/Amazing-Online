package ru.gorod.rusgorod.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import ru.gorod.rusgorod.RusGorod;

public final class ClientLayers {
    public static final ModelLayerLocation EPAULETTE = new ModelLayerLocation(new ResourceLocation(RusGorod.MODID, "epaulette"), "main");
    public static final ModelLayerLocation UNIFORM = new ModelLayerLocation(new ResourceLocation(RusGorod.MODID, "uniform"), "main");

    private ClientLayers() {
    }
}
