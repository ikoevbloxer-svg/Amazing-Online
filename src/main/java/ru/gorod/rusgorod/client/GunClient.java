package ru.gorod.rusgorod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import ru.gorod.rusgorod.item.GunItem;
import ru.gorod.rusgorod.registry.ModSounds;

/** Клиентские эффекты оружия: отдача камеры и ствола, прицеливание, маркер попадания, вспышка у дула. */
public final class GunClient {
    private static float kick, kickPrev;
    private static float aim, aimPrev;
    public static int hitTicks;
    public static int hitType;
    public static int shotsFlash;

    public static void onFx(float recoil, int hit) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        kick = Math.min(1.6f, kick + 0.55f + recoil * 0.06f);
        p.setXRot(Mth.clamp(p.getXRot() - recoil * (0.75f + p.getRandom().nextFloat() * 0.5f), -90f, 90f));
        p.setYRot(p.getYRot() + (p.getRandom().nextFloat() - 0.5f) * recoil * 0.45f);
        shotsFlash = 2;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1f);
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 m = eye.add(look.scale(1.2)).add(right.scale(aim > 0.5f ? 0.0 : 0.3)).add(0, aim > 0.5f ? -0.12 : -0.28, 0);
        for (int i = 0; i < 3; i++) mc.level.addParticle(ParticleTypes.FLAME, m.x, m.y, m.z, look.x * 0.05, look.y * 0.05, look.z * 0.05);
        mc.level.addParticle(ParticleTypes.SMOKE, m.x, m.y, m.z, look.x * 0.02, 0.02, look.z * 0.02);
        if (hit > 0) {
            hitTicks = 8;
            hitType = hit;
            p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), hit >= 2 ? ModSounds.HEADSHOT.get() : ModSounds.HIT.get(), SoundSource.PLAYERS, 0.6f, hit == 3 ? 0.8f : 1f, false);
        }
    }

    public static void tick() {
        kickPrev = kick;
        kick *= 0.62f;
        if (kick < 0.01f) kick = 0;
        aimPrev = aim;
        LocalPlayer p = Minecraft.getInstance().player;
        boolean aiming = p != null && p.getMainHandItem().getItem() instanceof GunItem && GunItem.aiming(p)
                && !GunItem.isReloading(p.getMainHandItem(), p.level().getGameTime());
        aim = Mth.clamp(aim + (aiming ? 0.25f : -0.25f), 0f, 1f);
        if (hitTicks > 0) hitTicks--;
        if (shotsFlash > 0) shotsFlash--;
    }

    public static float aim(float pt) {
        return Mth.lerp(pt, aimPrev, aim);
    }

    public static float kick(float pt) {
        return Mth.lerp(pt, kickPrev, kick);
    }

    /** Множитель FOV при прицеливании. */
    public static float fovModifier(float base) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !(p.getMainHandItem().getItem() instanceof GunItem g) || aim <= 0) return base;
        float z = 1f + (g.spec.zoom() - 1f) * aim;
        return base / z;
    }

    public static boolean scoped() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && aim > 0.9f && p.getMainHandItem().getItem() instanceof GunItem g && g.spec.kind() == GunItem.Kind.SNIPER
                && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    /** Положение оружия в руке от первого лица: от бедра, прицел (по центру), отдача, перезарядка. */
    public static boolean handTransform(PoseStack ps, LocalPlayer player, HumanoidArm arm, ItemStack stack, float pt, float equip) {
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        float a = aim(pt);
        float k = kick(pt);
        if (scoped()) {
            ps.translate(0, -5, 0); // в оптике оружие не видно
            return true;
        }
        float bob = player.isSprinting() ? Mth.sin(player.walkDist * 2.2f) * 0.03f : 0f;
        ps.translate(i * 0.50f * (1f - a), -0.5f + 0.24f * a + equip * -0.6f + bob, -0.72f + 0.12f * a);
        if (player.isSprinting()) {
            ps.mulPose(Axis.YP.rotationDegrees(i * 35f));
            ps.mulPose(Axis.XP.rotationDegrees(-12f));
        }
        ps.translate(0, k * 0.02f, k * 0.11f);
        ps.mulPose(Axis.XP.rotationDegrees(k * 7f));
        if (stack.getItem() instanceof GunItem g) {
            float r = g.reloadProgress(stack, player.level().getGameTime());
            if (r >= 0f) {
                float s = Mth.sin(r * (float) Math.PI);
                ps.translate(0, -0.18f * s, 0.05f * s);
                ps.mulPose(Axis.ZP.rotationDegrees(-i * 38f * s));
                ps.mulPose(Axis.XP.rotationDegrees(22f * s));
            }
        }
        return true;
    }

    private GunClient() {
    }
}
