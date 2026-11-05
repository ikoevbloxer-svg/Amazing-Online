package ru.gorod.rusgorod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Фракция, звание и форма всех игроков онлайн — для отрисовки формы и погон у других. */
public class RanksPacket {
    public record Row(UUID id, int faction, int rank, boolean uniform) {
    }

    public final List<Row> rows;

    public RanksPacket(List<Row> rows) {
        this.rows = rows;
    }

    public static void encode(RanksPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.rows.size());
        for (Row r : m.rows) {
            buf.writeUUID(r.id());
            buf.writeVarInt(r.faction());
            buf.writeVarInt(r.rank());
            buf.writeBoolean(r.uniform());
        }
    }

    public static RanksPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) rows.add(new Row(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        return new RanksPacket(rows);
    }

    public static void handle(RanksPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.gorod.rusgorod.client.ClientPacketHandler.onRanks(m));
    }
}
