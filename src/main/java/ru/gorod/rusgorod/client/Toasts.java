package ru.gorod.rusgorod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.registry.ModSounds;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Всплывающие уведомления: новости, деньги, розыск, смена района. */
public final class Toasts {
    private record Toast(String title, String text, int color, long born) {
    }

    private static final List<Toast> LIST = new ArrayList<>();
    public static String banner = "";
    public static String bannerSub = "";
    public static long bannerBorn;
    public static long moneyDelta;
    public static long moneyBorn;
    private static long now() {
        return net.minecraft.Util.getMillis();
    }

    public static void push(String title, String text, int color) {
        LIST.add(0, new Toast(title, text, color, now()));
        while (LIST.size() > 4) LIST.remove(LIST.size() - 1);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.level().playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), ModSounds.NOTIFY.get(), SoundSource.MASTER, 0.35f, 1f, false);
    }

    public static void onSync(CompoundTag old, PlayerRecord oldRec, CompoundTag t, PlayerRecord rec) {
        ListTag on = old.getList("news", Tag.TAG_STRING), nn = t.getList("news", Tag.TAG_STRING);
        if (nn.size() > 0 && (on.size() == 0 || !on.getString(on.size() - 1).equals(nn.getString(nn.size() - 1)))) {
            push("НОВОСТИ", nn.getString(nn.size() - 1), 0xFF4FA3FF);
        }
        long dm = rec.balance - oldRec.balance;
        if (dm != 0) {
            moneyDelta = (now() - moneyBorn < 2500 ? moneyDelta : 0) + dm;
            moneyBorn = now();
        }
        if (rec.wantedStars() > oldRec.wantedStars()) push("РОЗЫСК", "Уровень розыска: " + "★".repeat(rec.wantedStars()), 0xFFFF4040);
        if (rec.wantedStars() == 0 && oldRec.wantedStars() > 0) push("РОЗЫСК СНЯТ", "Вы больше не в розыске", 0xFF50D070);
        if (!rec.questId.equals(oldRec.questId) && !rec.questId.isEmpty()) push("ЗАДАНИЕ", "Новое задание получено", 0xFFFFC040);
        if (rec.rank != oldRec.rank && rec.faction == oldRec.faction && rec.faction != ru.gorod.rusgorod.faction.Faction.CIVILIAN)
            push(rec.rank > oldRec.rank ? "ПОВЫШЕНИЕ" : "ПОНИЖЕНИЕ", rec.faction.rankName(rec.rank), 0xFFFFD700);
        if (rec.faction != oldRec.faction) push("ФРАКЦИЯ", rec.faction.title, 0xFF000000 | rec.faction.color);
        String turf = t.getString("turf"), oldTurf = old.getString("turf");
        String district = district(turf), oldDistrict = district(oldTurf);
        if (!district.isEmpty() && !district.equals(oldDistrict)) {
            int i = turf.indexOf(" — ");
            showBanner(district, i > 0 ? (turf.startsWith("Точка") ? "Крыша: " : "Контроль: ") + turf.substring(i + 3).trim() : "");
        }
    }

    public static void showBanner(String title, String sub) {
        banner = title;
        bannerSub = sub;
        bannerBorn = now();
    }

    private static String district(String turf) {
        if (turf == null || turf.isEmpty()) return "";
        int i = turf.indexOf(" — ");
        String s = i > 0 ? turf.substring(0, i) : turf;
        return s.replace("Район:", "").replace("Точка:", "").trim();
    }

    public static void render(GuiGraphics g, Font font, int width, int height, int topY) {
        long t = now();
        int y = topY;
        Iterator<Toast> it = LIST.iterator();
        while (it.hasNext()) {
            Toast x = it.next();
            long age = t - x.born;
            if (age > 7000) {
                it.remove();
                continue;
            }
            float slide = age < 250 ? 1f - age / 250f : age > 6500 ? (age - 6500) / 500f : 0f;
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(net.minecraft.network.chat.Component.literal(x.text), 150);
            int bh = 14 + Math.min(3, lines.size()) * 9;
            int bw = 162;
            int bx = width - bw - 6 + (int) (slide * (bw + 10));
            g.fill(bx, y, bx + bw, y + bh, 0xD0101418);
            g.fill(bx, y, bx + 2, y + bh, x.color);
            g.fill(bx + 2, y, bx + bw, y + 1, (x.color & 0x00FFFFFF) | 0x60000000);
            g.drawString(font, x.title, bx + 7, y + 3, x.color, false);
            for (int i = 0; i < Math.min(3, lines.size()); i++) g.drawString(font, lines.get(i), bx + 7, y + 13 + i * 9, 0xFFE6E6E6, false);
            y += bh + 3;
        }
        // баннер района
        long ba = t - bannerBorn;
        if (!banner.isEmpty() && ba < 4200) {
            float a = ba < 400 ? ba / 400f : ba > 3400 ? 1f - (ba - 3400) / 800f : 1f;
            int alpha = (int) (Mth.clamp(a, 0f, 1f) * 255);
            if (alpha > 8) {
                g.pose().pushPose();
                g.pose().translate(width / 2f, height * 0.22f, 0);
                g.pose().scale(2f, 2f, 1f);
                String s = banner.toUpperCase();
                int sw = font.width(s);
                g.drawString(font, s, -sw / 2, 0, (alpha << 24) | 0xF2F2F2, true);
                g.pose().popPose();
                int lw = (int) (Math.min(1f, ba / 600f) * 110);
                g.fill(width / 2 - lw, (int) (height * 0.22f) + 20, width / 2 + lw, (int) (height * 0.22f) + 21, (alpha << 24) | 0xE0B040);
                if (!bannerSub.isEmpty())
                    g.drawCenteredString(font, bannerSub, width / 2, (int) (height * 0.22f) + 25, (alpha << 24) | 0xC8C8C8);
            }
        }
    }

    private Toasts() {
    }
}
