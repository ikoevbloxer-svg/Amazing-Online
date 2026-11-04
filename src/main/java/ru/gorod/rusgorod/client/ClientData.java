package ru.gorod.rusgorod.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import ru.gorod.rusgorod.data.PlayerRecord;

import java.util.ArrayList;
import java.util.List;

/** Последнее состояние, присланное сервером. */
public final class ClientData {
    public static CompoundTag tag = new CompoundTag();
    public static PlayerRecord rec = new PlayerRecord();

    public static void update(CompoundTag t) {
        tag = t;
        rec = PlayerRecord.load(t.getCompound("rec"));
    }

    public static boolean has() {
        return tag.contains("rec");
    }

    public static List<String> strings(String key) {
        List<String> out = new ArrayList<>();
        ListTag l = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    public static List<String> unmet(String factionId) {
        List<String> out = new ArrayList<>();
        ListTag l = tag.getCompound("unmet").getList(factionId, Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    public static int count(String factionId) {
        return tag.getCompound("counts").getInt(factionId);
    }

    public static int chance(String factionId) {
        return tag.getCompound("chances").getInt(factionId);
    }

    private ClientData() {
    }
}
