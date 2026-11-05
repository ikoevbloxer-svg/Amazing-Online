package ru.gorod.rusgorod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Смартфон. Открывается ПКМ или клавишей K (телефон не обязателен в инвентаре — он «в кармане»). */
public class PhoneItem extends Item {
    public PhoneItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ru.gorod.rusgorod.client.PhoneScreen::open);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Карта, банк, «Госуслуги», такси, 112, контакты, «Калибр»").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Клавиша K").withStyle(ChatFormatting.DARK_GRAY));
    }
}
