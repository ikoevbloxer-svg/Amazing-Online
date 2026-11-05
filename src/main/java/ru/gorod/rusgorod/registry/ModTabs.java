package ru.gorod.rusgorod.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ru.gorod.rusgorod.RusGorod;

public class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RusGorod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.literal("РусГород"))
            .icon(() -> new ItemStack(ModItems.ID_CARD.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.ID_CARD.get());
                out.accept(ModItems.BATON.get());
                out.accept(ModItems.CUFFS.get());
                out.accept(ModItems.DIPLOMA.get());
                out.accept(ModItems.PM.get());
                out.accept(ModItems.AK74.get());
                out.accept(ModItems.AMMO_9MM.get());
                out.accept(ModItems.AMMO_545.get());
                out.accept(ModItems.LETTER.get());
                out.accept(ModItems.CARGO_BOX.get());
                out.accept(ModItems.LITTER.get());
                out.accept(ModItems.CITIZEN_SPAWN_EGG.get());
            })
            .build());
}
