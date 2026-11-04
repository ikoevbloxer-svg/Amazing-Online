package ru.gorod.rusgorod.net;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.entity.Profession;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.faction.FactionService;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.law.CriminalCase;
import ru.gorod.rusgorod.quest.QuestManager;
import ru.gorod.rusgorod.world.CityManager;

import java.util.ArrayList;
import java.util.List;

/** Диалоги игрока с NPC: набор действий зависит от фракций обеих сторон. */
public final class DialogService {

    private static final String[] CIV_LINES = {"Опять дорогу перекопали, третий раз за год.", "Пенсию проиндексировали, а цены в «Продуктах» видели?",
            "У нас во дворе лавочку поставили, хоть что-то.", "Говорят, на рынке опять «эти» деньги собирают.", "Автобус ходит раз в час, как жить?",
            "Сын в армию пошёл, по контракту. Горжусь.", "Вечером в парк лучше не ходить — шпана.", "Управляйка трубы не меняет, всё на бумаге."};
    private static final String[] COP_LINES = {"Проходите, гражданин, не задерживайтесь.", "Если что-то увидите — звоните 102 или 112.",
            "Ночью на районе неспокойно, будьте внимательны.", "Документы при себе носите — так спокойнее."};
    private static final String[] ARMY_LINES = {"Посторонним вход на территорию части запрещён.", "Служу России!", "До дембеля — как до Луны."};
    private static final String[] CRIM_LINES = {"Чё надо?", "Иди своей дорогой.", "Ты по делу или так?", "Здесь лишних не любят."};
    private static final String[] OFFICIAL_LINES = {"Приём граждан — по вторникам и четвергам.", "Обращение подайте в письменной форме, ответим в течение 30 дней.",
            "Работаем, работаем, всё под контролем."};

    public static void open(ServerPlayer p, CitizenEntity c) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        Faction pf = r.faction;
        Faction cf = c.getFaction();

        StringBuilder body = new StringBuilder();
        body.append(c.fullName()).append("\n");
        if (cf == Faction.CIVILIAN) body.append("Занятие: ").append(c.profession.title).append("\n");
        else body.append(cf.title).append(" — ").append(cf.positionName(c.rank)).append("\n");
        if (c.isJailed()) body.append("§cНаходится под стражей.\n");
        if (c.isProtesting()) body.append("§eУчастник публичного мероприятия.\n");
        if (pf.canArrest() && c.isWanted()) body.append("§cЧислится в розыске (ориентировка).\n");

        List<String[]> opts = new ArrayList<>();
        opts.add(new String[]{"talk", "Поговорить"});
        if (c.isJailed()) {
            send(p, c, body.toString(), opts);
            return;
        }
        if (c.profession.isTrader() && cf == Faction.CIVILIAN) {
            opts.add(new String[]{"buy_bread", "Купить хлеб (60 ₽)"});
            opts.add(new String[]{"buy_meat", "Купить шашлык (250 ₽)"});
        }
        if (c.isProtesting()) opts.add(new String[]{"demands", "Выслушать требования"});

        boolean isOfficial = pf.isState();
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

        if (!isOfficial && c.profession.isTrader() && cf == Faction.CIVILIAN)
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
        Entity e = level.getEntity(entityId);
        if (!(e instanceof CitizenEntity c) || p.distanceToSqr(c) > 8 * 8 || !c.isAlive()) return;
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        RandomSource rnd = level.random;
        Faction pf = r.faction;
        Faction cf = c.getFaction();
        c.getLookControl().setLookAt(p);

        switch (option) {
            case "talk" -> {
                String[] lines = switch (cf.type) {
                    case CIVIL -> CIV_LINES;
                    case CRIMINAL -> CRIM_LINES;
                    default -> cf == Faction.ARMY ? ARMY_LINES : (cf.isLawEnforcement() ? COP_LINES : OFFICIAL_LINES);
                };
                if (c.isProtesting()) lines = new String[]{"Мы мирно стоим! Имеем право — ст. 31 Конституции!"};
                if (c.isJailed()) lines = new String[]{"Я ни в чём не виноват, начальник...", "Адвоката мне! Ст. 48 Конституции!"};
                c.say(lines[rnd.nextInt(lines.length)]);
                QuestManager.onAction(p, "talk", c);
            }
            case "buy_bread" -> buy(p, r, c, 60, new ItemStack(Items.BREAD, 2));
            case "buy_meat" -> buy(p, r, c, 250, new ItemStack(Items.COOKED_BEEF, 2));
            case "demands" -> {
                c.say("Требуем: " + ru.gorod.rusgorod.entity.ai.ActivityPlanner.SLOGANS[rnd.nextInt(ru.gorod.rusgorod.entity.ai.ActivityPlanner.SLOGANS.length)]);
                if (pf == Faction.GOV) {
                    CityManager.changeSatisfaction(d, 1.0);
                    msg(p, "Вы выслушали протестующих. Доверие к власти немного выросло.", ChatFormatting.GREEN);
                }
            }
            case "check_docs" -> {
                if (!pf.canArrest()) return;
                c.getNavigation().stop();
                if (c.isWanted()) {
                    StringBuilder sb = new StringBuilder();
                    for (Article a : c.wanted) sb.append(a.code).append("; ");
                    msg(p, "По базе ИБД-Р: " + c.fullName() + " — В РОЗЫСКЕ: " + sb, ChatFormatting.RED);
                } else if (cf.isCriminal() && !c.getMainHandItem().isEmpty() && rnd.nextFloat() < 0.4f) {
                    c.addWanted(Article.UK_222);
                    CrimeSystem.registerWitnessedCase(level, c, Article.UK_222, c.blockPosition());
                    msg(p, "При досмотре обнаружено оружие без разрешения! Основание для задержания: ст. 222 УК РФ.", ChatFormatting.RED);
                } else {
                    msg(p, "Паспорт гражданина РФ: " + c.fullName() + (c.everConvicted ? " (ранее судим)" : "") + ". В розыске не значится.", ChatFormatting.GREEN);
                    c.say("Вот мой паспорт. Всё в порядке?");
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
                r.civRep++;
                QuestManager.onAction(p, "reception", c);
            }
            case "informant" -> {
                if (rnd.nextFloat() < 0.35f + (c.isWanted() ? 0.3f : 0f)) {
                    c.informant = true;
                    c.say("Ладно... Но если наши узнают — мне конец.");
                    CitizenEntity mate = ru.gorod.rusgorod.entity.ai.ActivityPlanner.findNear(level, d.loc(cf.hqKey), 40,
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
                msg(p, "Получено " + sum + " ₽.", ChatFormatting.GOLD);
                CrimeSystem.commitByPlayer(p, Article.UK_163, c);
                QuestManager.onAction(p, "extort", c);
            }
            case "recruit" -> {
                if (!pf.isCriminal()) return;
                if (c.everConvicted || rnd.nextFloat() < c.criminality) {
                    c.setFaction(pf);
                    c.rank = 0;
                    c.nickname = ru.gorod.rusgorod.entity.NameGen.nickname(rnd);
                    c.work = d.loc(pf.hqKey);
                    c.profession = Profession.UNEMPLOYED;
                    c.equip();
                    c.refreshName();
                    d.npcs.put(c.getUUID(), pf);
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
                    // Латентное дело — может быть раскрыто
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
        if (r.balance < price) {
            c.say("Денег не хватает, молодой человек.");
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
