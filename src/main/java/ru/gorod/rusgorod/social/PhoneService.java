package ru.gorod.rusgorod.social;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.registry.ModItems;
import ru.gorod.rusgorod.registry.ModSounds;
import ru.gorod.rusgorod.world.Places;

import java.util.List;

/** Серверная часть смартфона: банк, «Госуслуги» (ЛОА), такси, 112/103, контакты. */
public final class PhoneService {
    public static void handle(ServerPlayer p, String action, String arg) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        switch (action) {
            case "ph_license" -> license(p, d, r);
            case "ph_send" -> send(p, d, r, arg);
            case "ph_112" -> call112(p, level, d, r);
            case "ph_103" -> ambulance(p, level, d, r);
            case "ph_taxi" -> taxi(p, level, d, r, arg);
            case "ph_shop" -> BlackMarket.shopBuy(p, arg);
            case "ph_call" -> callFriend(p, level, r, arg);
            default -> {
            }
        }
    }

    private static void license(ServerPlayer p, GorodData d, PlayerRecord r) {
        long day = p.serverLevel().getDayTime() / 24000L;
        if (r.gunLicense) {
            msg(p, "«Госуслуги»: лицензия ЛОА уже выдана.", ChatFormatting.YELLOW);
            return;
        }
        // ст. 13 ФЗ-150: не выдаётся при судимости за умышленное преступление, при неснятой адм. ответственности и т.п.
        if (r.hasActiveRecord(day)) {
            msg(p, "«Госуслуги»: отказ — неснятая или непогашенная судимость (ст. 13 ФЗ «Об оружии»).", ChatFormatting.RED);
            return;
        }
        if (r.isWanted() || r.fineDebt > 0) {
            msg(p, "«Госуслуги»: отказ — вы в розыске или есть неоплаченные штрафы.", ChatFormatting.RED);
            return;
        }
        int fee = 2000;
        if (r.balance < fee) {
            msg(p, "«Госуслуги»: госпошлина и медкомиссия — " + fee + " ₽. Недостаточно средств.", ChatFormatting.RED);
            return;
        }
        r.balance -= fee;
        d.budget += fee;
        r.gunLicense = true;
        ItemStack lic = new ItemStack(ModItems.GUN_LICENSE.get());
        lic.setHoverName(Component.literal("Лицензия ЛОА — " + p.getName().getString()));
        if (!p.getInventory().add(lic)) p.drop(lic, false);
        msg(p, "«Госуслуги»: Росгвардия выдала лицензию ЛОА. Теперь в «Калибре» на рынке можно купить «Осу», «Сайгу» и патроны. "
                + "Ношение в нетрезвом виде и стрельба в городе — ст. 20.8 КоАП.", ChatFormatting.GREEN);
    }

    private static void send(ServerPlayer p, GorodData d, PlayerRecord r, String arg) {
        String[] a = arg.split("\\|");
        if (a.length != 2) return;
        long amount;
        try {
            amount = Long.parseLong(a[1].trim());
        } catch (NumberFormatException e) {
            msg(p, "Банк: неверная сумма.", ChatFormatting.RED);
            return;
        }
        if (amount <= 0 || amount > r.balance) {
            msg(p, "Банк: недостаточно средств.", ChatFormatting.RED);
            return;
        }
        ServerPlayer t = p.getServer().getPlayerList().getPlayerByName(a[0].trim());
        if (t == null || t == p) {
            msg(p, "Банк: получатель «" + a[0] + "» не найден (нужен игрок онлайн).", ChatFormatting.RED);
            return;
        }
        r.balance -= amount;
        d.player(t.getUUID()).balance += amount;
        msg(p, "Банк: перевод " + amount + " ₽ → " + t.getName().getString() + " выполнен.", ChatFormatting.GREEN);
        t.sendSystemMessage(Component.literal("Банк: поступление " + amount + " ₽ от " + p.getName().getString() + ".").withStyle(ChatFormatting.GREEN));
        t.playNotifySound(ModSounds.NOTIFY.get(), SoundSource.MASTER, 0.7f, 1f);
        ru.gorod.rusgorod.net.Network.syncPlayer(t);
    }

    private static void call112(ServerPlayer p, ServerLevel level, GorodData d, PlayerRecord r) {
        long now = level.getGameTime();
        if (now - r.lastCallTime < 400) {
            msg(p, "112: линия занята, перезвоните позже.", ChatFormatting.GRAY);
            return;
        }
        r.lastCallTime = now;
        AABB near = p.getBoundingBox().inflate(28);
        List<Entity> threats = level.getEntities(p, near, e -> (e instanceof CitizenEntity c && c.isWanted() && !c.isJailed())
                || e instanceof Enemy || (e instanceof CitizenEntity c2 && c2.getTarget() == p)
                || (e instanceof ServerPlayer sp && sp != p && CrimeSystem.isWanted(sp)));
        List<CitizenEntity> cops = level.getEntitiesOfClass(CitizenEntity.class, p.getBoundingBox().inflate(160),
                c -> (c.getFaction() == Faction.UMVD || c.getFaction() == Faction.UGIBDD) && !c.isJailed() && c.order == CitizenEntity.Order.NONE);
        cops.sort((x, y) -> Double.compare(x.distanceToSqr(p), y.distanceToSqr(p)));
        if (threats.isEmpty() && p.getHealth() >= p.getMaxHealth() * 0.6f) {
            r.falseCalls++;
            if (r.falseCalls >= 2) {
                r.falseCalls = 0;
                msg(p, "112: вызов признан заведомо ложным.", ChatFormatting.RED);
                CrimeSystem.commitByPlayer(p, Article.KOAP_19_13, null);
                if (!r.wanted.contains(Article.KOAP_19_13)) {
                    r.fineDebt += Article.KOAP_19_13.fine;
                    if (r.fineDueDay < 0) r.fineDueDay = level.getDayTime() / 24000L + 3;
                    msg(p, "Штраф " + Article.KOAP_19_13.fine + " ₽ по ст. 19.13 КоАП РФ (оплатить в «Госуслугах»).", ChatFormatting.RED);
                }
                return;
            }
            msg(p, "112: «Что у вас случилось? Наряд проверит адрес. Напоминаем: ложный вызов — ст. 19.13 КоАП РФ».", ChatFormatting.GOLD);
        } else {
            r.falseCalls = 0;
            msg(p, "112: «Вызов принят, наряд выехал. Оставайтесь на месте, если это безопасно».", ChatFormatting.AQUA);
        }
        int n = 0;
        for (CitizenEntity c : cops) {
            if (n >= (threats.isEmpty() ? 1 : 3)) break;
            c.order = CitizenEntity.Order.ASSAULT;
            c.holdPos = p.blockPosition();
            c.commander = p.getUUID();
            c.orderUntil = now + 1600;
            if (!threats.isEmpty()) {
                for (Entity t : threats) {
                    if (t instanceof net.minecraft.world.entity.LivingEntity le && c.getTarget() == null) c.setTarget(le);
                }
            }
            n++;
        }
        if (n > 0) level.playSound(null, cops.get(0).blockPosition(), ModSounds.SIREN.get(), SoundSource.NEUTRAL, 3f, 1f);
        if (n == 0) msg(p, "112: «Свободных нарядов нет. Ожидайте».", ChatFormatting.GRAY);
    }

    private static void ambulance(ServerPlayer p, ServerLevel level, GorodData d, PlayerRecord r) {
        if (p.getHealth() > p.getMaxHealth() * 0.7f) {
            msg(p, "103: «Жалобы есть? Если всё в порядке — не занимайте линию».", ChatFormatting.GRAY);
            return;
        }
        if (r.isWanted() && CrimeSystem.isWanted(p)) {
            msg(p, "103: бригада выехала… и вызвала полицию: вы в розыске.", ChatFormatting.RED);
        }
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        msg(p, "103: «Скорая» оказала помощь на месте (бесплатно по ОМС, ст. 41 Конституции РФ).", ChatFormatting.GREEN);
    }

    public static int taxiPrice(BlockPos from, BlockPos to) {
        double dist = Math.sqrt(from.distSqr(to));
        return (int) (60 + dist * 1.6);
    }

    private static void taxi(ServerPlayer p, ServerLevel level, GorodData d, PlayerRecord r, String key) {
        BlockPos to = d.locs.get(key);
        if (to == null) return;
        if (r.cuffed || r.isJailed(level.getGameTime())) {
            msg(p, "Такси: водитель не поедет.", ChatFormatting.RED);
            return;
        }
        if (CrimeSystem.isWanted(p) && level.random.nextFloat() < 0.5f) {
            msg(p, "Такси: «Вас что-то по телевизору показывали… Нет, не поеду». (вы в розыске)", ChatFormatting.RED);
            return;
        }
        if (p.getLastHurtByMobTimestamp() > 0 && p.tickCount - p.getLastHurtByMobTimestamp() < 200) {
            msg(p, "Такси не приедет под обстрел. Уйдите в безопасное место.", ChatFormatting.RED);
            return;
        }
        int price = taxiPrice(p.blockPosition(), to);
        if (r.balance < price) {
            msg(p, "Такси: поездка стоит " + price + " ₽, на счёте " + r.balance + " ₽.", ChatFormatting.RED);
            return;
        }
        if (p.isPassenger()) p.stopRiding();
        r.balance -= price;
        r.taxiRides++;
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
        BlockPos safe = safeSpot(level, to);
        p.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, p.getYRot(), p.getXRot());
        String[] drivers = {"Рустам на «Солярисе»", "Сергей на «Гранте»", "Ашот на «Камри»", "Виктор Палыч на «Логане»"};
        msg(p, "Такси «Берёзка»: " + drivers[level.random.nextInt(drivers.length)] + " довёз до места «" + Places.name(key) + "». С вас " + price + " ₽.", ChatFormatting.YELLOW);
        p.playNotifySound(ModSounds.NOTIFY.get(), SoundSource.MASTER, 0.6f, 1.2f);
    }

    private static BlockPos safeSpot(ServerLevel level, BlockPos to) {
        for (int dy = 0; dy < 6; dy++) {
            BlockPos b = to.above(dy);
            if (level.getBlockState(b).isAir() && level.getBlockState(b.above()).isAir()) return b;
        }
        return to;
    }

    private static void callFriend(ServerPlayer p, ServerLevel level, PlayerRecord r, String arg) {
        int id;
        try {
            id = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return;
        }
        if (!(level.getEntity(id) instanceof CitizenEntity c) || !c.isAlive() || c.isJailed()) {
            msg(p, "Абонент недоступен.", ChatFormatting.GRAY);
            return;
        }
        int rel = c.relation(p.getUUID());
        String name = c.getCustomName() != null ? c.getCustomName().getString() : "Абонент";
        if (rel < Relations.FRIEND) {
            msg(p, name + ": «Алло? А, это вы… Извините, занят». (нужны дружеские отношения)", ChatFormatting.GRAY);
            return;
        }
        if (c.getFaction().isState() && c.getFaction() != r.faction) {
            msg(p, name + ": «Я на службе, не могу отойти. Вечером созвонимся».", ChatFormatting.GRAY);
            return;
        }
        c.order = CitizenEntity.Order.FOLLOW;
        c.commander = p.getUUID();
        c.orderUntil = level.getGameTime() + 12000;
        msg(p, name + ": «" + (c.getFaction().isCriminal() ? "Понял, брат, выдвигаюсь." : "Конечно! Уже иду.") + "»", ChatFormatting.GREEN);
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting f) {
        p.sendSystemMessage(Component.literal(s).withStyle(f));
    }

    private PhoneService() {
    }
}
