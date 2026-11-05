package ru.gorod.rusgorod.registry;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.gorod.rusgorod.RusGorod;
import ru.gorod.rusgorod.item.BronikMaterial;
import ru.gorod.rusgorod.item.CuffsItem;
import ru.gorod.rusgorod.item.GunItem;
import ru.gorod.rusgorod.item.GunItem.Kind;
import ru.gorod.rusgorod.item.IdCardItem;
import ru.gorod.rusgorod.item.LoreItem;
import ru.gorod.rusgorod.item.PhoneItem;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RusGorod.MODID);

    /** Палка резиновая ПР-73 — специальное средство (ст. 21 ФЗ «О полиции»). */
    public static final RegistryObject<Item> BATON = ITEMS.register("baton",
            () -> new SwordItem(Tiers.WOOD, 1, -2.0f, new Item.Properties().stacksTo(1)));
    /** Бейсбольная бита — не оружие, пока ей не ударили (ст. 213 УК — «с применением предметов, используемых в качестве оружия»). */
    public static final RegistryObject<Item> BAT = ITEMS.register("bat",
            () -> new SwordItem(Tiers.WOOD, 3, -2.6f, new Item.Properties().stacksTo(1).durability(180)));
    /** Финка — холодное оружие (ст. 222 ч. 4 УК — сбыт). */
    public static final RegistryObject<Item> KNIFE = ITEMS.register("knife",
            () -> new SwordItem(Tiers.IRON, 2, -1.6f, new Item.Properties().stacksTo(1)));
    /** Кастет — запрещён к обороту (ст. 6 ФЗ «Об оружии»). */
    public static final RegistryObject<Item> KASTET = ITEMS.register("kastet",
            () -> new SwordItem(Tiers.IRON, 1, -1.2f, new Item.Properties().stacksTo(1)));

    // ------------------------------------------------------------------ патроны
    public static final RegistryObject<Item> AMMO_9MM = ITEMS.register("ammo_9mm", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> AMMO_9X19 = ITEMS.register("ammo_9x19", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> AMMO_762TT = ITEMS.register("ammo_762tt", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> AMMO_545 = ITEMS.register("ammo_545", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> AMMO_12GA = ITEMS.register("ammo_12ga", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> AMMO_762X54 = ITEMS.register("ammo_762x54", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> AMMO_RUBBER = ITEMS.register("ammo_rubber", () -> new Item(new Item.Properties()));

    // ------------------------------------------------------------------ огнестрел
    //                                              вид           урон  задержка дальн. магазин перезар. разброс дробь авто отдача зум
    /** Пистолет Макарова — табельное оружие (ст. 23 ФЗ «О полиции»). */
    public static final RegistryObject<Item> PM = ITEMS.register("pm", () -> new GunItem(GunItem.spec(Kind.PISTOL, "ПМ", 6.0f, 6, 45, 8, 30, 2.2f, 1, false, 4.5f, 1.15f,
            ModSounds.PISTOL, AMMO_9MM, false, "9×18 мм ПМ")));
    public static final RegistryObject<Item> GRACH = ITEMS.register("grach", () -> new GunItem(GunItem.spec(Kind.PISTOL, "МР-443 «Грач»", 7.0f, 5, 50, 17, 34, 1.8f, 1, false, 4.0f, 1.15f,
            ModSounds.PISTOL, AMMO_9X19, false, "9×19 мм")));
    public static final RegistryObject<Item> TT = ITEMS.register("tt", () -> new GunItem(GunItem.spec(Kind.PISTOL, "ТТ", 7.5f, 7, 50, 8, 32, 2.0f, 1, false, 6.0f, 1.15f,
            ModSounds.PISTOL_HEAVY, AMMO_762TT, false, "7,62×25 мм ТТ")));
    public static final RegistryObject<Item> OSA = ITEMS.register("osa", () -> new GunItem(GunItem.spec(Kind.TRAUMA, "ПБ-4-2 «Оса»", 3.0f, 8, 18, 4, 40, 3.5f, 1, false, 7.0f, 1.1f,
            ModSounds.TRAUMA, AMMO_RUBBER, true, "18×45 мм травм.")));
    public static final RegistryObject<Item> AKS74U = ITEMS.register("aks74u", () -> new GunItem(GunItem.spec(Kind.SMG, "АКС-74У", 6.0f, 3, 55, 30, 44, 3.0f, 1, true, 2.4f, 1.3f,
            ModSounds.SMG, AMMO_545, false, "5,45×39 мм")));
    /** АК-74М — штатное оружие мотострелка. */
    public static final RegistryObject<Item> AK74 = ITEMS.register("ak74", () -> new GunItem(GunItem.spec(Kind.RIFLE, "АК-74М", 7.0f, 3, 80, 30, 50, 2.0f, 1, true, 2.2f, 1.45f,
            ModSounds.RIFLE, AMMO_545, false, "5,45×39 мм")));
    public static final RegistryObject<Item> SAIGA12 = ITEMS.register("saiga12", () -> new GunItem(GunItem.spec(Kind.SHOTGUN, "Сайга-12", 3.2f, 9, 30, 8, 50, 6.5f, 8, false, 8.0f, 1.2f,
            ModSounds.SHOTGUN, AMMO_12GA, true, "12/76")));
    public static final RegistryObject<Item> IZH43 = ITEMS.register("izh43", () -> new GunItem(GunItem.spec(Kind.SHOTGUN, "Обрез ИЖ-43", 3.6f, 6, 20, 2, 44, 10.0f, 9, false, 11.0f, 1.05f,
            ModSounds.SHOTGUN, AMMO_12GA, false, "12/70")));
    public static final RegistryObject<Item> SVD = ITEMS.register("svd", () -> new GunItem(GunItem.spec(Kind.SNIPER, "СВД", 18.0f, 22, 160, 10, 60, 0.15f, 1, false, 9.0f, 4.0f,
            ModSounds.SNIPER, AMMO_762X54, false, "7,62×54R")));

    /** Бронежилет 6Б23 (класс защиты Бр2–Бр4). */
    public static final RegistryObject<Item> BRONIK = ITEMS.register("bronik",
            () -> new ArmorItem(BronikMaterial.INSTANCE, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    /** Смартфон: карта, банк, «Госуслуги», такси, 112, контакты, магазин. */
    public static final RegistryObject<Item> PHONE = ITEMS.register("phone", () -> new PhoneItem(new Item.Properties().stacksTo(1)));
    /** Лицензия ЛОА (ст. 9, 13 ФЗ «Об оружии»). */
    public static final RegistryObject<Item> GUN_LICENSE = ITEMS.register("gun_license",
            () -> new LoreItem(new Item.Properties().stacksTo(1), "Лицензия на приобретение, хранение и ношение",
                    "гладкоствольного и травматического оружия (ЛОА), ст. 13 ФЗ-150"));

    public static final RegistryObject<Item> ID_CARD = ITEMS.register("id_card",
            () -> new IdCardItem(new Item.Properties().stacksTo(1)));
    /** Наручники БР-С2. */
    public static final RegistryObject<Item> CUFFS = ITEMS.register("cuffs", () -> new CuffsItem(new Item.Properties().stacksTo(1)));
    /** Диплом бакалавра юриспруденции. */
    public static final RegistryObject<Item> DIPLOMA = ITEMS.register("diploma", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> LETTER = ITEMS.register("letter", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> CARGO_BOX = ITEMS.register("cargo_box", () -> new Item(new Item.Properties().stacksTo(4)));

    public static final RegistryObject<Item> LITTER = ITEMS.register("litter",
            () -> new BlockItem(ModBlocks.LITTER.get(), new Item.Properties()));

    public static final RegistryObject<Item> CITIZEN_SPAWN_EGG = ITEMS.register("citizen_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.CITIZEN, 0x3B5BA5, 0xD52B1E, new Item.Properties()));
}
