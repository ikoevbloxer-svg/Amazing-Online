package ru.gorod.rusgorod.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.gorod.rusgorod.RusGorod;
import ru.gorod.rusgorod.item.GunItem;
import ru.gorod.rusgorod.item.IdCardItem;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RusGorod.MODID);

    /** Палка резиновая ПР-73 — специальное средство (ст. 21 ФЗ «О полиции»). */
    public static final RegistryObject<Item> BATON = ITEMS.register("baton",
            () -> new SwordItem(Tiers.WOOD, 1, -2.0f, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> AMMO_9MM = ITEMS.register("ammo_9mm", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> AMMO_545 = ITEMS.register("ammo_545", () -> new Item(new Item.Properties()));

    /** Пистолет Макарова — табельное оружие (ст. 23 ФЗ «О полиции»). */
    public static final RegistryObject<Item> PM = ITEMS.register("pm",
            () -> new GunItem(new Item.Properties().stacksTo(1), 5.0f, 12, 40.0, AMMO_9MM));
    /** АК-74М — штатное оружие мотострелка. */
    public static final RegistryObject<Item> AK74 = ITEMS.register("ak74",
            () -> new GunItem(new Item.Properties().stacksTo(1), 6.0f, 4, 70.0, AMMO_545));

    public static final RegistryObject<Item> ID_CARD = ITEMS.register("id_card",
            () -> new IdCardItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> LETTER = ITEMS.register("letter", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> CARGO_BOX = ITEMS.register("cargo_box", () -> new Item(new Item.Properties().stacksTo(4)));

    public static final RegistryObject<Item> LITTER = ITEMS.register("litter",
            () -> new BlockItem(ModBlocks.LITTER.get(), new Item.Properties()));

    public static final RegistryObject<Item> CITIZEN_SPAWN_EGG = ITEMS.register("citizen_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.CITIZEN, 0x3B5BA5, 0xD52B1E, new Item.Properties()));
}
