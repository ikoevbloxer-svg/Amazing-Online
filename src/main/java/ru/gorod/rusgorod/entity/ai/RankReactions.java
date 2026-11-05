package ru.gorod.rusgorod.entity.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.social.Address;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Реакция NPC на проходящих мимо игроков с учётом званий и должностей:
 * воинское приветствие старшему (ст. 44–50 Строевого устава), представление, «уважение» в ОПГ,
 * опаска гражданских, охрана КПП воинской части.
 */
public final class RankReactions {
    private static final Map<UUID, Integer> BASE_WARNINGS = new HashMap<>();

    public static void tickNpc(ServerLevel level, CitizenEntity c) {
        if (c.getTarget() != null) return;
        GorodData d = GorodData.get(level);
        long now = level.getGameTime();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, c.getBoundingBox().inflate(5))) {
            if (p.isSpectator() || !c.hasLineOfSight(p)) continue;
            // часовой на КПП — отдельная логика, без паузы
            if (c.getFaction() == Faction.ARMY && d.inBase(p.blockPosition()) && guard(level, d, c, p)) continue;
            Long last = c.greeted.get(p.getUUID());
            if (last != null && now - last < 3600) continue;
            c.greeted.put(p.getUUID(), now);
            if (c.greeted.size() > 40) c.greeted.clear();
            react(level, d, c, p, d.player(p.getUUID()));
            return;
        }
    }

    private static void react(ServerLevel level, GorodData d, CitizenEntity c, ServerPlayer p, PlayerRecord r) {
        Faction cf = c.getFaction();
        Faction pf = r.faction;
        String addr = Address.toPlayer(c, p, r);
        var rnd = c.getRandom();
        c.getLookControl().setLookAt(p);

        // ---- личные отношения важнее формальностей
        int rel = c.relation(p.getUUID());
        String name = p.getName().getString();
        if (rel <= ru.gorod.rusgorod.social.Relations.ENEMY && c.isCombatant() && !cf.isState()) {
            c.say(rnd.nextBoolean() ? "А, это ты... Ну всё, держись!" : "Я тебя запомнил, " + name + ". Получай!");
            c.setTarget(p);
            return;
        }
        if (rel <= ru.gorod.rusgorod.social.Relations.DISLIKE) {
            if (rnd.nextBoolean()) c.say(rnd.nextBoolean() ? "Опять этот " + name + "..." : "Тьфу. Глаза б мои не видели.");
            return;
        }
        if (rel >= ru.gorod.rusgorod.social.Relations.KNOWN && !(cf.insignia && pf.insignia && Address.sameService(cf, pf))) {
            String[] hi = rel >= ru.gorod.rusgorod.social.Relations.FRIEND
                    ? new String[]{"О, " + name + "! Сколько лет, сколько зим!", name + ", дружище! Заходи как-нибудь.", "Привет, " + name + "! Как жизнь?"}
                    : new String[]{"Здравствуй, " + name + ".", "О, " + name + ", привет.", "Добрый день, " + name + "!"};
            c.say(hi[rnd.nextInt(hi.length)]);
            return;
        }

        // ---- одна служба: чинопочитание
        if (cf.isState() && pf.isState() && Address.sameService(cf, pf)) {
            int gp = Address.grade(pf, r.rank), gc = Address.grade(cf, c.rank);
            if (gp > gc) {
                if (cf.insignia) c.setAnim(CitizenEntity.ANIM_SALUTE, 40);
                String greet = cf == Faction.GOV ? "Добрый день, " + addr + "!" : (cf == Faction.COURT ? "Здравствуйте, коллега." : "Здравия желаю, " + addr + "!");
                if (r.rank >= pf.topRank() - 1 && cf.insignia) {
                    greet += " " + (cf == Faction.ARMY ? "Без происшествий, личный состав занимается по плану!" : "За время дежурства происшествий не случилось!");
                }
                c.say(greet);
            } else if (gp < gc) {
                if (rnd.nextInt(3) == 0) c.say(rnd.nextBoolean() ? "Здравствуйте, " + addr + ". Службу несёте?" : addr + ", форма одежды в порядке? Подтянитесь.");
            } else if (rnd.nextBoolean()) {
                c.say(rnd.nextBoolean() ? "Привет, коллега." : "Здорово! Как смена?");
            }
            return;
        }
        // ---- военнослужащие и сотрудники разных ведомств
        if (cf.insignia && pf.insignia && r.uniform) {
            if (Address.grade(pf, r.rank) > Address.grade(cf, c.rank)) {
                c.setAnim(CitizenEntity.ANIM_SALUTE, 30);
                c.say("Здравия желаю, " + addr + "!");
            }
            return;
        }
        // ---- криминальная иерархия
        if (cf.isCriminal()) {
            if (pf == cf) {
                if (r.rank > c.rank) c.say(rnd.nextBoolean() ? "Уважение, " + addr + "." : "Моё почтение, " + addr + ". Всё ровно на районе.");
                else if (r.rank < c.rank && rnd.nextBoolean()) c.say("Здорово, " + addr + ". Не расслабляйся.");
                return;
            }
            if (pf.isCriminal() && pf.isHostileTo(cf)) {
                c.say(rnd.nextBoolean() ? "Ты чё тут забыл? Это наша территория." : "Вали отсюда, пока цел.");
                return;
            }
            if (pf.isLawEnforcement() && r.uniform) {
                c.say(rnd.nextBoolean() ? "Начальник, мы ничего не делаем." : "Мусора... Тихо, пацаны.");
                return;
            }
            if (pf == Faction.CIVILIAN && rnd.nextInt(4) == 0) c.say(rnd.nextBoolean() ? "Чё смотришь?" : "Закурить не найдётся?");
            return;
        }
        // ---- гражданские
        if (cf == Faction.CIVILIAN) {
            if (r.wantedStars() >= 3 && !r.uniform) {
                c.say("Это же тот, кого полиция ищет... Мамочки.");
                return;
            }
            if (pf.isState() && r.uniform && rnd.nextBoolean()) {
                c.say("Здравствуйте, " + addr + "!");
                return;
            }
            if (pf == Faction.COURT && rnd.nextBoolean()) {
                c.say("Добрый день, Ваша честь.");
                return;
            }
            if (rnd.nextInt(5) == 0) c.say(c.female ? "Здравствуйте." : "Добрый день.");
            return;
        }
        // ---- сотрудник при исполнении — гражданину / разыскиваемому
        if (cf.canArrest() && r.isWanted()) {
            c.say("Гражданин, стоять! Вы в розыске!");
            c.setTarget(p);
            return;
        }
        if (cf.isState() && rnd.nextInt(4) == 0) c.say("Здравствуйте, " + addr + ".");
    }

    /** Часовой: окрик по Уставу гарнизонной и караульной служб, затем нарушение пропускного режима. */
    private static boolean guard(ServerLevel level, GorodData d, CitizenEntity c, ServerPlayer p) {
        PlayerRecord r = d.player(p.getUUID());
        if (r.faction == Faction.ARMY || r.faction == Faction.UFSB || p.isCreative()) return false;
        long now = level.getGameTime();
        Long last = c.greeted.get(p.getUUID());
        if (last != null && now - last < 100) return true;
        c.greeted.put(p.getUUID(), now);
        int w = BASE_WARNINGS.merge(p.getUUID(), 1, Integer::sum);
        c.getLookControl().setLookAt(p);
        switch (w) {
            case 1 -> c.say("Стой, кто идёт! Посторонним вход на территорию части запрещён!");
            case 2 -> c.say("Стой, стрелять буду! Немедленно покиньте территорию!");
            default -> {
                c.say("Нарушитель на охраняемом объекте! Караул, в ружьё! (ст. 20.17 КоАП РФ)");
                CrimeSystem.commitByPlayer(p, Article.KOAP_20_17, null);
                BASE_WARNINGS.remove(p.getUUID());
            }
        }
        return true;
    }

    public static void forgetWarnings(UUID id) {
        BASE_WARNINGS.remove(id);
    }

    private RankReactions() {
    }
}
