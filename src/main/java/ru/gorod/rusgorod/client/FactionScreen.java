package ru.gorod.rusgorod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.job.Job;
import ru.gorod.rusgorod.net.FactionActionPacket;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.quest.Quest;
import ru.gorod.rusgorod.quest.QuestRegistry;
import ru.gorod.rusgorod.study.LawSchool;

import java.util.List;

/** Меню (клавиша G). */
public class FactionScreen extends Screen {
    private static int tab = 0;
    private static Faction selected = Faction.UMVD;
    private static final String[] TABS = {"Фракции", "Служба", "Структура", "Задания", "Работа", "Юрфак", "Город"};

    private int x0, y0, w, h;
    private int cx, cy, cw;

    public FactionScreen() {
        super(Component.literal("РусГород"));
    }

    public void refresh() {
        clearWidgets();
        init();
    }

    private void act(String action, String arg) {
        Network.sendToServer(new FactionActionPacket(action, arg));
    }

    @Override
    protected void init() {
        w = Math.min(470, width - 12);
        h = Math.min(280, height - 12);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        int tw = (w - 8) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button b = Button.builder(Component.literal(TABS[i]), btn -> {
                tab = idx;
                refresh();
            }).bounds(x0 + 4 + i * tw, y0 + 5, tw - 2, 18).build();
            b.active = tab != i;
            addRenderableWidget(b);
        }
        PlayerRecord r = ClientData.rec;
        cy = y0 + 28;
        cx = x0 + 8;
        cw = w - 16;
        switch (tab) {
            case 0 -> initFactions(r);
            case 1 -> initService(r);
            case 3 -> initQuests(r);
            case 4 -> initJobs(r);
            case 5 -> initUni(r);
            case 6 -> initCity(r);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ вкладки: кнопки

    private void initFactions(PlayerRecord r) {
        Faction[] all = Faction.values();
        int colW = 76;
        for (int i = 1; i < all.length; i++) {
            Faction f = all[i];
            int col = (i - 1) % 2, row = (i - 1) / 2;
            Button b = Button.builder(Component.literal(shortTitle(f)), btn -> {
                selected = f;
                refresh();
            }).bounds(x0 + 6 + col * (colW + 2), cy + row * 20, colW, 18).build();
            b.active = selected != f;
            addRenderableWidget(b);
        }
        cx = x0 + 6 + 2 * (colW + 2) + 6;
        cw = x0 + w - 8 - cx;
        if (r.faction == Faction.CIVILIAN && selected != Faction.CIVILIAN) {
            addRenderableWidget(Button.builder(Component.literal(selected.isState() ? "Подать заявление / рапорт" : "Попроситься к ним"),
                    b -> act("apply", selected.id)).bounds(cx, y0 + h - 26, cw, 20).build());
        } else if (r.faction == selected) {
            addRenderableWidget(Button.builder(Component.literal(selected.isState() ? "Уволиться" : "Выйти из группировки"),
                    b -> act("leave", "")).bounds(cx, y0 + h - 26, cw, 20).build());
        }
    }

    private void initService(PlayerRecord r) {
        if (r.faction == Faction.CIVILIAN) return;
        int bw = (w - 28) / 4;
        int y = y0 + h - 26;
        addRenderableWidget(Button.builder(Component.literal(r.faction.isState() ? "Рапорт на звание" : "Просить повышения"), b -> act("promote", ""))
                .bounds(x0 + 8, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal(r.faction.isState() ? "Табельное" : "Снаряжение"), b -> act("gear", ""))
                .bounds(x0 + 12 + bw, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal(r.uniform ? "Снять форму" : "Надеть форму"), b -> act("uniform", ""))
                .bounds(x0 + 16 + 2 * bw, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal(r.faction.isState() ? "Уволиться" : "Выйти"), b -> act("leave", ""))
                .bounds(x0 + 20 + 3 * bw, y, bw, 20).build());
    }

    private void initQuests(PlayerRecord r) {
        if (!r.questId.isEmpty()) {
            Quest q = QuestRegistry.get(r.questId);
            int y = y0 + h - 26;
            if (q != null && q.type == Quest.Type.PAY) {
                addRenderableWidget(Button.builder(Component.literal("Сдать " + q.count + " ₽"), b -> act("quest_turnin", ""))
                        .bounds(x0 + 8, y, 140, 20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Отказаться от задания"), b -> act("quest_drop", ""))
                    .bounds(x0 + w - 158, y, 150, 20).build());
            return;
        }
        List<Quest> list = QuestRegistry.forFaction(r.faction);
        int y = cy + 12;
        for (Quest q : list) {
            if (y > y0 + h - 22) break;
            boolean ok = r.rank >= q.minRank;
            Button b = Button.builder(Component.literal((ok ? "" : "🔒 ") + q.title), btn -> act("quest_take", q.id))
                    .bounds(x0 + 8, y, 200, 18).build();
            b.active = ok;
            b.setTooltip(Tooltip.create(Component.literal(q.description + "\n\n" + q.law + "\n\nНаграда: " + q.money + " ₽, " + q.points + " баллов"
                    + (ok ? "" : "\n\nТребуется: " + q.faction.rankName(q.minRank)))));
            addRenderableWidget(b);
            y += 20;
        }
        cx = x0 + 216;
        cw = w - 224;
    }

    private void initJobs(PlayerRecord r) {
        if (r.faction != Faction.CIVILIAN) return;
        Job cur = Job.byName(r.job);
        int y = cy + 12;
        if (cur == null) {
            for (Job j : Job.values()) {
                Button b = Button.builder(Component.literal(j.title), btn -> act("job_take", j.name()))
                        .bounds(x0 + 8, y, 240, 18).build();
                b.setTooltip(Tooltip.create(Component.literal(j.description + "\n" + j.pay)));
                addRenderableWidget(b);
                y += 20;
            }
            cx = x0 + 256;
            cw = w - 264;
        } else {
            int by = y0 + h - 26;
            int bx = x0 + 8;
            if (cur == Job.GRUZCHIK) {
                addRenderableWidget(Button.builder(Component.literal("Взять груз"), b -> act("job_action", "cargo_take")).bounds(bx, by, 110, 20).build());
                bx += 114;
                addRenderableWidget(Button.builder(Component.literal("Сдать груз"), b -> act("job_action", "cargo_deliver")).bounds(bx, by, 110, 20).build());
            } else if (cur == Job.POCHTALION) {
                addRenderableWidget(Button.builder(Component.literal("Взять письма"), b -> act("job_action", "mail_take")).bounds(bx, by, 110, 20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Уволиться"), b -> act("job_quit", "")).bounds(x0 + w - 108, by, 100, 20).build());
        }
    }

    private void initUni(PlayerRecord r) {
        int y = y0 + h - 26;
        if (!r.legalEducation && !r.enrolled) {
            addRenderableWidget(Button.builder(Component.literal("Поступить в институт"), b -> act("uni_enroll", "")).bounds(x0 + 8, y, 180, 20).build());
        } else if (r.enrolled) {
            int s = LawSchool.currentSubject(r);
            Button b = Button.builder(Component.literal("Сдать экзамен"), btn -> {
                act("uni_exam", "");
                onClose();
            }).bounds(x0 + 8, y, 180, 20).build();
            b.active = s >= 0 && r.lectures[s] >= LawSchool.LECTURES_PER_SUBJECT && ClientData.tag.getBoolean("inUni");
            b.setTooltip(Tooltip.create(Component.literal(b.active ? "3 вопроса, нужно 2 верных ответа." : "Нужно: прослушать 2 лекции по предмету и находиться в институте.")));
            addRenderableWidget(b);
        }
    }

    private void initCity(PlayerRecord r) {
        if (r.fineDebt > 0) {
            addRenderableWidget(Button.builder(Component.literal("Оплатить штрафы (" + r.fineDebt + " ₽) — «Госуслуги»"), b -> act("pay_fines", ""))
                    .bounds(x0 + 8, y0 + h - 26, 260, 20).build());
        }
    }

    // ------------------------------------------------------------------ отрисовка

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);
        g.fill(x0, y0, x0 + w, y0 + h, 0xE8101820);
        g.fill(x0, y0, x0 + w, y0 + 1, 0xFFFFFFFF);
        g.fill(x0, y0 + 1, x0 + w, y0 + 2, 0xFF0039A6);
        g.fill(x0, y0 + 2, x0 + w, y0 + 3, 0xFFD52B1E);
        PlayerRecord r = ClientData.rec;
        if (!ClientData.has()) {
            g.drawCenteredString(font, "Загрузка данных...", x0 + w / 2, y0 + h / 2, 0xFFFFFFFF);
        } else {
            switch (tab) {
                case 0 -> renderFactions(g, r);
                case 1 -> renderService(g, r);
                case 2 -> renderStructure(g, r);
                case 3 -> renderQuests(g, r);
                case 4 -> renderJobs(g, r);
                case 5 -> renderUni(g, r);
                default -> renderCity(g, r);
            }
        }
        super.render(g, mouseX, mouseY, pt);
    }

    private int text(GuiGraphics g, String s, int x, int y, int width, int color) {
        width = Math.max(80, width);
        for (FormattedCharSequence line : font.split(Component.literal(s), width)) {
            if (y > y0 + h - 30) return y;
            g.drawString(font, line, x, y, color);
            y += 10;
        }
        return y;
    }

    /** Погон: верхняя грань текстуры 128×64 (u 24..56, v 0..24). */
    private void epaulette(GuiGraphics g, Faction f, int rank, int x, int y, int ew, int eh) {
        ResourceLocation tex = ClientRanks.insignia(f, rank);
        if (tex == null) return;
        g.blit(tex, x, y, ew, eh, 24f, 0f, 32, 24, 128, 64);
    }

    private void renderFactions(GuiGraphics g, PlayerRecord r) {
        Faction f = selected;
        int y = cy;
        if (f.insignia) {
            epaulette(g, f, f.topRank(), x0 + w - 52, y, 40, 30);
        }
        g.drawString(font, f.title, cx, y, 0xFF000000 | f.color | 0x505050);
        y += 11;
        g.drawString(font, f.type.title + " • NPC в городе: " + ClientData.count(f.id), cx, y, 0xFFAAAAAA);
        y += 12;
        y = text(g, f.description, cx, y, cw - 50, 0xFFE0E0E0) + 3;
        y = text(g, "Подчинённость: " + f.superior, cx, y, cw, 0xFFB0C8A0) + 3;
        y = text(g, "Основание: " + f.lawBasis, cx, y, cw, 0xFF8FA3B8) + 3;
        g.drawString(font, "Звания: " + f.rankName(0) + " … " + f.rankName(f.topRank()), cx, y, 0xFFC8B070);
        y += 12;
        if (r.faction == f) {
            text(g, "Вы состоите здесь: " + f.rankName(r.rank), cx, y, cw, 0xFF70FF70);
        } else if (r.faction != Faction.CIVILIAN) {
            text(g, "Вы уже состоите в: " + r.faction.title + ". Совмещение невозможно.", cx, y, cw, 0xFFFFA040);
        } else {
            List<String> unmet = ClientData.unmet(f.id);
            if (unmet.isEmpty()) text(g, "Требования выполнены. Вероятность приёма: " + ClientData.chance(f.id) + "%", cx, y, cw, 0xFF70FF70);
            else for (String s : unmet) y = text(g, "✖ " + s, cx, y, cw, 0xFFFF6060);
        }
    }

    private void renderService(GuiGraphics g, PlayerRecord r) {
        Faction f = r.faction;
        int x = cx, y = cy;
        if (f == Faction.CIVILIAN) {
            y = text(g, "Вы гражданин. Выберите фракцию на вкладке «Фракции», работу — на вкладке «Работа», учёбу — «Юрфак».", x, y, cw, 0xFFE0E0E0) + 6;
            g.drawString(font, "Гражданская репутация: " + r.civRep + "   Авторитет: " + r.crimRep, x, y, 0xFFC8B070);
            y += 12;
            g.drawString(font, "Образование: " + (r.legalEducation ? "высшее юридическое" : "среднее") + "   Судимость: "
                    + (r.everConvicted ? (r.hasActiveRecord(ClientData.tag.getLong("day")) ? "непогашенная" : "погашенная") : "нет"), x, y, 0xFFC8B070);
            y += 12;
            g.drawString(font, "Стаж по юридической профессии: " + r.legalExpDays + " сут.", x, y, 0xFFC8B070);
            return;
        }
        long day = ClientData.tag.getLong("day");
        if (f.insignia) {
            epaulette(g, f, r.rank, x0 + w - 76, y, 64, 48);
            g.drawCenteredString(font, r.uniform ? "в форме" : "в гражданском", x0 + w - 44, y + 50, 0xFF909090);
        }
        g.drawString(font, f.title, x, y, 0xFF000000 | f.color | 0x505050);
        y += 12;
        g.drawString(font, "Звание: " + f.rankName(r.rank), x, y, 0xFFFFFFFF);
        y += 10;
        g.drawString(font, "Должность: " + f.positionName(r.rank), x, y, 0xFFFFFFFF);
        y += 12;
        int next = r.rank + 1;
        if (next < f.rankCount()) {
            g.drawString(font, "Служебные баллы: " + r.points + " / " + f.pointsForRank(next) + "  → " + f.rankName(next), x, y, 0xFFC8B070);
            y += 10;
            g.drawString(font, "Выслуга в звании: " + (day - r.rankSinceDay) + " / " + f.daysInRankFor(next) + " сут."
                    + (f.officerRankIndex >= 0 && next >= f.officerRankIndex && !r.legalEducation ? "  • нужен диплом (Юрфак)" : ""), x, y, 0xFFC8B070);
        } else {
            g.drawString(font, "Вы достигли вершины иерархии.", x, y, 0xFF70FF70);
        }
        y += 11;
        if (f.isState()) g.drawString(font, "Денежное довольствие: " + ClientData.tag.getLong("income") + " ₽ / сутки", x, y, 0xFF9AD0FF);
        else g.drawString(font, "Общак/касса: " + ClientData.tag.getLong("funds") + " ₽", x, y, 0xFF9AD0FF);
        y += 14;
        // лестница званий с погонами
        int half = (f.rankCount() + 1) / 2;
        int colW = (cw - 4) / 2;
        for (int i = 0; i < f.rankCount(); i++) {
            int col = i < half ? 0 : 1;
            int row = col == 0 ? i : i - half;
            int rx = x + col * colW, ry = y + row * 13;
            if (ry > y0 + h - 40) continue;
            int color = i == r.rank ? 0xFF70FF70 : (i < r.rank ? 0xFF808080 : 0xFFB0B0B0);
            int tx = rx;
            if (f.insignia) {
                epaulette(g, f, i, rx, ry, 16, 12);
                tx += 19;
            }
            String s = (i == r.rank ? "► " : "") + f.rankName(i);
            if (font.width(s) > colW - (tx - rx) - 4) s = font.plainSubstrByWidth(s, colW - (tx - rx) - 10) + "…";
            g.drawString(font, s, tx, ry + 2, color);
        }
    }

    private void renderStructure(GuiGraphics g, PlayerRecord r) {
        Faction f = r.faction;
        int y = cy;
        if (f == Faction.CIVILIAN) {
            y = text(g, "Вертикаль власти в РФ (упрощённо):", cx, y, cw, 0xFFFFD27A) + 2;
            y = text(g, "Президент РФ → Правительство → федеральные министерства и службы (МВД, ФСБ, ФСИН/Минюст, Минобороны). "
                    + "Следственный комитет подчиняется Президенту. Прокуратура — единая централизованная система во главе с Генпрокурором. "
                    + "Суды независимы и подчиняются только Конституции и закону. Местное самоуправление (администрация) не входит в систему органов госвласти (ст. 12 Конституции).", cx, y, cw, 0xFFE0E0E0) + 6;
            text(g, "Вступите во фракцию, чтобы увидеть её штат.", cx, y, cw, 0xFF909090);
            return;
        }
        g.drawString(font, "Штат: " + f.title, cx, y, 0xFFFFD27A);
        y += 11;
        y = text(g, "Подчинённость: " + f.superior, cx, y, cw, 0xFFB0C8A0) + 4;
        g.drawString(font, "★ — игроки. Приказы подчинённым: ПКМ по NPC или в чат («за мной», «стоять здесь», «свободны», «задержать», «доложить»).", cx, y, 0xFF909090);
        y += 12;
        List<String> roster = ClientData.strings("roster");
        int colW = (cw - 4) / 2;
        int i = 0;
        for (String row : roster) {
            int sep = row.indexOf('|');
            int rank = 0;
            try {
                rank = Integer.parseInt(row.substring(0, sep));
            } catch (Exception ignored) {
            }
            String s = row.substring(sep + 1);
            int col = i % 2, rr = i / 2;
            int rx = cx + col * colW, ry = y + rr * 13;
            if (ry > y0 + h - 16) break;
            int tx = rx;
            if (f.insignia) {
                epaulette(g, f, rank, rx, ry, 16, 12);
                tx += 19;
            }
            if (font.width(s) > colW - (tx - rx) - 4) s = font.plainSubstrByWidth(s, colW - (tx - rx) - 10) + "…";
            g.drawString(font, s, tx, ry + 2, s.startsWith("★") ? 0xFF70FF70 : 0xFFD0D0D0);
            i++;
        }
    }

    private void renderQuests(GuiGraphics g, PlayerRecord r) {
        if (!r.questId.isEmpty()) {
            Quest q = QuestRegistry.get(r.questId);
            if (q == null) return;
            int y = cy;
            g.drawString(font, "Текущее задание: " + q.title, cx, y, 0xFFFFD27A);
            y += 12;
            y = text(g, q.description, cx, y, cw, 0xFFE0E0E0) + 4;
            y = text(g, "Правовое основание: " + q.law, cx, y, cw, 0xFF8FA3B8) + 4;
            g.drawString(font, "Прогресс: " + r.questProgress + " / " + q.goal(), cx, y, 0xFF70FF70);
            y += 12;
            if (r.questProgress < r.questTargets.size()) {
                var t = r.questTargets.get(r.questProgress);
                g.drawString(font, "Цель: X " + t.getX() + ", Z " + t.getZ(), cx, y, 0xFF9AD0FF);
                y += 12;
            }
            g.drawString(font, "Награда: " + q.money + " ₽, " + q.points + " баллов", cx, y, 0xFFC8B070);
            return;
        }
        g.drawString(font, "Задания — " + r.faction.title + ":", x0 + 8, cy, 0xFFFFD27A);
        text(g, "Наведите на задание, чтобы прочитать описание и правовое основание. Одновременно — одно задание. За задания дают служебные баллы для повышения.",
                cx, cy + 12, cw, 0xFFAAAAAA);
    }

    private void renderJobs(GuiGraphics g, PlayerRecord r) {
        if (r.faction != Faction.CIVILIAN) {
            text(g, "Гражданские работы доступны только гражданским. Сотрудникам госорганов совмещение ограничено законом, а у «братвы» свои заработки. "
                    + "Учиться на юрфаке можно всем — вкладка «Юрфак».", cx, cy, cw, 0xFFE0E0E0);
            return;
        }
        Job cur = Job.byName(r.job);
        if (cur == null) {
            g.drawString(font, "Вакансии (центр занятости):", x0 + 8, cy, 0xFFFFD27A);
            text(g, "Трудоустройство по трудовому договору (ст. 56 ТК РФ). С заработка удерживается НДФЛ 13%. Наведите на вакансию для подробностей.",
                    cx, cy + 12, cw, 0xFFAAAAAA);
            return;
        }
        int y = cy;
        g.drawString(font, "Работа: " + cur.title, x0 + 8, y, 0xFFFFD27A);
        y += 12;
        y = text(g, cur.description, x0 + 8, y, w - 16, 0xFFE0E0E0) + 3;
        text(g, cur.pay, x0 + 8, y, w - 16, 0xFFAAAAAA);
    }

    private void renderUni(GuiGraphics g, PlayerRecord r) {
        int y = cy;
        g.drawString(font, "Юридический институт г. Новоберёзовска", cx, y, 0xFFFFD27A);
        y += 12;
        if (r.legalEducation) {
            y = text(g, "🎓 У вас есть диплом бакалавра юриспруденции.", cx, y, cw, 0xFF70FF70) + 4;
            text(g, "Доступны: Следственный комитет, прокуратура, ФСБ, суд (после 3 суток стажа по юридической профессии), "
                    + "офицерские звания в полиции, ГИБДД, УФСИН и армии. Можно быть защитником в суде.", cx, y, cw, 0xFFE0E0E0);
            return;
        }
        y = text(g, "Как получить диплом:  1) Поступить (кнопка внизу).  2) Днём (06:30–17:30) прийти в аудиторию 101 и простоять там "
                + LawSchool.LECTURE_SECONDS + " сек — это одна лекция; лектор зачитывает нормы в чат.  3) После " + LawSchool.LECTURES_PER_SUBJECT
                + " лекций сдать экзамен по предмету в институте (3 вопроса, нужно 2 верных).  4) Сдать все " + LawSchool.SUBJECTS.length
                + " предметов — диплом. Учиться можно в любой фракции, за лекцию гражданским — стипендия 100 ₽.", cx, y, cw, 0xFFE0E0E0) + 4;
        g.drawString(font, "Институт: X " + ClientData.tag.getInt("uniX") + ", Z " + ClientData.tag.getInt("uniZ")
                + (ClientData.tag.getBoolean("inUni") ? "  (вы в институте)" : ""), cx, y, 0xFF9AD0FF);
        y += 13;
        if (!r.enrolled) {
            g.drawString(font, "Вы ещё не студент.", cx, y, 0xFFFFA040);
            return;
        }
        int cur = LawSchool.currentSubject(r);
        for (int i = 0; i < LawSchool.SUBJECTS.length; i++) {
            if (y > y0 + h - 32) break;
            boolean passed = r.examPassed(i);
            String mark = passed ? "✔ сдан" : (i == cur ? "лекции " + r.lectures[i] + "/" + LawSchool.LECTURES_PER_SUBJECT
                    + (r.lectures[i] >= LawSchool.LECTURES_PER_SUBJECT ? " → экзамен!" : "") : "впереди");
            int color = passed ? 0xFF70FF70 : (i == cur ? 0xFFFFD27A : 0xFF808080);
            g.drawString(font, (i == cur ? "► " : "  ") + (i + 1) + ". " + LawSchool.SUBJECTS[i].name(), cx, y, color);
            g.drawString(font, mark, cx + 210, y, color);
            y += 11;
        }
    }

    private void renderCity(GuiGraphics g, PlayerRecord r) {
        int y = cy;
        g.drawString(font, "г. " + ru.gorod.rusgorod.world.CityGenerator.CITY_NAME, cx, y, 0xFFFFD27A);
        y += 12;
        g.drawString(font, "Бюджет: " + ClientData.tag.getLong("budget") + " ₽   Доверие к власти: " + Math.round(ClientData.tag.getDouble("sat"))
                + "%   Преступность: " + Math.round(ClientData.tag.getDouble("crime")), cx, y, 0xFFC8B070);
        y += 12;
        if (r.fineDebt > 0) {
            g.drawString(font, "Ваши неоплаченные штрафы: " + r.fineDebt + " ₽ (срок — до " + r.fineDueDay + "-х суток)", cx, y, 0xFFFF8040);
            y += 12;
        }
        if (ClientData.tag.getBoolean("rally")) {
            g.drawString(font, ClientData.tag.getBoolean("rallyNotified") ? "Идёт согласованный митинг" : "Идёт несогласованная акция", cx, y, 0xFFFFFF60);
            y += 12;
        }
        g.drawString(font, "Новости:", cx, y, 0xFFFFFFFF);
        y += 11;
        for (String s : ClientData.strings("news")) {
            y = text(g, "• " + s, cx, y, cw, 0xFFD0D0D0);
            if (y > y0 + h / 2 + 20) break;
        }
        List<String> cases = ClientData.strings("cases");
        if (!cases.isEmpty()) {
            y += 4;
            g.drawString(font, r.faction.isState() ? "Оперативная сводка (дела):" : "Ваши дела:", cx, y, 0xFFFFFFFF);
            y += 11;
            for (String s : cases) y = text(g, s, cx, y, cw, 0xFF9AD0FF);
        }
    }

    private static String shortTitle(Faction f) {
        return switch (f) {
            case RUS_MAFIA -> "Русская ОПГ";
            case KAVKAZ_MAFIA -> "Кавказ. ОПГ";
            case SKINHEADS -> "Скинхеды";
            case BIKERS -> "Байкеры";
            case ARMY -> "В/ч 12345";
            case UFSIN -> "УФСИН";
            case UGIBDD -> "ГИБДД";
            case UMVD -> "УМВД";
            case SK -> "СК";
            case PROK -> "Прокуратура";
            case UFSB -> "УФСБ";
            case GOV -> "Администр.";
            case COURT -> "Суд";
            default -> "Гражданские";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
