package ru.gorod.rusgorod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ru.gorod.rusgorod.law.CrimeSystem;

import java.util.List;
import java.util.function.Supplier;

/**
 * Простое огнестрельное оружие с мгновенным попаданием (hitscan).
 * Расходует патроны из инвентаря. Применение оружия по людям в городе
 * фиксируется системой правонарушений (если стрелок не сотрудник при исполнении).
 */
public class GunItem extends Item {
    private final float damage;
    private final int cooldown;
    private final double range;
    private final Supplier<Item> ammo;

    public GunItem(Properties props, float damage, int cooldown, double range, Supplier<Item> ammo) {
        super(props);
        this.damage = damage;
        this.cooldown = cooldown;
        this.range = range;
        this.ammo = ammo;
    }

    public float getDamage() {
        return damage;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!player.getAbilities().instabuild && !consumeAmmo(player)) {
            player.displayClientMessage(Component.literal("Нет патронов").withStyle(ChatFormatting.RED), true);
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
            return InteractionResultHolder.fail(stack);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.2f, 1.8f);

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 limit = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, limit, box,
                e -> !e.isSpectator() && e.isPickable() && e != player);
        if (hit != null) {
            Entity target = hit.getEntity();
            if (target instanceof LivingEntity living) {
                living.hurt(level.damageSources().playerAttack(player), damage);
                CrimeSystem.onPlayerShot(player, living);
            }
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIT, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 6, 0.1, 0.1, 0.1, 0.1);
            }
        } else if (level instanceof ServerLevel sl && blockHit.getType() != HitResult.Type.MISS) {
            sl.sendParticles(ParticleTypes.SMOKE, limit.x, limit.y, limit.z, 4, 0.05, 0.05, 0.05, 0.01);
        }
        player.getCooldowns().addCooldown(this, cooldown);
        return InteractionResultHolder.consume(stack);
    }

    private boolean consumeAmmo(Player player) {
        Item ammoItem = ammo.get();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() == ammoItem) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Урон: " + damage + ", дальность: " + (int) range).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Незаконное хранение — ст. 222 УК РФ").withStyle(ChatFormatting.DARK_RED));
    }
}
