package ru.gorod.rusgorod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.net.OpenDialogPacket;
import ru.gorod.rusgorod.net.RanksPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientPacketHandler {
    public static void onSync(CompoundTag tag) {
        ClientData.update(tag);
        if (Minecraft.getInstance().screen instanceof FactionScreen fs) fs.refresh();
    }

    public static void openDialog(OpenDialogPacket m) {
        Minecraft.getInstance().setScreen(new DialogScreen(m));
    }

    public static void onRanks(RanksPacket m) {
        Map<UUID, ClientRanks.Entry> map = new HashMap<>();
        for (RanksPacket.Row r : m.rows) map.put(r.id(), new ClientRanks.Entry(Faction.byOrdinal(r.faction()), r.rank(), r.uniform()));
        ClientRanks.set(map);
    }

    private ClientPacketHandler() {
    }
}
