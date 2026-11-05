package ru.gorod.rusgorod.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import ru.gorod.rusgorod.RusGorod;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Фракции/звания игроков на клиенте (для формы и погон) и кэш текстур. */
public final class ClientRanks {
    public record Entry(Faction faction, int rank, boolean uniform) {
    }

    private static final Map<UUID, Entry> PLAYERS = new HashMap<>();
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    public static void set(Map<UUID, Entry> map) {
        PLAYERS.clear();
        PLAYERS.putAll(map);
    }

    public static Entry get(UUID id) {
        return PLAYERS.get(id);
    }

    public static ResourceLocation rl(String path) {
        return CACHE.computeIfAbsent(path, p -> new ResourceLocation(RusGorod.MODID, p));
    }

    /** Текстура погон для звания (null — не носят). */
    public static ResourceLocation insignia(Faction f, int rank) {
        if (!f.insignia) return null;
        return rl("textures/entity/insignia/" + f.id + "_" + Math.max(0, Math.min(rank, f.topRank())) + ".png");
    }

    public static ResourceLocation insigniaForPlayer(Player p) {
        Entry e = PLAYERS.get(p.getUUID());
        if (e == null || !e.uniform()) return null;
        return insignia(e.faction(), e.rank());
    }

    public static ResourceLocation uniformFor(Player p) {
        Entry e = PLAYERS.get(p.getUUID());
        if (e == null || !e.uniform() || e.faction() == Faction.CIVILIAN) return null;
        return rl("textures/entity/uniform/" + e.faction().id + ".png");
    }

    public static ResourceLocation insigniaForNpc(CitizenEntity c) {
        return insignia(c.getFaction(), c.getSyncedRank());
    }

    private ClientRanks() {
    }
}
