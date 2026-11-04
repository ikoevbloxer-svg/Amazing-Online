package ru.gorod.rusgorod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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

    @Mod.EventBusSubscriber(modid = RusGorod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.CITIZEN.get(), CitizenRenderer::new);
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MENU);
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
        }
    }

    private ClientSetup() {
    }
}
