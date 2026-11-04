package ru.gorod.rusgorod.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncPacket {
    public final CompoundTag tag;

    public SyncPacket(CompoundTag tag) {
        this.tag = tag;
    }

    public static void encode(SyncPacket m, FriendlyByteBuf buf) {
        buf.writeNbt(m.tag);
    }

    public static SyncPacket decode(FriendlyByteBuf buf) {
        CompoundTag t = buf.readNbt();
        return new SyncPacket(t == null ? new CompoundTag() : t);
    }

    public static void handle(SyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.gorod.rusgorod.client.ClientPacketHandler.onSync(m.tag));
    }
}
