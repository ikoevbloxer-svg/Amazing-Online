package ru.gorod.rusgorod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;

import java.util.List;

/** Служебное удостоверение / паспорт гражданина. ПКМ — показать данные. */
public class IdCardItem extends Item {
    public IdCardItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            PlayerRecord r = GorodData.get(sp.serverLevel()).player(sp.getUUID());
            sp.sendSystemMessage(Component.literal("══ Удостоверение ══").withStyle(ChatFormatting.GOLD));
            sp.sendSystemMessage(Component.literal("Ф.И.О.: " + sp.getName().getString()));
            sp.sendSystemMessage(Component.literal("Ведомство: " + r.faction.title));
            sp.sendSystemMessage(Component.literal("Звание/статус: " + r.faction.rankName(r.rank)));
            sp.sendSystemMessage(Component.literal("Должность: " + r.faction.positionName(r.rank)));
            sp.sendSystemMessage(Component.literal("Судимость: " + (r.hasActiveRecord(level.getDayTime() / 24000L) ? "имеется" : (r.everConvicted ? "погашена" : "нет"))));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("ПКМ — предъявить").withStyle(ChatFormatting.GRAY));
    }
}
