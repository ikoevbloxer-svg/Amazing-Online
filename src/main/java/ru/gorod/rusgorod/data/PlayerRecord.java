package ru.gorod.rusgorod.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.law.Article;

import java.util.ArrayList;
import java.util.List;

/** Состояние игрока в городе: фракция, звание, деньги, судимость, розыск, задание, работа. */
public class PlayerRecord {
    public Faction faction = Faction.CIVILIAN;
    public int rank;
    public int points;
    public long rankSinceDay;
    public long balance = 3000;
    /** Гражданская репутация (законопослушность, трудовой стаж, общественная полезность). */
    public int civRep = 10;
    /** «Авторитет» в криминальном мире. */
    public int crimRep;
    public boolean everConvicted;
    public long recordUntilDay = -1;
    public final List<Article> wanted = new ArrayList<>();
    public long jailUntil = -1;
    /** 1 — ИВС отдела полиции (адм. арест), 2 — СИЗО. */
    public int jailType;
    public boolean legalEducation;
    public int lectures;
    public String job = "";
    public int jobCounter;
    public long lastApplyDay = -100;
    public long lastGearDay = -100;
    public long lastSalaryDay = -1;

    public String questId = "";
    public int questProgress;
    public int questStay;
    public final List<BlockPos> questTargets = new ArrayList<>();

    public boolean hasActiveRecord(long day) {
        return recordUntilDay >= 0 && day < recordUntilDay;
    }

    public boolean isWanted() {
        return !wanted.isEmpty();
    }

    public boolean isJailed(long gameTime) {
        return jailUntil > gameTime;
    }

    public int wantedStars() {
        int s = 0;
        for (Article a : wanted) s = Math.max(s, a.administrative ? 1 : switch (a.severity) {
            case MINOR -> 2;
            case MEDIUM -> 3;
            case GRAVE -> 4;
            default -> 5;
        });
        return s;
    }

    public void clearQuest() {
        questId = "";
        questProgress = 0;
        questStay = 0;
        questTargets.clear();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("faction", faction.id);
        t.putInt("rank", rank);
        t.putInt("points", points);
        t.putLong("rankSince", rankSinceDay);
        t.putLong("balance", balance);
        t.putInt("civRep", civRep);
        t.putInt("crimRep", crimRep);
        t.putBoolean("everConvicted", everConvicted);
        t.putLong("recordUntil", recordUntilDay);
        ListTag w = new ListTag();
        for (Article a : wanted) w.add(StringTag.valueOf(a.name()));
        t.put("wanted", w);
        t.putLong("jailUntil", jailUntil);
        t.putInt("jailType", jailType);
        t.putBoolean("legalEdu", legalEducation);
        t.putInt("lectures", lectures);
        t.putString("job", job);
        t.putInt("jobCounter", jobCounter);
        t.putLong("lastApply", lastApplyDay);
        t.putLong("lastGear", lastGearDay);
        t.putLong("lastSalary", lastSalaryDay);
        t.putString("quest", questId);
        t.putInt("questProgress", questProgress);
        t.putInt("questStay", questStay);
        ListTag qt = new ListTag();
        for (BlockPos p : questTargets) qt.add(NbtUtils.writeBlockPos(p));
        t.put("questTargets", qt);
        return t;
    }

    public static PlayerRecord load(CompoundTag t) {
        PlayerRecord r = new PlayerRecord();
        r.faction = Faction.byId(t.getString("faction"));
        r.rank = t.getInt("rank");
        r.points = t.getInt("points");
        r.rankSinceDay = t.getLong("rankSince");
        r.balance = t.getLong("balance");
        r.civRep = t.getInt("civRep");
        r.crimRep = t.getInt("crimRep");
        r.everConvicted = t.getBoolean("everConvicted");
        r.recordUntilDay = t.getLong("recordUntil");
        ListTag w = t.getList("wanted", Tag.TAG_STRING);
        for (int i = 0; i < w.size(); i++) r.wanted.add(Article.byName(w.getString(i)));
        r.jailUntil = t.getLong("jailUntil");
        r.jailType = t.getInt("jailType");
        r.legalEducation = t.getBoolean("legalEdu");
        r.lectures = t.getInt("lectures");
        r.job = t.getString("job");
        r.jobCounter = t.getInt("jobCounter");
        r.lastApplyDay = t.getLong("lastApply");
        r.lastGearDay = t.getLong("lastGear");
        r.lastSalaryDay = t.getLong("lastSalary");
        r.questId = t.getString("quest");
        r.questProgress = t.getInt("questProgress");
        r.questStay = t.getInt("questStay");
        ListTag qt = t.getList("questTargets", Tag.TAG_COMPOUND);
        for (int i = 0; i < qt.size(); i++) r.questTargets.add(NbtUtils.readBlockPos(qt.getCompound(i)));
        return r;
    }
}
