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
            .icon(() -> new ItemStack(ModItems.AK74.get()))
            .displayItems((params, out) -> {
                for (var ro : ModItems.ITEMS.getEntries()) out.accept(ro.get());
            })
            .build());
}
