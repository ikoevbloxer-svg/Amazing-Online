package ru.gorod.rusgorod.quest;

import ru.gorod.rusgorod.faction.Faction;

/**
 * Шаблон задания.
 * <ul>
 *   <li>VISIT — пройти точки locs по порядку (на последней простоять stayTicks).</li>
 *   <li>PATROL — пройти count случайных перекрёстков.</li>
 *   <li>ACTION — выполнить действие action в диалоге с NPC фракции target (null — любой) count раз.</li>
 *   <li>ARREST — задержать count разыскиваемых (target — фильтр фракции или null).</li>
 *   <li>KILL — нейтрализовать count членов фракции target.</li>
 *   <li>PAY — внести count рублей в фонд фракции.</li>
 * </ul>
 * Спец-ключи точек: @node — случайный перекрёсток, @crime — место последнего преступления, @home — случайный дом.
 */
public class Quest {
    public enum Type {VISIT, PATROL, ACTION, ARREST, KILL, PAY}

    public final String id;
    public final Faction faction;
    public final int minRank;
    public final String title;
    public final String description;
    public final String law;
    public final Type type;
    public final String[] locs;
    public final int count;
    public final int stayTicks;
    public final String action;
    public final Faction target;
    public final int money;
    public final int points;
    public final int civRep;
    public final int crimRep;

    public Quest(String id, Faction faction, int minRank, String title, String description, String law, Type type,
                 String[] locs, int count, int stayTicks, String action, Faction target,
                 int money, int points, int civRep, int crimRep) {
        this.id = id;
        this.faction = faction;
        this.minRank = minRank;
        this.title = title;
        this.description = description;
        this.law = law;
        this.type = type;
        this.locs = locs;
        this.count = count;
        this.stayTicks = stayTicks;
        this.action = action;
        this.target = target;
        this.money = money;
        this.points = points;
        this.civRep = civRep;
        this.crimRep = crimRep;
    }

    public int goal() {
        return switch (type) {
            case VISIT -> locs.length;
            default -> count;
        };
    }
}
