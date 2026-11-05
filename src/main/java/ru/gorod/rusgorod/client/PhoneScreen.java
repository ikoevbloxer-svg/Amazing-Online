package ru.gorod.rusgorod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.net.FactionActionPacket;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.registry.ModSounds;
import ru.gorod.rusgorod.social.BlackMarket;
import ru.gorod.rusgorod.social.Relations;

import java.util.ArrayList;
import java.util.List;

/** Смартфон (K): карта, банк, «Госуслуги», новости, такси, 112, контакты, «Калибр», фракция. */
public class PhoneScreen extends Screen {
    private enum App {
        HOME("", 0), MAP("Карта", 0x2E8B57), BANK("Банк", 0x21A038), GOV("Госуслуги", 0x0D4CD3), NEWS("Новости", 0xE0453A),
        TAXI("Такси", 0xFFCC00), SOS("112", 0xD62828), CONTACTS("Контакты", 0x3A86FF), SHOP("Калибр", 0x6B4F2A), FACTION("Фракция", 0x7B2CBF);
        final String title;
        final int color;

        App(String t, int c) {
            title = t;
            color = c;
        }
    }

    private static final String[] GLYPH = {"", "◎", "₽", "Г", "N", "T", "!", "☎", "К", "★"};
    private App app = App.HOME;
    private int px, py, pw, ph;
    private int sx0, sy0, sw, sh;
    private int scroll;
    private EditBox toName, toAmount;
    private final List<int[]> rects = new ArrayList<>();
    private final List<Runnable> acts = new ArrayList<>();

    public PhoneScreen() {
        super(Component.literal("Смартфон"));
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Network.sendToServer(new FactionActionPacket("sync", ""));
        mc.player.level().playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), ModSounds.PHONE.get(), SoundSource.MASTER, 0.5f, 1f, false);
        mc.setScreen(new PhoneScreen());
    }

    @Override
    protected void init() {
        ph = Math.min(260, height - 12);
        pw = (int) (ph * 0.58f);
        px = width / 2 - pw / 2;
        py = height / 2 - ph / 2;
        sx0 = px + 6;
        sy0 = py + 16;
        sw = pw - 12;
        sh = ph - 30;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        toName = null;
        toAmount = null;
        if (app == App.BANK) {
            toName = new EditBox(font, sx0 + 6, sy0 + 112, sw - 12, 14, Component.literal("Ник получателя"));
            toName.setHint(Component.literal("ник игрока"));
            toName.setMaxLength(16);
            toAmount = new EditBox(font, sx0 + 6, sy0 + 130, sw - 12, 14, Component.literal("Сумма"));
            toAmount.setHint(Component.literal("сумма, ₽"));
            toAmount.setMaxLength(9);
            toAmount.setFilter(s -> s.chars().allMatch(Character::isDigit));
            addRenderableWidget(toName);
            addRenderableWidget(toAmount);
        }
    }

    private void go(App a) {
        if (a == App.MAP) {
            Minecraft.getInstance().setScreen(new MapScreen(this));
            return;
        }
        if (a == App.FACTION) {
            Network.sendToServer(new FactionActionPacket("sync", ""));
            Minecraft.getInstance().setScreen(new FactionScreen());
            return;
        }
        app = a;
        scroll = 0;
        rebuild();
    }

    private void act(int x, int y, int w, int h, Runnable r) {
        rects.add(new int[]{x, y, w, h});
        acts.add(r);
    }

    private static String clock() {
        long dt = ClientData.tag.getLong("dayTime") % 24000L;
        int hh = (int) ((dt / 1000 + 6) % 24), mm = (int) ((dt % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hh, mm);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        rects.clear();
        acts.clear();
        g.fill(0, 0, width, height, 0x88000000);
        // корпус
        g.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, 0xFF2A2C31);
        g.fill(px, py, px + pw, py + ph, 0xFF060607);
        g.fill(px + pw / 2 - 14, py + 4, px + pw / 2 + 14, py + 8, 0xFF151518);
        // экран
        int top = 0xFF16324F, bottom = 0xFF0B1220;
        if (app != App.HOME) {
            top = 0xFF111418;
            bottom = 0xFF0B0D10;
        }
        g.fillGradient(sx0, sy0 - 6, sx0 + sw, sy0 + sh, top, bottom);
        // статус-бар
        g.drawString(font, clock(), sx0 + 4, sy0 - 4, 0xFFFFFFFF, false);
        String st = "МТС 4G ▮▮▮ 87%";
        g.pose().pushPose();
        g.pose().translate(sx0 + sw - 4 - font.width(st) * 0.7f, sy0 - 3, 0);
        g.pose().scale(0.7f, 0.7f, 1);
        g.drawString(font, st, 0, 0, 0xFFD0D0D0, false);
        g.pose().popPose();
        int cy = sy0 + 8;
        g.enableScissor(sx0, sy0 + 6, sx0 + sw, sy0 + sh);
        switch (app) {
            case HOME -> home(g, mx, my, cy);
            case BANK -> bank(g, mx, my, cy);
            case GOV -> gov(g, mx, my, cy);
            case NEWS -> news(g, mx, my, cy);
            case TAXI -> taxi(g, mx, my, cy);
            case SOS -> sos(g, mx, my, cy);
            case CONTACTS -> contacts(g, mx, my, cy);
            case SHOP -> shop(g, mx, my, cy);
            default -> {
            }
        }
        g.disableScissor();
        if (app != App.HOME) {
            // шапка приложения
            g.fill(sx0, sy0 + 6, sx0 + sw, sy0 + 20, 0xFF000000 | app.color);
            g.drawString(font, "‹", sx0 + 5, sy0 + 9, 0xFFFFFFFF, false);
            g.drawCenteredString(font, app.title, sx0 + sw / 2, sy0 + 9, 0xFFFFFFFF);
            act(sx0, sy0 + 6, 24, 14, () -> go(App.HOME));
        }
        // кнопка «домой»
        g.fill(px + pw / 2 - 16, py + ph - 9, px + pw / 2 + 16, py + ph - 7, 0xFF8A8A90);
        act(px + pw / 2 - 20, py + ph - 12, 40, 8, () -> go(App.HOME));
        super.render(g, mx, my, pt);
    }

    // ------------------------------------------------------------------ рабочий стол
    private void home(GuiGraphics g, int mx, int my, int y) {
        g.pose().pushPose();
        g.pose().translate(sx0 + sw / 2f, y + 6, 0);
        g.pose().scale(2.4f, 2.4f, 1);
        g.drawCenteredString(font, clock(), 0, 0, 0xFFFFFFFF);
        g.pose().popPose();
        g.drawCenteredString(font, "День " + ClientData.tag.getLong("day") + " · Новоберёзовск", sx0 + sw / 2, y + 30, 0xFFB8C8E0);
        PlayerRecord r = ClientData.rec;
        g.drawCenteredString(font, Ui.money(r.balance), sx0 + sw / 2, y + 42, Ui.GOLD);
        App[] apps = {App.MAP, App.BANK, App.GOV, App.NEWS, App.TAXI, App.SOS, App.CONTACTS, App.SHOP, App.FACTION};
        int cell = sw / 3;
        int iy = y + 60;
        for (int i = 0; i < apps.length; i++) {
            App a = apps[i];
            int cx = sx0 + (i % 3) * cell + cell / 2, ty = iy + (i / 3) * 44;
            int s = 13;
            boolean hov = Ui.in(mx, my, cx - s, ty, s * 2, s * 2);
            g.fill(cx - s, ty, cx + s, ty + s * 2, 0xFF000000 | (hov ? brighten(a.color) : a.color));
            g.fill(cx - s, ty, cx + s, ty + 1, 0x40FFFFFF);
            g.pose().pushPose();
            g.pose().translate(cx, ty + 6, 0);
            g.pose().scale(1.6f, 1.6f, 1);
            g.drawCenteredString(font, GLYPH[a.ordinal()], 0, 0, 0xFFFFFFFF);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(cx, ty + s * 2 + 3, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawCenteredString(font, a.title, 0, 0, 0xFFFFFFFF);
            g.pose().popPose();
            // счётчики-бейджи
            int badge = a == App.GOV ? r.wanted.size() + (r.fineDebt > 0 ? 1 : 0) : a == App.NEWS ? 0 : 0;
            if (badge > 0) {
                g.fill(cx + s - 6, ty - 3, cx + s + 3, ty + 6, 0xFFFF3B30);
                g.drawCenteredString(font, String.valueOf(badge), cx + s - 1, ty - 2, 0xFFFFFFFF);
            }
            act(cx - s, ty, s * 2, s * 2 + 10, () -> go(a));
        }
    }

    private static int brighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 40), gg = Math.min(255, ((c >> 8) & 255) + 40), b = Math.min(255, (c & 255) + 40);
        return (r << 16) | (gg << 8) | b;
    }

    // ------------------------------------------------------------------ банк
    private void bank(GuiGraphics g, int mx, int my, int y) {
        PlayerRecord r = ClientData.rec;
        y += 18;
        g.drawString(font, "Баланс", sx0 + 6, y, Ui.MUTED, false);
        g.pose().pushPose();
        g.pose().translate(sx0 + 6, y + 10, 0);
        g.pose().scale(1.7f, 1.7f, 1);
        g.drawString(font, Ui.money(r.balance), 0, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
        g.drawString(font, "Доход в сутки: " + Ui.money(ClientData.tag.getLong("income")), sx0 + 6, y + 30, Ui.GREEN, false);
        g.drawString(font, "Штрафы: " + (r.fineDebt > 0 ? Ui.money(r.fineDebt) : "нет"), sx0 + 6, y + 42, r.fineDebt > 0 ? 0xFFFFA040 : Ui.MUTED, false);
        if (r.fineDebt > 0) {
            boolean ok = r.balance >= r.fineDebt;
            Ui.button(g, font, sx0 + 6, y + 54, sw - 12, 14, "Оплатить штрафы", mx, my, 0x21A038, ok);
            if (ok) act(sx0 + 6, y + 54, sw - 12, 14, () -> Network.sendToServer(new FactionActionPacket("pay_fines", "")));
        }
        g.drawString(font, "Перевод игроку", sx0 + 6, sy0 + 100, Ui.MUTED, false);
        Ui.button(g, font, sx0 + 6, sy0 + 150, sw - 12, 15, "Перевести", mx, my, 0x21A038, true);
        act(sx0 + 6, sy0 + 150, sw - 12, 15, () -> {
            if (toName != null && toAmount != null && !toName.getValue().isBlank() && !toAmount.getValue().isBlank()) {
                Network.sendToServer(new FactionActionPacket("ph_send", toName.getValue().trim() + "|" + toAmount.getValue().trim()));
                toAmount.setValue("");
            }
        });
        g.drawString(font, "Касса фракции: " + Ui.money(ClientData.tag.getLong("funds")), sx0 + 6, sy0 + 172, Ui.MUTED, false);
    }

    // ------------------------------------------------------------------ госуслуги
    private void gov(GuiGraphics g, int mx, int my, int y) {
        PlayerRecord r = ClientData.rec;
        Minecraft mc = Minecraft.getInstance();
        y += 18 - scroll;
        long day = ClientData.tag.getLong("day");
        line(g, "Гражданин", mc.player == null ? "" : mc.player.getName().getString(), y, Ui.TEXT);
        y += 11;
        line(g, "Статус", r.faction == Faction.CIVILIAN ? "гражданское лицо" : r.faction.title, y, 0xFF000000 | r.faction.color);
        y += 11;
        if (r.faction != Faction.CIVILIAN) {
            line(g, "Звание", r.faction.rankName(r.rank), y, Ui.TEXT);
            y += 11;
        }
        line(g, "Судимость", r.hasActiveRecord(day) ? "есть (до дня " + r.recordUntilDay + ")" : r.everConvicted ? "погашена" : "нет", y,
                r.hasActiveRecord(day) ? Ui.RED : Ui.GREEN);
        y += 11;
        line(g, "Розыск", r.wanted.isEmpty() ? "нет" : "★".repeat(r.wantedStars()), y, r.wanted.isEmpty() ? Ui.GREEN : Ui.RED);
        y += 11;
        for (Article a : r.wanted) {
            for (FormattedCharSequence s : font.split(Component.literal("• " + a.code + " — " + a.title), sw - 14)) {
                g.drawString(font, s, sx0 + 8, y, 0xFFFF8080, false);
                y += 9;
            }
        }
        line(g, "Штрафы", r.fineDebt > 0 ? Ui.money(r.fineDebt) + (r.fineDueDay >= 0 ? " до дня " + r.fineDueDay : "") : "нет", y, r.fineDebt > 0 ? 0xFFFFA040 : Ui.GREEN);
        y += 11;
        if (r.fineDebt > 0 && r.balance >= r.fineDebt) {
            Ui.button(g, font, sx0 + 6, y, sw - 12, 13, "Оплатить штрафы", mx, my, 0x0D4CD3, true);
            act(sx0 + 6, y, sw - 12, 13, () -> Network.sendToServer(new FactionActionPacket("pay_fines", "")));
            y += 16;
        }
        line(g, "Лицензия ЛОА", r.gunLicense ? "действует" : "нет", y, r.gunLicense ? Ui.GREEN : Ui.MUTED);
        y += 11;
        if (!r.gunLicense) {
            Ui.button(g, font, sx0 + 6, y, sw - 12, 13, "Оформить ЛОА — 2 000 ₽", mx, my, 0x0D4CD3, true);
            act(sx0 + 6, y, sw - 12, 13, () -> Network.sendToServer(new FactionActionPacket("ph_license", "")));
            y += 15;
            for (FormattedCharSequence s : font.split(Component.literal("ФЗ-150, ст. 13: без судимости, розыска и долгов. Даёт право на «Осу» и «Сайгу» в «Калибре»."), sw - 12)) {
                g.drawString(font, s, sx0 + 6, y, Ui.MUTED, false);
                y += 9;
            }
            y += 2;
        }
        line(g, "Диплом юриста", r.legalEducation ? "есть" : r.enrolled ? "учится (" + r.examsPassed + "/7)" : "нет", y, r.legalEducation ? Ui.GREEN : Ui.MUTED);
        y += 11;
        if (r.trialAt > 0) {
            line(g, "Суд", "назначено заседание", y, 0xFFFFA040);
            y += 11;
        }
        line(g, "Работа", r.job.isEmpty() ? "—" : r.job, y, Ui.TEXT);
        y += 11;
        line(g, "Поездок на такси", String.valueOf(r.taxiRides), y, Ui.MUTED);
        y += 11;
        maxScroll = Math.max(0, y + scroll - (sy0 + sh) + 6);
    }

    private int maxScroll;

    private void line(GuiGraphics g, String k, String v, int y, int color) {
        g.drawString(font, k, sx0 + 6, y, Ui.MUTED, false);
        int w = font.width(v);
        if (w > sw - 70) v = font.plainSubstrByWidth(v, sw - 76) + "…";
        g.drawString(font, v, sx0 + sw - 6 - font.width(v), y, color, false);
        g.fill(sx0 + 6, y + 9, sx0 + sw - 6, y + 10, 0x18FFFFFF);
    }

    // ------------------------------------------------------------------ новости
    private void news(GuiGraphics g, int mx, int my, int y) {
        y += 18 - scroll;
        List<String> n = ClientData.strings("news");
        for (int i = n.size() - 1; i >= 0; i--) {
            List<FormattedCharSequence> ls = font.split(Component.literal(n.get(i)), sw - 14);
            g.fill(sx0 + 4, y - 2, sx0 + sw - 4, y + ls.size() * 9 + 2, 0x30FFFFFF);
            g.fill(sx0 + 4, y - 2, sx0 + 6, y + ls.size() * 9 + 2, 0xFFE0453A);
            for (FormattedCharSequence s : ls) {
                g.drawString(font, s, sx0 + 9, y, 0xFFE8E8E8, false);
                y += 9;
            }
            y += 6;
        }
        if (n.isEmpty()) g.drawCenteredString(font, "Новостей пока нет", sx0 + sw / 2, y + 20, Ui.MUTED);
        maxScroll = Math.max(0, y + scroll - (sy0 + sh) + 6);
    }

    // ------------------------------------------------------------------ такси
    private void taxi(GuiGraphics g, int mx, int my, int y) {
        Minecraft mc = Minecraft.getInstance();
        y += 18;
        g.drawString(font, "«Берёзка» — подача 1 мин", sx0 + 6, y, 0xFFFFCC00, false);
        y += 12 - scroll;
        List<ClientData.Mark> list = new ArrayList<>(ClientData.marks());
        if (mc.player != null) list.sort((a, b) -> Double.compare(mc.player.distanceToSqr(a.x(), mc.player.getY(), a.z()), mc.player.distanceToSqr(b.x(), mc.player.getY(), b.z())));
        int start = y;
        for (ClientData.Mark m : list) {
            int dist = mc.player == null ? 0 : (int) Math.sqrt(mc.player.distanceToSqr(m.x(), mc.player.getY(), m.z()));
            if (dist < 12) continue;
            int price = (int) (60 + dist * 1.6);
            boolean hov = Ui.in(mx, my, sx0 + 4, y - 1, sw - 8, 20) && my > sy0 + 30;
            g.fill(sx0 + 4, y - 1, sx0 + sw - 4, y + 19, hov ? 0x40FFCC00 : 0x18FFFFFF);
            g.fill(sx0 + 6, y + 3, sx0 + 9, y + 15, 0xFF000000 | m.place().cat().color);
            g.drawString(font, font.plainSubstrByWidth(m.place().name(), sw - 24), sx0 + 12, y + 1, 0xFFFFFFFF, false);
            g.drawString(font, dist + " м · " + price + " ₽", sx0 + 12, y + 10, ClientData.rec.balance >= price ? Ui.MUTED : Ui.RED, false);
            if (my > sy0 + 30) act(sx0 + 4, y - 1, sw - 8, 20, () -> {
                Network.sendToServer(new FactionActionPacket("ph_taxi", m.key()));
                onClose();
            });
            y += 22;
        }
        maxScroll = Math.max(0, y - start + 40 - sh);
    }

    // ------------------------------------------------------------------ 112
    private void sos(GuiGraphics g, int mx, int my, int y) {
        y += 24;
        Ui.button(g, font, sx0 + 10, y, sw - 20, 34, "Полиция — 112", mx, my, 0x1E5BD6, true);
        act(sx0 + 10, y, sw - 20, 34, () -> {
            Network.sendToServer(new FactionActionPacket("ph_112", ""));
            onClose();
        });
        y += 42;
        Ui.button(g, font, sx0 + 10, y, sw - 20, 34, "Скорая — 103", mx, my, 0xD62828, true);
        act(sx0 + 10, y, sw - 20, 34, () -> {
            Network.sendToServer(new FactionActionPacket("ph_103", ""));
            onClose();
        });
        y += 46;
        for (FormattedCharSequence s : font.split(Component.literal("Наряд приедет к вам и задержит нарушителей рядом. Заведомо ложный вызов — штраф по ст. 19.13 КоАП РФ."), sw - 16)) {
            g.drawString(font, s, sx0 + 8, y, Ui.MUTED, false);
            y += 9;
        }
    }

    // ------------------------------------------------------------------ контакты
    private void contacts(GuiGraphics g, int mx, int my, int y) {
        y += 18 - scroll;
        List<String> cs = ClientData.strings("contacts");
        if (cs.isEmpty()) {
            for (FormattedCharSequence s : font.split(Component.literal("Пока никого. Знакомьтесь с жителями: разговор, подарки, помощь — и они появятся здесь."), sw - 16)) {
                g.drawString(font, s, sx0 + 8, y + 10, Ui.MUTED, false);
                y += 9;
            }
            return;
        }
        int start = y;
        for (String c : cs) {
            String[] a = c.split("\\|");
            if (a.length < 6) continue;
            int rel = Integer.parseInt(a[3]);
            g.fill(sx0 + 4, y - 1, sx0 + sw - 4, y + 27, 0x18FFFFFF);
            int col = 0xFF000000 | Integer.parseInt(a[5]);
            g.fill(sx0 + 6, y + 2, sx0 + 18, y + 14, col);
            g.drawCenteredString(font, a[1].isEmpty() ? "?" : a[1].substring(0, 1), sx0 + 12, y + 4, 0xFFFFFFFF);
            g.drawString(font, font.plainSubstrByWidth(a[1], sw - 30), sx0 + 22, y, 0xFFFFFFFF, false);
            g.pose().pushPose();
            g.pose().translate(sx0 + 22, y + 9, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, a[2] + " · " + a[4] + " м", 0, 0, Ui.MUTED, false);
            g.pose().popPose();
            g.drawString(font, Relations.label(rel), sx0 + 22, y + 17, Relations.color(rel), false);
            if (rel >= Relations.FRIEND) {
                boolean hov = Ui.button(g, font, sx0 + sw - 30, y + 14, 24, 11, "☎", mx, my, 0x21A038, true);
                if (hov || true) act(sx0 + sw - 30, y + 14, 24, 11, () -> Network.sendToServer(new FactionActionPacket("ph_call", a[0])));
            }
            y += 30;
        }
        maxScroll = Math.max(0, y - start + 30 - sh);
    }

    // ------------------------------------------------------------------ «Калибр»
    private void shop(GuiGraphics g, int mx, int my, int y) {
        y += 18;
        PlayerRecord r = ClientData.rec;
        g.drawString(font, r.gunLicense ? "ЛОА: предъявлена ✔" : "ЛОА: нет — оформите в «Госуслугах»", sx0 + 6, y, r.gunLicense ? Ui.GREEN : 0xFFFFA040, false);
        y += 11;
        Ui.button(g, font, sx0 + 6, y, sw - 12, 12, "Маршрут до павильона на рынке", mx, my, 0x6B4F2A, true);
        act(sx0 + 6, y, sw - 12, 12, () -> {
            ClientData.Mark m = ClientData.mark("market");
            if (m != null) ClientData.setWaypoint(m.x(), m.z(), "«Калибр» (рынок)");
        });
        y += 16 - scroll;
        int start = y;
        for (BlackMarket.Offer o : BlackMarket.SHOP) {
            boolean lic = o.note().contains("ЛОА");
            boolean can = (!lic || r.gunLicense) && r.balance >= o.price();
            g.fill(sx0 + 4, y - 1, sx0 + sw - 4, y + 21, 0x18FFFFFF);
            g.renderItem(new net.minecraft.world.item.ItemStack(o.item().get(), o.count()), sx0 + 6, y + 2);
            String nm = o.item().get().getDescription().getString() + (o.count() > 1 ? " ×" + o.count() : "");
            g.drawString(font, font.plainSubstrByWidth(nm, sw - 60), sx0 + 25, y + 1, 0xFFFFFFFF, false);
            g.pose().pushPose();
            g.pose().translate(sx0 + 25, y + 11, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, o.note() + " · " + Ui.money(o.price()), 0, 0, lic && !r.gunLicense ? 0xFFFF8060 : Ui.MUTED, false);
            g.pose().popPose();
            Ui.button(g, font, sx0 + sw - 32, y + 4, 26, 12, "₽", mx, my, 0x6B4F2A, can);
            if (can) act(sx0 + sw - 32, y + 4, 26, 12, () -> Network.sendToServer(new FactionActionPacket("ph_shop", o.id())));
            y += 24;
        }
        maxScroll = Math.max(0, y - start + 60 - sh);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (int i = rects.size() - 1; i >= 0; i--) {
            int[] r = rects.get(i);
            if (Ui.in(mx, my, r[0], r[1], r[2], r[3])) {
                acts.get(i).run();
                return true;
            }
        }
        if (!Ui.in(mx, my, px, py, pw, ph)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) (delta * 14), 0, Math.max(0, maxScroll));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (toName != null && (toName.isFocused() || (toAmount != null && toAmount.isFocused())) && key != GLFW.GLFW_KEY_ESCAPE)
            return super.keyPressed(key, scan, mods);
        if (key == GLFW.GLFW_KEY_BACKSPACE && app != App.HOME) {
            go(App.HOME);
            return true;
        }
        if (ClientSetup.OPEN_PHONE.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
