package ru.gorod.rusgorod;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import ru.gorod.rusgorod.event.ServerEvents;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.registry.ModBlocks;
import ru.gorod.rusgorod.registry.ModEntities;
import ru.gorod.rusgorod.registry.ModItems;
import ru.gorod.rusgorod.registry.ModTabs;

/**
 * RusGorod — город в стиле Восточной Европы с фракциями, построенными
 * на реальной вертикали власти РФ, живыми NPC, квестами и работами.
 */
@Mod(RusGorod.MODID)
public class RusGorod {
    public static final String MODID = "rusgorod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RusGorod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ru.gorod.rusgorod.registry.ModSounds.SOUNDS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModTabs.TABS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(ModEntities::onAttributes);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        MinecraftForge.EVENT_BUS.register(new ServerEvents());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(Network::register);
        LOGGER.info("[RusGorod] Город готов к застройке.");
    }
}
