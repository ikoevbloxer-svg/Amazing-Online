package ru.gorod.rusgorod.client;

import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Мини-набор отрисовки в едином стиле (тёмное стекло, акцентные полосы, кнопки). */
public final class Ui {
    public static final int GLASS = 0xC80E1217;
    public static final int GLASS_DARK = 0xE6080A0D;
    public static final int LINE = 0x30FFFFFF;
    public static final int TEXT = 0xFFE8EAED;
    public static final int MUTED = 0xFF9AA3AD;
    public static final int GOLD = 0xFFE8C860;
    public static final int RED = 0xFFFF4B4B;
    public static final int GREEN = 0xFF4CD07A;
    public static final int BLUE = 0xFF4FA3FF;

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, GLASS);
        g.fill(x, y, x + w, y + 1, (accent & 0x00FFFFFF) | 0xA0000000);
        g.fill(x, y + h - 1, x + w, y + h, 0x40000000);
    }

    /** Кнопка; возвращает true, если под курсором. */
    public static boolean button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, int mx, int my, int color, boolean enabled) {
        boolean hov = enabled && in(mx, my, x, y, w, h);
        int bg = !enabled ? 0x60202428 : hov ? (color & 0x00FFFFFF) | 0xE0000000 : (color & 0x00FFFFFF) | 0x70000000;
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y + h - 1, x + w, y + h, (color & 0x00FFFFFF) | 0xFF000000);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, enabled ? 0xFFFFFFFF : 0xFF707070);
        return hov;
    }

    /** Значок места: цветная плашка с буквой. */
    public static void marker(GuiGraphics g, Font font, float x, float y, String icon, int color, float scale, boolean selected) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        int s = 5;
        if (selected) g.fill(-s - 2, -s - 2, s + 2, s + 2, 0xFFFFFFFF);
        g.fill(-s - 1, -s - 1, s + 1, s + 1, 0xFF000000);
        g.fill(-s, -s, s, s, color | 0xFF000000);
        g.fill(-s, -s, s, -s + 1, 0x50FFFFFF);
        float ts = icon.length() > 1 ? 0.55f : 0.85f;
        g.pose().scale(ts, ts, 1f);
        int tw = font.width(icon);
        g.drawString(font, icon, -tw / 2, -4, 0xFFFFFFFF, false);
        g.pose().popPose();
    }

    /** Стрелка игрока, повёрнутая по направлению взгляда (градусы, 0 = вверх). */
    public static void arrow(GuiGraphics g, float x, float y, float rotDeg, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));
        g.pose().scale(scale, scale, 1f);
        for (int i = 0; i < 7; i++) {
            int half = i / 2 + 1;
            g.fill(-half - 1, -4 + i, half + 1, -3 + i, 0xFF000000);
        }
        for (int i = 0; i < 6; i++) {
            int half = i / 2;
            g.fill(-half, -3 + i, half + 1, -2 + i, color);
        }
        g.fill(-1, 1, 2, 2, 0xFF000000);
        g.pose().popPose();
    }

    /** Окружность из точек (зоны районов). */
    public static void ring(GuiGraphics g, float cx, float cy, float r, int color) {
        int n = Math.max(16, (int) (r * 0.8f));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            int x = (int) (cx + Math.cos(a) * r), y = (int) (cy + Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        g.fill(x, y, x + w, y + h, 0x80000000);
        g.fill(x, y, x + Math.round(w * Math.max(0, Math.min(1, frac))), y + h, color);
    }

    public static String money(long v) {
        return String.format("%,d", v).replace(',', ' ') + " ₽";
    }

    private Ui() {
    }
}
