package ru.gorod.rusgorod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import ru.gorod.rusgorod.net.OpenDialogPacket;

public final class ClientPacketHandler {
    public static void onSync(CompoundTag tag) {
        ClientData.update(tag);
        if (Minecraft.getInstance().screen instanceof FactionScreen fs) fs.refresh();
    }

    public static void openDialog(OpenDialogPacket m) {
        Minecraft.getInstance().setScreen(new DialogScreen(m));
    }

    private ClientPacketHandler() {
    }
}
