package ru.gorod.rusgorod.law;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.util.Scheduler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Судебное заседание по уголовному делу игрока: подсудимый доставляется в зал (клетку),
 * судья открывает заседание, государственный обвинитель (NPC или игрок-прокурор) просит наказание,
 * защитник (игрок с дипломом юриста) просит снисхождения, подсудимому даётся последнее слово (ст. 293 УПК),
 * затем оглашается приговор (ст. 310 УПК).
 */
public final class Court {
    private static final Map<UUID, Long> LAST_WORD = new HashMap<>();
    private static final Set<UUID> REPENTED = new HashSet<>();
    private static final Set<UUID> IN_TRIAL = new HashSet<>();

    /** Каждые 20 тиков для каждого игрока. */
    public static void tick(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        long now = level.getGameTime();
        if (r.trialAt < 0 || now < r.trialAt || IN_TRIAL.contains(p.getUUID())) return;
        r.trialAt = -1;
        if (!r.isJailed(now) || r.jailType != 2) return;
        start(level, d, p, r);
    }

    /** Последнее слово подсудимого — перехват сообщения в чате. */
    public static boolean onChat(ServerPlayer p, String raw) {
        Long until = LAST_WORD.get(p.getUUID());
        if (until == null || p.serverLevel().getGameTime() > until) return false;
        String m = raw.toLowerCase().replace('ё', 'е');
        if (m.contains("раскаива") || m.contains("признаю") || m.contains("прошу прощения") || m.contains("виноват") || m.contains("прошу снисхожд")) {
            REPENTED.add(p.getUUID());
        }
        return true;
    }

    private static void start(ServerLevel level, GorodData d, ServerPlayer p, PlayerRecord r) {
        UUID id = p.getUUID();
        IN_TRIAL.add(id);
        BlockPos dock = d.loc("court_dock");
        BlockPos hall = d.loc("court");
        p.teleportTo(level, dock.getX() + 0.5, dock.getY(), dock.getZ() + 0.5, p.getYRot(), p.getXRot());
        String judge = "Председательствующий судья";
        CitizenEntity judgeNpc = null;
        for (CitizenEntity c : level.getEntitiesOfClass(CitizenEntity.class, new AABB(hall).inflate(40), c -> c.getFaction() == Faction.COURT && !c.isJailed())) {
            judgeNpc = c;
            judge = "Судья " + c.lastName + " " + c.initials();
            break;
        }
        String prosecutor = "Государственный обвинитель";
        ServerPlayer prokPlayer = null;
        ServerPlayer lawyer = null;
        for (ServerPlayer o : level.getEntitiesOfClass(ServerPlayer.class, new AABB(hall).inflate(16))) {
            if (o == p) continue;
            PlayerRecord or = d.player(o.getUUID());
            if (or.faction == Faction.PROK && prokPlayer == null) {
                prokPlayer = o;
                prosecutor = or.faction.positionName(or.rank) + " " + o.getName().getString();
            } else if (or.legalEducation && lawyer == null && !or.faction.isState()) {
                lawyer = o;
            }
        }
        if (prokPlayer == null) {
            for (CitizenEntity c : level.getEntitiesOfClass(CitizenEntity.class, new AABB(hall).inflate(60), c -> c.getFaction() == Faction.PROK)) {
                prosecutor = "Помощник прокурора " + c.lastName + " " + c.initials();
                break;
            }
        }
        long remain = Math.max(600, r.jailUntil - level.getGameTime() - 700);
        int years = (int) Math.max(1, Math.min(20, remain / 1200));
        final String fJudge = judge, fPros = prosecutor, arts = r.trialArticles, name = p.getName().getString();
        final CitizenEntity fJudgeNpc = judgeNpc;
        final ServerPlayer fProk = prokPlayer, fLawyer = lawyer;

        say(level, hall, p, "[Секретарь судебного заседания]", "Встать, суд идёт!", ChatFormatting.GRAY, 20);
        say(level, hall, p, "[" + fJudge + "]", "Прошу садиться. Судебное заседание объявляется открытым. Рассматривается уголовное дело в отношении " + name
                + ", обвиняемого по " + arts + ".", ChatFormatting.GOLD, 70);
        say(level, hall, p, "[" + fJudge + "]", "Подсудимому разъяснены права, предусмотренные ст. 47 УПК РФ. Слово предоставляется государственному обвинителю.", ChatFormatting.GOLD, 130);
        say(level, hall, p, "[" + fPros + "]", "Вина подсудимого доказана материалами дела. Прошу назначить наказание в виде лишения свободы сроком на "
                + years + " " + yearsWord(years) + " в исправительной колонии общего режима.", ChatFormatting.AQUA, 190);
        if (fLawyer != null) {
            say(level, hall, p, "[Защитник " + fLawyer.getName().getString() + "]", "Прошу учесть смягчающие обстоятельства (ст. 61 УК РФ) и назначить минимальное наказание.", ChatFormatting.GREEN, 250);
        }
        Scheduler.later(300, () -> {
            if (!p.isAlive()) return;
            LAST_WORD.put(id, level.getGameTime() + 240);
            line(level, hall, p, "[" + fJudge + "]", "Подсудимый, вам предоставляется последнее слово (ст. 293 УПК РФ). (Напишите в чат — у вас 12 секунд.)", ChatFormatting.GOLD);
        });
        Scheduler.later(560, () -> {
            LAST_WORD.remove(id);
            boolean repent = REPENTED.remove(id);
            PlayerRecord rr = GorodData.get(level).player(id);
            long rem = Math.max(600, rr.jailUntil - level.getGameTime());
            double k = 1.0;
            if (repent) k -= 0.2;
            if (fLawyer != null && fLawyer.isAlive()) {
                k -= 0.1;
                PlayerRecord lr = GorodData.get(level).player(fLawyer.getUUID());
                long fee = Math.min(rr.balance, 3000);
                rr.balance -= fee;
                lr.balance += fee;
                lr.civRep += 2;
                fLawyer.sendSystemMessage(Component.literal("Гонорар за защиту: " + fee + " ₽.").withStyle(ChatFormatting.GREEN));
            }
            if (fProk != null && fProk.isAlive()) {
                k += 0.15;
                PlayerRecord pr = GorodData.get(level).player(fProk.getUUID());
                pr.points += 20;
                pr.legalExpDays += 1;
                fProk.sendSystemMessage(Component.literal("Вы поддержали государственное обвинение. +20 служебных баллов.").withStyle(ChatFormatting.AQUA));
                Network.syncPlayer(fProk);
            }
            long finalRem = Math.max(600, (long) (rem * k));
            int finalYears = (int) Math.max(1, Math.min(20, finalRem / 1200));
            rr.jailUntil = level.getGameTime() + finalRem;
            if (fJudgeNpc != null && fJudgeNpc.isAlive()) fJudgeNpc.setAnim(CitizenEntity.ANIM_TALK, 60);
            line(level, hall, p, "[" + fJudge + "]", "Именем Российской Федерации! Суд приговорил: признать " + name + " виновным по " + arts
                    + " и назначить наказание " + finalYears + " " + yearsWord(finalYears) + " лишения свободы"
                    + (repent ? " с учётом раскаяния (ч. 2 ст. 61 УК РФ)" : "") + ". Приговор может быть обжалован в течение 15 суток (ст. 389.4 УПК РФ).", ChatFormatting.GOLD);
            if (p.isAlive()) {
                List<BlockPos> cells = GorodData.get(level).sizoCells;
                BlockPos cell = cells.isEmpty() ? GorodData.get(level).loc("sizo") : cells.get(level.random.nextInt(cells.size()));
                p.teleportTo(level, cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, p.getYRot(), p.getXRot());
                p.sendSystemMessage(Component.literal("Вас конвоировали обратно. Осталось отбыть: " + finalRem / 20 + " сек.").withStyle(ChatFormatting.RED));
            }
            GorodData.get(level).addNews("Суд: " + name + " приговорён по " + arts + ".");
            IN_TRIAL.remove(id);
            Network.syncPlayer(p);
        });
    }

    private static void say(ServerLevel level, BlockPos hall, ServerPlayer defendant, String who, String text, ChatFormatting c, int delay) {
        Scheduler.later(delay, () -> line(level, hall, defendant, who, text, c));
    }

    private static void line(ServerLevel level, BlockPos hall, ServerPlayer defendant, String who, String text, ChatFormatting c) {
        Component msg = Component.literal(who + " ").withStyle(c).append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        Set<UUID> sent = new HashSet<>();
        for (ServerPlayer o : level.getEntitiesOfClass(ServerPlayer.class, new AABB(hall).inflate(24))) {
            o.sendSystemMessage(msg);
            sent.add(o.getUUID());
        }
        if (!sent.contains(defendant.getUUID())) defendant.sendSystemMessage(msg);
    }

    private static String yearsWord(int n) {
        int m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return "год";
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "года";
        return "лет";
    }

    private Court() {
    }
}
