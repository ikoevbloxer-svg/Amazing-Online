package ru.gorod.rusgorod.entity;

/** Занятость гражданских NPC. skin — особая одежда (null — обычная). */
public enum Profession {
    WORKER("рабочий склада", "warehouse", "worker"),
    FACTORY("рабочий завода «Берёзовский машзавод»", "factory", "worker"),
    TRADER("продавец на рынке", "market", null),
    SHOPKEEPER("продавец магазина «Продукты»", "shop", null),
    TEACHER("учитель", "school", null),
    CLERK("оператор почтовой связи", "post", null),
    MFC_CLERK("специалист МФЦ «Мои документы»", "mfc", null),
    BANKER("операционист банка", "bank", null),
    DOCTOR("врач городской больницы", "hospital", "doctor"),
    PROFESSOR("преподаватель юридического института", "university", "professor"),
    STUDENT("студент юридического института", "university", null),
    BARMAN("бармен рюмочной «Встреча»", "bar", null),
    RAILWAY("кассир железнодорожного вокзала", "vokzal", null),
    CULTURE("работник Дома культуры", "dk", null),
    FUEL("заправщик АЗС", "azs", "worker"),
    PENSIONER("пенсионер", "park", null),
    PRIEST("священнослужитель", "church", "priest"),
    UNEMPLOYED("безработный", "square", null);

    public final String title;
    public final String workKey;
    public final String skin;

    Profession(String title, String workKey, String skin) {
        this.title = title;
        this.workKey = workKey;
        this.skin = skin;
    }

    public boolean isTrader() {
        return this == TRADER || this == SHOPKEEPER || this == BARMAN || this == FUEL;
    }

    /** Ключевые профессии, которые должны быть в городе хотя бы в одном экземпляре. */
    public static final Profession[] KEY = {PROFESSOR, PROFESSOR, DOCTOR, DOCTOR, BANKER, PRIEST, SHOPKEEPER, TRADER, TRADER,
            MFC_CLERK, BARMAN, RAILWAY, TEACHER, CLERK, CULTURE, FUEL, FACTORY, FACTORY, WORKER};

    public static Profession byName(String n) {
        try {
            return valueOf(n);
        } catch (Exception e) {
            return UNEMPLOYED;
        }
    }

    public static Profession byOrdinal(int o) {
        Profession[] v = values();
        return o >= 0 && o < v.length ? v[o] : UNEMPLOYED;
    }
}
