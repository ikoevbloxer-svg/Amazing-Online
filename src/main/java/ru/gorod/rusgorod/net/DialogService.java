package ru.gorod.rusgorod.net;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.entity.Profession;
import ru.gorod.rusgorod.entity.ai.ActivityPlanner;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.faction.FactionService;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.law.CriminalCase;
import ru.gorod.rusgorod.quest.QuestManager;
import ru.gorod.rusgorod.registry.ModItems;
import ru.gorod.rusgorod.social.Address;
import ru.gorod.rusgorod.social.Orders;
import ru.gorod.rusgorod.social.PlayerInteractions;
import ru.gorod.rusgorod.study.LawSchool;
import ru.gorod.rusgorod.world.CityManager;

import java.util.ArrayList;
import java.util.List;

/** Диалоги игрока с NPC: набор действий зависит от фракций, званий и профессий обеих сторон. */
public final class DialogService {

    private static final String[] CIV_LINES = {"Опять дорогу перекопали, третий раз за год.", "Пенсию проиндексировали, а цены в «Продуктах» видели?",
            "У нас во дворе лавочку поставили, хоть что-то.", "Говорят, на рынке опять «эти» деньги собирают.", "Автобус ходит раз в час, как жить?",
            "Сын в армию пошёл, по контракту. Горжусь.", "Вечером в парк лучше не ходить — шпана.", "Управляйка трубы не меняет, всё на бумаге.",
            "В ДК в субботу концерт, приходите.", "На заводе опять сокращения, говорят."};
    private static final String[] COP_LINES = {"Проходите, гражданин, не задерживайтесь.", "Если что-то увидите — звоните 102 или 112.",
            "Ночью на районе неспокойно, будьте внимательны.", "Документы при себе носите — так спокойнее."};
    private static final String[] ARMY_LINES = {"Посторонним вход на территорию части запрещён.", "Служу России!", "До дембеля — как до Луны."};
    private static final String[] CRIM_LINES = {"Чё надо?", "Иди своей дорогой.", "Ты по делу или так?", "Здесь лишних не любят."};
    private static final String[] OFFICIAL_LINES = {"Приём граждан — по вторникам и четвергам.", "Обращение подайте в письменной форме, ответим в течение 30 дней.",
            "Работаем, работаем, всё под контролем."};
    private static final String[] JUDGE_LINES = {"Суд независим и подчиняется только Конституции и закону.", "Обжаловать решение можно в апелляционном порядке.",
            "Не вправе комментировать дела, находящиеся в производстве."};

    public static void open(ServerPlayer p, CitizenEntity c) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        Faction pf = r.faction;
        Faction cf = c.getFaction();

        StringBuilder body = new StringBuilder();
        body.append(c.fullName()).append("\n");
        if (cf == Faction.CIVILIAN) body.append("Занятие: ").append(c.profession.title).append("\n");
        else if (cf.isState()) body.append(cf.title).append("\n§7Звание: §f").append(cf.rankName(c.rank)).append("\n§7Должность: §f").append(cf.positionName(c.rank)).append("\n");
        else body.append(cf.title).append(" — ").append(cf.rankName(c.rank)).append(" (").append(cf.positionName(c.rank)).append(")\n");
        if (Address.isSuperior(r, c)) body.append("§aВаш подчинённый.\n");
        else if (cf != Faction.CIVILIAN && Address.sameService(pf, cf) && Address.grade(cf, c.rank) > Address.grade(pf, r.rank)) body.append("§6Ваш начальник по службе.\n");
        if (c.order != CitizenEntity.Order.NONE && c.isCommander(p)) body.append("§bВыполняет ваш приказ: ").append(c.order == CitizenEntity.Order.FOLLOW ? "следует за вами" : "охраняет пост").append("\n");
        if (c.isJailed()) body.append("§cНаходится под стражей.\n");
        if (c.isProtesting()) body.append("§eУчастник публичного мероприятия.\n");
        if (c.drunk) body.append("§eЗаметно пьян.\n");
        if (pf.canArrest() && c.isWanted()) body.append("§cЧислится в розыске (ориентировка).\n");
        int rel = c.relation(p.getUUID());
        body.append("§7Отношение к вам: ").append(ru.gorod.rusgorod.social.Relations.fmt(rel))
                .append(ru.gorod.rusgorod.social.Relations.label(rel)).append(" (").append(rel).append(")\n");
        String turf = ru.gorod.rusgorod.world.Turfs.describe(d, c.blockPosition());
        if (!turf.isEmpty() && (c.profession.isTrader() || cf.isCriminal())) body.append("§8").append(turf).append("\n");

        List<String[]> opts = new ArrayList<>();
        // свидетель вашего преступления
        if (p.getUUID().equals(c.witnessOf)) {
            body.append("§cВидел ваше преступление и собирается звонить в полицию.\n");
            opts.add(new String[]{"witness_bribe", "§cЗаплатить за молчание 2 000 ₽ (ст. 309 УК РФ)"});
            opts.add(new String[]{"witness_threat", "§cЗапугать (ст. 309 ч. 2, ст. 119 УК РФ)"});
            opts.add(new String[]{"witness_beg", "Попросить по-хорошему"});
            send(p, c, body.toString(), opts);
            return;
        }
        if (ru.gorod.rusgorod.social.NpcApproach.hasPending(p, c)) {
            body.append("§eОн(а) ждёт вашего ответа.\n");
        }
        if (rel <= ru.gorod.rusgorod.social.Relations.DISLIKE && !c.isJailed()) {
            body.append("§6Не хочет с вами разговаривать.\n");
            opts.add(new String[]{"apologize", "Извиниться"});
            opts.add(new String[]{"gift", "Загладить вину: подарить 500 ₽"});
            if (pf.canArrest() && !cf.isState()) opts.add(new String[]{"check_docs", "Проверить документы (ст. 13 ФЗ «О полиции»)"});
            if (pf.canArrest() && c.isWanted()) opts.add(new String[]{"arrest", "Задержать (ст. 91 УПК РФ)"});
            send(p, c, body.toString(), opts);
            return;
        }
        opts.add(new String[]{"talk", "Поговорить"});
        if (c.isJailed()) {
            send(p, c, body.toString(), opts);
            return;
        }
        opts.add(new String[]{"gift", "Подарить 500 ₽"});
        if (rel >= ru.gorod.rusgorod.social.Relations.KNOWN) opts.add(new String[]{"tip", "«Что слышно в городе?»"});
        if (rel >= ru.gorod.rusgorod.social.Relations.FRIEND && (cf == Faction.CIVILIAN || cf == pf) && c.order == CitizenEntity.Order.NONE)
            opts.add(new String[]{"companion", "«Пойдём со мной» (10 минут)"});
        if (c.profession.isTrader() || cf == Faction.CIVILIAN && c.profession == Profession.BARMAN) opts.add(new String[]{"whorules", "«Кто вас крышует?»"});
        if (cf.isState()) opts.add(new String[]{"introduce", "Попросить представиться"});
        if (cf != Faction.CIVILIAN) opts.add(new String[]{"boss", "Кто ваш начальник?"});

        // ---- приказы подчинённому
        if (Address.isSuperior(r, c)) {
            if (c.order == CitizenEntity.Order.NONE || !c.isCommander(p)) {
                opts.add(new String[]{"order_follow", "§bПриказ: следовать за мной"});
                opts.add(new String[]{"order_hold", "§bПриказ: занять пост здесь"});
            } else {
                opts.add(new String[]{"order_release", "§bПриказ: вернуться к службе"});
            }
            opts.add(new String[]{"order_report", "§bПриказ: доложить обстановку"});
        }

        // ---- профессии
        switch (c.profession) {
            case TRADER, SHOPKEEPER -> {
                if (cf == Faction.CIVILIAN) {
                    opts.add(new String[]{"buy_bread", "Купить хлеб (60 ₽)"});
                    opts.add(new String[]{"buy_meat", "Купить шашлык (250 ₽)"});
                }
            }
            case DOCTOR -> opts.add(new String[]{"heal", "Пройти лечение (бесплатно по полису ОМС)"});
            case BANKER -> {
                opts.add(new String[]{"pay_fines", "Оплатить штрафы (" + r.fineDebt + " ₽)"});
                opts.add(new String[]{"statement", "Выписка по счёту"});
            }
            case MFC_CLERK -> {
                opts.add(new String[]{"passport", "Получить паспорт / удостоверение (200 ₽ — госпошлина)"});
                opts.add(new String[]{"pay_fines", "Оплатить штрафы через «Госуслуги» (" + r.fineDebt + " ₽)"});
            }
            case PROFESSOR -> {
                opts.add(new String[]{"uni_info", "Как получить диплом юриста?"});
                if (!r.enrolled && !r.legalEducation) opts.add(new String[]{"uni_enroll", "Поступить в институт"});
                if (r.enrolled) opts.add(new String[]{"uni_exam", "Сдать экзамен по текущему предмету"});
            }
            case BARMAN -> opts.add(new String[]{"drink", "Выпить сто грамм (150 ₽)"});
            case PRIEST -> opts.add(new String[]{"candle", "Поставить свечку (50 ₽)"});
            default -> {
            }
        }
        if (c.isProtesting()) opts.add(new String[]{"demands", "Выслушать требования"});

        if (pf.canArrest() && !cf.isState()) {
            opts.add(new String[]{"check_docs", "Проверить документы (ст. 13 ФЗ «О полиции»)"});
            if (c.isWanted()) opts.add(new String[]{"arrest", "Задержать (ст. 91 УПК РФ)"});
        }
        if ((pf == Faction.SK || (pf == Faction.UMVD && r.rank >= 7)) && cf == Faction.CIVILIAN)
            opts.add(new String[]{"interrogate", "Допросить как свидетеля (ст. 189 УПК РФ)"});
        if (pf == Faction.PROK && cf == Faction.CIVILIAN) opts.add(new String[]{"complaint", "Принять жалобу"});
        if (pf == Faction.GOV && cf == Faction.CIVILIAN) opts.add(new String[]{"reception", "Провести приём гражданина"});
        if (pf == Faction.UFSB && cf.isCriminal() && !c.informant)
            opts.add(new String[]{"informant", "Предложить сотрудничество (ст. 17 ФЗ «Об ОРД»)"});

        if (!pf.isState() && c.profession.isTrader() && cf == Faction.CIVILIAN)
            opts.add(new String[]{"extort", "§cПотребовать деньги «за крышу» (ст. 163 УК РФ)"});
        if (pf.isCriminal() && r.rank >= 1 && cf == Faction.CIVILIAN)
            opts.add(new String[]{"recruit", "Предложить вступить в «" + pf.title + "»"});

        if (cf.canArrest() && r.isWanted()) {
            opts.add(new String[]{"surrender", "Сдаться (явка с повинной, ст. 142 УПК РФ)"});
            opts.add(new String[]{"bribe", "§cПредложить взятку (ст. 291 УК РФ)"});
        }
        if (pf == Faction.CIVILIAN && cf != Faction.CIVILIAN) opts.add(new String[]{"join_info", "Как к вам попасть?"});
        send(p, c, body.toString(), opts);
    }

    private static void send(ServerPlayer p, CitizenEntity c, String body, List<String[]> opts) {
        Network.sendTo(p, new OpenDialogPacket(c.getId(), c.displayTitle(), body, opts));
    }

    public static void handle(ServerPlayer p, int entityId, String option) {
        ServerLevel level = p.serverLevel();
        if (entityId == -1) {
            if (option.startsWith("exam:")) LawSchool.answer(p, option);
            return;
        }
        Entity e = level.getEntity(entityId);
        if (e instanceof ServerPlayer t && t != p) {
            if (p.distanceToSqr(t) < 8 * 8) PlayerInteractions.handle(p, t, option);
            return;
        }
        if (!(e instanceof CitizenEntity c) || p.distanceToSqr(c) > 8 * 8 || !c.isAlive()) return;
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        RandomSource rnd = level.random;
        Faction pf = r.faction;
        Faction cf = c.getFaction();
        c.getLookControl().setLookAt(p);
        String addr = Address.toPlayer(c, p, r);
        if (option.startsWith("ap_")) {
            ru.gorod.rusgorod.social.NpcApproach.handle(p, c, option);
            return;
        }

        switch (option) {
            case "witness_bribe" -> {
                if (!p.getUUID().equals(c.witnessOf)) return;
                if (r.balance < 2000) {
                    c.say("У тебя и денег-то нет.", true);
                    return;
                }
                r.balance -= 2000;
                if (rnd.nextInt(100) < 70) {
                    c.witnessOf = null;
                    c.setTarget(null);
                    c.say("...Я ничего не видел. И тебя не знаю.", true);
                    c.changeRelation(p.getUUID(), -5);
                    // подкуп — сам по себе преступление, иногда всплывает
                    if (rnd.nextInt(3) == 0) CrimeSystem.commitByPlayer(p, Article.UK_309, c);
                } else {
                    c.say("Взятку мне суёшь? Всё полиции расскажу, и про это тоже!", true);
                    CrimeSystem.commitByPlayer(p, Article.UK_309, c);
                }
            }
            case "witness_threat" -> {
                if (!p.getUUID().equals(c.witnessOf)) return;
                c.changeRelation(p.getUUID(), -40);
                if (rnd.nextBoolean()) {
                    c.witnessOf = null;
                    c.say("Ладно, ладно! Молчу! Только не трогайте!", true);
                    c.setAnim(CitizenEntity.ANIM_HANDS_UP, 60);
                } else {
                    c.say("Помогите! Мне угрожают!", true);
                    CrimeSystem.commitByPlayer(p, Article.UK_119, c);
                }
            }
            case "witness_beg" -> {
                if (!p.getUUID().equals(c.witnessOf)) return;
                if (c.relation(p.getUUID()) >= ru.gorod.rusgorod.social.Relations.KNOWN && rnd.nextBoolean()) {
                    c.witnessOf = null;
                    c.say("Эх... Ладно, ради тебя промолчу. Но больше так не делай.", true);
                } else {
                    c.say("Нет уж. Закон есть закон.", true);
                }
            }
            case "apologize" -> {
                if (rnd.nextInt(3) == 0) {
                    c.changeRelation(p.getUUID(), 8);
                    c.say("Ну... ладно. Посмотрим на тебя.", true);
                } else {
                    c.changeRelation(p.getUUID(), 2);
                    c.say("Извинения не принимаются.", true);
                }
            }
            case "gift" -> {
                if (r.balance < 500) {
                    msg(p, "Не хватает денег.", ChatFormatting.RED);
                    return;
                }
                r.balance -= 500;
                c.changeRelation(p.getUUID(), 10);
                c.mood = Math.min(100, c.mood + 10);
                // подарок должностному лицу — это уже взятка (ст. 290–291 УК, ст. 575 ГК)
                if (cf.isState() && cf != Faction.CIVILIAN) {
                    c.say("Вы что, это же взятка! Заберите немедленно.", true);
                    r.balance += 500;
                    c.changeRelation(p.getUUID(), -12);
                    msg(p, "Должностным лицам дарить запрещено (ст. 575 ГК РФ). Деньги возвращены.", ChatFormatting.GRAY);
                } else {
                    c.say(cf.isCriminal() ? "Уважаю. Запомню." : "Ой, ну зачем вы... Спасибо большое!", true);
                }
            }
            case "tip" -> c.say(ru.gorod.rusgorod.social.NpcApproach.tip(level, d, c), true);
            case "companion" -> {
                if (c.relation(p.getUUID()) < ru.gorod.rusgorod.social.Relations.FRIEND) return;
                c.order = CitizenEntity.Order.FOLLOW;
                c.commander = p.getUUID();
                c.orderUntil = level.getGameTime() + 12000;
                c.say(cf.isCriminal() ? "Пошли, прикрою." : "Конечно, пойдём! Куда?", true);
            }
            case "whorules" -> {
                String w = ru.gorod.rusgorod.world.Turfs.whoRules(d, c.blockPosition());
                c.say(w == null ? "Не понимаю, о чём вы." : (c.relation(p.getUUID()) >= ru.gorod.rusgorod.social.Relations.KNOWN || pf.isCriminal() ? w : "Ни о какой крыше не слышал. Идите."), true);
            }
            case "talk" -> {
                String[] lines = switch (cf.type) {
                    case CIVIL -> CIV_LINES;
                    case CRIMINAL -> CRIM_LINES;
                    default -> cf == Faction.ARMY ? ARMY_LINES : cf == Faction.COURT ? JUDGE_LINES : (cf.isLawEnforcement() ? COP_LINES : OFFICIAL_LINES);
                };
                if (c.isProtesting()) lines = new String[]{"Мы мирно стоим! Имеем право — ст. 31 Конституции!"};
                if (c.isJailed()) lines = new String[]{"Я ни в чём не виноват, начальник...", "Адвоката мне! Ст. 48 Конституции!"};
                if (c.drunk) lines = new String[]{"Ты меня уважаешь? Ик.", "Жизнь — она такая... сложная."};
                if (Address.isSuperior(r, c) && cf.insignia) {
                    c.setAnim(CitizenEntity.ANIM_SALUTE, 30);
                    c.say("Слушаю вас, " + addr + "!");
                } else {
                    c.say(lines[rnd.nextInt(lines.length)]);
                }
                if (rnd.nextInt(4) == 0) c.changeRelation(p.getUUID(), 1);
                QuestManager.onAction(p, "talk", c);
            }
            case "introduce" -> {
                if (cf.insignia) c.setAnim(CitizenEntity.ANIM_SALUTE, 30);
                c.say(c.introduce());
            }
            case "boss" -> {
                CitizenEntity boss = null;
                for (CitizenEntity x : level.getEntitiesOfClass(CitizenEntity.class, c.getBoundingBox().inflate(200), x -> x.getFaction() == cf && x != c && !x.isJailed())) {
                    if (x.rank > c.rank && (boss == null || x.rank < boss.rank)) boss = x;
                }
                if (cf.isCriminal()) c.say(boss == null ? "Я тут сам себе старший." : "Мой старший — «" + boss.nickname + "». Больше ничего не скажу.");
                else if (boss == null) c.say("Я руковожу подразделением. Выше — " + cf.superior + ".");
                else c.say("Мой непосредственный начальник — " + cf.positionName(boss.rank) + ", " + cf.rankName(boss.rank).toLowerCase() + " " + boss.fullName() + ".");
            }
            case "order_follow" -> Orders.single(p, r, Orders.Kind.FOLLOW, c);
            case "order_hold" -> Orders.single(p, r, Orders.Kind.HOLD, c);
            case "order_release" -> Orders.single(p, r, Orders.Kind.RELEASE, c);
            case "order_report" -> Orders.single(p, r, Orders.Kind.REPORT, c);
            case "buy_bread" -> buy(p, r, c, 60, new ItemStack(Items.BREAD, 2));
            case "buy_meat" -> buy(p, r, c, 250, new ItemStack(Items.COOKED_BEEF, 2));
            case "heal" -> {
                p.setHealth(p.getMaxHealth());
                p.removeAllEffects();
                c.say("Готово. Берегите себя. Медицинская помощь оказана бесплатно (ст. 41 Конституции РФ).");
            }
            case "pay_fines" -> {
                if (r.fineDebt <= 0) {
                    c.say("Задолженности по штрафам нет.");
                } else if (r.balance < r.fineDebt) {
                    c.say("Недостаточно средств: нужно " + r.fineDebt + " ₽.");
                } else {
                    r.balance -= r.fineDebt;
                    d.budget += r.fineDebt;
                    c.say("Оплачено " + r.fineDebt + " ₽. Квитанция сформирована.");
                    r.fineDebt = 0;
                    r.fineDueDay = -1;
                    r.wanted.remove(Article.KOAP_20_25);
                }
            }
            case "statement" -> c.say("На вашем счёте " + r.balance + " ₽. Неоплаченных штрафов — " + r.fineDebt + " ₽.");
            case "passport" -> {
                if (r.balance < 200) {
                    c.say("Госпошлина 200 ₽ (ст. 333.33 НК РФ). Не хватает.");
                    return;
                }
                r.balance -= 200;
                d.budget += 200;
                ItemStack id = new ItemStack(ModItems.ID_CARD.get());
                id.setHoverName(Component.literal(pf.isState() ? "Служебное удостоверение (" + pf.title + ")" : "Паспорт гражданина РФ"));
                if (!p.getInventory().add(id)) p.drop(id, false);
                c.say("Ваш документ готов. Распишитесь в получении.");
            }
            case "uni_info" -> {
                c.say("Всё просто, " + addr + ". Поступаете, ходите днём на лекции в аудиторию 101 — " + LawSchool.LECTURE_SECONDS
                        + " секунд в аудитории это одна пара. По каждому из семи предметов — две лекции и экзамен из трёх вопросов. Сдали всё — диплом.");
            }
            case "uni_enroll" -> LawSchool.enroll(p);
            case "uni_exam" -> LawSchool.startExam(p);
            case "drink" -> {
                if (r.balance < 150) {
                    c.say("Без денег не наливаем.");
                    return;
                }
                r.balance -= 150;
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 400, 0));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 0));
                c.say("Будем! Закусывайте. И на улице — потише, ППС рядом ходит.");
            }
            case "candle" -> {
                if (r.balance < 50) return;
                r.balance -= 50;
                r.civRep += rnd.nextInt(3) == 0 ? 1 : 0;
                c.say("Храни вас Господь.");
            }
            case "demands" -> {
                c.say("Требуем: " + ActivityPlanner.SLOGANS[rnd.nextInt(ActivityPlanner.SLOGANS.length)]);
                if (pf == Faction.GOV) {
                    CityManager.changeSatisfaction(d, 1.0);
                    msg(p, "Вы выслушали протестующих. Доверие к власти немного выросло.", ChatFormatting.GREEN);
                }
            }
            case "check_docs" -> {
                if (!pf.canArrest()) return;
                c.getNavigation().stop();
                c.say(c.female ? "Вот, пожалуйста." : "Пожалуйста, " + addr + ".");
                if (c.isWanted()) {
                    StringBuilder sb = new StringBuilder();
                    for (Article a : c.wanted) sb.append(a.code).append("; ");
                    msg(p, "По базе ИБД-Р: " + c.fullName() + " — В РОЗЫСКЕ: " + sb, ChatFormatting.RED);
                } else if (c.drunk) {
                    c.addWanted(Article.KOAP_20_21);
                    msg(p, "Гражданин в состоянии опьянения в общественном месте — основание для доставления (ст. 20.21 КоАП РФ).", ChatFormatting.YELLOW);
                } else if (cf.isCriminal() && !c.getMainHandItem().isEmpty() && rnd.nextFloat() < 0.4f) {
                    c.addWanted(Article.UK_222);
                    CrimeSystem.registerWitnessedCase(level, c, Article.UK_222, c.blockPosition());
                    msg(p, "При досмотре обнаружено оружие без разрешения! Основание для задержания: ст. 222 УК РФ.", ChatFormatting.RED);
                } else {
                    msg(p, "Паспорт гражданина РФ: " + c.fullName() + (c.everConvicted ? " (ранее судим)" : "") + ". В розыске не значится.", ChatFormatting.GREEN);
                }
                QuestManager.onAction(p, "check_docs", c);
            }
            case "arrest" -> CrimeSystem.playerArrestsNpc(p, c);
            case "interrogate" -> {
                CriminalCase cs = CrimeSystem.pickCaseFor(d, pf == Faction.SK ? Faction.SK : Faction.UMVD, rnd);
                if (cs == null) cs = CrimeSystem.pickCaseFor(d, Faction.UMVD, rnd);
                if (cs != null && rnd.nextFloat() < 0.6f) {
                    c.say("Да, видел кое-что... Запишите: возле " + cs.pos.getX() + ", " + cs.pos.getZ() + ".");
                    CrimeSystem.progressCase(level, d, cs, 15);
                    msg(p, "Показания приобщены к делу №" + cs.id + ".", ChatFormatting.AQUA);
                } else {
                    c.say("Ничего не видел, ничего не знаю. Ст. 51 Конституции, могу не свидетельствовать?");
                    msg(p, "(Против себя и близких — да. В остальных случаях свидетель обязан дать показания, ст. 56 УПК РФ.)", ChatFormatting.GRAY);
                }
                QuestManager.onAction(p, "interrogate", c);
            }
            case "complaint" -> {
                c.say("Управляющая компания не делает перерасчёт уже полгода!");
                CityManager.changeSatisfaction(d, 0.5);
                QuestManager.onAction(p, "complaint", c);
            }
            case "reception" -> {
                c.say(rnd.nextBoolean() ? "Сделайте пешеходный переход у школы!" : "Во дворе нет освещения, страшно ходить.");
                CityManager.changeSatisfaction(d, 0.7);
                c.mood = Math.min(100, c.mood + 10);
                r.civRep++;
                QuestManager.onAction(p, "reception", c);
            }
            case "informant" -> {
                if (rnd.nextFloat() < 0.35f + (c.isWanted() ? 0.3f : 0f)) {
                    c.informant = true;
                    c.say("Ладно... Но если наши узнают — мне конец.");
                    CitizenEntity mate = ActivityPlanner.findNear(level, d.loc(cf.hqKey), 40,
                            x -> x.getFaction() == cf && x != c && !x.isWanted() && !x.isJailed());
                    if (mate != null) {
                        Article a = cf == Faction.SKINHEADS ? Article.UK_282_1 : (cf == Faction.BIKERS ? Article.UK_222 : Article.UK_210);
                        mate.addWanted(a);
                        CrimeSystem.registerWitnessedCase(level, mate, a, mate.blockPosition());
                        msg(p, "Источник сообщил: «" + mate.nickname + "» (" + mate.fullName() + ") причастен к " + a.code + ". Объявлен в розыск.", ChatFormatting.AQUA);
                    }
                    QuestManager.onAction(p, "informant", c);
                } else {
                    c.say("Я с вами разговаривать не буду.");
                }
            }
            case "extort" -> {
                if (pf.isState()) return;
                long sum = 1000 + rnd.nextInt(2000);
                r.balance += sum;
                c.say(rnd.nextBoolean() ? "Берите и уходите..." : "Я в полицию пойду!");
                c.mood = Math.max(0, c.mood - 15);
                c.changeRelation(p.getUUID(), -20);
                msg(p, "Получено " + sum + " ₽.", ChatFormatting.GOLD);
                CrimeSystem.commitByPlayer(p, Article.UK_163, c);
                QuestManager.onAction(p, "extort", c);
            }
            case "recruit" -> {
                if (!pf.isCriminal()) return;
                if (c.everConvicted || rnd.nextFloat() < c.criminality || c.mood < 25) {
                    c.changeFaction(pf, 0, d);
                    c.say("Я в деле.");
                    r.crimRep += 2;
                    QuestManager.onAction(p, "recruit", c);
                    // Вовлечение — ч. 1.1 ст. 210 УК РФ
                    CrimeSystem.commitByPlayer(p, Article.UK_210, null);
                } else {
                    c.say("Не, мне проблемы не нужны.");
                }
            }
            case "surrender" -> {
                if (cf.canArrest() && r.isWanted()) CrimeSystem.arrestPlayer(level, c, p, true);
            }
            case "bribe" -> {
                if (!cf.canArrest() || !r.isWanted()) return;
                int cost = 20000 * Math.max(1, r.wantedStars());
                if (r.balance < cost) {
                    msg(p, "Нужно " + cost + " ₽. У вас столько нет.", ChatFormatting.RED);
                    return;
                }
                if (rnd.nextFloat() < 0.25f + c.criminality) {
                    r.balance -= cost;
                    r.wanted.clear();
                    c.setTarget(null);
                    c.say("Я ничего не видел. Свободен.");
                    msg(p, "Взятка принята. Розыск снят... пока. Коррупционные преступления выявляют ФСБ и СК.", ChatFormatting.DARK_RED);
                    CrimeSystem.commitByPlayer(p, Article.UK_291, null);
                    c.criminality = Math.min(1f, c.criminality + 0.1f);
                } else {
                    c.say("Взятку предлагаете?! Вы задержаны — ст. 291 УК РФ!");
                    r.wanted.add(Article.UK_291);
                    c.setTarget(p);
                }
            }
            case "join_info" -> {
                long day = level.getDayTime() / 24000L;
                List<String> unmet = FactionService.unmetRequirements(r, cf, day);
                msg(p, "── " + cf.title + " ──", ChatFormatting.GOLD);
                msg(p, cf.description, ChatFormatting.WHITE);
                msg(p, "Подчинённость: " + cf.superior, ChatFormatting.GRAY);
                msg(p, "Основание: " + cf.lawBasis, ChatFormatting.GRAY);
                if (unmet.isEmpty()) msg(p, "Требования выполнены. Шанс приёма: " + Math.round(FactionService.chance(r, cf) * 100) + "%. Подайте заявление (клавиша G).", ChatFormatting.GREEN);
                else for (String s : unmet) msg(p, " • " + s, ChatFormatting.RED);
            }
            default -> {
            }
        }
        Network.syncPlayer(p);
    }

    private static void buy(ServerPlayer p, PlayerRecord r, CitizenEntity c, int price, ItemStack stack) {
        if (c.relation(p.getUUID()) >= ru.gorod.rusgorod.social.Relations.FRIEND) {
            price = price * 7 / 10;
            c.say("Для своих — скидка!", true);
        }
        c.changeRelation(p.getUUID(), 1);
        if (r.balance < price) {
            c.say("Денег не хватает.");
            return;
        }
        r.balance -= price;
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        c.say("Спасибо за покупку! Чек не забудьте (54-ФЗ о ККТ).");
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.sendSystemMessage(Component.literal(s).withStyle(c));
    }

    private DialogService() {
    }
}
