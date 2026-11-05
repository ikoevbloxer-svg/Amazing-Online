package ru.gorod.rusgorod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Отдача и маркер попадания стрелку. hit: 0 мимо, 1 попадание, 2 в голову, 3 наповал. */
public class GunFxPacket {
    public final float recoil;
    public final int hit;

    public GunFxPacket(float recoil, int hit) {
        this.recoil = recoil;
        this.hit = hit;
    }

    public static void encode(GunFxPacket m, FriendlyByteBuf buf) {
        buf.writeFloat(m.recoil);
        buf.writeByte(m.hit);
    }

    public static GunFxPacket decode(FriendlyByteBuf buf) {
        return new GunFxPacket(buf.readFloat(), buf.readByte());
    }

    public static void handle(GunFxPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.gorod.rusgorod.client.GunClient.onFx(m.recoil, m.hit));
    }
}
