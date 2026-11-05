package ru.gorod.rusgorod.social;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.util.Scheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * Приказы подчинённым NPC. Приказывать можно только тем, кто младше по званию в той же службе
 * (ст. 38–41 Устава внутренней службы ВС РФ — единоначалие; для ОПГ — «старший сказал»).
 */
public final class Orders {
    public static final int MAX_FOLLOWERS = 6;
    private static final long ORDER_TIME = 20 * 60 * 10;

    public enum Kind {FOLLOW, HOLD, RELEASE, ATTENTION, ATTACK, REPORT}

    /** Распознать приказ в тексте чата. */
    public static Kind parse(String m) {
        if (has(m, "за мной", "ко мне", "следуй", "пошли со мной", "идем со мной", "идём со мной", "айда", "сопровождай")) return Kind.FOLLOW;
        if (has(m, "охраня", "на пост", "занять пост", "жди здесь", "ждите здесь", "оставайся", "оставайтесь", "стой здесь", "стоять здесь", "на месте стой")) return Kind.HOLD;
        if (has(m, "свободен", "свободны", "вольно", "разойдись", "отбой", "продолжайте службу", "возвращайся", "возвращайтесь")) return Kind.RELEASE;
        if (has(m, "смирно", "равняйсь", "становись")) return Kind.ATTENTION;
        if (has(m, "задержать", "взять его", "взять ее", "фас", "огонь", "мочи", "вали его", "арестовать", "хватай", "задержите", "в атаку")) return Kind.ATTACK;
        if (has(m, "доложить", "доклад", "доложите", "обстановка", "что у вас")) return Kind.REPORT;
        return null;
    }

    /** Приказ из чата. Возвращает true, если сообщение воспринято как приказ. */
    public static boolean fromChat(ServerPlayer p, PlayerRecord r, String msg, List<CitizenEntity> near) {
        Kind k = parse(msg);
        if (k == null || r.faction == Faction.CIVILIAN) return false;
        List<CitizenEntity> subs = new ArrayList<>();
        CitizenEntity senior = null;
        for (CitizenEntity c : near) {
            if (c.isJailed() || p.distanceToSqr(c) > 12 * 12) continue;
            if (Address.isSuperior(r, c)) subs.add(c);
            else if (Address.sameService(r.faction, c.getFaction())) senior = c;
        }
        if (subs.isEmpty()) {
            if (senior != null) {
                final CitizenEntity s = senior;
                Scheduler.later(15, () -> s.say("Вы мне приказываете, " + Address.toPlayer(s, p, r) + "? Отставить! Звание сначала получите."));
                return true;
            }
            return false;
        }
        apply(p, r, k, subs);
        return true;
    }

    /** Приказ одному NPC (из диалога). */
    public static void single(ServerPlayer p, PlayerRecord r, Kind k, CitizenEntity c) {
        if (!Address.isSuperior(r, c)) {
            c.say("Вы не мой начальник. Отказываю.");
            return;
        }
        List<CitizenEntity> one = new ArrayList<>();
        one.add(c);
        apply(p, r, k, one);
    }

    private static void apply(ServerPlayer p, PlayerRecord r, Kind k, List<CitizenEntity> subs) {
        ServerLevel level = p.serverLevel();
        long until = level.getGameTime() + ORDER_TIME;
        boolean crim = r.faction.isCriminal();
        String yes = crim ? "Понял, " + r.faction.rankName(r.rank).toLowerCase() + "." : "Есть, " + r.faction.address(r.rank) + "!";
        switch (k) {
            case FOLLOW -> {
                int have = countFollowers(level, p);
                int added = 0;
                for (CitizenEntity c : subs) {
                    if (c.order == CitizenEntity.Order.FOLLOW && c.isCommander(p)) continue;
                    if (have + added >= MAX_FOLLOWERS) break;
                    c.order = CitizenEntity.Order.FOLLOW;
                    c.commander = p.getUUID();
                    c.orderUntil = until;
                    c.setTarget(null);
                    added++;
                }
                say(subs, yes, crim);
                p.displayClientMessage(Component.literal("За вами следует подчинённых: " + (have + added) + " (макс. " + MAX_FOLLOWERS + "). «Свободны» — отпустить.").withStyle(ChatFormatting.GRAY), true);
            }
            case HOLD -> {
                for (CitizenEntity c : subs) {
                    c.order = CitizenEntity.Order.HOLD;
                    c.commander = p.getUUID();
                    c.holdPos = c.blockPosition();
                    c.orderUntil = until;
                }
                say(subs, crim ? "Постоим, присмотрим." : "Есть занять пост!", crim);
            }
            case RELEASE -> {
                for (CitizenEntity c : subs) {
                    c.order = CitizenEntity.Order.NONE;
                    c.commander = null;
                    c.setTarget(null);
                }
                say(subs, crim ? "Ну, бывай." : "Есть! Разрешите идти?", crim);
            }
            case ATTENTION -> {
                for (CitizenEntity c : subs) {
                    c.getNavigation().stop();
                    c.getLookControl().setLookAt(p);
                    if (c.getFaction().insignia) c.setAnim(CitizenEntity.ANIM_SALUTE, 50);
                }
                say(subs, crim ? "Слушаем." : "Здравия желаю, " + r.faction.address(r.rank) + "!", crim);
            }
            case ATTACK -> {
                LivingEntity target = lookTarget(p, 32);
                if (target == null) {
                    say(subs, "Кого, " + (crim ? "старшой" : r.faction.address(r.rank)) + "? Не вижу цели.", crim);
                    return;
                }
                for (CitizenEntity c : subs) {
                    if (c.isCombatant() && c != target) c.setTarget(target);
                }
                say(subs, crim ? "Сделаем!" : "Есть задержать!", crim);
            }
            case REPORT -> {
                CitizenEntity best = subs.get(0);
                for (CitizenEntity c : subs) if (c.rank > best.rank) best = c;
                GorodData d = GorodData.get(level);
                final CitizenEntity rep = best;
                final String text = crim
                        ? "Всё ровно. В общаке " + d.fundsOf(rep.getFaction()) + ". Мусора не беспокоят."
                        : "Докладываю: открытых дел — " + openCases(d) + ", индекс преступности " + Math.round(d.crimeIndex)
                        + ", личный состав на месте, " + d.npcCount(rep.getFaction()) + " человек по списку.";
                Scheduler.later(15, () -> {
                    if (rep.getFaction().insignia) rep.setAnim(CitizenEntity.ANIM_SALUTE, 30);
                    rep.say(text, true);
                });
            }
        }
    }

    private static int openCases(GorodData d) {
        int n = 0;
        for (var c : d.cases) if (c.stage != ru.gorod.rusgorod.law.CriminalCase.Stage.CLOSED) n++;
        return n;
    }

    private static void say(List<CitizenEntity> subs, String text, boolean crim) {
        CitizenEntity first = subs.get(0);
        for (CitizenEntity c : subs) if (c.rank > first.rank) first = c;
        final CitizenEntity sp = first;
        Scheduler.later(12, () -> {
            if (!crim && sp.getFaction().insignia) sp.setAnim(CitizenEntity.ANIM_SALUTE, 30);
            sp.say(text, true);
        });
    }

    public static int countFollowers(ServerLevel level, ServerPlayer p) {
        AABB box = p.getBoundingBox().inflate(64);
        return level.getEntitiesOfClass(CitizenEntity.class, box, c -> c.order == CitizenEntity.Order.FOLLOW && c.isCommander(p)).size();
    }

    /** Отпустить всех подчинённых игрока (при выходе из игры, смене фракции). */
    public static void releaseAll(ServerLevel level, ServerPlayer p) {
        for (CitizenEntity c : level.getEntitiesOfClass(CitizenEntity.class, p.getBoundingBox().inflate(128), c -> c.isCommander(p))) {
            c.order = CitizenEntity.Order.NONE;
            c.commander = null;
        }
    }

    /** На кого смотрит игрок (луч до distance блоков). */
    public static LivingEntity lookTarget(ServerPlayer p, double distance) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(distance));
        AABB box = p.getBoundingBox().expandTowards(look.scale(distance)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box, e -> e instanceof LivingEntity && e.isPickable() && e != p);
        if (hit == null) return null;
        Entity e = hit.getEntity();
        return e instanceof LivingEntity le ? le : null;
    }

    static boolean has(String m, String... keys) {
        for (String k : keys) if (m.contains(k)) return true;
        return false;
    }

    private Orders() {
    }
}
