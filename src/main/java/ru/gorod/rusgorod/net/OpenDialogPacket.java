package ru.gorod.rusgorod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenDialogPacket {
    public final int entityId;
    public final String title;
    public final String body;
    public final List<String[]> options;

    public OpenDialogPacket(int entityId, String title, String body, List<String[]> options) {
        this.entityId = entityId;
        this.title = title;
        this.body = body;
        this.options = options;
    }

    public static void encode(OpenDialogPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeUtf(m.title);
        buf.writeUtf(m.body);
        buf.writeVarInt(m.options.size());
        for (String[] o : m.options) {
            buf.writeUtf(o[0]);
            buf.writeUtf(o[1]);
        }
    }

    public static OpenDialogPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String title = buf.readUtf();
        String body = buf.readUtf();
        int n = buf.readVarInt();
        List<String[]> opts = new ArrayList<>();
        for (int i = 0; i < n; i++) opts.add(new String[]{buf.readUtf(), buf.readUtf()});
        return new OpenDialogPacket(id, title, body, opts);
    }

    public static void handle(OpenDialogPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.gorod.rusgorod.client.ClientPacketHandler.openDialog(m));
    }
}
