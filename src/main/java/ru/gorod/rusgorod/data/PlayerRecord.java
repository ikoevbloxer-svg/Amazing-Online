package ru.gorod.rusgorod.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.study.LawSchool;

import java.util.ArrayList;
import java.util.List;

/** Состояние игрока в городе: фракция, звание, деньги, судимость, розыск, задание, работа, учёба. */
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
    public String job = "";
    public int jobCounter;
    public long lastApplyDay = -100;
    public long lastGearDay = -100;
    public long lastSalaryDay = -1;
    /** Носит форму своего ведомства (видно другим игрокам). */
    public boolean uniform;
    /** Неоплаченные административные штрафы и срок уплаты (ст. 32.2 КоАП — 60 дней, у нас 3 игровых суток). */
    public long fineDebt;
    public long fineDueDay = -1;
    /** Стаж работы по юридической профессии, игровые сутки (для назначения судьёй). */
    public int legalExpDays;
    public boolean cuffed;
    /** Время судебного заседания по делу игрока (-1 — нет). */
    public long trialAt = -1;
    public String trialArticles = "";

    // ---- учёба
    public final int[] lectures = new int[LawSchool.SUBJECTS.length];
    public int examsPassed;
    public int lectureTicks;
    public int lectureSubject = -1;
    public boolean enrolled;
    /** Лицензия ЛОА (ФЗ-150). */
    public boolean gunLicense;
    public int falseCalls;
    public long lastCallTime = -10000;
    public int taxiRides;
    public int kills;

    // ---- задание
    public String questId = "";
    public int questProgress;
    public int questStay;
    public final List<BlockPos> questTargets = new ArrayList<>();

    // ---- не сохраняется: идущий экзамен
    public transient int examSubject = -1;
    public transient int[] examQuestions = new int[0];
    public transient int examIndex;
    public transient int examCorrect;

    public boolean hasActiveRecord(long day) {
        return recordUntilDay >= 0 && day < recordUntilDay;
    }

    public boolean isWanted() {
        return !wanted.isEmpty();
    }

    public boolean isJailed(long gameTime) {
        return jailUntil > gameTime;
    }

    public boolean examPassed(int subject) {
        return (examsPassed & (1 << subject)) != 0;
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
        t.putString("job", job);
        t.putInt("jobCounter", jobCounter);
        t.putLong("lastApply", lastApplyDay);
        t.putLong("lastGear", lastGearDay);
        t.putLong("lastSalary", lastSalaryDay);
        t.putBoolean("uniform", uniform);
        t.putLong("fineDebt", fineDebt);
        t.putLong("fineDue", fineDueDay);
        t.putInt("legalExp", legalExpDays);
        t.putBoolean("cuffed", cuffed);
        t.putLong("trialAt", trialAt);
        t.putString("trialArts", trialArticles);
        t.putIntArray("lectures", lectures);
        t.putInt("exams", examsPassed);
        t.putInt("lectureTicks", lectureTicks);
        t.putInt("lectureSubject", lectureSubject);
        t.putBoolean("enrolled", enrolled);
        t.putBoolean("gunLicense", gunLicense);
        t.putInt("falseCalls", falseCalls);
        t.putLong("lastCall", lastCallTime);
        t.putInt("taxiRides", taxiRides);
        t.putInt("kills", kills);
        t.putString("quest", questId);
        t.putInt("questProgress", questProgress);
        t.putInt("questStay", questStay);
        ListTag qt = new ListTag();
        for (BlockPos p : questTargets) qt.add(NbtUtils.writeBlockPos(p));
        t.put("questTargets", qt);
        // для клиента: идущий экзамен
        t.putInt("examSubject", examSubject);
        return t;
    }

    public static PlayerRecord load(CompoundTag t) {
        PlayerRecord r = new PlayerRecord();
        r.faction = Faction.byId(t.getString("faction"));
        r.rank = Math.min(t.getInt("rank"), r.faction.topRank());
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
        r.job = t.getString("job");
        r.jobCounter = t.getInt("jobCounter");
        r.lastApplyDay = t.getLong("lastApply");
        r.lastGearDay = t.getLong("lastGear");
        r.lastSalaryDay = t.getLong("lastSalary");
        r.uniform = t.getBoolean("uniform");
        r.fineDebt = t.getLong("fineDebt");
        r.fineDueDay = t.contains("fineDue") ? t.getLong("fineDue") : -1;
        r.legalExpDays = t.getInt("legalExp");
        r.cuffed = t.getBoolean("cuffed");
        r.trialAt = t.contains("trialAt") ? t.getLong("trialAt") : -1;
        r.trialArticles = t.getString("trialArts");
        int[] lec = t.getIntArray("lectures");
        System.arraycopy(lec, 0, r.lectures, 0, Math.min(lec.length, r.lectures.length));
        r.examsPassed = t.getInt("exams");
        r.lectureTicks = t.getInt("lectureTicks");
        r.lectureSubject = t.contains("lectureSubject") ? t.getInt("lectureSubject") : -1;
        r.enrolled = t.getBoolean("enrolled");
        r.gunLicense = t.getBoolean("gunLicense");
        r.falseCalls = t.getInt("falseCalls");
        r.lastCallTime = t.contains("lastCall") ? t.getLong("lastCall") : -10000;
        r.taxiRides = t.getInt("taxiRides");
        r.kills = t.getInt("kills");
        r.questId = t.getString("quest");
        r.questProgress = t.getInt("questProgress");
        r.questStay = t.getInt("questStay");
        ListTag qt = t.getList("questTargets", Tag.TAG_COMPOUND);
        for (int i = 0; i < qt.size(); i++) r.questTargets.add(NbtUtils.readBlockPos(qt.getCompound(i)));
        r.examSubject = t.contains("examSubject") ? t.getInt("examSubject") : -1;
        return r;
    }
}
