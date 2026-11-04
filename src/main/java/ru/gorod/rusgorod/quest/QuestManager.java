package ru.gorod.rusgorod.quest;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.net.Network;

public final class QuestManager {

    public static void take(ServerPlayer p, String id) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        Quest q = QuestRegistry.get(id);
        if (q == null) return;
        if (!d.generated) {
            msg(p, "Город ещё строится.", ChatFormatting.RED);
            return;
        }
        if (r.isJailed(level.getGameTime())) {
            msg(p, "Вы под стражей.", ChatFormatting.RED);
            return;
        }
        if (q.faction != r.faction) {
            msg(p, "Это задание другой фракции.", ChatFormatting.RED);
            return;
        }
        if (r.rank < q.minRank) {
            msg(p, "Требуется звание не ниже: " + q.faction.rankName(q.minRank), ChatFormatting.RED);
            return;
        }
        if (!r.questId.isEmpty()) {
            msg(p, "Сначала завершите или сдайте текущее задание.", ChatFormatting.RED);
            return;
        }
        r.clearQuest();
        r.questId = q.id;
        if (q.type == Quest.Type.VISIT) {
            for (String key : q.locs) r.questTargets.add(resolve(d, key, level));
        } else if (q.type == Quest.Type.PATROL) {
            BlockPos prev = null;
            for (int i = 0; i < q.count; i++) {
                BlockPos n;
                int guard = 0;
                do {
                    n = d.randomNode(level.random);
                } while (n.equals(prev) && ++guard < 10);
                r.questTargets.add(n);
                prev = n;
            }
        }
        d.setDirty();
        msg(p, "Задание получено: «" + q.title + "». " + q.description, ChatFormatting.GOLD);
        msg(p, "Правовое основание: " + q.law, ChatFormatting.GRAY);
        if (!r.questTargets.isEmpty()) {
            BlockPos t = r.questTargets.get(0);
            msg(p, "Первая точка: X " + t.getX() + ", Z " + t.getZ(), ChatFormatting.YELLOW);
        }
        Network.syncPlayer(p);
    }

    public static void drop(ServerPlayer p) {
        PlayerRecord r = GorodData.get(p.serverLevel()).player(p.getUUID());
        if (r.questId.isEmpty()) return;
        r.clearQuest();
        r.points = Math.max(0, r.points - 3);
        msg(p, "Задание отменено. (−3 служебных балла)", ChatFormatting.GRAY);
        Network.syncPlayer(p);
    }

    public static void turnIn(ServerPlayer p) {
        GorodData d = GorodData.get(p.serverLevel());
        PlayerRecord r = d.player(p.getUUID());
        Quest q = QuestRegistry.get(r.questId);
        if (q == null || q.type != Quest.Type.PAY) return;
        if (r.balance < q.count) {
            msg(p, "Не хватает денег: нужно " + q.count + " ₽.", ChatFormatting.RED);
            return;
        }
        r.balance -= q.count;
        d.addFunds(r.faction, q.count);
        complete(p, d, r, q);
    }

    private static BlockPos resolve(GorodData d, String key, ServerLevel level) {
        return switch (key) {
            case "@node" -> d.randomNode(level.random);
            case "@crime" -> d.lastCrime.equals(BlockPos.ZERO) ? d.randomNode(level.random) : d.lastCrime;
            case "@home" -> d.randomHome(level.random);
            default -> d.loc(key);
        };
    }

    /** Вызывается каждые 10 тиков. */
    public static void tick(ServerPlayer p) {
        GorodData d = GorodData.get(p.serverLevel());
        PlayerRecord r = d.player(p.getUUID());
        if (r.questId.isEmpty()) return;
        Quest q = QuestRegistry.get(r.questId);
        if (q == null) {
            r.clearQuest();
            return;
        }
        if (q.faction != r.faction) {
            r.clearQuest();
            Network.syncPlayer(p);
            return;
        }
        if (q.type != Quest.Type.VISIT && q.type != Quest.Type.PATROL) return;
        if (r.questProgress >= r.questTargets.size()) {
            complete(p, d, r, q);
            return;
        }
        BlockPos t = r.questTargets.get(r.questProgress);
        double dx = p.getX() - (t.getX() + 0.5), dz = p.getZ() - (t.getZ() + 0.5);
        double dist = Math.sqrt(dx * dx + dz * dz);
        boolean last = r.questProgress == r.questTargets.size() - 1;
        if (dist < 5 && Math.abs(p.getY() - t.getY()) < 6) {
            if (last && q.stayTicks > 0) {
                r.questStay += 10;
                int left = Math.max(0, (q.stayTicks - r.questStay) / 20);
                p.displayClientMessage(Component.literal("«" + q.title + "»: осталось " + left + " сек.").withStyle(ChatFormatting.YELLOW), true);
                if (r.questStay >= q.stayTicks) {
                    r.questProgress++;
                }
            } else {
                r.questProgress++;
                p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f);
                if (r.questProgress < r.questTargets.size()) {
                    BlockPos n = r.questTargets.get(r.questProgress);
                    msg(p, "Точка пройдена. Следующая: X " + n.getX() + ", Z " + n.getZ(), ChatFormatting.YELLOW);
                }
            }
            Network.syncPlayer(p);
            if (r.questProgress >= r.questTargets.size()) complete(p, d, r, q);
        } else if (p.tickCount % 100 == 0) {
            p.displayClientMessage(Component.literal("«" + q.title + "» → X " + t.getX() + ", Z " + t.getZ() + " (" + (int) dist + " м)").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    public static void onAction(ServerPlayer p, String action, CitizenEntity target) {
        GorodData d = GorodData.get(p.serverLevel());
        PlayerRecord r = d.player(p.getUUID());
        Quest q = QuestRegistry.get(r.questId);
        if (q == null || q.type != Quest.Type.ACTION || !q.action.equals(action)) return;
        if (q.target != null && target.getFaction() != q.target) return;
        r.questProgress++;
        p.displayClientMessage(Component.literal("«" + q.title + "»: " + r.questProgress + "/" + q.count).withStyle(ChatFormatting.YELLOW), true);
        if (r.questProgress >= q.count) complete(p, d, r, q);
        else Network.syncPlayer(p);
    }

    public static void onArrest(ServerPlayer p, Faction targetFaction) {
        GorodData d = GorodData.get(p.serverLevel());
        PlayerRecord r = d.player(p.getUUID());
        Quest q = QuestRegistry.get(r.questId);
        if (q == null || q.type != Quest.Type.ARREST) return;
        if (q.target != null && q.target != targetFaction) return;
        r.questProgress++;
        if (r.questProgress >= q.count) complete(p, d, r, q);
        else Network.syncPlayer(p);
    }

    public static void onKill(ServerPlayer p, Faction victimFaction) {
        GorodData d = GorodData.get(p.serverLevel());
        PlayerRecord r = d.player(p.getUUID());
        Quest q = QuestRegistry.get(r.questId);
        if (q == null || q.type != Quest.Type.KILL || q.target != victimFaction) return;
        r.questProgress++;
        if (r.questProgress >= q.count) complete(p, d, r, q);
        else Network.syncPlayer(p);
    }

    private static void complete(ServerPlayer p, GorodData d, PlayerRecord r, Quest q) {
        r.balance += q.money;
        r.points += q.points;
        r.civRep += q.civRep;
        r.crimRep += q.crimRep;
        if (q.faction.isState()) d.budget -= q.money;
        r.clearQuest();
        d.setDirty();
        msg(p, "✔ Задание «" + q.title + "» выполнено. +" + q.money + " ₽, +" + q.points + " служ. баллов"
                + (q.civRep > 0 ? ", +" + q.civRep + " репутации" : "") + (q.crimRep > 0 ? ", +" + q.crimRep + " авторитета" : ""), ChatFormatting.GREEN);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.0f);
        int next = r.rank + 1;
        if (next < r.faction.rankCount() && r.points >= r.faction.pointsForRank(next)) {
            msg(p, "Набрано достаточно баллов для представления к званию «" + r.faction.rankName(next) + "». Подайте рапорт (клавиша G → «Моя служба»).", ChatFormatting.AQUA);
        }
        Network.syncPlayer(p);
    }

    static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.sendSystemMessage(Component.literal(s).withStyle(c));
    }

    private QuestManager() {
    }
}
