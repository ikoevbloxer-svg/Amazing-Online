package ru.gorod.rusgorod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.net.Network;

import java.util.List;

/** Наручники БР-С2 — специальное средство (п. 2 ч. 1 ст. 21 ФЗ «О полиции»). ПКМ по человеку. */
public class CuffsItem extends Item {
    public CuffsItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide || !(player instanceof ServerPlayer officer)) return InteractionResult.SUCCESS;
        PlayerRecord or = GorodData.get(officer.serverLevel()).player(officer.getUUID());
        if (!or.faction.isLawEnforcement()) {
            officer.sendSystemMessage(Component.literal("Применять спецсредства вправе только сотрудники (ст. 21 ФЗ «О полиции»).").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (target instanceof ServerPlayer tp) {
            PlayerRecord tr = GorodData.get(officer.serverLevel()).player(tp.getUUID());
            if (tr.cuffed) {
                tr.cuffed = false;
                tp.sendSystemMessage(Component.literal("С вас сняли наручники.").withStyle(ChatFormatting.GREEN));
                officer.sendSystemMessage(Component.literal("Наручники сняты.").withStyle(ChatFormatting.GRAY));
            } else {
                if (!tr.isWanted() && !tp.isCrouching()) {
                    officer.sendSystemMessage(Component.literal("Оснований для применения наручников нет: человек не в розыске и не оказывает сопротивления (ст. 21 ФЗ «О полиции»).").withStyle(ChatFormatting.RED));
                    return InteractionResult.FAIL;
                }
                tr.cuffed = true;
                tp.sendSystemMessage(Component.literal("На вас надели наручники. Вы не можете сопротивляться.").withStyle(ChatFormatting.RED));
                officer.sendSystemMessage(Component.literal("Наручники надеты. ПКМ без предмета → «Доставить в отдел» (если в розыске).").withStyle(ChatFormatting.GRAY));
            }
            officer.level().playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 1.2f);
            Network.syncPlayer(tp);
            return InteractionResult.CONSUME;
        }
        if (target instanceof CitizenEntity c) {
            officer.level().playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 1.2f);
            CrimeSystem.playerArrestsNpc(officer, c);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("ПКМ по разыскиваемому — задержать / надеть наручники").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Спецсредство: ст. 21 ФЗ «О полиции»").withStyle(ChatFormatting.DARK_GRAY));
    }
}
