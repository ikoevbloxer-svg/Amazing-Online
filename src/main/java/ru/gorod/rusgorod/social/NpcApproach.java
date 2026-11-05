package ru.gorod.rusgorod.social;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.entity.Profession;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.net.OpenDialogPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NPC сами подходят к игрокам: гопники «просят мелочь», пенсионеры просят на хлеб или проводить до дома,
 * пьяный цепляется, друзья делятся слухами. Ответ игрока имеет последствия: деньги, отношения, драка, уголовное дело.
 */
public final class NpcApproach {
    private static final Map<UUID, Long> PLAYER_COOLDOWN = new HashMap<>();
    /** Кто к кому сейчас «подошёл»: игрок → NPC и тип просьбы. */
    private static final Map<UUID, Object[]> PENDING = new HashMap<>();

    public static void tick(ServerLevel level, CitizenEntity c) {
        RandomSource r = c.getRandom();
        c.approachCooldown = 400 + r.nextInt(600);
        if (r.nextInt(4) != 0) return;
        List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, c.getBoundingBox().inflate(7), p -> !p.isSpectator() && c.hasLineOfSight(p));
        if (near.isEmpty()) return;
        ServerPlayer p = near.get(r.nextInt(near.size()));
        long now = level.getGameTime();
        Long last = PLAYER_COOLDOWN.get(p.getUUID());
        if (last != null && now - last < 2400) return;
        GorodData d = GorodData.get(level);
        PlayerRecord pr = d.player(p.getUUID());
        if (pr.isJailed(now)) return;
        Faction cf = c.getFaction();
        int rel = c.relation(p.getUUID());
        String kind = null;
        String line = null;
        List<String[]> opts = new ArrayList<>();

        if ((cf == Faction.SKINHEADS || cf == Faction.RUS_MAFIA && c.rank <= 1 || cf == Faction.KAVKAZ_MAFIA && c.rank <= 1)
                && !pr.faction.isState() && pr.faction != cf && rel < Relations.KNOWN && !(pr.uniform && pr.faction.isLawEnforcement())) {
            kind = "gop";
            line = cf == Faction.SKINHEADS ? "Слышь, мелочь есть? А если найду?" : "Э, брат, подкинь пару тысяч на общее. Тебе же спокойнее будет.";
            opts.add(new String[]{"ap_pay", "Отдать 500 ₽"});
            opts.add(new String[]{"ap_refuse", "§cОтказать («Иди своей дорогой»)"});
            opts.add(new String[]{"ap_police", "Позвать полицию"});
        } else if (cf == Faction.CIVILIAN && c.drunk && rel < Relations.KNOWN) {
            kind = "drunk";
            line = "Ты чё на меня смотришь? Ты кто такой вообще, а?";
            opts.add(new String[]{"ap_calm", "«Всё нормально, отец, иди домой»"});
            opts.add(new String[]{"ap_insult", "§cПослать его"});
        } else if (cf == Faction.CIVILIAN && c.profession == Profession.PENSIONER && r.nextBoolean()) {
            if (r.nextBoolean() && c.home != null && c.distanceToSqr(c.home.getX(), c.home.getY(), c.home.getZ()) > 30 * 30) {
                kind = "escort";
                line = "Сынок, проводи меня до дома, а? Вечером страшно одной, шпана кругом.";
                opts.add(new String[]{"ap_escort", "Проводить (по пути он(а) пойдёт за вами)"});
                opts.add(new String[]{"ap_no", "Извините, тороплюсь"});
            } else {
                kind = "beg";
                line = "Внучок, не поможешь пенсионеру? Пенсию задержали, на хлеб не хватает...";
                opts.add(new String[]{"ap_give", "Дать 100 ₽"});
                opts.add(new String[]{"ap_no", "Нет, извините"});
            }
        } else if (rel >= Relations.FRIEND && (cf == Faction.CIVILIAN || cf.isCriminal())) {
            kind = "tip";
            line = "О, " + p.getName().getString() + "! Слушай, по секрету скажу...";
            opts.add(new String[]{"ap_listen", "Выслушать"});
            opts.add(new String[]{"ap_no", "Потом, некогда"});
        } else if (cf == Faction.CIVILIAN && c.profession.isTrader() && r.nextInt(3) == 0) {
            kind = "trade";
            line = "Пирожки горячие, с капустой! Возьмёте? Для вас — по сто рублей.";
            opts.add(new String[]{"ap_buy", "Купить 3 пирожка (100 ₽)"});
            opts.add(new String[]{"ap_no", "Не надо"});
        }
        if (kind == null) return;
        PLAYER_COOLDOWN.put(p.getUUID(), now);
        PENDING.put(p.getUUID(), new Object[]{c.getId(), kind});
        c.getNavigation().moveTo(p, 0.8);
        c.getLookControl().setLookAt(p);
        c.talkUntil = now + 200;
        c.say(line, true);
        Network.sendTo(p, new OpenDialogPacket(c.getId(), c.displayTitle(), "§e" + line, opts));
    }

    /** Ответ игрока (опции с префиксом ap_). */
    public static void handle(ServerPlayer p, CitizenEntity c, String option) {
        Object[] pend = PENDING.remove(p.getUUID());
        if (pend == null || (int) pend[0] != c.getId()) return;
        String kind = (String) pend[1];
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        RandomSource rnd = level.random;
        c.talkUntil = 0;
        switch (option) {
            case "ap_pay" -> {
                if (r.balance < 500) {
                    c.say("Пустой? Ну смотри, в следующий раз приготовь.", true);
                    return;
                }
                r.balance -= 500;
                d.addFunds(c.getFaction(), 500);
                c.changeRelation(p.getUUID(), 3);
                c.say("Вот и молодец. Теперь ты под нами ходишь, никто не тронет.", true);
                msg(p, "Вы отдали 500 ₽. Это вымогательство (ст. 163 УК РФ) — можете написать заявление в полиции.", ChatFormatting.GRAY);
            }
            case "ap_refuse" -> {
                c.changeRelation(p.getUUID(), -15);
                if (rnd.nextInt(100) < 40) {
                    c.say("Ну всё, ты сам напросился!", true);
                    c.setTarget(p);
                    CrimeSystem.commitByNpc(level, c, Article.UK_161, p);
                } else {
                    c.say("Ладно, гуляй пока. Ещё встретимся.", true);
                    p.sendSystemMessage(Component.literal("+1 к уважению на районе: вы не дали себя запугать.").withStyle(ChatFormatting.GRAY));
                    r.civRep++;
                }
            }
            case "ap_police" -> {
                CitizenEntity cop = null;
                for (CitizenEntity x : level.getEntitiesOfClass(CitizenEntity.class, p.getBoundingBox().inflate(40), x -> x.getFaction().canArrest() && !x.isBusy())) {
                    cop = x;
                    break;
                }
                c.changeRelation(p.getUUID(), -30);
                if (cop != null) {
                    c.addWanted(Article.UK_163);
                    cop.setTarget(c);
                    cop.say("Стоять! Вымогательство — ст. 163 УК РФ!", true);
                    r.civRep += 2;
                } else {
                    c.say("Полицию? Ну-ну. Нет тут никого. Запомню тебя.", true);
                    if (rnd.nextBoolean()) c.setTarget(p);
                }
            }
            case "ap_calm" -> {
                if (rnd.nextInt(3) > 0) {
                    c.say("Ладно... Ты нормальный мужик. Пойду я.", true);
                    c.changeRelation(p.getUUID(), 5);
                } else {
                    c.say("Ты меня учить будешь?!", true);
                    c.setTarget(p);
                }
            }
            case "ap_insult" -> {
                c.say("Ах ты!..", true);
                c.setTarget(p);
                c.changeRelation(p.getUUID(), -20);
            }
            case "ap_give" -> {
                if (r.balance < 100) return;
                r.balance -= 100;
                r.civRep += 2;
                c.changeRelation(p.getUUID(), 12);
                c.mood = Math.min(100, c.mood + 15);
                c.say("Спасибо, родной! Дай бог тебе здоровья.", true);
            }
            case "ap_escort" -> {
                c.order = CitizenEntity.Order.FOLLOW;
                c.commander = p.getUUID();
                c.escortTo = c.home;
                c.orderUntil = level.getGameTime() + 20 * 60 * 5;
                c.say("Ой, спасибо! Я за тобой, только не быстро.", true);
                msg(p, "Проводите до дома: X " + c.home.getX() + ", Z " + c.home.getZ() + ". Он(а) идёт за вами.", ChatFormatting.YELLOW);
            }
            case "ap_listen" -> c.say(tip(level, d, c), true);
            case "ap_buy" -> {
                if (r.balance < 100) return;
                r.balance -= 100;
                ItemStackHelper.give(p, net.minecraft.world.item.Items.BREAD, 3);
                c.changeRelation(p.getUUID(), 3);
                c.say("Кушайте на здоровье!", true);
            }
            default -> {
                c.changeRelation(p.getUUID(), -1);
                c.say(rnd.nextBoolean() ? "Ну и ладно." : "Эх...", true);
            }
        }
        Network.syncPlayer(p);
    }

    public static boolean hasPending(ServerPlayer p, CitizenEntity c) {
        Object[] pend = PENDING.get(p.getUUID());
        return pend != null && (int) pend[0] == c.getId();
    }

    /** Довели до дома. */
    public static void escortDone(ServerPlayer p, CitizenEntity c) {
        GorodData d = GorodData.get(p.serverLevel());
        PlayerRecord r = d.player(p.getUUID());
        c.order = CitizenEntity.Order.NONE;
        c.commander = null;
        c.escortTo = null;
        c.changeRelation(p.getUUID(), 20);
        r.balance += 500;
        r.civRep += 3;
        c.say("Вот я и дома! Спасибо тебе, " + p.getName().getString() + "! Возьми, не обижай старика(у).", true);
        msg(p, "+500 ₽, +3 репутации. " + c.firstName + " теперь относится к вам лучше.", ChatFormatting.GREEN);
        Network.syncPlayer(p);
    }

    /** Слух от друга: где разыскиваемый, кто кого крышует, что замышляют. */
    public static String tip(ServerLevel level, GorodData d, CitizenEntity c) {
        RandomSource r = level.random;
        List<CitizenEntity> wanted = level.getEntitiesOfClass(CitizenEntity.class, c.getBoundingBox().inflate(150), x -> x.isWanted() && !x.isJailed());
        if (!wanted.isEmpty() && r.nextBoolean()) {
            CitizenEntity w = wanted.get(r.nextInt(wanted.size()));
            return "Видел " + w.fullName() + " — его вроде полиция ищет. Был возле X " + w.getBlockX() + ", Z " + w.getBlockZ() + ".";
        }
        ru.gorod.rusgorod.world.Turfs.Turf t = d.turfs.isEmpty() ? null : d.turfs.get(r.nextInt(d.turfs.size()));
        if (t != null) {
            return t.owner == null ? "«" + t.name + "» сейчас ничья. Скоро кто-нибудь приберёт к рукам."
                    : "«" + t.name + "» держит «" + t.owner.title + "». Говорят, " + (t.business ? "платят им каждый месяц." : "чужим туда лучше не соваться.");
        }
        return "Да ничего особенного. Цены растут, а так — живём.";
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.sendSystemMessage(Component.literal(s).withStyle(c));
    }

    /** Мелкий помощник выдачи предметов. */
    static final class ItemStackHelper {
        static void give(ServerPlayer p, net.minecraft.world.item.Item item, int n) {
            net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(item, n);
            if (!p.getInventory().add(st)) p.drop(st, false);
        }
    }

    private NpcApproach() {
    }
}
