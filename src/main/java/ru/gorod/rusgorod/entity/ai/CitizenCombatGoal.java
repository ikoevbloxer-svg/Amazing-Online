package ru.gorod.rusgorod.entity.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.item.GunItem;
import ru.gorod.rusgorod.law.CrimeSystem;

import java.util.EnumSet;

/**
 * Бой и задержание. Сотрудники с правом задержания сначала пытаются задержать
 * (физическая сила и спецсредства — ст. 20–21 ФЗ «О полиции»), огнестрельное оружие
 * применяют только против вооружённого сопротивления (ст. 23). Гражданские убегают.
 */
public class CitizenCombatGoal extends Goal {
    private final CitizenEntity c;
    private int attackCooldown;
    private int shootCooldown;
    private int burst;
    private int fleeTicks;
    private int repath;

    public CitizenCombatGoal(CitizenEntity c) {
        this.c = c;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = c.getTarget();
        return t != null && t.isAlive() && !c.isJailed();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = c.getTarget();
        if (t == null || !t.isAlive() || c.isJailed()) return false;
        if (t instanceof CitizenEntity tc && tc.isJailed()) return false;
        if (t instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator())) return false;
        return c.distanceToSqr(t) < 48 * 48;
    }

    @Override
    public void start() {
        repath = 0;
        fleeTicks = 0;
    }

    @Override
    public void stop() {
        c.setTarget(null);
        c.getNavigation().stop();
        c.setAggressive(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = c.getTarget();
        if (t == null || !(c.level() instanceof ServerLevel level)) return;
        if (attackCooldown > 0) attackCooldown--;
        if (shootCooldown > 0) shootCooldown--;

        // Гражданские и чиновники — убегают
        if (!c.isCombatant()) {
            if (fleeTicks++ == 0 || c.getNavigation().isDone()) {
                Vec3 away = DefaultRandomPos.getPosAway(c, 16, 7, t.position());
                if (away != null) c.getNavigation().moveTo(away.x, away.y, away.z, 1.15);
            }
            if (fleeTicks > 140) c.setTarget(null);
            return;
        }

        c.setAggressive(true);
        c.getLookControl().setLookAt(t, 30f, 30f);
        double distSq = c.distanceToSqr(t);
        boolean arrestMode = c.getFaction().canArrest() && CrimeSystem.isWanted(t);
        boolean hasGun = c.getMainHandItem().getItem() instanceof GunItem;
        boolean armedResistance = t instanceof ServerPlayer sp ? CrimeSystem.wantedStars(sp) >= 3
                : (t instanceof CitizenEntity tc && tc.isCombatant() && tc.getTarget() == c);
        boolean mayShoot = hasGun && (!arrestMode || armedResistance) && c.getFaction() != Faction.UGIBDD;

        if (mayShoot && distSq > 9 && distSq < 40 * 40 && c.hasLineOfSight(t)) {
            c.getNavigation().stop();
            GunItem gun = (GunItem) c.getMainHandItem().getItem();
            // стрейф в бою — не стоять столбом
            if (c.tickCount % 30 == 0 && c.getRandom().nextFloat() < 0.5f) {
                Vec3 side = DefaultRandomPos.getPos(c, 4, 2);
                if (side != null) c.getNavigation().moveTo(side.x, side.y, side.z, 0.9);
            }
            if (shootCooldown <= 0) {
                if (gun.npcShoot(level, c, c.getMainHandItem(), t)) {
                 if (burst <= 0) burst = gun.spec.auto() ? 2 + c.getRandom().nextInt(4) : 1;
                    burst--;
                    shootCooldown = burst > 0 ? gun.spec.delay() + 1 : 18 + c.getRandom().nextInt(16) + (gun.spec.kind() == GunItem.Kind.SNIPER ? 25 : 0);
                } else {
                    shootCooldown = 10;
                }
            }
            return;
        }

        if (--repath <= 0) {
            repath = 10;
            c.getNavigation().moveTo(t, arrestMode ? 1.1 : 1.0);
        }
        if (distSq < 2.6 * 2.6 && attackCooldown <= 0) {
            attackCooldown = 20;
            c.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (arrestMode) {
                CrimeSystem.tryArrest(level, c, t);
            } else {
                c.doHurtTarget(t);
            }
        }
    }
}
