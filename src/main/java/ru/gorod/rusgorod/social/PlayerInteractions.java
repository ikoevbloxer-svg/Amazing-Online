package ru.gorod.rusgorod.social;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.faction.FactionService;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.net.OpenDialogPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Взаимодействие игрок ↔ игрок: документы, честь, деньги, штрафы, задержание, кадровые решения, приглашения. */
public final class PlayerInteractions {
    private record Invite(Faction faction, int rank, UUID from, long until) {
    }

    private static final Map<UUID, Invite> INVITES = new HashMap<>();
    private static final Article[] FINES = {Article.KOAP_20_1, Article.KOAP_20_20, Article.KOAP_20_21, Article.KOAP_19_3};

    public static void open(ServerPlayer p, ServerPlayer t) {
        GorodData d = GorodData.get(p.serverLevel());
        PlayerRecord r = d.player(p.getUUID());
        PlayerRecord tr = d.player(t.getUUID());
        StringBuilder body = new StringBuilder(t.getName().getString()).append("\n");
        if (tr.uniform && tr.faction != Faction.CIVILIAN) body.append("В форме: ").append(tr.faction.title).append(", ").append(tr.faction.rankName(tr.rank)).append("\n");
        if (r.faction.canArrest() && tr.isWanted()) body.append("§cЧислится в розыске.\n");
        if (tr.cuffed) body.append("§eВ наручниках.\n");

        List<String[]> o = new ArrayList<>();
        Invite inv = INVITES.get(p.getUUID());
        if (inv != null && inv.from().equals(t.getUUID()) && p.serverLevel().getGameTime() < inv.until()) {
            o.add(new String[]{"pl:inv_yes", "§aПринять приглашение: " + inv.faction().title + " (" + inv.faction().rankName(inv.rank()) + ")"});
            o.add(new String[]{"pl:inv_no", "Отказаться"});
        }
        o.add(new String[]{"pl:show_id", r.faction.isState() ? "Предъявить удостоверение" : "Показать паспорт"});
        if (r.faction.insignia && tr.faction.insignia && r.uniform) o.add(new String[]{"pl:salute", "Отдать воинское приветствие"});
        o.add(new String[]{"pl:pay_100", "Передать 100 ₽"});
        o.add(new String[]{"pl:pay_1000", "Передать 1 000 ₽"});
        o.add(new String[]{"pl:pay_5000", "Передать 5 000 ₽"});
        if (r.faction.canArrest() && !tr.faction.isLawEnforcement()) {
            o.add(new String[]{"pl:check", "Проверить документы (ст. 13 ФЗ «О полиции»)"});
            for (int i = 0; i < FINES.length; i++)
                o.add(new String[]{"pl:fine_" + i, "Выписать штраф: " + FINES[i].code + " (" + FINES[i].fine + " ₽)"});
            if (tr.isWanted()) o.add(new String[]{"pl:arrest", "§cЗадержать и доставить (ст. 91 УПК РФ)"});
        }
        // кадровые решения: руководитель (две верхние ступени) или старший по званию в своей фракции
        if (r.faction != Faction.CIVILIAN) {
            boolean leader = r.rank >= r.faction.topRank() - 1;
            if (tr.faction == r.faction && r.rank > tr.rank) {
                if (tr.rank + 1 < r.rank) o.add(new String[]{"pl:promote", "Повысить до: " + r.faction.rankName(tr.rank + 1)});
                if (tr.rank > 0) o.add(new String[]{"pl:demote", "Понизить до: " + r.faction.rankName(tr.rank - 1)});
                if (leader) o.add(new String[]{"pl:fire", "§cУволить / выгнать"});
            }
            if (tr.faction == Faction.CIVILIAN && leader) o.add(new String[]{"pl:invite", "Пригласить в «" + r.faction.title + "»"});
        }
        Network.sendTo(p, new OpenDialogPacket(t.getId(), t.getName().getString(), body.toString(), o));
    }

    public static void handle(ServerPlayer p, ServerPlayer t, String option) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        PlayerRecord tr = d.player(t.getUUID());
        String name = p.getName().getString(), tname = t.getName().getString();
        switch (option) {
            case "pl:show_id" -> {
                t.sendSystemMessage(Component.literal("══ " + name + " предъявляет " + (r.faction.isState() ? "служебное удостоверение" : "паспорт") + " ══").withStyle(ChatFormatting.GOLD));
                t.sendSystemMessage(Component.literal("Ф.И.О.: " + name));
                if (r.faction.isState()) {
                    t.sendSystemMessage(Component.literal("Ведомство: " + r.faction.title));
                    t.sendSystemMessage(Component.literal("Звание: " + r.faction.rankName(r.rank) + ", должность: " + r.faction.positionName(r.rank)));
                } else {
                    t.sendSystemMessage(Component.literal("Гражданство: Российская Федерация. Место жительства: г. Новоберёзовск."));
                }
                if (r.legalEducation) t.sendSystemMessage(Component.literal("Образование: высшее юридическое").withStyle(ChatFormatting.GRAY));
                p.sendSystemMessage(Component.literal("Вы предъявили документы " + tname + ".").withStyle(ChatFormatting.GRAY));
            }
            case "pl:salute" -> me(level, p, "отдаёт воинское приветствие " + (tr.uniform ? tr.faction.rankName(tr.rank).toLowerCase() + "у " : "") + tname);
            case "pl:pay_100", "pl:pay_1000", "pl:pay_5000" -> {
                long sum = Long.parseLong(option.substring(7));
                if (r.balance < sum) {
                    p.sendSystemMessage(Component.literal("Недостаточно средств.").withStyle(ChatFormatting.RED));
                    return;
                }
                r.balance -= sum;
                tr.balance += sum;
                me(level, p, "передаёт " + sum + " ₽ " + tname);
                // Передача денег сотруднику при исполнении от разыскиваемого — признак взятки
                if (tr.faction.isLawEnforcement() && r.isWanted() && sum >= 1000) {
                    CrimeSystem.commitByPlayer(p, Article.UK_291, null);
                    CrimeSystem.commitByPlayer(t, Article.UK_290, null);
                }
            }
            case "pl:check" -> {
                if (!r.faction.canArrest()) return;
                p.sendSystemMessage(Component.literal("══ Проверка по базам (ИБД-Р, ФИС ГИБДД-М) ══").withStyle(ChatFormatting.GOLD));
                p.sendSystemMessage(Component.literal("Ф.И.О.: " + tname));
                p.sendSystemMessage(Component.literal("Розыск: " + (tr.isWanted() ? "ДА — " + articles(tr) : "не значится")).withStyle(tr.isWanted() ? ChatFormatting.RED : ChatFormatting.GREEN));
                p.sendSystemMessage(Component.literal("Судимость: " + (tr.hasActiveRecord(level.getDayTime() / 24000L) ? "непогашенная" : tr.everConvicted ? "погашена" : "нет")));
                p.sendSystemMessage(Component.literal("Неоплаченные штрафы: " + tr.fineDebt + " ₽"));
                t.sendSystemMessage(Component.literal(r.faction.positionName(r.rank) + " " + r.faction.rankName(r.rank).toLowerCase() + " " + name + " проверяет ваши документы.").withStyle(ChatFormatting.GRAY));
                r.points += 1;
            }
            case "pl:arrest" -> {
                if (!r.faction.canArrest() || !tr.isWanted()) return;
                if (!tr.cuffed && !t.isCrouching() && t.getHealth() > t.getMaxHealth() * 0.5f) {
                    p.sendSystemMessage(Component.literal("Сначала наденьте наручники или дождитесь, пока задержанный сдастся (присядет).").withStyle(ChatFormatting.GRAY));
                    return;
                }
                CrimeSystem.arrestPlayer(level, p, t, t.isCrouching());
            }
            case "pl:promote", "pl:demote" -> {
                if (tr.faction != r.faction || r.rank <= tr.rank) return;
                int nr = tr.rank + (option.equals("pl:promote") ? 1 : -1);
                if (nr >= r.rank || nr < 0) return;
                String block = option.equals("pl:promote") ? FactionService.rankBlock(tr, tr.faction, nr) : null;
                if (block != null) {
                    p.sendSystemMessage(Component.literal("Нельзя: " + block).withStyle(ChatFormatting.RED));
                    return;
                }
                FactionService.setRank(t, tr, nr, p);
                p.sendSystemMessage(Component.literal(tname + " — теперь " + tr.faction.rankName(tr.rank) + ".").withStyle(ChatFormatting.GRAY));
            }
            case "pl:fire" -> {
                if (tr.faction != r.faction || r.rank <= tr.rank || r.rank < r.faction.topRank() - 1) return;
                FactionService.fire(t, tr, p);
            }
            case "pl:invite" -> {
                if (tr.faction != Faction.CIVILIAN || r.rank < r.faction.topRank() - 1) return;
                List<String> unmet = FactionService.unmetRequirements(tr, r.faction, level.getDayTime() / 24000L);
                if (!unmet.isEmpty()) {
                    p.sendSystemMessage(Component.literal(tname + " не соответствует требованиям: " + String.join("; ", unmet)).withStyle(ChatFormatting.RED));
                    return;
                }
                int startRank = tr.legalEducation && r.faction.officerRankIndex >= 0 ? r.faction.officerRankIndex : r.faction.startRank;
                INVITES.put(t.getUUID(), new Invite(r.faction, startRank, p.getUUID(), level.getGameTime() + 2400));
                t.sendSystemMessage(Component.literal(name + " приглашает вас: " + r.faction.title + ". ПКМ по нему → «Принять приглашение» (2 минуты).").withStyle(ChatFormatting.GOLD));
                p.sendSystemMessage(Component.literal("Приглашение отправлено.").withStyle(ChatFormatting.GRAY));
            }
            case "pl:inv_yes" -> {
                Invite inv = INVITES.remove(p.getUUID());
                if (inv == null || level.getGameTime() > inv.until() || r.faction != Faction.CIVILIAN) return;
                FactionService.enlist(p, r, inv.faction(), inv.rank());
                t.sendSystemMessage(Component.literal(name + " принял(а) приглашение.").withStyle(ChatFormatting.GREEN));
            }
            case "pl:inv_no" -> {
                INVITES.remove(p.getUUID());
                t.sendSystemMessage(Component.literal(name + " отказался(ась).").withStyle(ChatFormatting.GRAY));
            }
            default -> {
                if (option.startsWith("pl:fine_") && r.faction.canArrest()) {
                    int i = Integer.parseInt(option.substring(8));
                    if (i < 0 || i >= FINES.length) return;
                    Article a = FINES[i];
                    long day = level.getDayTime() / 24000L;
                    tr.fineDebt += a.fine;
                    if (tr.fineDueDay < 0) tr.fineDueDay = day + 3;
                    r.points += 5;
                    t.sendSystemMessage(Component.literal("Вам выписан штраф по " + a.full() + ": " + a.fine + " ₽. Оплатить в течение 3 суток (G → «Город»). Обжаловать — в суд (ст. 30.1 КоАП).").withStyle(ChatFormatting.YELLOW));
                    p.sendSystemMessage(Component.literal("Постановление вынесено: " + tname + ", " + a.code + ".").withStyle(ChatFormatting.GRAY));
                    Network.syncPlayer(t);
                }
            }
        }
        Network.syncPlayer(p);
        Network.syncPlayer(t);
    }

    private static String articles(PlayerRecord r) {
        StringBuilder sb = new StringBuilder();
        for (Article a : r.wanted) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(a.code);
        }
        return sb.toString();
    }

    /** Отыгрыш действия: «* Имя делает что-то». */
    public static void me(ServerLevel level, ServerPlayer p, String action) {
        Component msg = Component.literal("* " + p.getName().getString() + " " + action).withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer o : level.getEntitiesOfClass(ServerPlayer.class, new AABB(p.blockPosition()).inflate(20))) o.sendSystemMessage(msg);
    }

    private PlayerInteractions() {
    }
}
