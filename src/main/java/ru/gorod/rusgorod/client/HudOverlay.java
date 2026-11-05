package ru.gorod.rusgorod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.entity.Profession;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.item.GunItem;
import ru.gorod.rusgorod.quest.Quest;
import ru.gorod.rusgorod.quest.QuestRegistry;
import ru.gorod.rusgorod.social.Relations;
import ru.gorod.rusgorod.study.LawSchool;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD 3.0: карточка игрока (фракция, звание, погон, деньги с дельтой, розыск звёздами, репутация),
 * компас-лента с метками, вращающаяся мини-карта с местами/NPC/районами, часы и район, панель целей и навигатора,
 * панель оружия (патроны, перезарядка, режим огня), маркер попадания, оптический прицел, уведомления,
 * баннер района, карточка NPC под прицелом.
 */
public final class HudOverlay {
    public static final float[] ZOOMS = {0.55f, 1.0f, 1.7f};
    public static int zoom = 1;
    private static String lastPlace = "";

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.options.renderDebug) return;
        Font font = mc.font;
        if (GunClient.scoped()) scope(g, width, height);
        hitMarker(g, width, height);
        if (!ClientData.has()) return;
        LocalPlayer pl = mc.player;
        int mapBottom = minimap(g, font, mc, pl, pt, width);
        int objBottom = objectives(g, font, pl, width, mapBottom + 4);
        compass(g, font, pl, pt, width);
        card(g, font, width);
        weapon(g, font, pl, width, height);
        lookAt(g, font, mc, width, height);
        Toasts.render(g, font, width, height, objBottom + 4);
    }

    // ------------------------------------------------------------------ карточка игрока
    private static void card(GuiGraphics g, Font font, int width) {
        PlayerRecord r = ClientData.rec;
        Faction f = r.faction;
        long time = ClientData.tag.getLong("time");
        String head = f == Faction.CIVILIAN ? "ГРАЖДАНИН РФ" : f.title.toUpperCase();
        String sub = f == Faction.CIVILIAN ? (r.job.isEmpty() ? "без работы" : jobTitle(r.job))
                : f.rankName(r.rank) + (f.isState() && !r.uniform ? " · в гражданском" : "");
        String pos = f == Faction.CIVILIAN ? "" : f.positionName(r.rank);
        boolean ep = f.insignia && r.uniform;
        int w = Math.max(132, Math.max(font.width(head), Math.max(font.width(sub), font.width(pos))) + (ep ? 34 : 14) + 12);
        int x = 6, y = 6;
        List<String> chips = new ArrayList<>();
        if (r.cuffed) chips.add("§c⛓ наручники");
        if (r.jailUntil > time) chips.add("§6" + (r.jailType == 2 ? "СИЗО " : "ИВС ") + (r.jailUntil - time) / 20 + "с");
        if (r.fineDebt > 0) chips.add("§eштраф " + r.fineDebt + "₽");
        if (r.gunLicense) chips.add("§aЛОА");
        if (r.legalEducation) chips.add("§bюрист");
        int h = 64 + (pos.isEmpty() ? 0 : 9) + (chips.isEmpty() ? 0 : 11);
        g.fill(x, y, x + w, y + h, Ui.GLASS);
        g.fill(x, y, x + 3, y + h, 0xFF000000 | f.color);
        g.fillGradient(x + 3, y, x + w, y + 22, (f.color & 0xFFFFFF) | 0x50000000, 0x00000000);
        // эмблема
        g.fill(x + 7, y + 4, x + 21, y + 18, 0xFF000000 | f.color);
        g.fill(x + 7, y + 4, x + 21, y + 5, 0x60FFFFFF);
        String em = emblem(f);
        g.pose().pushPose();
        g.pose().translate(x + 14, y + 7.5f, 0);
        float es = em.length() > 1 ? 0.7f : 1f;
        g.pose().scale(es, es, 1);
        g.drawCenteredString(font, em, 0, 0, 0xFFFFFFFF);
        g.pose().popPose();
        g.drawString(font, head, x + 25, y + 4, 0xFF000000 | brighten(f.color), true);
        g.drawString(font, sub, x + 25, y + 13, 0xFFE0E0E0, false);
        int ly = y + 24;
        if (!pos.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x + 25, ly - 1, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, pos, 0, 0, Ui.MUTED, false);
            g.pose().popPose();
            ly += 9;
        }
        if (ep) {
            ResourceLocation tex = ClientRanks.insignia(f, r.rank);
            if (tex != null) g.blit(tex, x + w - 30, y + 4, 24, 18, 24f, 0f, 32, 24, 128, 64);
        }
        // деньги
        g.fill(x + 6, ly, x + w - 6, ly + 1, Ui.LINE);
        ly += 4;
        g.pose().pushPose();
        g.pose().translate(x + 8, ly, 0);
        g.pose().scale(1.35f, 1.35f, 1);
        g.drawString(font, Ui.money(r.balance), 0, 0, Ui.GOLD, true);
        g.pose().popPose();
        long age = net.minecraft.Util.getMillis() - Toasts.moneyBorn;
        if (Toasts.moneyDelta != 0 && age < 2600) {
            int a = (int) (255 * Mth.clamp(1f - age / 2600f, 0f, 1f));
            String d = (Toasts.moneyDelta > 0 ? "+" : "") + Ui.money(Toasts.moneyDelta);
            int col = Toasts.moneyDelta > 0 ? 0x4CD07A : 0xFF5050;
            if (a > 8) g.drawString(font, d, x + w - 8 - font.width(d), ly + 2 - (int) (age / 300), (a << 24) | col, true);
        }
        ly += 15;
        // звёзды розыска
        int stars = r.wantedStars();
        boolean blink = stars > 0 && (System.currentTimeMillis() / 400) % 2 == 0;
        for (int i = 0; i < 5; i++) {
            boolean on = i < stars;
            int c = on ? (blink ? 0xFFFFFFFF : 0xFFFF4040) : 0x50FFFFFF;
            g.drawString(font, "★", x + 8 + i * 10, ly, c, on);
        }
        if (stars > 0) g.drawString(font, "РОЗЫСК", x + 62, ly, blink ? 0xFFFF4040 : 0xFFFFFFFF, true);
        else g.drawString(font, "чист перед законом", x + 62, ly, Ui.MUTED, false);
        ly += 11;
        // репутация
        g.pose().pushPose();
        g.pose().translate(x + 8, ly, 0);
        g.pose().scale(0.7f, 0.7f, 1);
        g.drawString(font, "Репутация", 0, 0, Ui.MUTED, false);
        g.pose().popPose();
        int bw = (w - 60) / 2;
        Ui.bar(g, x + 44, ly + 1, bw, 3, Math.min(1f, r.civRep / 100f), 0xFF4FA3FF);
        Ui.bar(g, x + 48 + bw, ly + 1, bw, 3, Math.min(1f, r.crimRep / 100f), 0xFFE04848);
        ly += 7;
        if (!chips.isEmpty()) {
            int cx = x + 8;
            for (String c : chips) {
                int cw = font.width(c.substring(2)) + 6;
                if (cx + cw > x + w - 4) break;
                g.fill(cx, ly + 1, cx + cw, ly + 11, 0x60000000);
                g.drawString(font, c, cx + 3, ly + 2, 0xFFFFFFFF, false);
                cx += cw + 3;
            }
        }
    }

    private static String emblem(Faction f) {
        return switch (f) {
            case CIVILIAN -> "РФ";
            case RUS_MAFIA -> "РМ";
            case KAVKAZ_MAFIA -> "КМ";
            case SKINHEADS -> "СК";
            case BIKERS -> "МК";
            case ARMY -> "ВС";
            case UFSIN -> "ФСИН";
            case UGIBDD -> "ГАИ";
            case UMVD -> "МВД";
            case SK -> "СК";
            case PROK -> "ПР";
            case UFSB -> "ФСБ";
            case GOV -> "АДМ";
            case COURT -> "СУД";
        };
    }

    // ------------------------------------------------------------------ мини-карта
    private static int minimap(GuiGraphics g, Font font, Minecraft mc, LocalPlayer pl, float pt, int width) {
        int S = 96;
        int x = width - S - 6, y = 6;
        double px = Mth.lerp(pt, pl.xo, pl.getX()), pz = Mth.lerp(pt, pl.zo, pl.getZ());
        float yaw = pl.getViewYRot(pt);
        float theta = 180f - yaw;
        double rad = Math.toRadians(theta);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        float sc = 1f / ZOOMS[zoom];
        float cx = x + S / 2f, cy = y + S / 2f;
        g.fill(x - 2, y - 2, x + S + 2, y + S + 2, 0xFF000000);
        g.fill(x - 1, y - 1, x + S + 1, y + S + 1, 0xFF2A3038);
        g.fill(x, y, x + S, y + S, 0xFF0B0F14);
        g.enableScissor(x, y, x + S, y + S);
        if (CityMap.ready()) {
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(theta));
            g.pose().scale(sc, sc, 1f);
            g.pose().translate((float) -(px - CityMap.x0), (float) -(pz - CityMap.z0), 0);
            g.blit(CityMap.TEX, 0, 0, 0, 0, CityMap.w, CityMap.h, CityMap.w, CityMap.h);
            g.pose().popPose();
        } else {
            g.drawCenteredString(font, "НЕТ СИГНАЛА", (int) cx, (int) cy - 4, 0xFF606A74);
        }
        // районы (точки захвата)
        for (String s : ClientData.strings("turfs")) {
            String[] a = s.split("\\|");
            if (a.length < 9) continue;
            float[] p = rot(Integer.parseInt(a[6]) + 0.5 - px, Integer.parseInt(a[7]) + 0.5 - pz, cos, sin, sc);
            Faction owner = a[3].isEmpty() ? null : Faction.byId(a[3]);
            int col = owner == null ? 0x90A0A0A0 : 0xB0000000 | owner.color;
            if (!a[4].isEmpty() && (System.currentTimeMillis() / 300) % 2 == 0) col = 0xFFFF3030;
            Ui.ring(g, cx + p[0], cy + p[1], 12 * sc, col);
        }
        // сущности
        boolean wanted = ClientData.rec.wantedStars() > 0;
        boolean blink = (System.currentTimeMillis() / 250) % 2 == 0;
        float range = S / 2f / sc + 4;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == pl) continue;
            double dx = e.getX() - px, dz = e.getZ() - pz;
            if (Math.abs(dx) > range * 1.5 || Math.abs(dz) > range * 1.5) continue;
            float[] p = rot(dx, dz, cos, sin, sc);
            int ex = (int) (cx + p[0]), ey = (int) (cy + p[1]);
            if (e instanceof CitizenEntity c) {
                int col = 0xFF000000 | c.getFaction().color;
                if (wanted && c.getFaction().canArrest()) col = blink ? 0xFFFF2020 : 0xFF2050FF;
                g.fill(ex - 1, ey - 1, ex + 2, ey + 2, 0xFF000000);
                g.fill(ex, ey, ex + 1, ey + 1, col);
                g.fill(ex - 1, ey, ex + 2, ey + 1, col);
                g.fill(ex, ey - 1, ex + 1, ey + 2, col);
            } else if (e instanceof Player) {
                g.fill(ex - 2, ey - 2, ex + 2, ey + 2, 0xFF000000);
                g.fill(ex - 1, ey - 1, ex + 1, ey + 1, 0xFFFFFFFF);
            } else if (e instanceof Enemy) {
                g.fill(ex - 1, ey - 1, ex + 1, ey + 1, 0xFFFF3030);
            }
        }
        g.disableScissor();
        // места (прижаты к краю, если далеко)
        float half = S / 2f - 5;
        for (ClientData.Mark m : ClientData.marks()) {
            float[] p = rot(m.x() + 0.5 - px, m.z() + 0.5 - pz, cos, sin, sc);
            boolean edge = Math.abs(p[0]) > half || Math.abs(p[1]) > half;
            if (edge && !(ClientData.wpSet && m.x() == ClientData.wpX && m.z() == ClientData.wpZ)) continue;
            float k = edge ? half / Math.max(Math.abs(p[0]), Math.abs(p[1])) : 1f;
            Ui.marker(g, font, cx + p[0] * k, cy + p[1] * k, m.place().icon(), m.place().cat().color, 0.62f, false);
        }
        for (BlockPos q : ClientData.rec.questTargets) edgeMarker(g, font, q.getX() + 0.5 - px, q.getZ() + 0.5 - pz, cos, sin, sc, cx, cy, half, "!", 0xFFC830);
        if (ClientData.wpSet) edgeMarker(g, font, ClientData.wpX + 0.5 - px, ClientData.wpZ + 0.5 - pz, cos, sin, sc, cx, cy, half, "⚑", 0xE03030);
        // север
        float[] n = rot(0, -1000, cos, sin, 1);
        float nk = (S / 2f - 1) / Math.max(Math.abs(n[0]), Math.abs(n[1]));
        int nx = (int) (cx + n[0] * nk), ny = (int) (cy + n[1] * nk);
        g.fill(nx - 4, ny - 4, nx + 5, ny + 5, 0xFFB02020);
        g.drawCenteredString(font, "С", nx + 1, ny - 3, 0xFFFFFFFF);
        Ui.arrow(g, cx, cy, 0, 0xFFFFFFFF, 1.1f);
        // подпись: время, район, координаты
        int ty = y + S + 4;
        g.fill(x - 2, ty - 1, x + S + 2, ty + 21, Ui.GLASS);
        long dt = ClientData.tag.getLong("dayTime") % 24000L;
        int hh = (int) ((dt / 1000 + 6) % 24), mm = (int) ((dt % 1000) * 60 / 1000);
        String clock = String.format("%02d:%02d", hh, mm);
        g.drawString(font, clock, x + 2, ty + 1, 0xFFFFFFFF, true);
        String day = "день " + ClientData.tag.getLong("day");
        g.drawString(font, day, x + S - font.width(day) - 1, ty + 1, Ui.MUTED, false);
        String place = nearestPlace(pl);
        g.pose().pushPose();
        g.pose().translate(x + 2, ty + 11, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        String loc = place + "  " + pl.getBlockX() + " " + pl.getBlockZ();
        g.drawString(font, font.plainSubstrByWidth(loc, (int) ((S - 2) / 0.75f)), 0, 0, 0xFFB8C4D0, false);
        g.pose().popPose();
        return ty + 21;
    }

    private static void edgeMarker(GuiGraphics g, Font font, double dx, double dz, float cos, float sin, float sc, float cx, float cy, float half, String icon, int color) {
        float[] p = rot(dx, dz, cos, sin, sc);
        float k = Math.max(Math.abs(p[0]), Math.abs(p[1])) > half ? half / Math.max(Math.abs(p[0]), Math.abs(p[1])) : 1f;
        Ui.marker(g, font, cx + p[0] * k, cy + p[1] * k, icon, color, 0.8f, true);
    }

    private static float[] rot(double dx, double dz, float cos, float sin, float sc) {
        return new float[]{(float) (dx * cos - dz * sin) * sc, (float) (dx * sin + dz * cos) * sc};
    }

    private static String nearestPlace(LocalPlayer pl) {
        String turf = ClientData.tag.getString("turf");
        if (!turf.isEmpty()) {
            int i = turf.indexOf(" — ");
            return (i > 0 ? turf.substring(0, i) : turf).replace("Район: ", "").replace("Точка: ", "");
        }
        ClientData.Mark best = null;
        double bd = 40 * 40;
        for (ClientData.Mark m : ClientData.marks()) {
            double d = pl.distanceToSqr(m.x(), pl.getY(), m.z());
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best != null ? best.place().name() : (ClientData.tag.getBoolean("generated") ? "Новоберёзовск" : "");
    }

    // ------------------------------------------------------------------ цели и навигатор
    private static int objectives(GuiGraphics g, Font font, LocalPlayer pl, int width, int y) {
        PlayerRecord r = ClientData.rec;
        List<String[]> rows = new ArrayList<>(); // текст, цвет
        if (ClientData.wpSet) {
            double dx = ClientData.wpX + 0.5 - pl.getX(), dz = ClientData.wpZ + 0.5 - pl.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            rows.add(new String[]{"⚑ " + ClientData.wpName + " · " + dist + " м " + arrowTo(pl, dx, dz), "FF6060"});
        }
        if (!r.questId.isEmpty()) {
            Quest q = QuestRegistry.get(r.questId);
            if (q != null) rows.add(new String[]{"▶ " + q.title + "  " + r.questProgress + "/" + q.goal(), "FFC830"});
        }
        if (r.enrolled && !r.legalEducation) {
            int s = LawSchool.currentSubject(r);
            if (s >= 0) rows.add(new String[]{"Юрфак: " + LawSchool.SUBJECTS[s].name() + " " + r.lectures[s] + "/" + LawSchool.LECTURES_PER_SUBJECT
                    + (r.lectures[s] >= LawSchool.LECTURES_PER_SUBJECT ? " → экзамен" : ""), "B0A0FF"});
        }
        if (r.trialAt > 0) rows.add(new String[]{"Суд: явитесь в городской суд", "FF9040"});
        if (ClientData.tag.getBoolean("rally")) rows.add(new String[]{"⚑ У администрации митинг", "FFFF60"});
        String turf = ClientData.tag.getString("turf");
        if (turf.contains("захват")) rows.add(new String[]{turf.substring(turf.indexOf('(') + 1).replace(")", ""), "FF7050"});
        if (rows.isEmpty()) return y - 4;
        int w = 150;
        int x = width - w - 6;
        int h = 4;
        List<List<net.minecraft.util.FormattedCharSequence>> wrapped = new ArrayList<>();
        for (String[] row : rows) {
            List<net.minecraft.util.FormattedCharSequence> l = font.split(net.minecraft.network.chat.Component.literal(row[0]), w - 10);
            if (l.size() > 2) l = l.subList(0, 2);
            wrapped.add(l);
            h += l.size() * 9 + 2;
        }
        g.fill(x, y, x + w, y + h, Ui.GLASS);
        g.fill(x, y, x + w, y + 1, 0x60E8C860);
        int ly = y + 3;
        for (int i = 0; i < rows.size(); i++) {
            int col = 0xFF000000 | Integer.parseInt(rows.get(i)[1], 16);
            g.fill(x, ly - 1, x + 2, ly + wrapped.get(i).size() * 9, col);
            for (net.minecraft.util.FormattedCharSequence s : wrapped.get(i)) {
                g.drawString(font, s, x + 6, ly, col, false);
                ly += 9;
            }
            ly += 2;
        }
        return y + h;
    }

    private static String arrowTo(LocalPlayer pl, double dx, double dz) {
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        double heading = pl.getYRot() + 180;
        double rel = Mth.wrapDegrees(bearing - heading);
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        int i = (int) Math.floorMod(Math.round(rel / 45.0), 8);
        return arrows[i];
    }

    // ------------------------------------------------------------------ компас
    private static void compass(GuiGraphics g, Font font, LocalPlayer pl, float pt, int width) {
        int W = 170, x = width / 2 - W / 2, y = 4;
        float heading = Mth.wrapDegrees(pl.getViewYRot(pt) + 180f);
        if (heading < 0) heading += 360;
        g.fillGradient(x, y, x + W, y + 13, 0xA0000000, 0x40000000);
        g.enableScissor(x, y, x + W, y + 14);
        float ppd = (W / 2f) / 90f;
        String[] names = {"С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};
        for (int a = 0; a < 360; a += 15) {
            float d = Mth.wrapDegrees(a - heading);
            if (Math.abs(d) > 95) continue;
            int tx = (int) (x + W / 2f + d * ppd);
            if (a % 45 == 0) {
                String n = names[a / 45];
                g.drawCenteredString(font, n, tx, y + 3, a == 0 ? 0xFFFF5050 : 0xFFFFFFFF);
            } else {
                g.fill(tx, y + 8, tx + 1, y + 12, 0x80FFFFFF);
            }
        }
        // метки на компасе
        if (ClientData.wpSet) compassMark(g, font, pl, x, y, W, ppd, heading, ClientData.wpX, ClientData.wpZ, "⚑", 0xFFFF5050);
        for (BlockPos q : ClientData.rec.questTargets) compassMark(g, font, pl, x, y, W, ppd, heading, q.getX(), q.getZ(), "!", 0xFFFFC830);
        g.disableScissor();
        g.fill(x + W / 2, y + 12, x + W / 2 + 1, y + 15, 0xFFE8C860);
        String deg = Math.round(heading) + "°";
        g.pose().pushPose();
        g.pose().translate(x + W / 2f, y + 15, 0);
        g.pose().scale(0.65f, 0.65f, 1);
        g.drawCenteredString(font, deg, 0, 0, 0xFFC0C0C0);
        g.pose().popPose();
    }

    private static void compassMark(GuiGraphics g, Font font, LocalPlayer pl, int x, int y, int W, float ppd, float heading, int wx, int wz, String icon, int color) {
        double dx = wx + 0.5 - pl.getX(), dz = wz + 0.5 - pl.getZ();
        float bearing = (float) Math.toDegrees(Math.atan2(dx, -dz));
        float d = Mth.clamp(Mth.wrapDegrees(bearing - heading), -88, 88);
        int tx = (int) (x + W / 2f + d * ppd);
        g.drawCenteredString(font, icon, tx, y + 3, color);
    }

    // ------------------------------------------------------------------ оружие
    private static void weapon(GuiGraphics g, Font font, LocalPlayer pl, int width, int height) {
        ItemStack st = pl.getMainHandItem();
        if (!(st.getItem() instanceof GunItem gun)) return;
        int w = 132, h = 44;
        int x = width - w - 6, y = height - h - 6;
        Ui.panel(g, x, y, w, h, Ui.GOLD);
        int ammo = GunItem.ammo(st);
        int reserve = GunItem.countAmmo(pl, gun.spec.ammo().get());
        g.drawString(font, gun.spec.title(), x + 6, y + 4, 0xFFFFFFFF, true);
        String mode = gun.spec.auto() ? "АВТО" : gun.spec.pellets() > 1 ? "ДРОБЬ" : "ОДИН.";
        g.pose().pushPose();
        g.pose().translate(x + w - 6 - font.width(mode) * 0.7f, y + 5, 0);
        g.pose().scale(0.7f, 0.7f, 1);
        g.drawString(font, mode, 0, 0, Ui.MUTED, false);
        g.pose().popPose();
        boolean low = ammo <= Math.max(1, gun.spec.mag() / 4);
        g.pose().pushPose();
        g.pose().translate(x + 6, y + 15, 0);
        g.pose().scale(2f, 2f, 1);
        g.drawString(font, String.valueOf(ammo), 0, 0, low ? Ui.RED : 0xFFFFFFFF, true);
        g.pose().popPose();
        int aw = font.width(String.valueOf(ammo)) * 2;
        g.drawString(font, "/ " + (pl.getAbilities().instabuild ? "∞" : reserve), x + 10 + aw, y + 22, Ui.MUTED, false);
        // «пули» магазина
        int pips = Math.min(gun.spec.mag(), 30);
        int pw = Math.max(1, (w - 12 - (pips - 1)) / pips);
        for (int i = 0; i < pips; i++) {
            int filled = Math.round(ammo * pips / (float) gun.spec.mag());
            g.fill(x + 6 + i * (pw + 1), y + h - 7, x + 6 + i * (pw + 1) + pw, y + h - 4, i < filled ? Ui.GOLD : 0x40FFFFFF);
        }
        float rp = gun.reloadProgress(st, pl.level().getGameTime());
        if (rp >= 0) {
            g.drawString(font, "ПЕРЕЗАРЯДКА", x + w - 6 - font.width("ПЕРЕЗАРЯДКА"), y + 18, 0xFFFFC830, false);
            Ui.bar(g, x + w - 6 - 64, y + 28, 64, 3, rp, 0xFFFFC830);
        } else if (low) {
            String hint = reserve > 0 || pl.getAbilities().instabuild ? "R — перезарядка" : "нет патронов";
            g.drawString(font, hint, x + w - 6 - font.width(hint), y + 22, (System.currentTimeMillis() / 400) % 2 == 0 ? Ui.RED : 0xFFFFFFFF, false);
        }
    }

    private static void hitMarker(GuiGraphics g, int width, int height) {
        if (GunClient.hitTicks <= 0) return;
        int cx = width / 2, cy = height / 2;
        int col = GunClient.hitType == 3 ? 0xFFFF2020 : GunClient.hitType == 2 ? 0xFFFFC020 : 0xFFFFFFFF;
        int s = 3 + GunClient.hitTicks / 3;
        for (int i = 0; i < 3; i++) {
            int o = s + i;
            g.fill(cx - o - 1, cy - o - 1, cx - o + 1, cy - o + 1, col);
            g.fill(cx + o - 1, cy - o - 1, cx + o + 1, cy - o + 1, col);
            g.fill(cx - o - 1, cy + o - 1, cx - o + 1, cy + o + 1, col);
            g.fill(cx + o - 1, cy + o - 1, cx + o + 1, cy + o + 1, col);
        }
    }

    /** Оптика ПСО-1: тёмная маска, шеврон и дальномерная шкала. */
    private static void scope(GuiGraphics g, int width, int height) {
        int cx = width / 2, cy = height / 2;
        int r = (int) (Math.min(width, height) * 0.46f);
        g.fill(0, 0, width, cy - r, 0xFF000000);
        g.fill(0, cy + r, width, height, 0xFF000000);
        for (int y = -r; y < r; y++) {
            int half = (int) Math.sqrt((double) r * r - (double) y * y);
            g.fill(0, cy + y, cx - half, cy + y + 1, 0xFF000000);
            g.fill(cx + half, cy + y, width, cy + y + 1, 0xFF000000);
        }
        int col = 0xFF101010;
        g.fill(cx - r, cy, cx - 12, cy + 1, col);
        g.fill(cx + 12, cy, cx + r, cy + 1, col);
        g.fill(cx, cy + 12, cx + 1, cy + r, col);
        for (int i = 1; i <= 4; i++) {
            g.fill(cx - 10 - i * 12, cy - 3, cx - 9 - i * 12, cy + 4, col);
            g.fill(cx + 9 + i * 12, cy - 3, cx + 10 + i * 12, cy + 4, col);
        }
        // шеврон
        for (int i = 0; i < 6; i++) {
            g.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, 0xFFE03020);
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, 0xFFE03020);
        }
    }

    // ------------------------------------------------------------------ NPC под прицелом
    private static void lookAt(GuiGraphics g, Font font, Minecraft mc, int width, int height) {
        Entity e = mc.crosshairPickEntity;
        if (!(e instanceof CitizenEntity c) || mc.player.distanceToSqr(c) > 9 * 9 || GunClient.scoped()) return;
        Faction cf = c.getFaction();
        String name = c.getCustomName() != null ? c.getCustomName().getString() : "Житель";
        String role;
        if (cf == Faction.CIVILIAN) {
            Profession pr = c.getSyncedProfession();
            role = pr.title;
        } else if (cf.isState()) {
            role = cf.positionName(c.getSyncedRank());
        } else {
            role = cf.rankName(c.getSyncedRank());
        }
        String org = cf == Faction.CIVILIAN ? "житель города" : cf.title;
        int rel = ClientData.tag.getCompound("rels").getInt(String.valueOf(c.getId()));
        String relText = Relations.label(rel);
        boolean ep = cf.insignia;
        int bw = Math.max(Math.max(font.width(name), font.width(role)), Math.max(font.width(org), 110)) + 16 + (ep ? 30 : 0);
        int bx = width / 2 + 16, by = height / 2 - 8;
        g.fill(bx, by, bx + bw, by + 48, Ui.GLASS);
        g.fill(bx, by, bx + 2, by + 48, 0xFF000000 | cf.color);
        g.drawString(font, name, bx + 7, by + 4, 0xFFFFFFFF, true);
        g.drawString(font, role, bx + 7, by + 14, 0xFFB8C8D8, false);
        g.pose().pushPose();
        g.pose().translate(bx + 7, by + 24, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, org, 0, 0, 0xFF000000 | brighten(cf.color), false);
        g.pose().popPose();
        // шкала отношения −100..+100
        int sx = bx + 7, sw = bw - 14 - (ep ? 30 : 0);
        g.fill(sx, by + 34, sx + sw, by + 37, 0x60000000);
        g.fill(sx + sw / 2, by + 33, sx + sw / 2 + 1, by + 38, 0x80FFFFFF);
        int fillTo = (int) (sx + sw / 2f + rel / 100f * sw / 2f);
        g.fill(Math.min(sx + sw / 2, fillTo), by + 34, Math.max(sx + sw / 2, fillTo), by + 37, Relations.color(rel));
        g.pose().pushPose();
        g.pose().translate(sx, by + 40, 0);
        g.pose().scale(0.65f, 0.65f, 1);
        g.drawString(font, relText + (rel != 0 ? " (" + rel + ")" : "") + " · ПКМ — разговор", 0, 0, Relations.color(rel), false);
        g.pose().popPose();
        if (ep) {
            ResourceLocation tex = ClientRanks.insignia(cf, c.getSyncedRank());
            if (tex != null) g.blit(tex, bx + bw - 30, by + 6, 24, 18, 24f, 0f, 32, 24, 128, 64);
        }
    }

    /** Клиентский тик HUD: прибытие к метке, баннеры мест. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer pl = mc.player;
        if (pl == null || !ClientData.has()) return;
        if (ClientData.wpSet && pl.distanceToSqr(ClientData.wpX + 0.5, pl.getY(), ClientData.wpZ + 0.5) < 6 * 6) {
            ClientData.wpSet = false;
            Toasts.push("НАВИГАТОР", "Вы прибыли: " + ClientData.wpName, 0xFF4CD07A);
        }
        if (pl.tickCount % 10 != 0) return;
        ClientData.Mark best = null;
        double bd = 11 * 11;
        for (ClientData.Mark m : ClientData.marks()) {
            double d = pl.distanceToSqr(m.x() + 0.5, pl.getY(), m.z() + 0.5);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        if (best != null && !best.key().equals(lastPlace)) {
            lastPlace = best.key();
            Toasts.showBanner(best.place().name(), best.place().cat().title);
        } else if (best == null && !lastPlace.isEmpty()) {
            ClientData.Mark lp = ClientData.mark(lastPlace);
            if (lp == null || pl.distanceToSqr(lp.x() + 0.5, pl.getY(), lp.z() + 0.5) > 24 * 24) lastPlace = "";
        }
    }

    private static String jobTitle(String job) {
        ru.gorod.rusgorod.job.Job j = ru.gorod.rusgorod.job.Job.byName(job);
        return j == null ? job : j.title;
    }

    static int brighten(int rgb) {
        int r = Math.min(255, ((rgb >> 16) & 255) + 90), gg = Math.min(255, ((rgb >> 8) & 255) + 90), b = Math.min(255, (rgb & 255) + 90);
        return (r << 16) | (gg << 8) | b;
    }

    private HudOverlay() {
    }
}
