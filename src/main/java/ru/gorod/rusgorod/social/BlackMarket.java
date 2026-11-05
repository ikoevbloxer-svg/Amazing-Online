package ru.gorod.rusgorod.social;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.net.OpenDialogPacket;
import ru.gorod.rusgorod.registry.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * «Чёрный рынок»: бандиты продают стволы своим и знакомым. Каждая покупка — ст. 222 УК РФ (приобретение),
 * если рядом окажется полицейский или свидетель — сразу в розыск. Обрез — ещё и ст. 223.
 * Легальный оружейный магазин «Калибр» — на рынке, через смартфон, только по лицензии ЛОА.
 */
public final class BlackMarket {
    public record Offer(String id, RegistryObject<Item> item, int count, int price, String note) {
    }

    public static final List<Offer> BLACK = List.of(
            new Offer("tt", ModItems.TT, 1, 7000, "ТТ «с историей»"),
            new Offer("pm", ModItems.PM, 1, 9000, "ПМ, номер сбит"),
            new Offer("izh43", ModItems.IZH43, 1, 4500, "обрез — ст. 223"),
            new Offer("aks74u", ModItems.AKS74U, 1, 26000, "«ксюха» со склада"),
            new Offer("kastet", ModItems.KASTET, 1, 600, ""),
            new Offer("knife", ModItems.KNIFE, 1, 900, ""),
            new Offer("a762tt", ModItems.AMMO_762TT, 16, 900, ""),
            new Offer("a9mm", ModItems.AMMO_9MM, 16, 900, ""),
            new Offer("a545", ModItems.AMMO_545, 30, 2200, ""),
            new Offer("a12", ModItems.AMMO_12GA, 10, 700, ""));

    public static final List<Offer> SHOP = List.of(
            new Offer("osa", ModItems.OSA, 1, 9000, "ОООП, ЛОА"),
            new Offer("saiga12", ModItems.SAIGA12, 1, 19000, "гладкоствольное, ЛОА"),
            new Offer("rubber", ModItems.AMMO_RUBBER, 12, 600, "ЛОА"),
            new Offer("a12", ModItems.AMMO_12GA, 10, 500, "ЛОА"),
            new Offer("bronik", ModItems.BRONIK, 1, 7000, "Бр2, без лицензии"),
            new Offer("bat", ModItems.BAT, 1, 800, "спорттовары"),
            new Offer("phone", ModItems.PHONE, 1, 3000, "смартфон"));

    public static boolean canTrade(ServerPlayer p, PlayerRecord r, CitizenEntity c) {
        Faction cf = c.getFaction();
        if (!(cf == Faction.RUS_MAFIA || cf == Faction.KAVKAZ_MAFIA || cf == Faction.BIKERS) || c.isJailed()) return false;
        return r.faction.isCriminal() || c.relation(p.getUUID()) >= Relations.KNOWN;
    }

    public static void open(ServerPlayer p, CitizenEntity c) {
        List<String[]> opts = new ArrayList<>();
        for (Offer o : BLACK) {
            opts.add(new String[]{"bm_" + o.id, "§8" + o.item.get().getDescription().getString() + (o.count > 1 ? " ×" + o.count : "") + " — " + o.price + " ₽"
                    + (o.note.isEmpty() ? "" : " §7(" + o.note + ")")});
        }
        opts.add(new String[]{"talk", "Передумал"});
        Network.sendTo(p, new OpenDialogPacket(c.getId(), c.displayTitle(), "— Тихо. Чего надо? Деньги вперёд, и ты меня не знаешь.\n§c§oПриобретение — ст. 222 УК РФ.", opts));
    }

    public static void buy(ServerPlayer p, CitizenEntity c, String id) {
        GorodData d = GorodData.get(p.serverLevel());
        PlayerRecord r = d.player(p.getUUID());
        if (!canTrade(p, r, c)) {
            c.say("Ты кто такой? Иди отсюда.", true);
            return;
        }
        for (Offer o : BLACK) {
            if (!o.id.equals(id)) continue;
            int price = c.relation(p.getUUID()) >= Relations.FRIEND ? o.price * 85 / 100 : o.price;
            if (r.balance < price) {
                c.say("Бабки покажи сначала.", true);
                return;
            }
            r.balance -= price;
            d.addFunds(c.getFaction(), price / 2);
            ItemStack st = new ItemStack(o.item.get(), o.count);
            if (!p.getInventory().add(st)) p.drop(st, false);
            c.changeRelation(p.getUUID(), 3);
            c.say(p.getRandom().nextBoolean() ? "Держи. Ствол чистый... почти." : "Бери. Если что — ты нашёл его в лесу.", true);
            if (o.item.get() instanceof ru.gorod.rusgorod.item.GunItem && r.faction != Faction.ARMY && !ru.gorod.rusgorod.law.WeaponLaw.armedService(r.faction)) {
                CrimeSystem.commitByPlayer(p, Article.UK_222, null);
            }
            p.sendSystemMessage(Component.literal("Куплено: " + st.getHoverName().getString() + " за " + price + " ₽. Хранение без права — ст. 222 УК; полиция изымет при задержании.")
                    .withStyle(ChatFormatting.DARK_GRAY));
            open(p, c);
            return;
        }
    }

    /** Покупка в «Калибре» (через смартфон, находясь на рынке). */
    public static void shopBuy(ServerPlayer p, String id) {
        GorodData d = GorodData.get(p.serverLevel());
        PlayerRecord r = d.player(p.getUUID());
        if (p.distanceToSqr(d.loc("market").getCenter()) > 26 * 26) {
            msg(p, "«Калибр»: продажа оружия только лично в павильоне на рынке «Центральный» (ст. 18 ФЗ-150). Метка поставлена на карте.", ChatFormatting.RED);
            return;
        }
        for (Offer o : SHOP) {
            if (!o.id.equals(id)) continue;
            boolean needLic = o.note.contains("ЛОА");
            if (needLic && !r.gunLicense) {
                msg(p, "«Калибр»: предъявите лицензию ЛОА. Оформляется в «Госуслугах» (смартфон).", ChatFormatting.RED);
                return;
            }
            if (r.balance < o.price) {
                msg(p, "«Калибр»: недостаточно средств (" + o.price + " ₽).", ChatFormatting.RED);
                return;
            }
            r.balance -= o.price;
            d.budget += o.price / 10;
            ItemStack st = new ItemStack(o.item.get(), o.count);
            if (!p.getInventory().add(st)) p.drop(st, false);
            msg(p, "«Калибр»: продано " + st.getHoverName().getString() + " — " + o.price + " ₽. Чек и запись в журнале учёта (ст. 18 ФЗ-150).", ChatFormatting.GREEN);
            return;
        }
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting f) {
        p.sendSystemMessage(Component.literal(s).withStyle(f));
    }

    private BlackMarket() {
    }
}
