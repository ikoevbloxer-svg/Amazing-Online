package ru.gorod.rusgorod.social;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.entity.Profession;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.faction.FactionService;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.law.CriminalCase;
import ru.gorod.rusgorod.study.LawSchool;
import ru.gorod.rusgorod.util.Scheduler;
import ru.gorod.rusgorod.world.CityGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NPC реагируют на сообщения игрока в чате: приветствия, вопросы «где», «кто ты», «как вступить»,
 * просьбы о помощи, мат и оскорбления при сотрудниках, попытки дать взятку, сдача, приказы подчинённым.
 */
public final class ChatResponder {
    private static final Map<UUID, Long> MAT_WARN = new HashMap<>();

    /** Слово → ключ места в городе. */
    private static final String[][] PLACES = {
            {"полиц", "police"}, {"отдел", "police"}, {"умвд", "police"}, {"гибдд", "gibdd"}, {"гаи", "gibdd"}, {"дпс", "gibdd_post"},
            {"следствен", "sk"}, {"ск ", "sk"}, {"прокурат", "prok"}, {"фсб", "fsb"}, {"администрац", "admin"}, {"мэри", "admin"},
            {"площад", "square"}, {"сизо", "sizo"}, {"тюрьм", "sizo"}, {"рынок", "market"}, {"рынк", "market"}, {"магазин", "shop"},
            {"продукт", "shop"}, {"аптек", "shop"}, {"кафе", "cafe"}, {"шашлык", "cafe"}, {"ресторан", "restaurant"}, {"импери", "restaurant"},
            {"гараж", "garages"}, {"байкер", "bikers"}, {"мотоклуб", "bikers"}, {"склад", "warehouse"}, {"парк", "park"},
            {"церк", "church"}, {"храм", "church"}, {"юрфак", "university"}, {"институт", "university"}, {"универ", "university"},
            {"школ", "school"}, {"почт", "post"}, {"больниц", "hospital"}, {"врач", "hospital"}, {"банк", "bank"}, {"суд", "court"},
            {"военкомат", "voenkomat"}, {"мфц", "mfc"}, {"паспорт", "mfc"}, {"госуслуг", "mfc"}, {"вокзал", "vokzal"}, {"поезд", "vokzal"},
            {"завод", "factory"}, {"дом культуры", "dk"}, {"дк", "dk"}, {"кино", "dk"}, {"заправк", "azs"}, {"азс", "azs"},
            {"рюмочн", "bar"}, {"бар", "bar"}, {"выпить", "bar"}, {"часть", "army_kpp"}, {"армия", "army_kpp"}, {"казарм", "army_kpp"},
            {"общаг", "hostel"}, {"общежит", "hostel"}
    };

    private static final String[] PLACE_NAMES_KEYS = {"police", "gibdd", "gibdd_post", "sk", "prok", "fsb", "admin", "square", "sizo", "market", "shop",
            "cafe", "restaurant", "garages", "bikers", "warehouse", "park", "church", "university", "school", "post", "hospital", "bank", "court",
            "voenkomat", "mfc", "vokzal", "factory", "dk", "azs", "bar", "army_kpp", "hostel"};
    private static final String[] PLACE_NAMES = {"Отдел полиции", "Госавтоинспекция", "Пост ДПС", "Следственный комитет", "Прокуратура", "Управление ФСБ",
            "Администрация", "Площадь", "СИЗО-1", "Рынок «Центральный»", "Магазин «Продукты»", "Кафе «Шашлычный двор»", "Ресторан «Империя»",
            "Гаражный кооператив", "Мотоклуб", "Склад", "Парк Победы", "Храм", "Юридический институт", "Школа №1", "Почта", "Городская больница",
            "Банк", "Городской суд", "Военкомат", "МФЦ «Мои документы»", "Вокзал", "Машзавод", "Дом культуры", "АЗС", "Рюмочная «Встреча»",
            "КПП воинской части", "Общежитие"};

    private static final String[] JOKES = {
            "Приходит мужик в МФЦ за справкой, а ему: «Нужна справка, что вы не можете получить справку». Он и сейчас там стоит.",
            "Гаишник останавливает машину: «Почему без ремня?» — «Так я же стою!» — «Вот и постоите, пока протокол напишу».",
            "Студент юрфака на экзамене: «Какое наказание за кражу?» — «Смотря у кого и сколько».",
            "Сержант: «Рядовой, почему не бритый?» — «Так брился, товарищ сержант!» — «Значит, плохо брился. Повторить!»"
    };

    public static void onChat(ServerPlayer p, String raw) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        if (!d.generated || raw.startsWith("/")) return;
        String m = " " + raw.toLowerCase().replace('ё', 'е') + " ";
        PlayerRecord r = d.player(p.getUUID());
        List<CitizenEntity> near = level.getEntitiesOfClass(CitizenEntity.class, p.getBoundingBox().inflate(12), c -> c.isAlive());
        if (near.isEmpty()) return;
        RandomSource rnd = level.random;

        // ---- 1. приказы подчинённым
        if (Orders.fromChat(p, r, m, near)) return;

        CitizenEntity cop = nearest(p, near, 14, c -> c.getFaction().canArrest() && !c.isJailed() && c.hasLineOfSight(p));

        // ---- 2. сдача
        if (Orders.has(m, "сдаюсь", "не стреляйте", "я сдаюсь", "руки вверх") && r.isWanted() && cop != null) {
            final CitizenEntity c = cop;
            Scheduler.later(10, () -> {
                c.say("Руки за голову! Медленно. Явку с повинной учтём (ст. 142 УПК РФ).", true);
                CrimeSystem.arrestPlayer(level, c, p, true);
            });
            return;
        }

        // ---- 3. мат и оскорбление представителя власти
        boolean mat = Profanity.contains(m);
        boolean insultCop = Orders.has(m, "мусор", "мент", "легав", "фараон", "волки позорные", "менты", "козлы");
        if ((mat || insultCop) && cop != null && !r.faction.isLawEnforcement()) {
            final CitizenEntity c = cop;
            if (insultCop && p.distanceToSqr(cop) < 64) {
                Scheduler.later(12, () -> {
                    c.say("Публичное оскорбление представителя власти при исполнении — ст. 319 УК РФ. Вы задержаны!", true);
                    CrimeSystem.commitByPlayer(p, Article.UK_319, c);
                });
                return;
            }
            Long last = MAT_WARN.get(p.getUUID());
            long now = level.getGameTime();
            if (last == null || now - last > 2400) {
                MAT_WARN.put(p.getUUID(), now);
                Scheduler.later(12, () -> c.say("Гражданин, нецензурная брань в общественном месте — мелкое хулиганство (ст. 20.1 КоАП РФ). Это предупреждение.", true));
            } else {
                MAT_WARN.remove(p.getUUID());
                Scheduler.later(12, () -> {
                    c.say("Предупреждали. Составляю протокол по ст. 20.1 КоАП РФ.", true);
                    CrimeSystem.commitByPlayer(p, Article.KOAP_20_1, null);
                });
            }
            return;
        }
        if (mat) {
            CitizenEntity c = nearest(p, near, 8, x -> !x.isJailed());
            if (c != null && rnd.nextBoolean()) {
                String line = switch (c.getFaction().type) {
                    case CRIMINAL -> c.getRandom().nextBoolean() ? "Ха, базаришь как надо." : "Полегче на поворотах.";
                    case STATE -> "Выбирайте выражения, гражданин.";
                    default -> c.female ? "Молодой человек, тут дети ходят!" : "Ну зачем так выражаться...";
                };
                Scheduler.later(15, () -> c.say(line, true));
            }
            return;
        }

        // ---- 4. экстремизм (ст. 20.3 КоАП)
        if (Orders.has(m, "хайль", "зиг хайл", "sieg heil", " 88 ", "14/88", "белая сила", "white power")) {
            boolean handled = false;
            for (CitizenEntity c : near) {
                if (c.getFaction() == Faction.SKINHEADS && !handled) {
                    Scheduler.later(12, () -> c.say("О, наш человек! Приходи в гаражи.", true));
                    if (r.faction == Faction.CIVILIAN) r.crimRep += 1;
                    handled = true;
                }
                if (c.getFaction() == Faction.KAVKAZ_MAFIA && c.getTarget() == null) c.setTarget(p);
            }
            if (cop != null) {
                final CitizenEntity c = cop;
                Scheduler.later(18, () -> {
                    c.say("Публичная пропаганда нацизма — ст. 20.3 КоАП РФ! Пройдёмте.", true);
                    CrimeSystem.commitByPlayer(p, Article.KOAP_20_3, null);
                });
            }
            return;
        }

        // ---- 5. помощь
        if (Orders.has(m, "помогите", "спасите", "на помощь", "убивают", "грабят", "караул", "полиция!", "вызовите полицию")) {
            LivingEntity attacker = p.getLastHurtByMob();
            boolean recent = attacker != null && p.tickCount - p.getLastHurtByMobTimestamp() < 200;
            List<CitizenEntity> cops = level.getEntitiesOfClass(CitizenEntity.class, p.getBoundingBox().inflate(40),
                    c -> c.getFaction().isLawEnforcement() && !c.isJailed() && c.getTarget() == null);
            for (CitizenEntity c : cops) {
                if (recent && attacker != c) {
                    if (attacker instanceof CitizenEntity ac) ac.addWanted(Article.KOAP_6_1_1);
                    c.setTarget(attacker);
                } else {
                    c.getNavigation().moveTo(p, 1.2);
                }
            }
            if (!cops.isEmpty()) {
                CitizenEntity c = cops.get(0);
                Scheduler.later(10, () -> c.say(recent ? "Держитесь, иду! Стоять, полиция!" : "Что случилось? Иду!", true));
            } else {
                CitizenEntity civ = nearest(p, near, 12, x -> x.getFaction() == Faction.CIVILIAN);
                if (civ != null) Scheduler.later(10, () -> civ.say("Звоните 112! Кто-нибудь, вызовите полицию!", true));
            }
            return;
        }

        // ---- 6. взятка
        if (cop != null && p.distanceToSqr(cop) < 100 && Orders.has(m, "взятк", "договоримся", "решим вопрос", "занесу", "денег дам", "сколько надо", "отблагодарю")) {
            final CitizenEntity c = cop;
            if (r.isWanted() && rnd.nextFloat() < 0.15f + c.criminality) {
                Scheduler.later(15, () -> c.say("Тише ты... Подойди, поговорим. (ПКМ → «Предложить взятку»)", true));
            } else if (r.isWanted() || Orders.has(m, "взятк", "денег дам", "занесу")) {
                Scheduler.later(15, () -> {
                    c.say("Покушение на дачу взятки должностному лицу — ст. 30, 291 УК РФ. Вы задержаны!", true);
                    CrimeSystem.commitByPlayer(p, Article.UK_291, c);
                });
            } else {
                Scheduler.later(15, () -> c.say("Вы о чём, гражданин? Проходите.", true));
            }
            return;
        }

        // ---- 7. адресат: по имени, по взгляду, ближайший
        CitizenEntity target = null;
        for (CitizenEntity c : near) {
            if (m.contains(c.lastName.toLowerCase()) || m.contains(" " + c.firstName.toLowerCase() + " ")
                    || (!c.nickname.isEmpty() && m.contains(c.nickname.toLowerCase()))) {
                target = c;
                break;
            }
        }
        if (target == null) target = lookedAt(p, near, 8);
        if (target == null) target = nearest(p, near, 5, c -> true);
        if (target == null) return;
        final CitizenEntity t = target;
        String reply = answer(level, d, p, r, t, m, rnd);
        if (reply == null) return;
        t.getNavigation().stop();
        t.getLookControl().setLookAt(p);
        Scheduler.later(14 + rnd.nextInt(14), () -> t.say(reply, true));
    }

    /** Ответ конкретного NPC на реплику. null — промолчать. */
    private static String answer(ServerLevel level, GorodData d, ServerPlayer p, PlayerRecord r, CitizenEntity c, String m, RandomSource rnd) {
        Faction cf = c.getFaction();
        String addr = Address.toPlayer(c, p, r);
        if (c.isJailed()) {
            return pick(rnd, "Начальник, я не виноват! Адвоката мне!", "Ст. 51 Конституции — не буду ничего говорить.", "Передай моим, что я тут.");
        }

        // где?
        if (Orders.has(m, "где ", "как пройти", "как добраться", "как найти", "дорогу до", "подскажите дорогу", "куда идти")) {
            for (String[] pl : PLACES) {
                if (m.contains(pl[0])) {
                    BlockPos pos = d.locs.get(pl[1]);
                    if (pos == null) continue;
                    c.setAnim(CitizenEntity.ANIM_POINT, 40);
                    c.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                    return placeName(pl[1]) + " — " + direction(c.blockPosition(), pos) + ". Координаты: X " + pos.getX() + ", Z " + pos.getZ() + ".";
                }
            }
            return "Не знаю такого места. Спросите про полицию, рынок, больницу, вокзал, институт...";
        }
        // кто ты / звание / должность
        if (Orders.has(m, "кто ты", "кто вы", "представь", "как тебя зовут", "как вас зовут", "ваше звание", "твое звание", "твоё звание",
                "кем работаешь", "кем работаете", "должность", "ваше имя")) {
            if (cf.isState()) {
                if (cf.insignia) c.setAnim(CitizenEntity.ANIM_SALUTE, 30);
                return c.introduce() + (cf == Faction.UMVD || cf == Faction.UGIBDD ? " (ст. 5 ФЗ «О полиции» — обязан представиться.)" : "");
            }
            return c.introduce();
        }
        // кто начальник
        if (Orders.has(m, "кто начальник", "кто главный", "кто старший", "кто командир", "кто у вас главный", "кто руководит", "ваш начальник")) {
            if (cf == Faction.CIVILIAN) return "Начальник? Глава городского округа, наверное. Ну и Президент.";
            CitizenEntity boss = topOf(level, d, cf);
            String bossName = boss == null ? "не знаю, на месте ли" : cf.rankName(boss.rank) + " " + boss.fullName() + ", " + cf.positionName(boss.rank);
            if (cf.isCriminal()) return c.rank >= cf.topRank() ? "Я и есть главный." : "Про старших не говорят. Но если очень надо — " + (boss == null ? "его тут нет" : "«" + boss.nickname + "»") + ".";
            return "Мой непосредственный руководитель — " + bossName + ". Вертикаль: " + cf.superior + ".";
        }
        // приветствие
        if (Orders.has(m, "привет", "здравствуй", "здорово", "добрый день", "добрый вечер", "доброе утро", "здравия желаю", "салам", "хай ", "приветствую")) {
            if (cf.isState() && cf.insignia && Address.isSuperior(r, c)) {
                c.setAnim(CitizenEntity.ANIM_SALUTE, 30);
                return "Здравия желаю, " + addr + "!";
            }
            return switch (cf.type) {
                case STATE -> m.contains("здравия желаю") ? "Здравия желаю." : "Здравствуйте, " + addr + ".";
                case CRIMINAL -> pick(rnd, "Ну, здорово, " + addr + ".", "Чего надо?", "Салам.");
                default -> pick(rnd, "Здравствуйте!", "Добрый день, " + addr + "!", "Привет-привет.");
            };
        }
        // как дела
        if (Orders.has(m, "как дела", "как служба", "как жизнь", "как сам", "как поживаете", "как настроение")) {
            if (cf == Faction.ARMY) return pick(rnd, "Служба идёт, " + addr + "!", "Нормально. До дембеля сто дней.");
            if (cf.isLawEnforcement()) return pick(rnd, "Работаем. Преступность сама себя не раскроет.", "Смена тяжёлая, но держимся.");
            if (cf.isCriminal()) return pick(rnd, "Живём, не жалуемся.", "Тебе какое дело?");
            if (c.mood < 30) return pick(rnd, "Да какие дела... Цены растут, двор разбит.", "Плохо. Зарплату опять задержали.");
            return pick(rnd, "Потихоньку, спасибо!", "Хорошо! Погода радует.", "Жить можно.");
        }
        // время
        if (Orders.has(m, "который час", "сколько времени", "сколько время", "время подскажите")) {
            long t = (level.getDayTime() + 6000) % 24000;
            int h = (int) (t / 1000), min = (int) ((t % 1000) * 60 / 1000);
            return String.format("Сейчас %02d:%02d.", h, min);
        }
        // спасибо
        if (Orders.has(m, "спасибо", "благодарю", "спс")) {
            if (cf == Faction.ARMY && m.contains("служб")) {
                c.setAnim(CitizenEntity.ANIM_SALUTE, 30);
                return "Служу России!";
            }
            return pick(rnd, "Не за что.", "Обращайтесь.", "Пожалуйста.");
        }
        if (Orders.has(m, "пока", "до свидания", "бывай", "удачи")) return pick(rnd, "До свидания.", "Всего доброго.", "Бывай.");
        // новости
        if (Orders.has(m, "что нового", "новост", "что слышно", "что происходит")) {
            if (d.news.isEmpty()) return "Тихо всё.";
            return "Говорят: " + d.news.get(rnd.nextInt(Math.min(4, d.news.size())));
        }
        // розыск (для сотрудников)
        if (Orders.has(m, "ориентировк", "кто в розыске", "розыск")) {
            if (!cf.isLawEnforcement()) return "Я в такие дела не лезу.";
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (CriminalCase cs : d.cases) {
                if (cs.stage == CriminalCase.Stage.SUSPECT_WANTED && n < 4) {
                    if (n > 0) sb.append("; ");
                    sb.append(cs.offenderName).append(" (").append(cs.article.code).append(")");
                    n++;
                }
            }
            return n == 0 ? "Ориентировок сейчас нет." : "В розыске: " + sb + ".";
        }
        // как вступить
        if (Orders.has(m, "как вступить", "хочу к вам", "возьмите меня", "как попасть к вам", "как стать", "хочу служить", "хочу в ", "можно к вам")) {
            Faction f = guessFaction(m, cf);
            if (f == Faction.CIVILIAN) return "Так вы и так гражданин. Работу ищете — спросите про работу.";
            List<String> unmet = FactionService.unmetRequirements(r, f, level.getDayTime() / 24000L);
            String where = f == Faction.ARMY ? "Через военкомат: " + coords(d, "voenkomat") + "." : "Заявление — клавиша G → «Фракции» → " + f.title + ".";
            if (f.isCriminal()) {
                if (cf != f) return "Это не ко мне. Ищи их — " + coords(d, f.hqKey) + ".";
                return unmet.isEmpty() ? "Посмотрим на тебя. Шанс есть (" + Math.round(FactionService.chance(r, f) * 100) + "%). Подходи к старшим — G → «Фракции»."
                        : "Рано тебе. " + String.join("; ", unmet) + ".";
            }
            if (unmet.isEmpty()) return "Требованиям соответствуете. " + where + " Вероятность приёма — " + Math.round(FactionService.chance(r, f) * 100) + "%.";
            return "В " + f.title + " пока не возьмут: " + String.join("; ", unmet) + ". " + where;
        }
        // работа
        if (Orders.has(m, "работ", "ваканс", "заработать", "подработ", "трудоустро")) {
            if (cf.isCriminal()) return "Есть одна работёнка... Но тебе пока рано. Сначала авторитет наработай.";
            return "Вакансии есть: дворник, грузчик, почтальон, продавец, рабочий на заводе, охранник ЧОП, санитар в больнице. Клавиша G → «Работа».";
        }
        // учёба
        if (Orders.has(m, "юрфак", "учиться", "учеба", "учёба", "диплом", "институт", "универ", "лекци", "экзамен", "поступить")) {
            if (c.profession == Profession.PROFESSOR) {
                return "Поступайте: G → «Юрфак» → «Поступить». Затем лекции в аудитории (днём, " + LawSchool.LECTURE_SECONDS
                        + " секунд в аудитории) и экзамен по каждому из " + LawSchool.SUBJECTS.length + " предметов. Диплом откроет СК, прокуратуру, ФСБ и суд.";
            }
            return "Юридический институт — " + coords(d, "university") + ". Там всё объяснят. Или G → «Юрфак».";
        }
        // цены
        if (Orders.has(m, "почем", "сколько стоит", "цена", "купить", "продай")) {
            if (c.profession.isTrader()) return "Хлеб — 60, шашлык — 250, кофе — 120. Нажмите на меня (ПКМ), чтобы купить.";
            return "Я не продаю. Сходите в «Продукты» или на рынок.";
        }
        // анекдот
        if (Orders.has(m, "анекдот", "пошути", "расскажи что-нибудь")) {
            if (cf.isState() && cf.insignia) return "Не положено при исполнении.";
            return JOKES[rnd.nextInt(JOKES.length)];
        }
        // город
        if (Orders.has(m, "что за город", "какой город", "как называется город")) {
            return "Это " + CityGenerator.CITY_NAME + ". Городской округ, тысяч пятьдесят населения. Центр — площадь у администрации.";
        }
        // прямой вопрос — вежливый «не знаю»
        Vec3 look = p.getViewVector(1.0f);
        Vec3 to = c.position().subtract(p.position()).normalize();
        if (m.trim().endsWith("?") && look.dot(to) > 0.9) {
            return switch (cf.type) {
                case STATE -> "По этому вопросу — письменное обращение, ответим в течение тридцати дней.";
                case CRIMINAL -> "Много вопросов задаёшь.";
                default -> "Ой, не знаю. Спросите в администрации.";
            };
        }
        return null;
    }

    private static Faction guessFaction(String m, Faction fallback) {
        if (Orders.has(m, "гаи", "гибдд", "дпс", "автоинспек")) return Faction.UGIBDD;
        if (Orders.has(m, "полиц", "мент", "участков", "опер")) return Faction.UMVD;
        if (Orders.has(m, "следовател", "следствен", " ск ")) return Faction.SK;
        if (Orders.has(m, "прокур")) return Faction.PROK;
        if (Orders.has(m, "фсб", "чекист", "контрразвед")) return Faction.UFSB;
        if (Orders.has(m, "арми", "армию", "контракт", "военн", "солдат", "служить")) return Faction.ARMY;
        if (Orders.has(m, "фсин", "сизо", "конвой", "надзират")) return Faction.UFSIN;
        if (Orders.has(m, "администрац", "чиновник", "мэри", "муниципал")) return Faction.GOV;
        if (Orders.has(m, "судь", " суд ")) return Faction.COURT;
        if (Orders.has(m, "байкер", "мотоклуб")) return Faction.BIKERS;
        if (Orders.has(m, "скин", "бритоголов")) return Faction.SKINHEADS;
        if (Orders.has(m, "кавказ")) return Faction.KAVKAZ_MAFIA;
        if (Orders.has(m, "банд", "мафи", "опг", "братв", "блатн")) return Faction.RUS_MAFIA;
        return fallback;
    }

    public static String placeName(String key) {
        for (int i = 0; i < PLACE_NAMES_KEYS.length; i++) if (PLACE_NAMES_KEYS[i].equals(key)) return PLACE_NAMES[i];
        return key;
    }

    private static String coords(GorodData d, String key) {
        BlockPos p = d.loc(key);
        return "X " + p.getX() + ", Z " + p.getZ();
    }

    /** «на северо-восток, метров 120». */
    public static String direction(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
        if (dist < 8) return "прямо здесь";
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        if (ang < 0) ang += 360;
        String[] dirs = {"на север", "на северо-восток", "на восток", "на юго-восток", "на юг", "на юго-запад", "на запад", "на северо-запад"};
        String dir = dirs[(int) Math.round(ang / 45.0) % 8];
        return dir + ", метров " + (dist / 10 * 10 + 10);
    }

    private static CitizenEntity topOf(ServerLevel level, GorodData d, Faction f) {
        AABB box = new AABB(d.minX - 16, level.getMinBuildHeight(), d.minZ - 16, Math.max(d.maxX, d.baseMaxX) + 16, level.getMaxBuildHeight(), Math.max(d.maxZ, d.baseMaxZ) + 16);
        CitizenEntity best = null;
        for (CitizenEntity c : level.getEntitiesOfClass(CitizenEntity.class, box, x -> x.getFaction() == f && !x.isJailed())) {
            if (best == null || c.rank > best.rank) best = c;
        }
        return best;
    }

    private static CitizenEntity lookedAt(ServerPlayer p, List<CitizenEntity> near, double max) {
        Vec3 look = p.getViewVector(1.0f);
        Vec3 eye = p.getEyePosition();
        CitizenEntity best = null;
        double bestDot = 0.93;
        for (CitizenEntity c : near) {
            if (p.distanceToSqr(c) > max * max) continue;
            Vec3 to = c.getEyePosition().subtract(eye).normalize();
            double dot = look.dot(to);
            if (dot > bestDot) {
                bestDot = dot;
                best = c;
            }
        }
        return best;
    }

    private static CitizenEntity nearest(ServerPlayer p, List<CitizenEntity> near, double max, java.util.function.Predicate<CitizenEntity> f) {
        CitizenEntity best = null;
        double bd = max * max;
        for (CitizenEntity c : near) {
            double dd = p.distanceToSqr(c);
            if (dd < bd && f.test(c)) {
                bd = dd;
                best = c;
            }
        }
        return best;
    }

    private static String pick(RandomSource r, String... o) {
        return o[r.nextInt(o.length)];
    }

    private ChatResponder() {
    }
}
