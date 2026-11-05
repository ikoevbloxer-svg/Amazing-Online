package ru.gorod.rusgorod.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.entity.ai.ActivityPlanner;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.util.Scheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * Случайные городские события рядом с игроками: карманник, пьяная драка у рюмочной,
 * «стрелка» между ОПГ, совместная облава полиции и ФСБ.
 */
public final class CityEvents {

    public static void tick(ServerLevel level) {
        GorodData d = GorodData.get(level);
        if (!d.generated || level.getGameTime() % 2400 != 1200) return;
        List<ServerPlayer> inCity = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (d.inCity(p.blockPosition()) && !p.isSpectator()) inCity.add(p);
        if (inCity.isEmpty()) return;
        RandomSource r = level.random;
        if (r.nextInt(3) != 0) return;
        ServerPlayer p = inCity.get(r.nextInt(inCity.size()));
        long t = level.getDayTime() % 24000L;
        boolean night = t >= 13000L && t < 23000L;
        int roll = r.nextInt(10);
        if (night && roll < 3) strelka(level, d, r);
        else if (roll < 5) pickpocket(level, d, p, r);
        else if (roll < 7) barFight(level, d, r);
        else if (roll < 8) raid(level, d, r);
        else pickpocket(level, d, p, r);
    }

    /** Карманник: любой может задержать (ст. 38 УК РФ) — ослабьте его ударами, он сдастся. */
    private static void pickpocket(ServerLevel level, GorodData d, ServerPlayer p, RandomSource r) {
        List<CitizenEntity> civs = level.getEntitiesOfClass(CitizenEntity.class, p.getBoundingBox().inflate(28),
                c -> c.getFaction() == Faction.CIVILIAN && !c.isBusy() && c.isAlive());
        if (civs.size() < 2) return;
        CitizenEntity thief = civs.get(0);
        for (CitizenEntity c : civs) if (c.criminality > thief.criminality) thief = c;
        CitizenEntity victim = civs.get(r.nextInt(civs.size()));
        if (victim == thief) return;
        final CitizenEntity th = thief;
        th.addWanted(Article.UK_158);
        th.bounty = 1500;
        CrimeSystem.registerWitnessedCase(level, th, Article.UK_158, th.blockPosition());
        victim.say("Держи вора! Кошелёк украли!", true);
        victim.setAnim(CitizenEntity.ANIM_POINT, 40);
        Vec3 away = DefaultRandomPos.getPosAway(th, 24, 7, victim.position());
        if (away != null) th.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
        for (ServerPlayer o : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(40))) {
            o.sendSystemMessage(Component.literal("[Событие] Карманник! " + th.fullName() + " убегает. Задержите его — ослабьте ударами, и он сдастся "
                    + "(ст. 38 УК РФ: задержание лица, совершившего преступление). Награда от потерпевшего — 1 500 ₽.").withStyle(ChatFormatting.GOLD));
        }
    }

    /** Пьяная драка у рюмочной. */
    private static void barFight(ServerLevel level, GorodData d, RandomSource r) {
        BlockPos bar = d.loc("bar");
        if (!level.isLoaded(bar)) return;
        List<CitizenEntity> civs = level.getEntitiesOfClass(CitizenEntity.class, new AABB(bar).inflate(14),
                c -> c.getFaction() == Faction.CIVILIAN && !c.isBusy());
        if (civs.size() < 2) return;
        CitizenEntity a = civs.get(0), b = civs.get(1);
        a.drunk = true;
        b.drunk = true;
        a.say("Ты на кого батон крошишь?!", true);
        Scheduler.later(40, () -> {
            b.say("Да я тебя!..", true);
            a.setTarget(b);
            b.setTarget(a);
            a.addWanted(Article.KOAP_20_1);
            b.addWanted(Article.KOAP_20_1);
        });
        CrimeSystem.notifyFaction(level, Faction.UMVD, "[Дежурная часть] Драка у рюмочной «Встреча» (X " + bar.getX() + ", Z " + bar.getZ() + "). Наряд — на выезд.", ChatFormatting.AQUA);
        d.addNews("Происшествие: пьяная драка у рюмочной «Встреча».");
    }

    /** «Стрелка» русской и кавказской ОПГ у гаражей. */
    private static void strelka(ServerLevel level, GorodData d, RandomSource r) {
        BlockPos spot = d.loc("garages");
        if (!level.isLoaded(spot) || ActivityPlanner.playerNear(level, spot, 10)) return;
        List<CitizenEntity> rus = gang(level, d, Faction.RUS_MAFIA, 3);
        List<CitizenEntity> kav = gang(level, d, Faction.KAVKAZ_MAFIA, 3);
        if (rus.isEmpty() || kav.isEmpty()) return;
        int i = 0;
        for (CitizenEntity c : rus) place(c, spot.offset(-3, 0, i++ - 1));
        i = 0;
        for (CitizenEntity c : kav) place(c, spot.offset(3, 0, i++ - 1));
        CitizenEntity ra = rus.get(0), ka = kav.get(0);
        ra.say("Ну что, поговорим по-людски. Рынок — наш.", true);
        Scheduler.later(50, () -> ka.say("Рынок общий был всегда. Не тебе решать.", true));
        Scheduler.later(100, () -> ra.say("Последний раз говорю по-хорошему.", true));
        Scheduler.later(150, () -> {
            if (r.nextBoolean()) {
                ka.say("Ладно. Пусть старшие решают.", true);
            } else {
                ka.say("Тогда по-плохому!", true);
                for (int k = 0; k < Math.min(rus.size(), kav.size()); k++) {
                    rus.get(k).setTarget(kav.get(k));
                    kav.get(k).setTarget(rus.get(k));
                }
                d.addNews("Криминал: перестрелка у гаражного кооператива.");
            }
        });
        CrimeSystem.notifyFaction(level, Faction.UFSB, "[Оперативная информация] Сходка лидеров ОПГ у гаражей (X " + spot.getX() + ", Z " + spot.getZ() + ").", ChatFormatting.GOLD);
        CrimeSystem.notifyFaction(level, Faction.UMVD, "[Оперативная информация] У гаражей собираются вооружённые люди.", ChatFormatting.GOLD);
    }

    /** Совместная облава полиции и ФСБ на точке ОПГ. */
    private static void raid(ServerLevel level, GorodData d, RandomSource r) {
        String[] targets = {"restaurant", "cafe", "market", "bikers"};
        String key = targets[r.nextInt(targets.length)];
        BlockPos spot = d.loc(key);
        if (!level.isLoaded(spot)) return;
        List<CitizenEntity> crims = level.getEntitiesOfClass(CitizenEntity.class, new AABB(spot).inflate(16),
                c -> c.getFaction().isCriminal() && !c.isJailed());
        if (crims.isEmpty()) return;
        List<CitizenEntity> cops = new ArrayList<>();
        for (CitizenEntity c : level.getEntitiesOfClass(CitizenEntity.class, new AABB(spot).inflate(120),
                c -> c.getFaction().canArrest() && !c.isBusy())) {
            if (cops.size() >= 4) break;
            cops.add(c);
        }
        if (cops.isEmpty()) return;
        boolean watched = ActivityPlanner.playerNear(level, spot, 24);
        int n = 0;
        for (CitizenEntity cop : cops) {
            if (!watched) place(cop, spot.offset(n++ - 2, 0, 4));
            CitizenEntity target = crims.get(r.nextInt(crims.size()));
            if (!target.isWanted()) {
                Article a = target.getFaction() == Faction.SKINHEADS ? Article.UK_282_1 : target.getFaction() == Faction.BIKERS ? Article.UK_222 : Article.UK_210;
                target.addWanted(a);
                CrimeSystem.registerWitnessedCase(level, target, a, target.blockPosition());
            }
            cop.setTarget(target);
        }
        cops.get(0).say("Всем оставаться на местах! Работает полиция и ФСБ! Руки на стену!", true);
        d.addNews("Облава: полиция и УФСБ провели рейд — " + ru.gorod.rusgorod.social.ChatResponder.placeName(key) + ".");
        CrimeSystem.notifyFaction(level, Faction.UMVD, "[Дежурная часть] Проводится облава: " + ru.gorod.rusgorod.social.ChatResponder.placeName(key) + ". Свободным нарядам — на усиление.", ChatFormatting.AQUA);
    }

    private static List<CitizenEntity> gang(ServerLevel level, GorodData d, Faction f, int max) {
        AABB box = new AABB(d.minX, level.getMinBuildHeight(), d.minZ, d.maxX, level.getMaxBuildHeight(), d.maxZ);
        List<CitizenEntity> all = level.getEntitiesOfClass(CitizenEntity.class, box, c -> c.getFaction() == f && !c.isBusy());
        all.sort((a, b) -> Integer.compare(b.rank, a.rank));
        return all.size() > max ? new ArrayList<>(all.subList(0, max)) : all;
    }

    private static void place(CitizenEntity c, BlockPos p) {
        if (ActivityPlanner.playerNear((ServerLevel) c.level(), c.blockPosition(), 40)) {
            c.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 1.1);
        } else {
            c.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            c.talkUntil = c.level().getGameTime() + 200;
        }
    }

    private CityEvents() {
    }
}
