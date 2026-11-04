package ru.gorod.rusgorod.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.gorod.rusgorod.command.GorodCommand;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.faction.FactionService;
import ru.gorod.rusgorod.job.JobManager;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.quest.QuestManager;
import ru.gorod.rusgorod.registry.ModBlocks;
import ru.gorod.rusgorod.world.CityGenerator;
import ru.gorod.rusgorod.world.CityManager;

public class ServerEvents {

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;
        if (event.level instanceof ServerLevel sl && sl.dimension() == Level.OVERWORLD) {
            CityManager.tick(sl);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (!GorodData.get(p.serverLevel()).generated) return;
        if (p.tickCount % 10 == 0) QuestManager.tick(p);
        if (p.tickCount % 20 == 0) {
            CrimeSystem.tickJailedPlayer(p);
            JobManager.tick(p);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        GorodData d = GorodData.get(p.serverLevel());
        boolean isNew = !d.players.containsKey(p.getUUID());
        PlayerRecord r = d.player(p.getUUID());
        if (isNew) {
            FactionService.giveGear(p, r, true);
            p.sendSystemMessage(Component.literal("Добро пожаловать в «" + CityGenerator.CITY_NAME + "»! Вы — гражданин. Нажмите G, чтобы выбрать фракцию, работу или взять задание. На счету: " + r.balance + " ₽.").withStyle(ChatFormatting.GOLD));
        }
        Network.syncPlayer(p);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() instanceof ServerPlayer killer && victim != killer) {
            CrimeSystem.onPlayerKilledPlayer(killer, victim);
        }
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() instanceof ServerPlayer attacker && victim != attacker) {
            GorodData d = GorodData.get(attacker.serverLevel());
            if (!d.inCity(attacker.blockPosition())) return;
            PlayerRecord ar = d.player(attacker.getUUID());
            PlayerRecord vr = d.player(victim.getUUID());
            boolean lawful = (ar.faction.canArrest() && vr.isWanted()) || victim.getLastHurtMob() == attacker;
            if (!lawful && attacker.tickCount % 3 == 0) {
                CrimeSystem.commitByPlayer(attacker, vr.faction.isLawEnforcement() ? Article.UK_318 : Article.KOAP_6_1_1, victim);
            }
        }
    }

    @SubscribeEvent
    public void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity() instanceof ServerPlayer officer && event.getTarget() instanceof ServerPlayer target) {
            GorodData d = GorodData.get(officer.serverLevel());
            PlayerRecord or = d.player(officer.getUUID());
            PlayerRecord tr = d.player(target.getUUID());
            if (or.faction.canArrest() && tr.isWanted()) {
                if (target.isCrouching() || target.getHealth() < target.getMaxHealth() * 0.5f) {
                    CrimeSystem.arrestPlayer(officer.serverLevel(), officer, target, target.isCrouching());
                } else {
                    officer.sendSystemMessage(Component.literal("Задержанный сопротивляется. Присядет (сдастся) или ослабнет — тогда задержание.").withStyle(ChatFormatting.GRAY));
                }
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && event.getState().is(ModBlocks.LITTER.get())) {
            JobManager.onLitterBroken(p);
        }
    }

    /** В городе и на территории части враждебные мобы не появляются. */
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel sl)) return;
        if (!(event.getEntity() instanceof Enemy)) return;
        if (sl.dimension() != Level.OVERWORLD) return;
        GorodData d = GorodData.get(sl);
        if (d.inCity(event.getEntity().blockPosition()) || d.inBase(event.getEntity().blockPosition())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        GorodCommand.register(event.getDispatcher());
    }
}
