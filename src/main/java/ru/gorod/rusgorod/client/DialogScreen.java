package ru.gorod.rusgorod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import ru.gorod.rusgorod.net.DialogActionPacket;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.net.OpenDialogPacket;

import java.util.List;

/** Диалог с NPC. */
public class DialogScreen extends Screen {
    private final OpenDialogPacket data;
    private int x0, y0, w, h;

    public DialogScreen(OpenDialogPacket data) {
        super(Component.literal(data.title));
        this.data = data;
    }

    @Override
    protected void init() {
        w = Math.min(320, width - 20);
        int bodyLines = font.split(Component.literal(data.body), w - 16).size();
        h = Math.min(height - 20, 30 + bodyLines * 10 + data.options.size() * 22 + 10);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        int y = y0 + 26 + bodyLines * 10 + 4;
        for (String[] o : data.options) {
            final String id = o[0];
            addRenderableWidget(Button.builder(Component.literal(o[1]), b -> {
                Network.sendToServer(new DialogActionPacket(data.entityId, id));
                onClose();
            }).bounds(x0 + 8, y, w - 16, 20).build());
            y += 22;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(x0, y0, x0 + w, y0 + h, 0xE0101820);
        g.fill(x0, y0, x0 + w, y0 + 1, 0xFFD52B1E);
        g.fill(x0, y0 + h - 1, x0 + w, y0 + h, 0xFF0039A6);
        g.drawCenteredString(font, data.title, x0 + w / 2, y0 + 8, 0xFFFFD27A);
        List<FormattedCharSequence> lines = font.split(Component.literal(data.body), w - 16);
        int y = y0 + 24;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x0 + 8, y, 0xFFE0E0E0);
            y += 10;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
