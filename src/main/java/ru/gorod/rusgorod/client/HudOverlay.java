package ru.gorod.rusgorod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.quest.Quest;
import ru.gorod.rusgorod.quest.QuestRegistry;

/** Компактная панель в левом верхнем углу: фракция, звание, счёт, розыск, задание. */
public final class HudOverlay {
    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.options.renderDebug || !ClientData.has()) return;
        PlayerRecord r = ClientData.rec;
        Faction f = r.faction;
        int x = 4, y = 4;
        String head = f == Faction.CIVILIAN ? "Гражданин" : f.title + " — " + f.rankName(r.rank) + (f.isState() && !r.uniform ? " (в гражданском)" : "");
        g.drawString(mc.font, head, x, y, 0xFF000000 | f.color | 0x404040, true);
        y += 10;
        g.drawString(mc.font, "Счёт: " + String.format("%,d", r.balance).replace(',', ' ') + " ₽", x, y, 0xFFE8D27A, true);
        y += 10;
        if (r.isWanted()) {
            g.drawString(mc.font, "РОЗЫСК: " + "★".repeat(r.wantedStars()), x, y, 0xFFFF4040, true);
            y += 10;
        }
        long time = ClientData.tag.getLong("time");
        if (r.jailUntil > time) {
            g.drawString(mc.font, (r.jailType == 2 ? "СИЗО-1: " : "ИВС: ") + (r.jailUntil - time) / 20 + " сек.", x, y, 0xFFFF8040, true);
            y += 10;
        }
        if (!r.questId.isEmpty()) {
            Quest q = QuestRegistry.get(r.questId);
            if (q != null) {
                g.drawString(mc.font, "Задание: " + q.title + " (" + r.questProgress + "/" + q.goal() + ")", x, y, 0xFF9AD0FF, true);
                y += 10;
            }
        }
        if (r.enrolled && !r.legalEducation) {
            int sub = ru.gorod.rusgorod.study.LawSchool.currentSubject(r);
            if (sub >= 0) {
                g.drawString(mc.font, "Юрфак: " + ru.gorod.rusgorod.study.LawSchool.SUBJECTS[sub].name() + " — лекции " + r.lectures[sub] + "/"
                        + ru.gorod.rusgorod.study.LawSchool.LECTURES_PER_SUBJECT, x, y, 0xFFB0A0FF, true);
                y += 10;
            }
        }
        if (r.fineDebt > 0) {
            g.drawString(mc.font, "Штрафы: " + r.fineDebt + " ₽", x, y, 0xFFFFA040, true);
            y += 10;
        }
        if (r.cuffed) {
            g.drawString(mc.font, "В НАРУЧНИКАХ", x, y, 0xFFFF4040, true);
            y += 10;
        }
        if (ClientData.tag.getBoolean("rally")) {
            g.drawString(mc.font, "У администрации — митинг", x, y, 0xFFFFFF60, true);
        }
    }

    private HudOverlay() {
    }
}
