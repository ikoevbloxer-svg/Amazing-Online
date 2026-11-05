package ru.gorod.rusgorod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.net.GunFxPacket;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.registry.ModItems;
import ru.gorod.rusgorod.registry.ModSounds;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Огнестрельное оружие 3.0: магазин (NBT), перезарядка (R), отдача, разброс, прицеливание (Shift),
 * дробь, трассеры, вспышка, попадания в голову, бронежилет, звуки, позы рук от третьего лица.
 * Правовой статус — ФЗ-150 «Об оружии»: гражданину с лицензией ЛОА доступны гладкоствольное и травматическое,
 * остальное — только служебное/боевое (ст. 222 УК РФ при незаконном обороте).
 */
public class GunItem extends Item {
    public enum Kind {
        PISTOL("Пистолет"), TRAUMA("Огнестрельное оружие ограниченного поражения"), SMG("Автомат укороченный"),
        RIFLE("Автомат"), SHOTGUN("Гладкоствольное ружьё"), SNIPER("Снайперская винтовка");
        public final String title;

        Kind(String t) {
            title = t;
        }
    }

    public record Spec(Kind kind, String title, float damage, int delay, double range, int mag, int reload, float spread, int pellets,
                       boolean auto, float recoil, float zoom, Supplier<SoundEvent> sound, Supplier<Item> ammo, boolean civilian, String caliber) {
    }

    public static Spec spec(Kind kind, String title, float damage, int delay, double range, int mag, int reload, float spread, int pellets,
                            boolean auto, float recoil, float zoom, RegistryObject<SoundEvent> sound, RegistryObject<Item> ammo, boolean civilian, String caliber) {
        return new Spec(kind, title, damage, delay, range, mag, reload, spread, pellets, auto, recoil, zoom, sound, ammo, civilian, caliber);
    }

    public final Spec spec;

    public GunItem(Spec spec) {
        super(new Properties().stacksTo(1));
        this.spec = spec;
    }

    public float getDamage() {
        return spec.damage * spec.pellets * 0.6f;
    }

    // ------------------------------------------------------------------ NBT магазина
    public static int ammo(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t == null || !t.contains("Ammo")) return s.getItem() instanceof GunItem g ? g.spec.mag : 0;
        return t.getInt("Ammo");
    }

    public static void setAmmo(ItemStack s, int n) {
        s.getOrCreateTag().putInt("Ammo", n);
    }

    public static long reloadEnd(ItemStack s) {
        return s.getTag() == null ? 0 : s.getTag().getLong("Reload");
    }

    public static boolean isReloading(ItemStack s, long gameTime) {
        long r = reloadEnd(s);
        return r > 0 && gameTime < r;
    }

    /** Доля перезарядки 0..1 (для HUD и анимации). */
    public float reloadProgress(ItemStack s, long gameTime) {
        long r = reloadEnd(s);
        if (r <= 0) return -1f;
        return Mth.clamp(1f - (r - gameTime) / (float) spec.reload, 0f, 1f);
    }

    public static int countAmmo(Player p, Item ammo) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() == ammo) n += s.getCount();
        }
        return n;
    }

    // ------------------------------------------------------------------ использование
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (isReloading(stack, level.getGameTime())) return InteractionResultHolder.fail(stack);
        if (spec.auto) player.startUsingItem(hand);
        if (!level.isClientSide) tryFire((ServerLevel) level, player, stack);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
        if (!level.isClientSide && spec.auto && remaining < getUseDuration(stack)) tryFire((ServerLevel) level, living, stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return spec.auto ? 72000 : 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true; // ЛКМ с оружием не машет рукой
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player p)) return;
        long r = reloadEnd(stack);
        if (r <= 0) return;
        if (!selected) {
            stack.getOrCreateTag().remove("Reload");
            return;
        }
        if (level.getGameTime() >= r) {
            stack.getOrCreateTag().remove("Reload");
            int need = spec.mag - ammo(stack);
            if (p.getAbilities().instabuild) {
                setAmmo(stack, spec.mag);
                return;
            }
            Item am = spec.ammo.get();
            for (int i = 0; i < p.getInventory().getContainerSize() && need > 0; i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (s.getItem() == am) {
                    int take = Math.min(need, s.getCount());
                    s.shrink(take);
                    need -= take;
                    setAmmo(stack, ammo(stack) + take);
                }
            }
        }
    }

    /** Начать перезарядку (клавиша R или пустой магазин). */
    public void startReload(ServerPlayer p, ItemStack stack) {
        long now = p.level().getGameTime();
        if (isReloading(stack, now) || ammo(stack) >= spec.mag) return;
        if (!p.getAbilities().instabuild && countAmmo(p, spec.ammo.get()) <= 0) {
            p.displayClientMessage(Component.literal("Нет патронов: " + spec.ammo.get().getDescription().getString()).withStyle(ChatFormatting.RED), true);
            return;
        }
        stack.getOrCreateTag().putLong("Reload", now + spec.reload);
        p.stopUsingItem();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.RELOAD.get(), SoundSource.PLAYERS, 0.8f, spec.reload < 36 ? 1.2f : 1.0f);
    }

    // ------------------------------------------------------------------ выстрел
    private void tryFire(ServerLevel level, LivingEntity shooter, ItemStack stack) {
        long now = level.getGameTime();
        CompoundTag tag = stack.getOrCreateTag();
        if (now - tag.getLong("Last") < spec.delay || isReloading(stack, now)) return;
        boolean creative = shooter instanceof Player pl && pl.getAbilities().instabuild;
        int a = ammo(stack);
        if (a <= 0) {
            if (now - tag.getLong("Last") > 8) {
                tag.putLong("Last", now);
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), ModSounds.EMPTY.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
                if (shooter instanceof ServerPlayer sp) {
                    sp.stopUsingItem();
                    startReload(sp, stack);
                }
            }
            return;
        }
        tag.putLong("Last", now);
        if (!creative) setAmmo(stack, a - 1);
        Vec3 eye = shooter.getEyePosition();
        fire(level, shooter, eye, shooter.getViewVector(1f), aiming(shooter));
    }

    public static boolean aiming(LivingEntity e) {
        return e instanceof Player p && p.isShiftKeyDown() && !p.isSprinting();
    }

    /** Выстрел NPC по цели. Возвращает false, если NPC перезаряжается. */
    public boolean npcShoot(ServerLevel level, CitizenEntity c, ItemStack stack, LivingEntity target) {
        long now = level.getGameTime();
        long r = reloadEnd(stack);
        if (r > 0) {
            if (now < r) return false;
            stack.getOrCreateTag().remove("Reload");
            setAmmo(stack, spec.mag);
        }
        int a = ammo(stack);
        if (a <= 0) {
            stack.getOrCreateTag().putLong("Reload", now + spec.reload + 10);
            level.playSound(null, c.getX(), c.getY(), c.getZ(), ModSounds.RELOAD.get(), SoundSource.HOSTILE, 0.8f, 1.0f);
            if (c.getRandom().nextFloat() < 0.4f) c.say(c.getRandom().nextBoolean() ? "Перезаряжаюсь!" : "Прикрой, меняю магазин!", true);
            return false;
        }
        setAmmo(stack, a - 1);
        Vec3 eye = c.getEyePosition();
        Vec3 aim = new Vec3(target.getX(), target.getY(0.6), target.getZ()).subtract(eye).normalize();
        fire(level, c, eye, aim, false);
        return true;
    }

    private void fire(ServerLevel level, LivingEntity shooter, Vec3 eye, Vec3 dir, boolean aiming) {
        RandomSource rnd = level.random;
        float spread = spec.spread * (aiming ? 0.3f : 1f) * (shooter.isSprinting() ? 2.2f : 1f)
                * (shooter.getDeltaMovement().horizontalDistanceSqr() > 0.004 ? 1.4f : 1f);
        if (shooter instanceof CitizenEntity) spread += 2.2f;
        if (spec.pellets > 1) spread = Math.max(spread, spec.spread * 0.8f);
        float vol = spec.kind == Kind.SNIPER ? 7f : spec.kind == Kind.TRAUMA ? 2.5f : 5f;
        level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), spec.sound.get(),
                shooter instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, vol, 0.94f + rnd.nextFloat() * 0.12f);

        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 muzzle = eye.add(dir.scale(1.1)).add(right.scale(0.22)).add(0, -0.18, 0);
        level.sendParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 2, 0.02, 0.02, 0.02, 0.01);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, spec.pellets > 1 ? 6 : 3, 0.04, 0.04, 0.04, 0.012);

        int hitType = 0;
        Set<LivingEntity> reported = new HashSet<>();
        for (int i = 0; i < spec.pellets; i++) {
            Vec3 d = deviate(dir, spread, rnd);
            Vec3 end = eye.add(d.scale(spec.range));
            BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            Vec3 limit = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
            AABB box = shooter.getBoundingBox().expandTowards(d.scale(spec.range)).inflate(1.0);
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, shooter, eye, limit, box, e -> canHit(shooter, e));
            Vec3 hitPos = eh != null ? eh.getLocation() : limit;
            if (i < 3) tracer(level, muzzle, hitPos);
            whiz(level, shooter, eye, hitPos);
            if (eh != null) {
                Entity t = eh.getEntity();
                if (t instanceof LivingEntity le) {
                    double dist = eye.distanceTo(hitPos);
                    float dmg = spec.damage * (float) (dist > spec.range * 0.6 ? 0.65 : 1.0);
                    boolean head = !(spec.pellets > 1) && hitPos.y >= le.getEyeY() - 0.24;
                    if (head) dmg *= spec.kind == Kind.SNIPER ? 2.5f : 1.8f;
                    ItemStack vest = le.getItemBySlot(EquipmentSlot.CHEST);
                    if (!head && vest.getItem() == ModItems.BRONIK.get() && spec.kind != Kind.SNIPER) {
                        dmg *= spec.kind == Kind.TRAUMA ? 0.2f : 0.5f;
                        vest.hurtAndBreak(2, le, x -> x.broadcastBreakEvent(EquipmentSlot.CHEST));
                        level.sendParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 5, 0.1, 0.1, 0.1, 0.2);
                    }
                    DamageSource src = shooter instanceof Player pl ? level.damageSources().playerAttack(pl) : level.damageSources().mobAttack(shooter);
                    le.invulnerableTime = 0;
                    boolean wasAlive = le.isAlive();
                    le.hurt(src, dmg);
                    le.invulnerableTime = 0;
                    if (spec.kind == Kind.TRAUMA) {
                        le.knockback(0.7, -d.x, -d.z);
                        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
                        le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
                    }
                    level.sendParticles(new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.02f), 1.1f), hitPos.x, hitPos.y, hitPos.z,
                            6, 0.12, 0.12, 0.12, 0.0);
                    int ht = head ? 2 : 1;
                    if (wasAlive && !le.isAlive()) ht = 3;
                    hitType = Math.max(hitType, ht);
                    if (shooter instanceof ServerPlayer sp && reported.add(le)) CrimeSystem.onPlayerShot(sp, le);
                } else {
                    t.hurt(shooter instanceof Player pl ? level.damageSources().playerAttack(pl) : level.damageSources().mobAttack(shooter), spec.damage);
                }
            } else if (bh.getType() == HitResult.Type.BLOCK) {
                BlockPos bp = bh.getBlockPos();
                BlockState st = level.getBlockState(bp);
                Vec3 l = bh.getLocation();
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), l.x, l.y, l.z, 6, 0.05, 0.05, 0.05, 0.1);
                level.sendParticles(ParticleTypes.SMOKE, l.x, l.y, l.z, 2, 0.02, 0.02, 0.02, 0.01);
                if (i < 2) level.playSound(null, bp, st.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.7f, 1.4f);
                // стекло бьётся
                if (spec.kind != Kind.TRAUMA && st.getSoundType() == net.minecraft.world.level.block.SoundType.GLASS && st.getDestroySpeed(level, bp) < 1f
                        && ru.gorod.rusgorod.data.GorodData.get(level).inCity(bp) && rnd.nextFloat() < 0.5f) {
                    level.destroyBlock(bp, false, shooter);
                }
            }
        }
        if (shooter instanceof ServerPlayer sp) {
            float rec = spec.recoil * (aiming ? 0.55f : 1f);
            Network.sendTo(sp, new GunFxPacket(rec, hitType));
        }
        panic(level, shooter);
    }

    private static boolean canHit(LivingEntity shooter, Entity e) {
        if (e == shooter || e.isSpectator() || !e.isPickable() || e == shooter.getVehicle()) return false;
        if (shooter instanceof CitizenEntity cs && e instanceof CitizenEntity ce && cs.getFaction() == ce.getFaction()) return false;
        return true;
    }

    private static Vec3 deviate(Vec3 dir, float spreadDeg, RandomSource rnd) {
        if (spreadDeg <= 0) return dir;
        double s = Math.toRadians(spreadDeg) * 0.5;
        return dir.add(rnd.nextGaussian() * s, rnd.nextGaussian() * s, rnd.nextGaussian() * s).normalize();
    }

    private static final DustParticleOptions TRACER = new DustParticleOptions(new Vector3f(1.0f, 0.86f, 0.5f), 0.55f);

    private static void tracer(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.5) return;
        int n = (int) Math.min(26, len / 1.4);
        Vec3 step = d.scale(1.0 / n);
        for (int i = 1; i < n; i++) {
            Vec3 p = from.add(step.scale(i));
            level.sendParticles(TRACER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** Свист пули рядом с головой. */
    private static void whiz(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 to) {
        Vec3 seg = to.subtract(from);
        double len2 = seg.lengthSqr();
        if (len2 < 9) return;
        for (ServerPlayer sp : level.players()) {
            if (sp == shooter) continue;
            Vec3 e = sp.getEyePosition();
            double t = Mth.clamp(e.subtract(from).dot(seg) / len2, 0, 1);
            Vec3 c = from.add(seg.scale(t));
            if (t > 0.1 && t < 0.97 && c.distanceToSqr(e) < 2.5 * 2.5) sp.playNotifySound(ModSounds.WHIZ.get(), SoundSource.HOSTILE, 0.8f, 0.9f + level.random.nextFloat() * 0.3f);
        }
    }

    /** Мирные жители рядом разбегаются при стрельбе. */
    private static void panic(ServerLevel level, LivingEntity shooter) {
        if (shooter instanceof CitizenEntity cs && cs.getFaction().isState()) return;
        for (CitizenEntity c : level.getEntitiesOfClass(CitizenEntity.class, shooter.getBoundingBox().inflate(14),
                c -> !c.isCombatant() && c.getTarget() == null && !c.isJailed() && c != shooter)) {
            c.setTarget(shooter);
            if (level.random.nextFloat() < 0.25f) c.say(level.random.nextBoolean() ? "Стреляют!!!" : "Ложись! Вызывайте полицию!", true);
        }
    }

    // ------------------------------------------------------------------ полоса патронов и подсказка
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * ammo(stack) / spec.mag);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float f = ammo(stack) / (float) spec.mag;
        return f > 0.3f ? 0xE8C860 : 0xE04030;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(spec.title);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(spec.kind.title + " · " + spec.caliber).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Магазин: " + ammo(stack) + "/" + spec.mag + (spec.auto ? " · автоматический огонь" : "")).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Урон " + spec.damage + (spec.pellets > 1 ? "×" + spec.pellets : "") + " · дальность " + (int) spec.range + " м").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("ПКМ — огонь, Shift — прицел, R — перезарядка").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal(spec.civilian ? "Гражданское: нужна лицензия ЛОА (ст. 13 ФЗ-150)" : "Боевое/служебное: без права — ст. 222 УК РФ")
                .withStyle(spec.civilian ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED));
    }

    // ------------------------------------------------------------------ клиент: поза рук и вид от первого лица
    @Override
    public void initializeClient(Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(LivingEntity e, InteractionHand hand, ItemStack stack) {
                if (e.isSprinting() || isReloading(stack, e.level().getGameTime())) return net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
                return net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }

            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.player.LocalPlayer player,
                                                   net.minecraft.world.entity.HumanoidArm arm, ItemStack stack, float partialTick, float equip, float swing) {
                return ru.gorod.rusgorod.client.GunClient.handTransform(poseStack, player, arm, stack, partialTick, equip);
            }
        });
    }
}
