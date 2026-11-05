package ru.gorod.rusgorod.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.gorod.rusgorod.RusGorod;

/** Звуки оружия и интерфейса (синтезированы под мод). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RusGorod.MODID);

    public static final RegistryObject<SoundEvent> PISTOL = reg("gun.pistol");
    public static final RegistryObject<SoundEvent> PISTOL_HEAVY = reg("gun.pistol_heavy");
    public static final RegistryObject<SoundEvent> RIFLE = reg("gun.rifle");
    public static final RegistryObject<SoundEvent> SMG = reg("gun.smg");
    public static final RegistryObject<SoundEvent> SHOTGUN = reg("gun.shotgun");
    public static final RegistryObject<SoundEvent> SNIPER = reg("gun.sniper");
    public static final RegistryObject<SoundEvent> TRAUMA = reg("gun.trauma");
    public static final RegistryObject<SoundEvent> RELOAD = reg("gun.reload");
    public static final RegistryObject<SoundEvent> EMPTY = reg("gun.empty");
    public static final RegistryObject<SoundEvent> WHIZ = reg("gun.whiz");
    public static final RegistryObject<SoundEvent> HIT = reg("ui.hit");
    public static final RegistryObject<SoundEvent> HEADSHOT = reg("ui.headshot");
    public static final RegistryObject<SoundEvent> NOTIFY = reg("ui.notify");
    public static final RegistryObject<SoundEvent> PHONE = reg("ui.phone");
    public static final RegistryObject<SoundEvent> SIREN = reg("ui.siren");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(RusGorod.MODID, name)));
    }

    private ModSounds() {
    }
}
