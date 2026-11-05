package ru.gorod.rusgorod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Предмет с пояснением в подсказке. */
public class LoreItem extends Item {
    private final String[] lines;

    public LoreItem(Properties p, String... lines) {
        super(p);
        this.lines = lines;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (String l : lines) tooltip.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
    }
}
