package ru.gorod.rusgorod.entity;

/** Занятость гражданских NPC. */
public enum Profession {
    WORKER("рабочий склада", "warehouse"),
    TRADER("продавец", "market"),
    SHOPKEEPER("продавец магазина «Продукты»", "shop"),
    TEACHER("учитель", "school"),
    CLERK("оператор почтовой связи", "post"),
    STUDENT("студент юридического института", "university"),
    PENSIONER("пенсионер", "park"),
    PRIEST("священнослужитель", "church"),
    UNEMPLOYED("безработный", "square");

    public final String title;
    public final String workKey;

    Profession(String title, String workKey) {
        this.title = title;
        this.workKey = workKey;
    }

    public boolean isTrader() {
        return this == TRADER || this == SHOPKEEPER;
    }

    public static Profession byName(String n) {
        try {
            return valueOf(n);
        } catch (Exception e) {
            return UNEMPLOYED;
        }
    }
}
