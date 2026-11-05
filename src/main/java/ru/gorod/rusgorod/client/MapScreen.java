package ru.gorod.rusgorod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.net.FactionActionPacket;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.world.Places;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Полноэкранная карта города (M): исследованная местность, места, районы, задания, метка навигатора, такси. */
public class MapScreen extends Screen {
    private static final int SIDE = 176;
    private double cx, cz;
    private float scale = 1.5f;
    private boolean dragging;
    private double dragX, dragY;
    private ClientData.Mark selected;
    private static final Set<Places.Cat> HIDDEN = EnumSet.noneOf(Places.Cat.class);
    private int listScroll;
    private final Screen parent;

    public MapScreen(Screen parent) {
        super(Component.literal("Карта города"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            cx = mc.player.getX();
            cz = mc.player.getZ();
        }
        if (CityMap.ready()) {
            float fit = Math.min((width - SIDE - 20) / (float) CityMap.w, (height - 20) / (float) CityMap.h);
            scale = Mth.clamp(Math.max(fit * 1.6f, 1.2f), 0.4f, 6f);
        }
    }

    private int mapW() {
        return width - SIDE;
    }

    private float sx(double wx) {
        return (float) (mapW() / 2f + (wx - cx) * scale);
    }

    private float sy(double wz) {
        return (float) (height / 2f + (wz - cz) * scale);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getInstance();
        g.fill(0, 0, width, height, 0xFF0A0D11);
        int mw = mapW();
        g.enableScissor(0, 0, mw, height);
        // сетка
        for (int x = (int) (sx(Math.floor(cx / 30) * 30 - 30 * 20)); x < mw; x += Math.max(8, (int) (30 * scale))) g.fill(x, 0, x + 1, height, 0x10FFFFFF);
        for (int y = (int) (sy(Math.floor(cz / 30) * 30 - 30 * 20)); y < height; y += Math.max(8, (int) (30 * scale))) g.fill(0, y, mw, y + 1, 0x10FFFFFF);
        if (CityMap.ready()) {
            g.pose().pushPose();
            g.pose().translate(sx(CityMap.x0), sy(CityMap.z0), 0);
            g.pose().scale(scale, scale, 1f);
            g.blit(CityMap.TEX, 0, 0, 0, 0, CityMap.w, CityMap.h, CityMap.w, CityMap.h);
            g.pose().popPose();
        }
        // районы
        for (String s : ClientData.strings("turfs")) {
            String[] a = s.split("\\|");
            if (a.length < 9) continue;
            Faction owner = a[3].isEmpty() ? null : Faction.byId(a[3]);
            int col = owner == null ? 0xA0A0A0A0 : 0xD0000000 | owner.color;
            float tx = sx(Integer.parseInt(a[6]) + 0.5), tz = sy(Integer.parseInt(a[7]) + 0.5);
            Ui.ring(g, tx, tz, 12 * scale, col);
            if (!a[4].isEmpty()) Ui.ring(g, tx, tz, 12 * scale - 2, 0xFFFF3030);
            if (scale > 1.4f) {
                g.pose().pushPose();
                g.pose().translate(tx, tz + 12 * scale + 2, 0);
                g.pose().scale(0.6f, 0.6f, 1);
                g.drawCenteredString(font, a[1] + (owner == null ? "" : " · " + owner.title), 0, 0, col | 0xFF000000);
                g.pose().popPose();
            }
        }
        // NPC и игроки поблизости
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof CitizenEntity c) {
                    float x = sx(e.getX()), y = sy(e.getZ());
                    g.fill((int) x - 1, (int) y - 1, (int) x + 1, (int) y + 1, 0xFF000000 | c.getFaction().color);
                } else if (e instanceof Player p && p != mc.player) {
                    float x = sx(e.getX()), y = sy(e.getZ());
                    g.fill((int) x - 2, (int) y - 2, (int) x + 2, (int) y + 2, 0xFF000000);
                    g.fill((int) x - 1, (int) y - 1, (int) x + 1, (int) y + 1, 0xFFFFFFFF);
                }
            }
        }
        // места
        ClientData.Mark hover = null;
        for (ClientData.Mark m : ClientData.marks()) {
            if (HIDDEN.contains(m.place().cat())) continue;
            float x = sx(m.x() + 0.5), y = sy(m.z() + 0.5);
            boolean h = Math.abs(mx - x) < 7 && Math.abs(my - y) < 7 && mx < mw;
            if (h) hover = m;
            Ui.marker(g, font, x, y, m.place().icon(), m.place().cat().color, scale > 2.5f ? 1.25f : 1f, m == selected);
            if (scale > 2.8f) {
                g.pose().pushPose();
                g.pose().translate(x, y + 8, 0);
                g.pose().scale(0.6f, 0.6f, 1);
                g.drawCenteredString(font, m.place().name(), 0, 0, 0xFFE0E0E0);
                g.pose().popPose();
            }
        }
        // задание
        for (BlockPos q : ClientData.rec.questTargets) {
            float x = sx(q.getX() + 0.5), y = sy(q.getZ() + 0.5);
            Ui.marker(g, font, x, y, "!", 0xFFC830, 1.2f, false);
        }
        // метка
        if (ClientData.wpSet) {
            float x = sx(ClientData.wpX + 0.5), y = sy(ClientData.wpZ + 0.5);
            Ui.marker(g, font, x, y, "⚑", 0xE03030, 1.3f, true);
        }
        if (mc.player != null) Ui.arrow(g, sx(mc.player.getX()), sy(mc.player.getZ()), mc.player.getYRot() + 180f, 0xFFFFFFFF, 1.4f);
        g.disableScissor();

        if (hover != null) {
            String d = mc.player == null ? "" : " · " + (int) Math.sqrt(mc.player.distanceToSqr(hover.x(), mc.player.getY(), hover.z())) + " м";
            g.renderTooltip(font, Component.literal(hover.place().name() + d), mx, my);
        }
        // заголовок и подсказки
        Ui.panel(g, 8, 8, 190, 30, Ui.GOLD);
        g.drawString(font, "НОВОБЕРЁЗОВСК", 14, 12, Ui.GOLD, false);
        g.drawString(font, mc.player == null ? "" : "X " + mc.player.getBlockX() + "  Z " + mc.player.getBlockZ() + "  ·  масштаб " + String.format("%.1f", scale),
                14, 24, Ui.MUTED, false);
        String hint = "ЛКМ — выбрать/тянуть · ПКМ — метка · колесо — масштаб · Пробел — ко мне";
        g.fill(8, height - 20, 16 + font.width(hint), height - 7, Ui.GLASS);
        g.drawString(font, hint, 12, height - 17, Ui.MUTED, false);
        if (!CityMap.ready()) g.drawCenteredString(font, "Карта появится после генерации города", mw / 2, height / 2 - 30, Ui.MUTED);

        renderSide(g, mx, my);
        super.render(g, mx, my, pt);
    }

    private final List<Runnable> clicks = new ArrayList<>();
    private final List<int[]> clickRects = new ArrayList<>();

    private void click(int x, int y, int w, int h, Runnable r) {
        clickRects.add(new int[]{x, y, w, h});
        clicks.add(r);
    }

    private void renderSide(GuiGraphics g, int mx, int my) {
        clicks.clear();
        clickRects.clear();
        Minecraft mc = Minecraft.getInstance();
        int x = width - SIDE, w = SIDE;
        g.fill(x, 0, width, height, Ui.GLASS_DARK);
        g.fill(x, 0, x + 1, height, Ui.LINE);
        int y = 8;
        g.drawString(font, "СЛОИ", x + 8, y, Ui.MUTED, false);
        y += 12;
        int col = 0;
        for (Places.Cat c : Places.Cat.values()) {
            int bx = x + 8 + col * 82, by = y;
            boolean on = !HIDDEN.contains(c);
            g.fill(bx, by, bx + 8, by + 8, on ? 0xFF000000 | c.color : 0xFF303030);
            g.drawString(font, shortCat(c), bx + 11, by, on ? Ui.TEXT : 0xFF707070, false);
            click(bx, by, 80, 9, () -> {
                if (!HIDDEN.remove(c)) HIDDEN.add(c);
            });
            if (++col == 2) {
                col = 0;
                y += 11;
            }
        }
        y += 6;
        g.fill(x + 6, y, width - 6, y + 1, Ui.LINE);
        y += 5;
        // выбранное место
        int detailH = selected != null ? 70 : (ClientData.wpSet ? 34 : 0);
        int listBottom = height - 8 - detailH;
        List<ClientData.Mark> list = new ArrayList<>();
        for (ClientData.Mark m : ClientData.marks()) if (!HIDDEN.contains(m.place().cat())) list.add(m);
        if (mc.player != null) list.sort((a, b) -> Double.compare(mc.player.distanceToSqr(a.x(), mc.player.getY(), a.z()), mc.player.distanceToSqr(b.x(), mc.player.getY(), b.z())));
        int rows = Math.max(1, (listBottom - y) / 11);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, list.size() - rows));
        for (int i = listScroll; i < list.size() && y + 11 <= listBottom; i++) {
            ClientData.Mark m = list.get(i);
            boolean hov = Ui.in(mx, my, x + 4, y - 1, w - 8, 11);
            if (hov || m == selected) g.fill(x + 4, y - 1, width - 4, y + 10, m == selected ? 0x50E8C860 : 0x30FFFFFF);
            g.fill(x + 8, y + 1, x + 14, y + 7, 0xFF000000 | m.place().cat().color);
            String name = m.place().name();
            String dist = mc.player == null ? "" : (int) Math.sqrt(mc.player.distanceToSqr(m.x(), mc.player.getY(), m.z())) + " м";
            int maxW = w - 30 - font.width(dist);
            if (font.width(name) > maxW) name = font.plainSubstrByWidth(name, maxW - 6) + "…";
            g.drawString(font, name, x + 18, y, Ui.TEXT, false);
            g.drawString(font, dist, width - 8 - font.width(dist), y, Ui.MUTED, false);
            click(x + 4, y - 1, w - 8, 11, () -> select(m));
            y += 11;
        }
        int dy = height - 8 - detailH;
        if (selected != null) {
            Ui.panel(g, x + 4, dy, w - 8, detailH, selected.place().cat().color);
            g.drawString(font, font.plainSubstrByWidth(selected.place().name(), w - 18), x + 9, dy + 5, Ui.TEXT, false);
            int dist = mc.player == null ? 0 : (int) Math.sqrt(mc.player.distanceToSqr(selected.x(), mc.player.getY(), selected.z()));
            g.drawString(font, selected.place().cat().title + " · " + dist + " м", x + 9, dy + 16, Ui.MUTED, false);
            int price = mc.player == null ? 0 : (int) (60 + dist * 1.6);
            final ClientData.Mark sel = selected;
            Ui.button(g, font, x + 9, dy + 30, 76, 14, "Метка", mx, my, 0xE03030, true);
            click(x + 9, dy + 30, 76, 14, () -> ClientData.setWaypoint(sel.x(), sel.z(), sel.place().name()));
            boolean afford = ClientData.rec.balance >= price;
            Ui.button(g, font, x + 89, dy + 30, 78, 14, "Такси " + price + "₽", mx, my, 0xE8C860, afford);
            if (afford) click(x + 89, dy + 30, 78, 14, () -> {
                Network.sendToServer(new FactionActionPacket("ph_taxi", sel.key()));
                onClose();
            });
            Ui.button(g, font, x + 9, dy + 48, 158, 14, "Показать на карте", mx, my, 0x4FA3FF, true);
            click(x + 9, dy + 48, 158, 14, () -> {
                cx = sel.x();
                cz = sel.z();
                scale = Math.max(scale, 3f);
            });
        } else if (ClientData.wpSet) {
            Ui.panel(g, x + 4, dy, w - 8, detailH, 0xE03030);
            g.drawString(font, "⚑ " + font.plainSubstrByWidth(ClientData.wpName, w - 60), x + 9, dy + 5, Ui.TEXT, false);
            Ui.button(g, font, x + 9, dy + 16, 158, 13, "Снять метку", mx, my, 0x808080, true);
            click(x + 9, dy + 16, 158, 13, () -> ClientData.wpSet = false);
        }
    }

    private static String shortCat(Places.Cat c) {
        return switch (c) {
            case STATE -> "Госорганы";
            case SERVICE -> "Услуги";
            case FOOD -> "Еда, магазины";
            case LEISURE -> "Отдых";
            case EDU -> "Учёба";
            case CRIME -> "Криминал";
            case ARMY -> "Армия";
            case TRANSPORT -> "Транспорт";
        };
    }

    private void select(ClientData.Mark m) {
        selected = m;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < clickRects.size(); i++) {
            int[] r = clickRects.get(i);
            if (Ui.in(mx, my, r[0], r[1], r[2], r[3])) {
                clicks.get(i).run();
                return true;
            }
        }
        if (mx < mapW()) {
            if (button == 1) {
                int wx = (int) Math.floor(cx + (mx - mapW() / 2.0) / scale), wz = (int) Math.floor(cz + (my - height / 2.0) / scale);
                if (ClientData.wpSet && Math.abs(sx(ClientData.wpX) - mx) < 6 && Math.abs(sy(ClientData.wpZ) - my) < 6) ClientData.wpSet = false;
                else ClientData.setWaypoint(wx, wz, "Метка X " + wx + " Z " + wz);
                return true;
            }
            for (ClientData.Mark m : ClientData.marks()) {
                if (HIDDEN.contains(m.place().cat())) continue;
                if (Math.abs(sx(m.x() + 0.5) - mx) < 7 && Math.abs(sy(m.z() + 0.5) - my) < 7) {
                    select(m);
                    return true;
                }
            }
            dragging = true;
            dragX = mx;
            dragY = my;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            cx -= (mx - dragX) / scale;
            cz -= (my - dragY) / scale;
            dragX = mx;
            dragY = my;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= mapW()) {
            listScroll -= (int) Math.signum(delta) * 2;
            return true;
        }
        double wx = cx + (mx - mapW() / 2.0) / scale, wz = cz + (my - height / 2.0) / scale;
        scale = Mth.clamp(scale * (delta > 0 ? 1.2f : 1 / 1.2f), 0.3f, 8f);
        cx = wx - (mx - mapW() / 2.0) / scale;
        cz = wz - (my - height / 2.0) / scale;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        Minecraft mc = Minecraft.getInstance();
        if (key == GLFW.GLFW_KEY_SPACE && mc.player != null) {
            cx = mc.player.getX();
            cz = mc.player.getZ();
            return true;
        }
        if (ClientSetup.OPEN_MAP.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
