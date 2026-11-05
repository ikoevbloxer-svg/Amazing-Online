package ru.gorod.rusgorod.law;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.item.GunItem;
import ru.gorod.rusgorod.registry.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * Оборот оружия по ФЗ-150 «Об оружии»:
 * гражданское (гладкоствольное, ОООП) — с лицензией ЛОА (ст. 13); служебное и боевое — только у госслужащих
 * силовых структур (ст. 5, 6 ФЗ-150, ст. 23 ФЗ «О полиции»); обрез — ст. 223 УК; кастет — запрещён (ст. 6 ФЗ-150).
 */
public final class WeaponLaw {
    public static boolean armedService(Faction f) {
        return f == Faction.UMVD || f == Faction.UGIBDD || f == Faction.UFSIN || f == Faction.UFSB || f == Faction.ARMY || f == Faction.SK;
    }

    /** Законно ли у игрока это оружие. */
    public static boolean legal(PlayerRecord r, Item item) {
        if (item == ModItems.IZH43.get()) return false;
        if (item == ModItems.KASTET.get()) return false;
        if (item instanceof GunItem g) {
            if (armedService(r.faction)) return true;
            return g.spec.civilian() && r.gunLicense;
        }
        return true;
    }

    /** При задержании: изъять незаконное оружие и добавить статью. Возвращает список изъятого. */
    public static List<String> confiscate(ServerPlayer p, PlayerRecord r) {
        List<String> out = new ArrayList<>();
        boolean sawn = false, illegal = false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            Item it = s.getItem();
            if (!legal(r, it)) {
                out.add(s.getHoverName().getString());
                if (it == ModItems.IZH43.get()) sawn = true;
                if (it instanceof GunItem) illegal = true;
                p.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (illegal && !r.wanted.contains(Article.UK_222)) r.wanted.add(Article.UK_222);
        if (sawn && !r.wanted.contains(Article.UK_223)) r.wanted.add(Article.UK_223);
        if (!out.isEmpty()) {
            p.sendSystemMessage(Component.literal("При личном досмотре (ст. 27.7 КоАП, ст. 93 УПК) изъято: " + String.join(", ", out)
                    + (illegal ? ". Добавлена ст. 222 УК РФ." : "")).withStyle(ChatFormatting.RED));
        }
        return out;
    }

    private WeaponLaw() {
    }
}
