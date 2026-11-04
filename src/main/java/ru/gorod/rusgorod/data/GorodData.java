package ru.gorod.rusgorod.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.law.CriminalCase;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Глобальные данные города (хранятся в сохранении мира overworld). */
public class GorodData extends SavedData {
    public static final String NAME = "rusgorod";

    public boolean generated;
    public int groundY;
    public int minX, minZ, maxX, maxZ;
    public int baseMinX, baseMinZ, baseMaxX, baseMaxZ;

    public final Map<String, BlockPos> locs = new HashMap<>();
    public final List<BlockPos> homes = new ArrayList<>();
    public final List<BlockPos> nodes = new ArrayList<>();
    public final List<BlockPos> sizoCells = new ArrayList<>();
    public final List<BlockPos> ivsCells = new ArrayList<>();
    public final List<BlockPos> barracks = new ArrayList<>();
    public final List<BlockPos> roadSpots = new ArrayList<>();

    public final Map<UUID, PlayerRecord> players = new HashMap<>();
    /** Реестр NPC: UUID → фракция. Нужен для подсчёта численности, в т.ч. в незагруженных чанках. */
    public final Map<UUID, Faction> npcs = new HashMap<>();
    public final List<CriminalCase> cases = new ArrayList<>();
    public int nextCaseId = 1;
    public final Map<Faction, Long> funds = new EnumMap<>(Faction.class);

    public long budget = 2_000_000;
    public double satisfaction = 65;
    public double crimeIndex = 10;
    public long rallyUntil = -1;
    public boolean rallyNotified;
    public long lastDay = -1;
    public BlockPos lastCrime = BlockPos.ZERO;
    public final LinkedList<String> news = new LinkedList<>();

    public static GorodData get(ServerLevel anyLevel) {
        ServerLevel ow = anyLevel.getServer().overworld();
        return ow.getDataStorage().computeIfAbsent(GorodData::load, GorodData::new, NAME);
    }

    public PlayerRecord player(UUID id) {
        PlayerRecord r = players.computeIfAbsent(id, k -> new PlayerRecord());
        setDirty();
        return r;
    }

    public BlockPos loc(String key) {
        return locs.getOrDefault(key, new BlockPos((minX + maxX) / 2, groundY + 1, (minZ + maxZ) / 2));
    }

    public BlockPos randomNode(RandomSource r) {
        if (nodes.isEmpty()) return loc("square");
        return nodes.get(r.nextInt(nodes.size()));
    }

    public BlockPos randomHome(RandomSource r) {
        if (homes.isEmpty()) return loc("square");
        return homes.get(r.nextInt(homes.size()));
    }

    public boolean inCity(BlockPos p) {
        return generated && p.getX() >= minX - 8 && p.getX() <= maxX + 8 && p.getZ() >= minZ - 8 && p.getZ() <= maxZ + 8;
    }

    public boolean inBase(BlockPos p) {
        return generated && p.getX() >= baseMinX && p.getX() <= baseMaxX && p.getZ() >= baseMinZ && p.getZ() <= baseMaxZ;
    }

    public int npcCount(Faction f) {
        int c = 0;
        for (Faction v : npcs.values()) if (v == f) c++;
        return c;
    }

    public void addFunds(Faction f, long amount) {
        funds.merge(f, amount, Long::sum);
        setDirty();
    }

    public long fundsOf(Faction f) {
        return funds.getOrDefault(f, 0L);
    }

    public void addNews(String s) {
        news.addFirst(s);
        while (news.size() > 12) news.removeLast();
        setDirty();
    }

    public CriminalCase findCase(int id) {
        for (CriminalCase c : cases) if (c.id == id) return c;
        return null;
    }

    public long rallyActiveUntil() {
        return rallyUntil;
    }

    // ------------------------------------------------------------------
    @Override
    public CompoundTag save(CompoundTag t) {
        t.putBoolean("generated", generated);
        t.putInt("groundY", groundY);
        t.putIntArray("bounds", new int[]{minX, minZ, maxX, maxZ, baseMinX, baseMinZ, baseMaxX, baseMaxZ});
        CompoundTag l = new CompoundTag();
        locs.forEach((k, v) -> l.put(k, NbtUtils.writeBlockPos(v)));
        t.put("locs", l);
        t.put("homes", posList(homes));
        t.put("nodes", posList(nodes));
        t.put("sizoCells", posList(sizoCells));
        t.put("ivsCells", posList(ivsCells));
        t.put("barracks", posList(barracks));
        t.put("roadSpots", posList(roadSpots));
        CompoundTag p = new CompoundTag();
        players.forEach((k, v) -> p.put(k.toString(), v.save()));
        t.put("players", p);
        CompoundTag n = new CompoundTag();
        npcs.forEach((k, v) -> n.putString(k.toString(), v.id));
        t.put("npcs", n);
        ListTag cs = new ListTag();
        for (CriminalCase c : cases) cs.add(c.save());
        t.put("cases", cs);
        t.putInt("nextCase", nextCaseId);
        CompoundTag f = new CompoundTag();
        funds.forEach((k, v) -> f.putLong(k.id, v));
        t.put("funds", f);
        t.putLong("budget", budget);
        t.putDouble("satisfaction", satisfaction);
        t.putDouble("crime", crimeIndex);
        t.putLong("rallyUntil", rallyUntil);
        t.putBoolean("rallyNotified", rallyNotified);
        t.putLong("lastDay", lastDay);
        t.put("lastCrime", NbtUtils.writeBlockPos(lastCrime));
        ListTag nw = new ListTag();
        for (String s : news) nw.add(StringTag.valueOf(s));
        t.put("news", nw);
        return t;
    }

    public static GorodData load(CompoundTag t) {
        GorodData d = new GorodData();
        d.generated = t.getBoolean("generated");
        d.groundY = t.getInt("groundY");
        int[] b = t.getIntArray("bounds");
        if (b.length == 8) {
            d.minX = b[0]; d.minZ = b[1]; d.maxX = b[2]; d.maxZ = b[3];
            d.baseMinX = b[4]; d.baseMinZ = b[5]; d.baseMaxX = b[6]; d.baseMaxZ = b[7];
        }
        CompoundTag l = t.getCompound("locs");
        for (String k : l.getAllKeys()) d.locs.put(k, NbtUtils.readBlockPos(l.getCompound(k)));
        readPosList(t, "homes", d.homes);
        readPosList(t, "nodes", d.nodes);
        readPosList(t, "sizoCells", d.sizoCells);
        readPosList(t, "ivsCells", d.ivsCells);
        readPosList(t, "barracks", d.barracks);
        readPosList(t, "roadSpots", d.roadSpots);
        CompoundTag p = t.getCompound("players");
        for (String k : p.getAllKeys()) {
            try {
                d.players.put(UUID.fromString(k), PlayerRecord.load(p.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag n = t.getCompound("npcs");
        for (String k : n.getAllKeys()) {
            try {
                d.npcs.put(UUID.fromString(k), Faction.byId(n.getString(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        ListTag cs = t.getList("cases", Tag.TAG_COMPOUND);
        for (int i = 0; i < cs.size(); i++) d.cases.add(CriminalCase.load(cs.getCompound(i)));
        d.nextCaseId = Math.max(1, t.getInt("nextCase"));
        CompoundTag f = t.getCompound("funds");
        for (String k : f.getAllKeys()) d.funds.put(Faction.byId(k), f.getLong(k));
        d.budget = t.getLong("budget");
        d.satisfaction = t.getDouble("satisfaction");
        d.crimeIndex = t.getDouble("crime");
        d.rallyUntil = t.getLong("rallyUntil");
        d.rallyNotified = t.getBoolean("rallyNotified");
        d.lastDay = t.getLong("lastDay");
        d.lastCrime = NbtUtils.readBlockPos(t.getCompound("lastCrime"));
        ListTag nw = t.getList("news", Tag.TAG_STRING);
        for (int i = 0; i < nw.size(); i++) d.news.add(nw.getString(i));
        return d;
    }

    private static ListTag posList(List<BlockPos> list) {
        ListTag l = new ListTag();
        for (BlockPos p : list) l.add(NbtUtils.writeBlockPos(p));
        return l;
    }

    private static void readPosList(CompoundTag t, String key, List<BlockPos> out) {
        ListTag l = t.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) out.add(NbtUtils.readBlockPos(l.getCompound(i)));
    }
}
