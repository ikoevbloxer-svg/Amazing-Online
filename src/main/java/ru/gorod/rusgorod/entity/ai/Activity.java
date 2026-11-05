package ru.gorod.rusgorod.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

/** Текущее занятие NPC: куда идти и что делать по прибытии. */
public class Activity {
    public enum Type {
        IDLE, HOME, WORK, LEISURE, PRAY,
        PATROL, CHECK_DOCS, POST, GUARD, INVESTIGATE, INSPECT, SURVEIL, RECEPTION, DISPERSE,
        FORMATION, DRILL, RANGE, MESS, HEARING,
        HANG_OUT, EXTORT, STEAL, DEAL, VANDAL, HOOLIGAN, DRINK, ARMS_DEAL, RIDE, FENCE,
        PROTEST
    }

    public final Type type;
    public BlockPos pos;
    public int duration;
    public double speed = 0.75;
    public double radius = 2.5;
    public LivingEntity target;
    public int caseId = -1;
    public boolean started;
    public int travelTicks;

    public Activity(Type type, BlockPos pos, int duration) {
        this.type = type;
        this.pos = pos;
        this.duration = duration;
    }

    public Activity speed(double s) {
        this.speed = s;
        return this;
    }

    public Activity target(LivingEntity e) {
        this.target = e;
        this.radius = 2.2;
        return this;
    }

    public Activity caseId(int id) {
        this.caseId = id;
        return this;
    }
}
