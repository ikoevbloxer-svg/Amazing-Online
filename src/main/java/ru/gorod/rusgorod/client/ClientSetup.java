package ru.gorod.rusgorod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ru.gorod.rusgorod.RusGorod;
import ru.gorod.rusgorod.net.FactionActionPacket;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.registry.ModEntities;

public final class ClientSetup {
    public static final KeyMapping OPEN_MENU = new KeyMapping("key.rusgorod.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.rusgorod");
    public static final KeyMapping OPEN_MAP = new KeyMapping("key.rusgorod.map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.rusgorod");
    public static final KeyMapping OPEN_PHONE = new KeyMapping("key.rusgorod.phone", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.rusgorod");
    public static final KeyMapping RELOAD = new KeyMapping("key.rusgorod.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.rusgorod");
    public static final KeyMapping MAP_ZOOM = new KeyMapping("key.rusgorod.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.rusgorod");

    @Mod.EventBusSubscriber(modid = RusGorod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.CITIZEN.get(), CitizenRenderer::new);
        }

        @SubscribeEvent
        public static void layerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(ClientLayers.EPAULETTE, InsigniaLayer::createLayer);
            event.registerLayerDefinition(ClientLayers.UNIFORM,
                    () -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.12F), false), 64, 64));
        }

        /** Форма и погоны на игроках. */
        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                Object r = event.getSkin(skin);
                if (r instanceof PlayerRenderer pr) {
                    pr.addLayer(new UniformLayer(pr, event.getEntityModels()));
                    pr.addLayer(new InsigniaLayer<>(pr, event.getEntityModels(), ClientRanks::insigniaForPlayer));
                }
            }
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MENU);
            event.register(OPEN_MAP);
            event.register(OPEN_PHONE);
            event.register(RELOAD);
            event.register(MAP_ZOOM);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("gorod_hud", HudOverlay::render);
        }
    }

    @Mod.EventBusSubscriber(modid = RusGorod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (OPEN_MENU.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    Network.sendToServer(new FactionActionPacket("sync", ""));
                    mc.setScreen(new FactionScreen());
                }
            }
            while (OPEN_MAP.consumeClick()) {
                if (mc.player != null && mc.screen == null) mc.setScreen(new MapScreen(null));
            }
            while (OPEN_PHONE.consumeClick()) {
                if (mc.player != null && mc.screen == null) PhoneScreen.open();
            }
            while (RELOAD.consumeClick()) {
                if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof ru.gorod.rusgorod.item.GunItem)
                    Network.sendToServer(new FactionActionPacket("reload", ""));
            }
            while (MAP_ZOOM.consumeClick()) {
                HudOverlay.zoom = (HudOverlay.zoom + 1) % HudOverlay.ZOOMS.length;
            }
            if (mc.player == null) return;
            GunClient.tick();
            HudOverlay.tick();
            try {
                CityMap.tick();
            } catch (Exception e) {
                ru.gorod.rusgorod.RusGorod.LOGGER.debug("[RusGorod] map tick: {}", e.toString());
            }
        }

        @SubscribeEvent
        public static void fov(net.minecraftforge.client.event.ComputeFovModifierEvent event) {
            event.setNewFovModifier(GunClient.fovModifier(event.getNewFovModifier()));
        }

        @SubscribeEvent
        public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            CityMap.save();
            CityMap.close();
            ClientData.wpSet = false;
            ClientData.tag = new net.minecraft.nbt.CompoundTag();
        }
    }

    private ClientSetup() {
    }
}
