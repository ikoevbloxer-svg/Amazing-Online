package ru.gorod.rusgorod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.entity.Profession;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.quest.Quest;
import ru.gorod.rusgorod.quest.QuestRegistry;
import ru.gorod.rusgorod.social.Relations;
import ru.gorod.rusgorod.study.LawSchool;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD: панель в левом верхнем углу (фракция, звание и погон, деньги, розыск, район, задание, учёба, штрафы)
 * и подсказка под прицелом при взгляде на жителя (имя, должность, отношение к вам).
 */
public final class HudOverlay {
    private record Line(String text, int color) {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.options.renderDebug || !ClientData.has()) return;
        Font font = mc.font;
        PlayerRecord r = ClientData.rec;
        Faction f = r.faction;
        long time = ClientData.tag.getLong("time");

        // ---------------------------------------------------------- панель
        List<Line> lines = new ArrayList<>();
        String head = f == Faction.CIVILIAN ? "Гражданин РФ" : f.title;
        String sub = f == Faction.CIVILIAN ? (r.job.isEmpty() ? "без работы" : "работа: " + jobTitle(r.job))
                : f.rankName(r.rank) + (f.isState() && !r.uniform ? " · в гражданском" : "");
        lines.add(new Line("₽ " + String.format("%,d", r.balance).replace(',', ' '), 0xFFE8D27A));
        if (r.isWanted()) lines.add(new Line("★".repeat(r.wantedStars()) + " РОЗЫСК", 0xFFFF4040));
        if (r.jailUntil > time) lines.add(new Line((r.jailType == 2 ? "СИЗО-1: " : "ИВС: ") + (r.jailUntil - time) / 20 + " с", 0xFFFF8040));
        if (r.cuffed) lines.add(new Line("⛓ В наручниках", 0xFFFF4040));
        if (r.fineDebt > 0) lines.add(new Line("Штрафы: " + r.fineDebt + " ₽", 0xFFFFA040));
        String turf = ClientData.tag.getString("turf");
        if (!turf.isEmpty()) lines.add(new Line(turf, turf.contains("захват") ? 0xFFFF7050 : 0xFFB0B0B0));
        if (!r.questId.isEmpty()) {
            Quest q = QuestRegistry.get(r.questId);
            if (q != null) lines.add(new Line("▶ " + q.title + " " + r.questProgress + "/" + q.goal(), 0xFF9AD0FF));
        }
        if (r.enrolled && !r.legalEducation) {
            int s = LawSchool.currentSubject(r);
            if (s >= 0) lines.add(new Line("Юрфак: " + LawSchool.SUBJECTS[s].name() + " " + r.lectures[s] + "/" + LawSchool.LECTURES_PER_SUBJECT
                    + (r.lectures[s] >= LawSchool.LECTURES_PER_SUBJECT ? " → экзамен" : ""), 0xFFB0A0FF));
        }
        if (ClientData.tag.getBoolean("rally")) lines.add(new Line("⚑ У администрации митинг", 0xFFFFFF60));

        int iconW = f.insignia && r.uniform ? 22 : 0;
        int w = Math.max(font.width(head), font.width(sub)) + iconW;
        for (Line l : lines) w = Math.max(w, font.width(l.text()));
        w += 12;
        int h = 24 + lines.size() * 10 + 4;
        int x = 4, y = 4;
        g.fill(x, y, x + w, y + h, 0x90000000);
        g.fill(x, y, x + 2, y + h, 0xFF000000 | f.color);
        g.fill(x + 2, y + 22, x + w, y + 23, 0x40FFFFFF);
        g.drawString(font, head, x + 7, y + 3, 0xFF000000 | brighten(f.color), true);
        g.drawString(font, sub, x + 7, y + 13, 0xFFE0E0E0, true);
        if (iconW > 0) {
            ResourceLocation tex = ClientRanks.insignia(f, r.rank);
            if (tex != null) g.blit(tex, x + w - 24, y + 4, 20, 15, 24f, 0f, 32, 24, 128, 64);
        }
        int ly = y + 26;
        for (Line l : lines) {
            g.drawString(font, l.text(), x + 7, ly, l.color(), true);
            ly += 10;
        }

        // ---------------------------------------------------------- подсказка под прицелом
        Entity e = mc.crosshairPickEntity;
        if (e instanceof CitizenEntity c && mc.player.distanceToSqr(c) < 8 * 8) {
            Faction cf = c.getFaction();
            String name = c.getCustomName() != null ? c.getCustomName().getString() : "Житель";
            String role;
            if (cf == Faction.CIVILIAN) {
                Profession pr = c.getSyncedProfession();
                role = pr.title;
            } else if (cf.isState()) {
                role = cf.positionName(c.getSyncedRank()) + " · " + cf.title;
            } else {
                role = cf.title;
            }
            int rel = ClientData.tag.getCompound("rels").getInt(String.valueOf(c.getId()));
            String relText = "Отношение: " + Relations.label(rel) + (rel != 0 ? " (" + rel + ")" : "");
            String hint = "ПКМ — поговорить · пишите в чат";
            int bw = Math.max(Math.max(font.width(name), font.width(role)), Math.max(font.width(relText), font.width(hint))) + 12;
            int bx = width / 2 - bw / 2, by = height / 2 + 14;
            g.fill(bx, by, bx + bw, by + 44, 0x90000000);
            g.fill(bx, by, bx + bw, by + 1, 0xFF000000 | cf.color);
            g.drawCenteredString(font, name, width / 2, by + 3, 0xFFFFFFFF);
            g.drawCenteredString(font, role, width / 2, by + 13, 0xFFB8C8D8);
            g.drawCenteredString(font, relText, width / 2, by + 23, Relations.color(rel));
            g.drawCenteredString(font, hint, width / 2, by + 33, 0xFF808080);
            if (cf.insignia) {
                ResourceLocation tex = ClientRanks.insignia(cf, c.getSyncedRank());
                if (tex != null) g.blit(tex, bx + bw + 3, by + 2, 24, 18, 24f, 0f, 32, 24, 128, 64);
            }
        }
    }

    private static String jobTitle(String job) {
        ru.gorod.rusgorod.job.Job j = ru.gorod.rusgorod.job.Job.byName(job);
        return j == null ? job : j.title;
    }

    private static int brighten(int rgb) {
        int r = Math.min(255, ((rgb >> 16) & 255) + 90), gg = Math.min(255, ((rgb >> 8) & 255) + 90), b = Math.min(255, (rgb & 255) + 90);
        return (r << 16) | (gg << 8) | b;
    }

    private HudOverlay() {
    }
}
