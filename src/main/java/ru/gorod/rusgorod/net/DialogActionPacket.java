package ru.gorod.rusgorod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DialogActionPacket {
    public final int entityId;
    public final String option;

    public DialogActionPacket(int entityId, String option) {
        this.entityId = entityId;
        this.option = option;
    }

    public static void encode(DialogActionPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeUtf(m.option, 64);
    }

    public static DialogActionPacket decode(FriendlyByteBuf buf) {
        return new DialogActionPacket(buf.readVarInt(), buf.readUtf(64));
    }

    public static void handle(DialogActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) DialogService.handle(p, m.entityId, m.option);
    }
}
