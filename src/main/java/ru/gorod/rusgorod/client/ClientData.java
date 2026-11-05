package ru.gorod.rusgorod.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.world.Places;

import java.util.ArrayList;
import java.util.List;

/** Последнее состояние, присланное сервером, и клиентские метки. */
public final class ClientData {
    public static CompoundTag tag = new CompoundTag();
    public static PlayerRecord rec = new PlayerRecord();

    /** Метка навигатора (ставится на карте). */
    public static boolean wpSet;
    public static int wpX, wpZ;
    public static String wpName = "";

    public record Mark(String key, int x, int z, Places.Place place) {
    }

    private static List<Mark> marks = new ArrayList<>();

    public static void update(CompoundTag t) {
        CompoundTag old = tag;
        PlayerRecord oldRec = rec;
        tag = t;
        rec = PlayerRecord.load(t.getCompound("rec"));
        List<Mark> m = new ArrayList<>();
        ListTag l = t.getList("places", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) {
            String[] a = l.getString(i).split("\\|");
            if (a.length < 3) continue;
            Places.Place p = Places.get(a[0]);
            if (p == null) continue;
            try {
                m.add(new Mark(a[0], Integer.parseInt(a[1]), Integer.parseInt(a[2]), p));
            } catch (NumberFormatException ignored) {
            }
        }
        marks = m;
        if (old.contains("rec")) Toasts.onSync(old, oldRec, t, rec);
    }

    public static List<Mark> marks() {
        return marks;
    }

    public static Mark mark(String key) {
        for (Mark m : marks) if (m.key().equals(key)) return m;
        return null;
    }

    public static void setWaypoint(int x, int z, String name) {
        wpSet = true;
        wpX = x;
        wpZ = z;
        wpName = name;
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
