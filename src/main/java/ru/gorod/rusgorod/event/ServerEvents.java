package ru.gorod.rusgorod.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.gorod.rusgorod.command.GorodCommand;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.faction.FactionService;
import ru.gorod.rusgorod.job.JobManager;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.Court;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.quest.QuestManager;
import ru.gorod.rusgorod.registry.ModBlocks;
import ru.gorod.rusgorod.registry.ModItems;
import ru.gorod.rusgorod.social.ChatResponder;
import ru.gorod.rusgorod.social.Orders;
import ru.gorod.rusgorod.social.PlayerInteractions;
import ru.gorod.rusgorod.study.LawSchool;
import ru.gorod.rusgorod.util.Scheduler;
import ru.gorod.rusgorod.world.CityEvents;
import ru.gorod.rusgorod.world.CityGenerator;
import ru.gorod.rusgorod.world.CityManager;

public class ServerEvents {

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;
        if (event.level instanceof ServerLevel sl && sl.dimension() == Level.OVERWORLD) {
            Scheduler.tick(sl.getGameTime());
            CityManager.tick(sl);
            CityEvents.tick(sl);
            ru.gorod.rusgorod.world.Turfs.tick(sl);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        GorodData d = GorodData.get(p.serverLevel());
        if (!d.generated) return;
        if (p.tickCount % 10 == 0) QuestManager.tick(p);
        if (p.tickCount % 20 == 0) {
            CrimeSystem.tickJailedPlayer(p);
            JobManager.tick(p);
            LawSchool.tick(p);
            Court.tick(p);
            PlayerRecord r = d.player(p.getUUID());
            if (r.cuffed) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 4, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 4, false, false));
            }
            if (p.tickCount % 100 == 0) {
                p.refreshDisplayName();
            }
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        GorodData d = GorodData.get(p.serverLevel());
        boolean isNew = !d.players.containsKey(p.getUUID());
        PlayerRecord r = d.player(p.getUUID());
        r.examSubject = -1;
        if (isNew) {
            FactionService.giveGear(p, r, true);
            p.sendSystemMessage(Component.literal("Добро пожаловать в «" + CityGenerator.CITY_NAME + "»! Вы — гражданин. Нажмите G: фракции, работа, юрфак, задания. "
                    + "Разговаривайте с жителями прямо в чате — они отвечают. На счету: " + r.balance + " ₽.").withStyle(ChatFormatting.GOLD));
        }
        p.refreshDisplayName();
        Network.syncPlayer(p);
        Network.broadcastRanks(p.getServer());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Orders.releaseAll(p.serverLevel(), p);
        Scheduler.later(5, () -> Network.broadcastRanks(p.getServer()));
    }

    /** Звание у ника в форме: «[Лейтенант пол.] Ник». */
    @SubscribeEvent
    public void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        PlayerRecord r = GorodData.get(p.serverLevel()).player(p.getUUID());
        if (r.faction == Faction.CIVILIAN || !r.uniform) return;
        String tag = r.faction.isState() ? r.faction.shortRank(r.rank) : r.faction.rankName(r.rank);
        MutableComponent name = Component.literal("[" + tag + "] ").withStyle(r.faction.isState() ? ChatFormatting.AQUA : ChatFormatting.DARK_RED)
                .append(event.getDisplayname().copy().withStyle(ChatFormatting.WHITE));
        event.setDisplayname(name);
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        ServerPlayer p = event.getPlayer();
        String raw = event.getRawText();
        if (Court.onChat(p, raw)) return;
        ChatResponder.onChat(p, raw);
    }

    /** Возрождение в городской больнице (если нет своей кровати). */
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer p)) return;
        GorodData d = GorodData.get(p.serverLevel());
        if (!d.generated || p.getRespawnPosition() != null) return;
        PlayerRecord r = d.player(p.getUUID());
        if (r.isJailed(p.serverLevel().getGameTime())) return;
        r.cuffed = false;
        BlockPos h = d.loc("hospital");
        ServerLevel ow = p.getServer().overworld();
        p.teleportTo(ow, h.getX() + 0.5, h.getY(), h.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.sendSystemMessage(Component.literal("Вас доставили в городскую больницу. Медицинская помощь оказана бесплатно по полису ОМС (ст. 41 Конституции РФ).").withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer k && !(event.getEntity() instanceof net.minecraft.world.entity.animal.Animal)
                && (event.getEntity() instanceof ServerPlayer || event.getEntity() instanceof ru.gorod.rusgorod.entity.CitizenEntity)) {
            GorodData.get(k.serverLevel()).player(k.getUUID()).kills++;
        }
        if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() instanceof ServerPlayer killer && victim != killer) {
            CrimeSystem.onPlayerKilledPlayer(killer, victim);
        }
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && !p.level().isClientSide) {
            if (GorodData.get(p.serverLevel()).player(p.getUUID()).cuffed) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || event.getEntity() == attacker) return;
        LivingEntity victim = event.getEntity();
        // подчинённые вступаются за командира
        if (attacker.serverLevel() != null) {
            for (CitizenEntity c : attacker.serverLevel().getEntitiesOfClass(CitizenEntity.class, attacker.getBoundingBox().inflate(24),
                    c -> c.order != CitizenEntity.Order.NONE && c.isCommander(attacker) && c != victim && c.isCombatant())) {
                if (!(victim instanceof CitizenEntity vc && vc.isCommander(attacker))) c.setTarget(victim);
            }
        }
        if (victim instanceof ServerPlayer vp) {
            GorodData d = GorodData.get(attacker.serverLevel());
            if (!d.inCity(attacker.blockPosition())) return;
            PlayerRecord ar = d.player(attacker.getUUID());
            PlayerRecord vr = d.player(vp.getUUID());
            boolean lawful = (ar.faction.canArrest() && vr.isWanted()) || vp.getLastHurtMob() == attacker;
            if (!lawful && attacker.tickCount % 3 == 0) {
                CrimeSystem.commitByPlayer(attacker, vr.faction.isLawEnforcement() ? Article.UK_318 : Article.KOAP_6_1_1, vp);
            }
        }
    }

    /** ПКМ по игроку пустой рукой — меню взаимодействия. */
    @SubscribeEvent
    public void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getEntity() instanceof ServerPlayer p && event.getTarget() instanceof ServerPlayer t) {
            if (p.getMainHandItem().is(ModItems.CUFFS.get())) return;
            PlayerInteractions.open(p, t);
            event.setCanceled(true);
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
    public void onStopping(ServerStoppingEvent event) {
        Scheduler.clear();
        CityGenerator.reset();
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        GorodCommand.register(event.getDispatcher());
    }
}
