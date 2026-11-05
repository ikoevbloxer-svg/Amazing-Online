package ru.gorod.rusgorod.entity.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.entity.Profession;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.social.Address;
import ru.gorod.rusgorod.util.Scheduler;
import ru.gorod.rusgorod.world.CityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Встречи NPC друг с другом: короткие сценки с репликами и последствиями.
 * Сценка выбирается по паре фракций/званий/профессий.
 */
public final class Encounters {
    private record Line(boolean byA, String text, int anim) {
    }

    private static final class Script {
        final List<Line> lines = new ArrayList<>();
        Consumer<ServerLevel> outcome;

        Script a(String t) {
            lines.add(new Line(true, t, CitizenEntity.ANIM_TALK));
            return this;
        }

        Script b(String t) {
            lines.add(new Line(false, t, CitizenEntity.ANIM_TALK));
            return this;
        }

        Script aSalute(String t) {
            lines.add(new Line(true, t, CitizenEntity.ANIM_SALUTE));
            return this;
        }

        Script bSalute(String t) {
            lines.add(new Line(false, t, CitizenEntity.ANIM_SALUTE));
            return this;
        }

        Script then(Consumer<ServerLevel> o) {
            this.outcome = o;
            return this;
        }
    }

    public static void tryStart(ServerLevel level, CitizenEntity a) {
        RandomSource r = a.getRandom();
        if (r.nextInt(5) != 0) return;
        a.encounterCooldown = 200;
        boolean audience = !level.getEntitiesOfClass(Player.class, a.getBoundingBox().inflate(20)).isEmpty();
        if (!audience && r.nextInt(4) != 0) return;
        final CitizenEntity self = a;
        List<CitizenEntity> near = level.getEntitiesOfClass(CitizenEntity.class, a.getBoundingBox().inflate(5),
                o -> o != self && !o.isBusy() && !o.isTalking(level) && o.encounterCooldown <= 0 && o.hasLineOfSight(self));
        if (near.isEmpty()) return;
        CitizenEntity b = near.get(r.nextInt(near.size()));
        // старший по званию говорит вторым, младший начинает (приветствует первым — ст. 44 Строевого устава)
        if (Address.isSubordinateTo(b, a)) {
            CitizenEntity t = a;
            a = b;
            b = t;
        }
        Script s = pick(level, a, b, r);
        if (s == null || s.lines.isEmpty()) return;
        play(level, a, b, s, audience);
    }

    private static void play(ServerLevel level, CitizenEntity a, CitizenEntity b, Script s, boolean audience) {
        int gap = 45;
        long until = level.getGameTime() + (long) gap * (s.lines.size() + 1);
        a.talkUntil = until;
        b.talkUntil = until;
        a.encounterCooldown = 1200 + a.getRandom().nextInt(1200);
        b.encounterCooldown = 1200 + b.getRandom().nextInt(1200);
        a.getNavigation().stop();
        b.getNavigation().stop();
        for (int i = 0; i < s.lines.size(); i++) {
            Line line = s.lines.get(i);
            final CitizenEntity sp = line.byA() ? a : b;
            final CitizenEntity ls = line.byA() ? b : a;
            Scheduler.later(5 + i * gap, () -> {
                if (!sp.isAlive() || !ls.isAlive() || sp.distanceToSqr(ls) > 100) return;
                sp.getLookControl().setLookAt(ls);
                ls.getLookControl().setLookAt(sp);
                sp.getNavigation().stop();
                ls.getNavigation().stop();
                sp.setAnim(line.anim(), line.anim() == CitizenEntity.ANIM_SALUTE ? 35 : 30);
                if (audience) sp.say(line.text(), true);
            });
        }
        if (s.outcome != null) {
            Scheduler.later(5 + s.lines.size() * gap, () -> {
                if (a.isAlive() && b.isAlive()) s.outcome.accept(level);
            });
        }
    }

    private static Script pick(ServerLevel level, CitizenEntity a, CitizenEntity b, RandomSource r) {
        Faction fa = a.getFaction(), fb = b.getFaction();
        GorodData d = GorodData.get(level);
        String toB = Address.toNpc(a, b), toA = Address.toNpc(b, a);
        Script s = new Script();

        // ---------- одна служба: доклад младшего старшему
        if (fa.isState() && Address.isSubordinateTo(a, b)) {
            if (fa.insignia) {
                s.aSalute("Здравия желаю, " + toB + "!");
                s.b(pick(r, "Здравствуйте. Доложите обстановку.", "Вольно. Что нового?", "Здравия желаю. Как служба?"));
                s.a(switch (fa) {
                    case ARMY -> pick(r, "Личный состав на занятиях по боевой подготовке, больных нет!", "Караул заступил, замечаний нет!", "Рота на стрельбище, отстрелялись на «хорошо»!");
                    case UFSIN -> pick(r, "Проверка численности проведена, все на месте!", "Нарушений режима содержания не выявлено!");
                    case SK -> pick(r, "Дело по сто пятой готовлю к направлению прокурору.", "Назначил экспертизу, жду заключение.");
                    case PROK -> pick(r, "Проверку в отделе закончил, готовлю представление.", "Обвинительное заключение изучил, замечаний нет.");
                    case UFSB -> pick(r, "Объект под наблюдением, контакты фиксируем.", "Источник вышел на связь, информация подтверждается.");
                    default -> pick(r, "За время несения службы происшествий не случилось!", "Отработали ориентировку, задержанных двое.", "Наряд в полном составе, замечаний нет!");
                });
                s.b(pick(r, "Хорошо. Продолжайте.", "Добро. Свободны.", "Понял. Не расслабляйтесь."));
            } else {
                s.a("Добрый день, " + toB + ".");
                s.b(pick(r, "Здравствуйте. Отчёт по обращениям подготовили?", "Добрый. Что по благоустройству?"));
                s.a(pick(r, "Да, всё в работе, сроки по пятьдесят девятому закону соблюдаем.", "Подрядчик опять сорвал сроки, готовлю претензию."));
            }
            return s;
        }
        // ---------- коллеги одного звания
        if (fa.isState() && Address.sameService(fa, fb)) {
            switch (fa) {
                case ARMY -> s.a("Слышь, когда увольнительная?").b(pick(r, "Не раньше субботы.", "Старшина сказал — по итогам проверки."));
                case UMVD, UGIBDD -> s.a(pick(r, "Как смена?", "Видел ориентировку?")).b(pick(r, "Два вызова, оба бытовуха.", "Да, фоторобот так себе."));
                case SK -> s.a("Сколько у тебя дел в производстве?").b(pick(r, "Двенадцать. Сроки горят.", "Девять, по двум продление."));
                case PROK -> s.a("Проверку в ОВД закончил?").b("Пишу представление об устранении нарушений.");
                case UFSIN -> s.a("Подъём провёл?").b("Провёл, все на месте.");
                case UFSB -> s.a("...").b("Не здесь.");
                case COURT -> s.a("Сколько дел в списке на сегодня?").b("Девять, из них два — с продлением стражи.");
                case GOV -> s.a("Бюджет утвердили?").b(d.budget < 0 ? "Какой там... дефицит." : "Дума утвердила, в целом без изменений.");
                default -> s.a("Привет.").b("Привет.");
            }
            return s;
        }
        // ---------- полиция и гражданский
        if (fa == Faction.CIVILIAN && fb.canArrest()) {
            CitizenEntity t = a;
            a = b;
            b = t;
            fa = a.getFaction();
            fb = b.getFaction();
            toB = Address.toNpc(a, b);
            toA = Address.toNpc(b, a);
        }
        if (fa.canArrest() && fb == Faction.CIVILIAN) {
            final CitizenEntity cop = a, civ = b;
            s.a("Здравствуйте. " + a.introduce() + " Предъявите документы, пожалуйста.");
            if (b.drunk) {
                s.b("Ик... А чё я? Я ничё...");
                s.a("Гражданин, вы в состоянии опьянения в общественном месте. Пройдёмте (ст. 20.21 КоАП РФ).");
                s.then(l -> {
                    civ.addWanted(Article.KOAP_20_21);
                    cop.setTarget(civ);
                });
            } else if (b.isWanted()) {
                s.b("Э-э... я паспорт дома забыл.");
                s.a("Вы числитесь в розыске. Руки за спину!");
                s.then(l -> cop.setTarget(civ));
            } else {
                s.b(pick(r, "Пожалуйста, вот паспорт.", "А что случилось, " + toB + "?", "Опять? Вчера же проверяли."));
                s.a(pick(r, "Всё в порядке. Всего доброго.", "Спасибо. Будьте внимательны, в районе карманники.", "Проходите."));
            }
            return s;
        }
        // ---------- полиция и криминал
        if (fa.canArrest() && fb.isCriminal()) {
            final CitizenEntity cop = a, crim = b;
            s.a("А, " + (b.nickname.isEmpty() ? b.lastName : b.nickname) + ". Опять ты. Что в карманах?");
            s.b(pick(r, "Начальник, я чистый, гражданин начальник.", "Ничего нет, ищи сколько хочешь.", "Адвоката позови сначала."));
            boolean found = r.nextInt(4) == 0 && !b.getMainHandItem().isEmpty();
            if (found) {
                s.a("А это что? Без разрешения? Вы задержаны (ст. 222 УК РФ).");
                s.then(l -> {
                    crim.addWanted(Article.UK_222);
                    CrimeSystem.registerWitnessedCase(l, crim, Article.UK_222, crim.blockPosition());
                    cop.setTarget(crim);
                });
            } else {
                s.a("Ладно, иди. Но я за тобой смотрю.");
            }
            return s;
        }
        // ---------- прокурор и полицейский / следователь
        if (fa == Faction.PROK && (fb == Faction.UMVD || fb == Faction.SK || fb == Faction.UFSIN)
                || fb == Faction.PROK && (fa == Faction.UMVD || fa == Faction.SK || fa == Faction.UFSIN)) {
            CitizenEntity p = fa == Faction.PROK ? a : b;
            CitizenEntity o = p == a ? b : a;
            boolean pa = p == a;
            String tp = Address.toNpc(o, p);
            Script s2 = new Script();
            String l1 = "Прокурорская проверка. Журнал КУСП и книгу учёта задержанных покажите.";
            String l2 = "Пожалуйста, " + tp + ". Всё зарегистрировано.";
            String l3 = r.nextBoolean() ? "Сроки проверки по трём сообщениям нарушены. Будет представление." : "Замечаний нет. Работайте.";
            if (pa) s2.a(l1).b(l2).a(l3);
            else s2.b(l1).a(l2).b(l3);
            if (l3.startsWith("Сроки")) s2.then(l -> CityManager.changeSatisfaction(d, 0.2));
            return s2;
        }
        // ---------- криминал своей группировки
        if (fa.isCriminal() && fa == fb) {
            final Faction gang = fa;
            if (a.rank < b.rank) {
                s.a("Уважение, " + toB + ".");
                s.b(pick(r, "Как дела на районе?", "Что по точкам?", "Всё собрали?"));
                s.a(pick(r, "Всё ровно, собрали, несу в общак.", "Барыги платят, никто не дёргается.", "Кавказ опять на рынке шумит."));
                s.then(l -> d.addFunds(gang, 2000 + r.nextInt(3000)));
            } else {
                s.a(pick(r, "Слышал, мусора на рынке облаву готовят?", "Надо бы с байкерами потолковать.")).b(pick(r, "Слышал. Пацанам скажу, чтоб не светились.", "Толковать не о чем."));
            }
            return s;
        }
        // ---------- враждующие группировки
        if (fa.isCriminal() && fb.isCriminal() && fa.isHostileTo(fb)) {
            final CitizenEntity x = a, y = b;
            s.a(pick(r, "Чё ты тут ходишь? Это наша поляна.", "Опять ваши на нашей территории.", "Передай своим: ещё раз — и разговор будет другой."));
            s.b(pick(r, "Поляна общая, брат.", "Ты кому это говоришь?", "Смотри, не пожалей."));
            if (r.nextInt(3) == 0) {
                s.a("Ну всё, ты попал.");
                s.then(l -> {
                    x.setTarget(y);
                    y.setTarget(x);
                });
            }
            return s;
        }
        // ---------- скинхед пристаёт к прохожему
        if (fa == Faction.SKINHEADS && fb == Faction.CIVILIAN) {
            final CitizenEntity sk = a, civ = b;
            s.a(pick(r, "Э, ты откуда такой красивый?", "Закурить есть?", "Чё смотришь, а?"));
            s.b(pick(r, "Отстаньте, пожалуйста!", "Я сейчас полицию вызову!", "Ребята, я просто домой иду..."));
            if (r.nextInt(3) == 0) s.a("Ха, беги давай!").then(l -> {
                civ.mood = Math.max(0, civ.mood - 20);
                CrimeSystem.commitByNpc(l, sk, Article.KOAP_20_1, civ);
            });
            return s;
        }
        // ---------- рэкет на рынке
        if ((fa == Faction.KAVKAZ_MAFIA || fa == Faction.RUS_MAFIA) && b.profession.isTrader()) {
            final CitizenEntity m = a, t = b;
            s.a("Хозяин, за место в этом месяце не забыл?");
            s.b(pick(r, "Да отдал я вашим, вчера ещё!", "Торговли нет совсем, подождите неделю...", "Вот, держите. Только не шумите."));
            s.a("Смотри, чтоб без сюрпризов.").then(l -> {
                d.addFunds(m.getFaction(), 3000);
                CrimeSystem.commitByNpc(l, m, Article.UK_163, t);
            });
            return s;
        }
        // ---------- чиновник и житель
        if (fa == Faction.CIVILIAN && fb == Faction.GOV || fa == Faction.GOV && fb == Faction.CIVILIAN) {
            CitizenEntity civ = fa == Faction.CIVILIAN ? a : b;
            boolean civFirst = civ == a;
            String q = pick(r, "Когда двор отремонтируют? Ямы по колено!", "Почему опять отключили горячую воду?", "Нам детская площадка нужна!");
            String ans = pick(r, "Включили в адресную программу на следующий год.", "Направьте письменное обращение, ответим в течение тридцати дней.", "Вопрос на контроле у главы.");
            String back = d.satisfaction < 40 ? "Вечно у вас «на контроле»..." : "Ну хоть так. Спасибо.";
            Script s2 = new Script();
            if (civFirst) s2.a(q).b(ans).a(back);
            else s2.b(q).a(ans).b(back);
            s2.then(l -> CityManager.changeSatisfaction(d, back.startsWith("Ну") ? 0.3 : -0.2));
            return s2;
        }
        // ---------- военные и гражданские
        if (fa == Faction.CIVILIAN && fb == Faction.ARMY) {
            s.a(pick(r, "Здравствуйте, служивые! Как служба?", "Сынок, а Петров из второй роты у вас служит?")).b(pick(r, "Здравия желаю! Служба идёт.", "Не положено говорить, извините."));
            return s;
        }
        // ---------- судья
        if (fb == Faction.COURT && fa != Faction.COURT) {
            s.a("Добрый день, Ваша честь.").b(pick(r, "Здравствуйте.", "Добрый день. Заседание в десять, не опаздывайте."));
            return s;
        }
        // ---------- профессии
        if (fa == Faction.CIVILIAN && fb == Faction.CIVILIAN) {
            final CitizenEntity x = a, y = b;
            if (b.profession == Profession.PRIEST) return s.a("Благословите, батюшка.").b("Бог благословит. Заходите на службу в воскресенье.");
            if (b.profession == Profession.DOCTOR) return s.a("Доктор, давление скачет, что делать?").b("Запишитесь на приём через Госуслуги, талоны есть на четверг.");
            if (b.profession == Profession.PROFESSOR) return s.a("Профессор, когда пересдача по уголовному праву?").b("В четверг. И учите сто пятую статью, а не шпаргалки пишите.");
            if (b.profession.isTrader()) return s.a(pick(r, "Почём картошка?", "Хлеб свежий?", "Сколько стоит?")).b(pick(r, "Сорок рублей кило.", "Утренний, ещё тёплый!", "Для вас — со скидкой.")).then(l -> d.budget += 30);
            if (a.drunk && r.nextBoolean()) {
                s.a("Слышь, " + toB + ", дай сотку до получки.").b(pick(r, "Иди проспись!", "У самого нет."));
                if (r.nextInt(4) == 0) s.a("Ах ты ж...").then(l -> {
                    x.setTarget(y);
                    CrimeSystem.commitByNpc(l, x, Article.KOAP_6_1_1, y);
                });
                return s;
            }
            String[][] gossip = gossip(d);
            String[] g = gossip[r.nextInt(gossip.length)];
            s.a("Здравствуй, " + toB + "! " + g[0]).b(g[1]);
            if (g.length > 2) s.a(g[2]);
            final int moodDelta = d.satisfaction < 40 ? -2 : 1;
            s.then(l -> {
                x.mood = Math.max(0, Math.min(100, x.mood + moodDelta));
                y.mood = Math.max(0, Math.min(100, y.mood + moodDelta));
            });
            return s;
        }
        // ---------- байкеры
        if (fa == Faction.CIVILIAN && fb == Faction.BIKERS) {
            return s.a("Классный байк!").b(pick(r, "Руками не трогай.", "Ага. Хочешь прокатиться — вступай в клуб."));
        }
        // ---------- запасной вариант
        if (r.nextBoolean()) return s.a("Добрый день.").b("Здравствуйте.");
        return null;
    }

    private static String[][] gossip(GorodData d) {
        List<String[]> l = new ArrayList<>();
        l.add(new String[]{"Слышал, опять за свет подняли?", "Да уж, с первого числа. Пенсию бы так индексировали."});
        l.add(new String[]{"Как внуки?", "Растут! Старший в юридический поступил.", "Молодец, следователем будет."});
        l.add(new String[]{"Видал, у администрации лавочки новые поставили?", "Видал. К выборам, не иначе."});
        l.add(new String[]{"Ты на завод-то устроился?", "Устроился, сменщиком. Платят хоть вовремя."});
        l.add(new String[]{"В субботу в ДК концерт, пойдёшь?", "Если жена отпустит."});
        if (d.crimeIndex > 15) {
            l.add(new String[]{"Вечером в парк не ходи, неспокойно стало.", "Да, говорят, вчера опять кого-то ограбили.", "Куда полиция смотрит..."});
            l.add(new String[]{"На рынке «эти» опять деньги собирают.", "Тише ты, услышат ещё."});
        }
        if (d.satisfaction < 40) {
            l.add(new String[]{"Надоело это всё. Дороги разбиты, зарплаты копейки.", "Говорят, люди на площадь собираются.", "Я тоже пойду."});
            l.add(new String[]{"Глава опять в отпуске?", "Ему-то что, а нам тут жить."});
        } else if (d.satisfaction > 70) {
            l.add(new String[]{"А город-то похорошел!", "Да, во дворе наконец асфальт положили."});
        }
        if (d.budget < 0) l.add(new String[]{"Учителям опять зарплату задержали.", "Бюджет, говорят, пустой."});
        return l.toArray(new String[0][]);
    }

    private static String pick(RandomSource r, String... options) {
        return options[r.nextInt(options.length)];
    }

    private Encounters() {
    }
}
