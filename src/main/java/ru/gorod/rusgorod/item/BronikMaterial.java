package ru.gorod.rusgorod.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Бронежилет 6Б23: защищает корпус, особенно от пуль (см. GunItem — снижение урона по корпусу). */
public final class BronikMaterial implements ArmorMaterial {
    public static final BronikMaterial INSTANCE = new BronikMaterial();

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return 420;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return type == ArmorItem.Type.CHESTPLATE ? 7 : 2;
    }

    @Override
    public int getEnchantmentValue() {
        return 5;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.IRON_INGOT);
    }

    @Override
    public String getName() {
        return "rusgorod:bronik";
    }

    @Override
    public float getToughness() {
        return 1.5f;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.05f;
    }

    private BronikMaterial() {
    }
}
