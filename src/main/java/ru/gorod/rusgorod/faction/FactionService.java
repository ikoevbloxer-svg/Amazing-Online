package ru.gorod.rusgorod.faction;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.registry.ModItems;
import ru.gorod.rusgorod.social.Orders;

import java.util.ArrayList;
import java.util.List;

/** Приём на службу / в группировку, увольнение, присвоение званий, снаряжение, денежное довольствие. */
public final class FactionService {

    /** Список невыполненных требований (пустой — можно подавать). */
    public static List<String> unmetRequirements(PlayerRecord r, Faction f, long day) {
        List<String> out = new ArrayList<>();
        if (f == Faction.CIVILIAN) return out;
        if (f.banIfEverConvicted && r.everConvicted)
            out.add("Не принимаются лица, имеющие или имевшие судимость");
        else if (f.banIfActiveRecord && r.hasActiveRecord(day))
            out.add("Имеется непогашенная судимость (ст. 86 УК РФ)");
        if (f.needsLegalEducation && !r.legalEducation)
            out.add("Требуется высшее юридическое образование (G → «Юрфак»)");
        if (f.minLegalExp > 0 && r.legalExpDays < f.minLegalExp)
            out.add("Стаж работы по юридической профессии " + r.legalExpDays + " / " + f.minLegalExp + " сут. (СК, прокуратура, полиция, ФСБ)");
        if (r.civRep < f.minCivRep)
            out.add("Гражданская репутация " + r.civRep + " / " + f.minCivRep);
        if (r.crimRep < f.minCrimRep)
            out.add("Авторитет " + r.crimRep + " / " + f.minCrimRep + (f == Faction.KAVKAZ_MAFIA ? " (нужна рекомендация «своих»)" : "")
                    + " — растёт от «дел» и «ходок»");
        if (r.fineDebt > 0 && f.isState())
            out.add("Неоплаченные штрафы: " + r.fineDebt + " ₽");
        return out;
    }

    public static double chance(PlayerRecord r, Faction f) {
        double c = f.joinChance;
        if (f.isState()) {
            c += Math.min(0.3, r.civRep / 300.0);
            if (r.legalEducation) c += 0.1;
        } else if (f.isCriminal()) {
            c += Math.min(0.4, r.crimRep / 100.0);
            if (r.everConvicted) c += 0.15;
        }
        return Math.min(0.95, c);
    }

    public static void apply(ServerPlayer p, Faction f) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        long day = level.getDayTime() / 24000L;
        if (f == Faction.CIVILIAN) return;
        if (r.faction != Faction.CIVILIAN) {
            msg(p, "Вы уже состоите в: " + r.faction.title + ". Сначала увольтесь.", ChatFormatting.RED);
            return;
        }
        if (r.isWanted() || r.isJailed(level.getGameTime())) {
            msg(p, "Лицо, находящееся в розыске или под стражей, не может быть принято.", ChatFormatting.RED);
            return;
        }
        if (day - r.lastApplyDay < 1) {
            msg(p, "Повторное заявление можно подать не ранее чем через сутки.", ChatFormatting.RED);
            return;
        }
        List<String> unmet = unmetRequirements(r, f, day);
        if (!unmet.isEmpty()) {
            msg(p, "Отказ в приёме (" + f.title + "):", ChatFormatting.RED);
            for (String s : unmet) msg(p, " • " + s, ChatFormatting.RED);
            msg(p, "Основание: " + f.lawBasis, ChatFormatting.GRAY);
            return;
        }
        r.lastApplyDay = day;
        double ch = chance(r, f);
        if (level.random.nextDouble() > ch) {
            String reason = switch (f.type) {
                case STATE -> switch (f) {
                    case UFSB -> "Не пройдена специальная проверка / психофизиологическое исследование.";
                    case ARMY -> "Не пройдена военно-врачебная комиссия в военкомате (категория годности).";
                    case PROK, SK -> "Не пройден конкурсный отбор на должность.";
                    case COURT -> "Квалификационная коллегия судей не рекомендовала вашу кандидатуру (ст. 5 Закона «О статусе судей»).";
                    case GOV -> "По итогам конкурса на замещение должности муниципальной службы выбран другой кандидат (ст. 17 ФЗ № 25-ФЗ).";
                    default -> "Не пройдены отбор и профессиональный психологический отбор (ст. 17 ФЗ № 342-ФЗ).";
                };
                case CRIMINAL -> "«Мы тебя не знаем. Приходи, когда за тебя скажут люди».";
                default -> "Отказ.";
            };
            msg(p, "Заявление рассмотрено. Отказано. " + reason + " (шанс был " + Math.round(ch * 100) + "%)", ChatFormatting.RED);
            Network.syncPlayer(p);
            return;
        }
        enlist(p, r, f, -1);
    }

    /** Зачисление (по заявлению или по приглашению руководителя). rank < 0 — стартовое звание. */
    public static void enlist(ServerPlayer p, PlayerRecord r, Faction f, int rank) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        long day = level.getDayTime() / 24000L;
        r.faction = f;
        r.rank = rank >= 0 ? Math.min(rank, f.topRank()) : f.startRank;
        if (rank < 0 && r.legalEducation && f.officerRankIndex >= 0) r.rank = f.officerRankIndex; // выпускник вуза — первое офицерское звание
        r.points = 0;
        r.rankSinceDay = day;
        r.job = "";
        r.uniform = f != Faction.CIVILIAN;
        r.clearQuest();
        msg(p, "══ " + f.title + " ══", ChatFormatting.GOLD);
        switch (f) {
            case ARMY -> msg(p, "Вы заключили контракт о прохождении военной службы и приняли Военную присягу (ст. 40 ФЗ № 53-ФЗ).", ChatFormatting.WHITE);
            case UMVD, UGIBDD -> msg(p, "Вы приняли Присягу сотрудника органов внутренних дел (ст. 28 ФЗ № 342-ФЗ). Испытательный срок — до 6 месяцев (ст. 24).", ChatFormatting.WHITE);
            case UFSIN -> msg(p, "Вы приняли Присягу сотрудника уголовно-исполнительной системы (ФЗ № 197-ФЗ).", ChatFormatting.WHITE);
            case SK -> msg(p, "Вы приняли присягу сотрудника Следственного комитета РФ (ст. 17 ФЗ № 403-ФЗ).", ChatFormatting.WHITE);
            case PROK -> msg(p, "Вы приняли Присягу прокурора (ст. 40.4 ФЗ «О прокуратуре РФ»).", ChatFormatting.WHITE);
            case UFSB -> msg(p, "Вы приняты на военную службу в органы ФСБ России (ст. 16 ФЗ № 40-ФЗ). Режим секретности — Закон «О государственной тайне».", ChatFormatting.WHITE);
            case GOV -> msg(p, "Вы назначены на должность муниципальной службы (ФЗ № 25-ФЗ). Обязательно — сведения о доходах (ст. 15).", ChatFormatting.WHITE);
            case COURT -> msg(p, "Указом Президента РФ вы назначены судьёй. Вы принесли присягу судьи (ст. 8 Закона «О статусе судей»). Судья независим и подчиняется только закону.", ChatFormatting.WHITE);
            case RUS_MAFIA -> msg(p, "«Теперь ты с нами. За базар отвечаешь, общак не трогаешь, с мусорами не говоришь».", ChatFormatting.WHITE);
            case KAVKAZ_MAFIA -> msg(p, "«Брат, тебе поверили. Не подведи людей».", ChatFormatting.WHITE);
            case SKINHEADS -> msg(p, "Вас приняли в ячейку. Помните: экстремистская деятельность преследуется по ст. 282.1 УК РФ.", ChatFormatting.WHITE);
            case BIKERS -> msg(p, "Вы — гость клуба (хэнгэраунд). Заслужите нашивку проспекта.", ChatFormatting.WHITE);
            default -> {
            }
        }
        msg(p, "Звание/статус: " + f.rankName(r.rank) + ", должность: " + f.positionName(r.rank) + ". Форма надета (G → «Моя служба» — снять/надеть).", ChatFormatting.AQUA);
        d.addNews(f.isState() ? "Кадры: " + p.getName().getString() + " принят(а) — " + f.title + "." : "Слухи: в городе у «" + f.title + "» новый человек.");
        giveGear(p, r, true);
        changed(p);
    }

    public static void leave(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        if (r.faction == Faction.CIVILIAN) return;
        Faction old = r.faction;
        if (old.isState()) {
            msg(p, "Рапорт об увольнении по собственному желанию удовлетворён (п. 2 ч. 2 ст. 82 ФЗ № 342-ФЗ / ст. 80 ТК РФ / аналог). Служебное удостоверение сдано.", ChatFormatting.GRAY);
        } else {
            msg(p, "Вы вышли из группировки. Авторитет потерян, а «бывших» не любят.", ChatFormatting.GRAY);
            r.crimRep /= 2;
        }
        reset(r);
        Orders.releaseAll(level, p);
        changed(p);
    }

    private static void reset(PlayerRecord r) {
        r.faction = Faction.CIVILIAN;
        r.rank = 0;
        r.points = 0;
        r.uniform = false;
        r.clearQuest();
    }

    /** Рапорт на присвоение очередного звания. */
    public static void promote(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        GorodData d = GorodData.get(level);
        PlayerRecord r = d.player(p.getUUID());
        Faction f = r.faction;
        long day = level.getDayTime() / 24000L;
        int next = r.rank + 1;
        if (f == Faction.CIVILIAN || next >= f.rankCount()) {
            msg(p, "Повышение невозможно.", ChatFormatting.RED);
            return;
        }
        if (r.points < f.pointsForRank(next)) {
            msg(p, "Недостаточно служебных баллов: " + r.points + " / " + f.pointsForRank(next), ChatFormatting.RED);
            return;
        }
        if (day - r.rankSinceDay < f.daysInRankFor(next)) {
            msg(p, "Не истёк срок выслуги в текущем звании: нужно " + f.daysInRankFor(next) + " сут., прошло " + (day - r.rankSinceDay)
                    + " (ст. 43 ФЗ № 342-ФЗ / ст. 22 Положения о порядке прохождения военной службы).", ChatFormatting.RED);
            return;
        }
        String block = rankBlock(r, f, next);
        if (block != null) {
            msg(p, block, ChatFormatting.RED);
            return;
        }
        setRank(p, r, next, null);
    }

    /** Препятствия для звания (образование, выборность главы). null — нет. */
    public static String rankBlock(PlayerRecord r, Faction f, int next) {
        if (f.officerRankIndex >= 0 && next >= f.officerRankIndex && !r.legalEducation)
            return "Для офицерского звания требуется высшее образование. Окончите юридический институт (G → «Юрфак»).";
        if (f == Faction.GOV && next == f.topRank() && r.civRep < 80)
            return "Глава городского округа избирается (ст. 36 ФЗ № 131-ФЗ). Нужна репутация 80+.";
        return null;
    }

    /** Присвоить звание (by — руководитель-игрок, null — по рапорту). */
    public static void setRank(ServerPlayer p, PlayerRecord r, int rank, ServerPlayer by) {
        GorodData d = GorodData.get(p.serverLevel());
        Faction f = r.faction;
        boolean up = rank > r.rank;
        r.rank = Math.max(0, Math.min(rank, f.topRank()));
        r.rankSinceDay = p.serverLevel().getDayTime() / 24000L;
        String how = switch (f.type) {
            case STATE -> up ? "Приказом " + (by == null ? "руководителя" : f.positionName(d.player(by.getUUID()).rank) + " " + by.getName().getString())
                    + " присвоено звание / назначена должность: " : "Приказом руководителя вы понижены: ";
            case CRIMINAL -> up ? "Люди решили — теперь ты: " : "Тебя опустили до: ";
            default -> "";
        };
        msg(p, how + f.rankName(r.rank) + " (" + f.positionName(r.rank) + ")", up ? ChatFormatting.GOLD : ChatFormatting.RED);
        if (up && f == Faction.RUS_MAFIA && r.rank == f.topRank()) {
            // «Коронация» — состав ст. 210.1 УК РФ
            msg(p, "Вас «короновали». Занятие высшего положения в преступной иерархии — ст. 210.1 УК РФ (8–15 лет). ФСБ и СК в курсе.", ChatFormatting.DARK_RED);
            CrimeSystem.commitByPlayer(p, Article.UK_210_1, null);
            if (!r.wanted.contains(Article.UK_210_1)) r.wanted.add(Article.UK_210_1);
        }
        if (f.isState() && up) d.addNews("Кадры: " + p.getName().getString() + " — " + f.rankName(r.rank) + " (" + f.title + ").");
        changed(p);
    }

    /** Уволить игрока по решению руководителя (за проступок, порочащий честь — п. 9 ч. 3 ст. 82 342-ФЗ и аналоги). */
    public static void fire(ServerPlayer target, PlayerRecord tr, ServerPlayer by) {
        Faction f = tr.faction;
        msg(target, f.isState() ? "Приказом руководителя вы уволены со службы — " + f.title + "." : "Тебя выгнали из «" + f.title + "».", ChatFormatting.RED);
        if (f.isCriminal()) tr.crimRep /= 2;
        reset(tr);
        Orders.releaseAll(target.serverLevel(), target);
        changed(target);
        msg(by, target.getName().getString() + " уволен(а).", ChatFormatting.GRAY);
    }

    public static void toggleUniform(ServerPlayer p) {
        PlayerRecord r = GorodData.get(p.serverLevel()).player(p.getUUID());
        if (r.faction == Faction.CIVILIAN) return;
        r.uniform = !r.uniform;
        msg(p, r.uniform ? "Вы надели форму" + (r.faction.insignia ? " с погонами «" + r.faction.rankName(r.rank) + "»." : ".") : "Вы переоделись в гражданское.", ChatFormatting.GRAY);
        changed(p);
    }

    /** После любых изменений фракции/звания/формы. */
    public static void changed(ServerPlayer p) {
        p.refreshDisplayName();
        Network.syncPlayer(p);
        Network.broadcastRanks(p.getServer());
    }

    public static void giveGear(ServerPlayer p, PlayerRecord r, boolean force) {
        long day = p.serverLevel().getDayTime() / 24000L;
        if (!force && day == r.lastGearDay) {
            msg(p, "Снаряжение уже выдавалось сегодня.", ChatFormatting.RED);
            return;
        }
        r.lastGearDay = day;
        List<ItemStack> items = new ArrayList<>();
        switch (r.faction) {
            case UMVD -> {
                items.add(new ItemStack(ModItems.BATON.get()));
                items.add(new ItemStack(ModItems.CUFFS.get()));
                items.add(new ItemStack(ModItems.PM.get()));
                items.add(new ItemStack(ModItems.AMMO_9MM.get(), 16));
            }
            case UGIBDD -> {
                items.add(new ItemStack(ModItems.BATON.get()));
                items.add(new ItemStack(ModItems.CUFFS.get()));
                items.add(new ItemStack(ModItems.PM.get()));
                items.add(new ItemStack(ModItems.AMMO_9MM.get(), 8));
            }
            case UFSIN -> {
                items.add(new ItemStack(ModItems.BATON.get()));
                items.add(new ItemStack(ModItems.CUFFS.get()));
            }
            case UFSB -> {
                items.add(new ItemStack(ModItems.CUFFS.get()));
                items.add(new ItemStack(ModItems.PM.get()));
                items.add(new ItemStack(ModItems.AMMO_9MM.get(), 16));
            }
            case ARMY -> {
                items.add(new ItemStack(ModItems.AK74.get()));
                items.add(new ItemStack(ModItems.AMMO_545.get(), 30));
                items.add(new ItemStack(Items.IRON_HELMET));
            }
            case RUS_MAFIA, KAVKAZ_MAFIA -> {
                items.add(new ItemStack(ModItems.PM.get()));
                items.add(new ItemStack(ModItems.AMMO_9MM.get(), 8));
            }
            case BIKERS -> items.add(new ItemStack(Items.IRON_AXE));
            case SKINHEADS -> items.add(new ItemStack(ModItems.BATON.get()));
            default -> {
            }
        }
        if (r.faction.isState() || r.faction == Faction.CIVILIAN) {
            ItemStack id = new ItemStack(ModItems.ID_CARD.get());
            id.setHoverName(Component.literal(r.faction == Faction.CIVILIAN ? "Паспорт гражданина РФ"
                    : r.faction == Faction.ARMY ? "Военный билет" : "Служебное удостоверение (" + r.faction.title + ")"));
            items.add(id);
        }
        for (ItemStack s : items) {
            if (!p.getInventory().add(s)) p.drop(s, false);
        }
        if (!items.isEmpty()) msg(p, r.faction.isState() ? "Получено табельное снаряжение." : "Получено снаряжение.", ChatFormatting.GRAY);
    }

    /** Ежедневное денежное довольствие. */
    public static long dailyIncome(PlayerRecord r) {
        Faction f = r.faction;
        if (f.isState()) {
            int base = switch (f) {
                case UMVD, UGIBDD -> 1500;
                case UFSIN -> 1300;
                case SK -> 2200;
                case PROK -> 2300;
                case UFSB -> 2600;
                case ARMY -> 1400;
                case GOV -> 1700;
                case COURT -> 3500;
                default -> 1000;
            };
            return Math.round(base * (1 + r.rank * 0.15));
        }
        return 0;
    }

    static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.sendSystemMessage(Component.literal(s).withStyle(c));
    }

    private FactionService() {
    }
}
