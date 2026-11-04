package ru.gorod.rusgorod.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.entity.Profession;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.law.CriminalCase;
import ru.gorod.rusgorod.world.CityManager;

import java.util.List;

import static ru.gorod.rusgorod.entity.ai.Activity.Type.*;

/**
 * Распорядок дня NPC. Время Minecraft: 0 = 06:00, 6000 = 12:00, 12000 = 18:00, 18000 = 00:00.
 * Каждая фракция занимается тем, чем занимается в реальности.
 */
public final class ActivityPlanner {

    public static final String[] SLOGANS = {"Почините дороги!", "Зарплаты — вовремя!", "Верните автобус №5!", "Нет точечной застройке!",
            "Хотим чистый двор!", "Тарифы ЖКХ — по справедливости!", "Глава, выйди к народу!", "Нам нужна поликлиника!"};

    public static Activity plan(CitizenEntity c, ServerLevel level, GorodData d) {
        RandomSource r = c.getRandom();
        long t = level.getDayTime() % 24000L;
        boolean night = t >= 13500L && t < 23000L;
        boolean workHours = t >= 1000L && t < 11000L;

        if (c.isProtesting()) {
            Activity a = new Activity(PROTEST, around(d.loc("square"), r, 5), 400);
            a.radius = 3;
            return a;
        }

        Faction f = c.getFaction();
        switch (f) {
            case CIVILIAN -> {
                if (night) return new Activity(HOME, c.home, 2400).speed(0.7);
                if (workHours && c.profession != Profession.UNEMPLOYED) {
                    // мелкие правонарушения от склонных к ним
                    if (c.criminality > 0.65f && r.nextFloat() < 0.05f)
                        return new Activity(STEAL, around(d.loc(r.nextBoolean() ? "shop" : "market"), r, 2), 60);
                    return new Activity(WORK, around(c.work, r, 3), 1200 + r.nextInt(800));
                }
                if (c.criminality > 0.5f && r.nextFloat() < 0.07f)
                    return new Activity(DRINK, around(d.loc("park"), r, 6), 600);
                int roll = r.nextInt(10);
                if (roll < 3) return new Activity(LEISURE, around(d.loc("park"), r, 7), 600);
                if (roll < 5) return new Activity(LEISURE, around(d.loc("shop"), r, 3), 300);
                if (roll < 6) return new Activity(PRAY, around(d.loc("church"), r, 3), 600);
                if (roll < 8) return new Activity(LEISURE, around(d.randomNode(r), r, 2), 200);
                return new Activity(HOME, c.home, 1200);
            }
            case UMVD -> {
                if (!c.isOnDuty()) return new Activity(HOME, c.home, 2400);
                if (d.rallyUntil > level.getGameTime() && !d.rallyNotified && r.nextFloat() < 0.5f) {
                    CitizenEntity p = findNear(level, d.loc("square"), 18, x -> x.isProtesting());
                    if (p != null) return new Activity(DISPERSE, p.blockPosition(), 20).target(p).speed(1.0);
                }
                if (d.rallyUntil > level.getGameTime() && r.nextFloat() < 0.4f)
                    return new Activity(GUARD, around(d.loc("square"), r, 7), 600);
                int roll = r.nextInt(10);
                if (roll < 5) return new Activity(PATROL, d.randomNode(r), 80);
                if (roll < 8) {
                    CitizenEntity s = findNear(level, c.blockPosition(), 28, x -> x != c && (x.getFaction() == Faction.CIVILIAN || x.getFaction().isCriminal()) && !x.isJailed());
                    if (s != null) return new Activity(CHECK_DOCS, s.blockPosition(), 60).target(s);
                }
                return new Activity(WORK, around(d.loc("police"), r, 3), 600);
            }
            case UGIBDD -> {
                if (!c.isOnDuty()) return new Activity(HOME, c.home, 2400);
                if (r.nextInt(10) < 6) return new Activity(POST, around(d.loc("gibdd_post"), r, 3), 1200);
                CitizenEntity s = findNear(level, c.blockPosition(), 24, x -> x != c && x.getFaction() != Faction.UGIBDD && !x.getFaction().isState() && !x.isJailed());
                if (s != null) return new Activity(CHECK_DOCS, s.blockPosition(), 60).target(s);
                return new Activity(PATROL, d.randomNode(r), 100);
            }
            case UFSIN -> {
                if (r.nextInt(3) == 0) return new Activity(GUARD, d.loc("sizo_tower"), 1200);
                if (!d.sizoCells.isEmpty() && r.nextBoolean())
                    return new Activity(PATROL, d.sizoCells.get(r.nextInt(d.sizoCells.size())).north(2), 100);
                return new Activity(GUARD, around(d.loc("sizo"), r, 3), 800);
            }
            case SK -> {
                if (night) return new Activity(HOME, c.home, 2400);
                CriminalCase cs = CrimeSystem.pickCaseFor(d, Faction.SK, r);
                if (cs != null && r.nextBoolean())
                    return new Activity(INVESTIGATE, around(cs.pos, r, 2), 400).caseId(cs.id);
                return new Activity(WORK, around(d.loc("sk"), r, 3), 1200);
            }
            case PROK -> {
                if (night) return new Activity(HOME, c.home, 2400);
                int roll = r.nextInt(10);
                if (roll < 2) return new Activity(INSPECT, around(d.loc("police"), r, 3), 600);
                if (roll < 4) return new Activity(INSPECT, around(d.loc("sizo"), r, 3), 600);
                if (roll < 5) return new Activity(INSPECT, around(d.loc("sk"), r, 3), 600);
                return new Activity(WORK, around(d.loc("prok"), r, 3), 1200);
            }
            case UFSB -> {
                if (night && r.nextBoolean()) return new Activity(HOME, c.home, 2400);
                int roll = r.nextInt(10);
                if (roll < 4) {
                    String[] targets = {"restaurant", "cafe", "garages", "bikers", "market"};
                    return new Activity(SURVEIL, around(d.loc(targets[r.nextInt(targets.length)]), r, 8), 1200);
                }
                if (roll < 5) return new Activity(INSPECT, around(d.loc("army_hq"), r, 3), 800);
                return new Activity(WORK, around(d.loc("fsb"), r, 3), 1200);
            }
            case GOV -> {
                if (night) return new Activity(HOME, c.home, 2400);
                if (t > 5000 && t < 7000 && r.nextInt(3) == 0)
                    return new Activity(RECEPTION, around(d.loc("square"), r, 4), 800);
                return new Activity(WORK, around(d.loc("admin"), r, 3), 1200);
            }
            case ARMY -> {
                if (night) return new Activity(HOME, c.home, 2400);
                if (t < 1200) return new Activity(FORMATION, around(d.loc("army_plac"), r, 5), 600);
                if (t > 6000 && t < 6800) return new Activity(MESS, around(d.loc("army_mess"), r, 3), 600);
                if (c.getId() % 4 == 0) return new Activity(GUARD, around(d.loc("army_kpp"), r, 1), 1600);
                int roll = r.nextInt(3);
                if (roll == 0) return new Activity(DRILL, around(d.loc("army_plac"), r, 6), 900);
                if (roll == 1) return new Activity(RANGE, around(d.loc("army_range"), r, 3), 900);
                return new Activity(WORK, around(d.loc("army_hq"), r, 3), 900);
            }
            case RUS_MAFIA -> {
                if (night) return r.nextBoolean() ? new Activity(HOME, c.home, 2400) : new Activity(HANG_OUT, around(d.loc("restaurant"), r, 3), 1500);
                int roll = r.nextInt(10);
                if (roll < 3) {
                    CitizenEntity v = findNear(level, d.loc(r.nextBoolean() ? "shop" : "market"), 20, x -> x.profession.isTrader() && !x.isJailed());
                    if (v != null) return new Activity(EXTORT, v.blockPosition(), 60).target(v);
                }
                if (roll < 4) return new Activity(DEAL, around(d.loc("park"), r, 8), 400);
                return new Activity(HANG_OUT, around(d.loc("restaurant"), r, 3), 1200);
            }
            case KAVKAZ_MAFIA -> {
                if (night) return r.nextBoolean() ? new Activity(HOME, c.home, 2400) : new Activity(HANG_OUT, around(d.loc("cafe"), r, 3), 1500);
                int roll = r.nextInt(10);
                if (roll < 3) {
                    CitizenEntity v = findNear(level, d.loc("market"), 20, x -> x.profession.isTrader() && !x.isJailed());
                    if (v != null) return new Activity(EXTORT, v.blockPosition(), 60).target(v);
                }
                if (roll < 4) return new Activity(FENCE, around(d.loc("market"), r, 4), 400);
                if (roll < 7) return new Activity(HANG_OUT, around(d.loc("market"), r, 6), 1000);
                return new Activity(HANG_OUT, around(d.loc("cafe"), r, 3), 1000);
            }
            case SKINHEADS -> {
                if (!night && t < 8000) return new Activity(HANG_OUT, around(d.loc("garages"), r, 5), 1200);
                int roll = r.nextInt(10);
                if (roll < 2) return new Activity(VANDAL, d.randomNode(r), 80);
                if (roll < 3) return new Activity(HOOLIGAN, around(d.loc("park"), r, 6), 200);
                if (roll < 6) return new Activity(HANG_OUT, around(d.loc("park"), r, 8), 800);
                return new Activity(HOME, c.home, 2000);
            }
            case BIKERS -> {
                if (night) {
                    if (r.nextInt(3) > 0) return new Activity(RIDE, d.randomNode(r), 20).speed(1.25);
                    return new Activity(HANG_OUT, around(d.loc("bikers"), r, 3), 1200);
                }
                if (r.nextInt(10) == 0) return new Activity(ARMS_DEAL, around(d.loc("market"), r, 5), 300);
                if (t > 9000) return new Activity(HOME, c.home, 1500);
                return new Activity(HANG_OUT, around(d.loc("bikers"), r, 4), 1200);
            }
        }
        return new Activity(IDLE, c.blockPosition(), 200);
    }

    /** Действие по прибытии на место. */
    public static void onArrive(CitizenEntity c, Activity a, ServerLevel level, GorodData d) {
        RandomSource r = c.getRandom();
        switch (a.type) {
            case PROTEST -> {
                c.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_BANNER));
                if (r.nextInt(3) == 0) c.say(SLOGANS[r.nextInt(SLOGANS.length)]);
            }
            case CHECK_DOCS -> {
                if (a.target instanceof CitizenEntity s) {
                    s.getNavigation().stop();
                    s.getLookControl().setLookAt(c);
                    c.say("Здравствуйте. " + c.getFaction().rankName(c.rank) + " " + c.lastName
                            + ". Предъявите, пожалуйста, документы.");
                    if (s.isWanted()) {
                        c.say("Гражданин, вы задержаны. Пройдёмте в отдел.");
                        c.setTarget(s);
                    } else if (s.getFaction().isCriminal() && r.nextFloat() < 0.25f && !s.getMainHandItem().isEmpty()) {
                        // Обнаружено оружие при досмотре (ст. 27.7 КоАП / ст. 13 ФЗ «О полиции»)
                        s.addWanted(Article.UK_222);
                        CrimeSystem.registerWitnessedCase(level, s, Article.UK_222, s.blockPosition());
                        c.say("При себе — оружие. Руки за голову!");
                        c.setTarget(s);
                    } else {
                        s.say(r.nextBoolean() ? "Вот, пожалуйста." : "А что случилось, товарищ начальник?");
                    }
                }
            }
            case DISPERSE -> {
                if (a.target instanceof CitizenEntity s && s.isProtesting()) {
                    c.say("Мероприятие не согласовано! Требую прекратить (ст. 20.2 КоАП РФ).");
                    s.addWanted(Article.KOAP_20_2);
                    c.setTarget(s);
                }
            }
            case EXTORT -> {
                if (a.target instanceof CitizenEntity v) {
                    c.say(r.nextBoolean() ? "За крышу платить будем? Неделя прошла." : "Слышь, хозяин, должок за место.");
                    v.say(r.nextBoolean() ? "Опять вы... Последнее забираете." : "Хорошо, хорошо, вот деньги.");
                    d.addFunds(c.getFaction(), 4000 + r.nextInt(4000));
                    CrimeSystem.commitByNpc(level, c, Article.UK_163, v);
                }
            }
            case STEAL -> {
                if (r.nextFloat() < 0.6f) {
                    CrimeSystem.commitByNpc(level, c, Article.UK_158, null);
                }
            }
            case DRINK -> CrimeSystem.commitByNpc(level, c, Article.KOAP_20_20, null);
            case DEAL -> {
                d.addFunds(c.getFaction(), 6000);
                CrimeSystem.commitByNpc(level, c, Article.UK_228_1, null);
            }
            case FENCE -> {
                d.addFunds(c.getFaction(), 3000);
                CrimeSystem.commitByNpc(level, c, Article.UK_175, null);
            }
            case ARMS_DEAL -> {
                d.addFunds(c.getFaction(), 8000);
                CrimeSystem.commitByNpc(level, c, Article.UK_222, null);
            }
            case VANDAL -> {
                c.say("Ха, красота!");
                CrimeSystem.commitByNpc(level, c, Article.UK_214, null);
            }
            case HOOLIGAN -> {
                c.say("Чё смотришь?!");
                CrimeSystem.commitByNpc(level, c, r.nextBoolean() ? Article.KOAP_20_1 : Article.UK_213, null);
            }
            case INVESTIGATE -> {
                CriminalCase cs = d.findCase(a.caseId);
                if (cs != null) {
                    c.say("Осмотр места происшествия (ст. 176 УПК РФ). Понятые, подойдите.");
                    CrimeSystem.progressCase(level, d, cs, 20);
                }
            }
            case INSPECT -> {
                if (c.getFaction() == Faction.PROK) c.say("Прокурорская проверка. Журналы КУСП и книгу учёта задержанных — на стол.");
                for (CriminalCase cs : d.cases) {
                    if (cs.stage == CriminalCase.Stage.PROSECUTOR) CrimeSystem.progressCase(level, d, cs, 25);
                }
            }
            case SURVEIL -> {
                // Оперативно-розыскные мероприятия: наблюдение (ст. 6 ФЗ «Об ОРД»)
                if (r.nextFloat() < 0.15f) {
                    CitizenEntity crim = findNear(level, c.blockPosition(), 20, x -> x.getFaction().isCriminal() && !x.isJailed() && !x.isWanted());
                    if (crim != null) {
                        Article art = crim.getFaction() == Faction.SKINHEADS ? Article.UK_282_1 : Article.UK_210;
                        crim.addWanted(art);
                        CrimeSystem.registerWitnessedCase(level, crim, art, crim.blockPosition());
                        d.addNews("УФСБ: по результатам ОРМ задокументирована деятельность группировки, возбуждено дело по " + art.code + ".");
                    }
                }
            }
            case RECEPTION -> {
                c.say("Приём граждан по личным вопросам (ФЗ № 59-ФЗ). Подходите.");
                CityManager.changeSatisfaction(d, 0.4);
            }
            case FORMATION -> {
                if (c.rank >= 8 && r.nextInt(3) == 0) c.say("Батальон, равняйсь! Смирно!");
            }
            case RANGE -> {
                if (r.nextInt(3) == 0) c.say("Огневой рубеж — занять! Короткими — огонь!");
            }
            case POST -> {
                if (r.nextInt(4) == 0) c.say("Пост ДПС. Скоростной режим — 60 км/ч (п. 10.2 ПДД).");
            }
            case GUARD -> {
                if (c.getFaction() == Faction.ARMY && r.nextInt(4) == 0) c.say("Стой, кто идёт! (Устав гарнизонной и караульной служб)");
            }
            case PRAY -> {
                if (r.nextInt(5) == 0) c.say("Господи, помилуй.");
            }
            default -> {
            }
        }
    }

    /** Завершение занятия. */
    public static void onComplete(CitizenEntity c, Activity a, ServerLevel level, GorodData d) {
        if (a.type == PROTEST && !c.isProtesting()) {
            c.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        if (a.type == WORK && c.getFaction() == Faction.CIVILIAN && c.profession != Profession.STUDENT && c.profession != Profession.PENSIONER) {
            d.budget += 150; // НДФЛ в местный бюджет (ст. 61.2 БК РФ)
        }
    }

    public static boolean isMovingActivity(Activity.Type t) {
        return t == PATROL || t == RIDE;
    }

    // -------------------------------------------------------------------------------------------

    public static BlockPos around(BlockPos p, RandomSource r, int spread) {
        if (spread <= 0) return p;
        return p.offset(r.nextInt(spread * 2 + 1) - spread, 0, r.nextInt(spread * 2 + 1) - spread);
    }

    public static CitizenEntity findNear(ServerLevel level, BlockPos center, int radius, java.util.function.Predicate<CitizenEntity> pred) {
        if (!level.isLoaded(center)) return null;
        List<CitizenEntity> list = level.getEntitiesOfClass(CitizenEntity.class, new AABB(center).inflate(radius), pred);
        if (list.isEmpty()) return null;
        return list.get(level.random.nextInt(list.size()));
    }

    public static boolean playerNear(ServerLevel level, BlockPos p, double radius) {
        for (Player pl : level.players()) {
            if (pl.distanceToSqr(p.getX(), p.getY(), p.getZ()) < radius * radius) return true;
        }
        return false;
    }

    private ActivityPlanner() {
    }
}
