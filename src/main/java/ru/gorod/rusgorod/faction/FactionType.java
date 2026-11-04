package ru.gorod.rusgorod.faction;

public enum FactionType {
    CIVIL("Гражданские"), STATE("Государственная структура"), CRIMINAL("Криминал");

    public final String title;

    FactionType(String title) {
        this.title = title;
    }
}
