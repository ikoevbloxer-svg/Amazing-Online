package ru.gorod.rusgorod.job;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.registry.ModItems;

public final class JobManager {

    public static void take(ServerPlayer p, String jobName) {
        PlayerRecord r = GorodData.get(p.serverLevel()).player(p.getUUID());
        Job job = Job.byName(jobName);
        if (job == null) return;
        if (r.faction != Faction.CIVILIAN) {
            msg(p, "Гражданские работы доступны только гражданским. Совмещение со службой ограничено (ст. 17 ФЗ № 342-ФЗ, ст. 14 ФЗ № 25-ФЗ).", ChatFormatting.RED);
            return;
        }
        if (job == Job.STUDENT && r.legalEducation) {
            msg(p, "У вас уже есть диплом юриста.", ChatFormatting.YELLOW);
            return;
        }
        r.job = job.name();
        r.jobCounter = 0;
        msg(p, "Вы приняты на работу: " + job.title + ". Трудовой договор заключён (ст. 67 ТК РФ). " + job.description, ChatFormatting.GREEN);
        msg(p, job.pay, ChatFormatting.GRAY);
        Network.syncPlayer(p);
    }

    public static void quit(ServerPlayer p) {
        PlayerRecord r = GorodData.get(p.serverLevel()).player(p.getUUID());
        if (r.job.isEmpty()) return;
        r.job = "";
        msg(p, "Вы уволились по собственному желанию (ст. 80 ТК РФ).", ChatFormatting.GRAY);
        Network.syncPlayer(p);
    }

    public static void action(ServerPlayer p, String action) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        Job job = Job.byName(r.job);
        if (job == null) return;
        switch (action) {
            case "cargo_take" -> {
                if (job != Job.GRUZCHIK) return;
                if (!near(p, d.loc("warehouse"), 12)) {
                    msg(p, "Подойдите к складу.", ChatFormatting.RED);
                    return;
                }
                if (count(p, ModItems.CARGO_BOX.get()) > 0) {
                    msg(p, "Сначала доставьте уже взятый груз.", ChatFormatting.RED);
                    return;
                }
                p.getInventory().add(new ItemStack(ModItems.CARGO_BOX.get()));
                msg(p, "Груз получен. Доставьте в «Продукты» или на рынок.", ChatFormatting.YELLOW);
            }
            case "cargo_deliver" -> {
                if (job != Job.GRUZCHIK) return;
                if (!near(p, d.loc("shop"), 12) && !near(p, d.loc("market"), 14)) {
                    msg(p, "Подойдите к магазину «Продукты» или к рынку.", ChatFormatting.RED);
                    return;
                }
                if (!remove(p, ModItems.CARGO_BOX.get())) {
                    msg(p, "У вас нет груза.", ChatFormatting.RED);
                    return;
                }
                pay(p, d, r, 350, "доставка груза");
            }
            case "mail_take" -> {
                if (job != Job.POCHTALION) return;
                if (!near(p, d.loc("post"), 12)) {
                    msg(p, "Подойдите к почтовому отделению.", ChatFormatting.RED);
                    return;
                }
                if (count(p, ModItems.LETTER.get()) > 0) {
                    msg(p, "Сначала разнесите текущие письма.", ChatFormatting.RED);
                    return;
                }
                for (int i = 0; i < 3; i++) {
                    BlockPos addr = d.randomHome(level.random);
                    ItemStack letter = new ItemStack(ModItems.LETTER.get());
                    CompoundTag tag = letter.getOrCreateTag();
                    tag.putInt("ax", addr.getX());
                    tag.putInt("ay", addr.getY());
                    tag.putInt("az", addr.getZ());
                    letter.setHoverName(Component.literal("Письмо: X " + addr.getX() + ", Z " + addr.getZ()));
                    p.getInventory().add(letter);
                }
                msg(p, "Получено 3 письма. Адреса — в названии писем.", ChatFormatting.YELLOW);
            }
            default -> {
            }
        }
        Network.syncPlayer(p);
    }

    /** Каждые 20 тиков. */
    public static void tick(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        Job job = Job.byName(r.job);
        if (job == null) return;
        if (r.faction != Faction.CIVILIAN) {
            r.job = "";
            return;
        }
        long t = level.getDayTime() % 24000L;
        boolean workTime = t < 11000L;
        switch (job) {
            case POCHTALION -> {
                for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                    ItemStack s = p.getInventory().getItem(i);
                    if (s.getItem() != ModItems.LETTER.get() || !s.hasTag()) continue;
                    CompoundTag tag = s.getTag();
                    if (tag == null || !tag.contains("ax")) continue;
                    BlockPos addr = new BlockPos(tag.getInt("ax"), tag.getInt("ay"), tag.getInt("az"));
                    if (near(p, addr, 4)) {
                        s.shrink(1);
                        pay(p, d, r, 150, "письмо доставлено");
                    }
                }
            }
            case PRODAVETS -> {
                if (workTime && (near(p, d.loc("shop"), 10) || near(p, d.loc("market"), 12))) {
                    r.jobCounter++;
                    if (r.jobCounter % 60 == 0) pay(p, d, r, 220, "смена");
                }
            }
            case STUDENT -> {
                if (workTime && near(p, d.loc("university"), 12)) {
                    r.jobCounter++;
                    if (r.jobCounter % 60 == 0) {
                        r.lectures++;
                        r.balance += 100;
                        msg(p, "Лекция прослушана (" + r.lectures + "/6). Стипендия +100 ₽.", ChatFormatting.AQUA);
                        if (r.lectures >= 6) {
                            r.legalEducation = true;
                            r.job = "";
                            r.civRep += 10;
                            msg(p, "🎓 Вы защитили диплом! Высшее юридическое образование получено. Открыт путь в СК, прокуратуру и ФСБ, офицерские звания.", ChatFormatting.GOLD);
                        }
                        Network.syncPlayer(p);
                    }
                }
            }
            default -> {
            }
        }
    }

    public static void onLitterBroken(ServerPlayer p) {
        GorodData d = GorodData.get(p.serverLevel());
        PlayerRecord r = d.player(p.getUUID());
        if (Job.byName(r.job) == Job.DVORNIK) pay(p, d, r, 120, "уборка");
    }

    private static void pay(ServerPlayer p, GorodData d, PlayerRecord r, int gross, String what) {
        int tax = Math.round(gross * 0.13f);
        r.balance += gross - tax;
        r.jobCounter++;
        if (r.jobCounter % 10 == 0) r.civRep++;
        d.budget += tax;
        d.setDirty();
        p.displayClientMessage(Component.literal("+" + (gross - tax) + " ₽ (" + what + ", НДФЛ " + tax + " ₽)").withStyle(ChatFormatting.GREEN), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.5f);
        Network.syncPlayer(p);
    }

    private static boolean near(ServerPlayer p, BlockPos pos, double r) {
        return p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < r * r;
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int c = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() == item) c += s.getCount();
        }
        return c;
    }

    private static boolean remove(ServerPlayer p, net.minecraft.world.item.Item item) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() == item) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.sendSystemMessage(Component.literal(s).withStyle(c));
    }

    private JobManager() {
    }
}
