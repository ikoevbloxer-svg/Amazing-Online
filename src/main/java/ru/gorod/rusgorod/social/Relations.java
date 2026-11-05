package ru.gorod.rusgorod.social;

/** Шкала отношений NPC к игроку. */
public final class Relations {
    public static final int FRIEND = 50, KNOWN = 20, DISLIKE = -30, ENEMY = -60;

    public static String label(int v) {
        if (v >= 80) return "близкий друг";
        if (v >= FRIEND) return "друг";
        if (v >= KNOWN) return "знакомый";
        if (v > DISLIKE) return "нейтрально";
        if (v > ENEMY) return "неприязнь";
        return "враг";
    }

    /** Цвет для интерфейса (ARGB). */
    public static int color(int v) {
        if (v >= FRIEND) return 0xFF60FF60;
        if (v >= KNOWN) return 0xFFB0FFB0;
        if (v > DISLIKE) return 0xFFC0C0C0;
        if (v > ENEMY) return 0xFFFFA060;
        return 0xFFFF4040;
    }

    /** Код форматирования для текста в диалоге. */
    public static String fmt(int v) {
        if (v >= FRIEND) return "§a";
        if (v >= KNOWN) return "§2";
        if (v > DISLIKE) return "§7";
        if (v > ENEMY) return "§6";
        return "§c";
    }

    private Relations() {
    }
}
