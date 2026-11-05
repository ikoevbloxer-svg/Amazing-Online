package ru.gorod.rusgorod.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.entity.CitizenEntity;

import java.util.EnumSet;

/**
 * «Мозг» NPC: берёт занятие из {@link ActivityPlanner}, доводит NPC до места
 * (с промежуточными точками на длинных маршрутах и защитой от застревания) и выполняет его.
 */
public class CitizenBrainGoal extends Goal {
    private final CitizenEntity c;
    private Activity current;
    private int repath;
    private Vec3 lastPos = Vec3.ZERO;
    private int stuckTicks;
    private int stuckStrikes;
    private int cooldown;

    public CitizenBrainGoal(CitizenEntity c) {
        this.c = c;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return c.getTarget() == null && !c.isJailed() && c.level() instanceof ServerLevel;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        c.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(c.level() instanceof ServerLevel level)) return;
        GorodData d = GorodData.get(level);
        if (!d.generated) return;
        if (c.isTalking(level)) {
            c.getNavigation().stop();
            return;
        }
        if (c.order != CitizenEntity.Order.NONE) {
            current = null;
            handleOrder(level);
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (current == null) {
            current = ActivityPlanner.plan(c, level, d);
            repath = 0;
            stuckStrikes = 0;
            stuckTicks = 0;
        }
        Activity a = current;
        if (a.target != null) {
            if (!a.target.isAlive() || a.target.isRemoved()) {
                finish(level, d);
                return;
            }
            a.pos = a.target.blockPosition();
        }

        double dist = Math.sqrt(c.distanceToSqr(a.pos.getX() + 0.5, a.pos.getY(), a.pos.getZ() + 0.5));
        if (!a.started && dist > a.radius) {
            a.travelTicks++;
            if (--repath <= 0) {
                repath = 30 + c.getRandom().nextInt(20);
                BlockPos step = a.pos;
                if (dist > 40) {
                    Vec3 dir = new Vec3(a.pos.getX() - c.getX(), 0, a.pos.getZ() - c.getZ()).normalize().scale(30);
                    int sx = (int) Math.floor(c.getX() + dir.x);
                    int sz = (int) Math.floor(c.getZ() + dir.z);
                    int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
                    step = new BlockPos(sx, sy, sz);
                }
                c.getNavigation().moveTo(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, a.speed);
            }
            // Проверка на застревание
            if (++stuckTicks >= 100) {
                stuckTicks = 0;
                if (c.position().distanceToSqr(lastPos) < 2.0) {
                    stuckStrikes++;
                    repath = 0;
                    if (stuckStrikes >= 3) {
                        // Телепорт, только если никто из игроков не видит (чтобы не ломать погружение)
                        if (!ActivityPlanner.playerNear(level, c.blockPosition(), 40) && !ActivityPlanner.playerNear(level, a.pos, 40)
                                && level.isLoaded(a.pos)) {
                            c.teleportTo(a.pos.getX() + 0.5, a.pos.getY() + 0.1, a.pos.getZ() + 0.5);
                        } else {
                            finish(level, d);
                            return;
                        }
                    }
                }
                lastPos = c.position();
            }
            if (a.travelTicks > 2400) {
                finish(level, d);
            }
            return;
        }

        if (!a.started) {
            a.started = true;
            c.getNavigation().stop();
            ActivityPlanner.onArrive(c, a, level, d);
        }
        a.duration--;
        // Небольшие перемещения на месте, чтобы NPC не стояли столбом
        if (a.duration % 160 == 0 && c.getRandom().nextInt(3) == 0 && !ActivityPlanner.isMovingActivity(a.type)) {
            BlockPos p = ActivityPlanner.around(a.pos, c.getRandom(), 2);
            c.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.5);
        }
        if (a.duration <= 0) {
            finish(level, d);
        }
    }

    /** Выполнение приказа командира: следовать за ним или охранять точку. */
    private void handleOrder(ServerLevel level) {
        Player cmd = c.commander == null ? null : level.getPlayerByUUID(c.commander);
        if (cmd == null || !cmd.isAlive() || cmd.level() != c.level()) {
            c.order = CitizenEntity.Order.NONE;
            c.commander = null;
            return;
        }
        if (c.order == CitizenEntity.Order.FOLLOW) {
            double dist = c.distanceTo(cmd);
            if (dist > 48) {
                c.teleportTo(cmd.getX() - cmd.getLookAngle().x * 2, cmd.getY(), cmd.getZ() - cmd.getLookAngle().z * 2);
                c.getNavigation().stop();
            } else if (dist > 3.5) {
                if (--repath <= 0) {
                    repath = 10;
                    c.getNavigation().moveTo(cmd, dist > 10 ? 1.2 : 0.9);
                }
            } else {
                c.getNavigation().stop();
                c.getLookControl().setLookAt(cmd);
            }
        } else if (c.order == CitizenEntity.Order.HOLD) {
            BlockPos h = c.holdPos;
            double dist = Math.sqrt(c.distanceToSqr(h.getX() + 0.5, h.getY(), h.getZ() + 0.5));
            if (dist > 2.0) {
                if (--repath <= 0) {
                    repath = 20;
                    c.getNavigation().moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, 0.8);
                }
            } else {
                c.getNavigation().stop();
            }
        }
    }

    private void finish(ServerLevel level, GorodData d) {
        if (current != null) ActivityPlanner.onComplete(c, current, level, d);
        current = null;
        cooldown = 10 + c.getRandom().nextInt(30);
    }

    public Activity current() {
        return current;
    }
}
