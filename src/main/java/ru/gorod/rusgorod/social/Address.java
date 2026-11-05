package ru.gorod.rusgorod.social;

import net.minecraft.server.level.ServerPlayer;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.faction.Ranks;

/** Обращения по званию/должности и сравнение старшинства между ведомствами. */
public final class Address {

    /**
     * Старшинство в «армейской» шкале (0 рядовой … 15 генерал-майор), чтобы сравнивать звания разных ведомств.
     * Классные чины прокуратуры — по количеству звёзд на погонах; судьи и чиновники — условно.
     */
    public static int grade(Faction f, int rank) {
        return switch (f) {
            case ARMY -> rank;
            case UMVD, UGIBDD, UFSIN -> rank == 0 ? 0 : rank + 1;
            case SK -> 8 + rank;
            case PROK -> rank <= 2 ? 9 + rank : 9 + rank;
            case UFSB -> 9 + rank;
            case COURT -> 11 + rank;
            case GOV -> 5 + rank;
            default -> rank;
        };
    }

    /** Одна служба/ведомство (подчинённость по прямой вертикали). */
    public static boolean sameService(Faction a, Faction b) {
        if (a == b) return true;
        boolean mvdA = a == Faction.UMVD || a == Faction.UGIBDD;
        boolean mvdB = b == Faction.UMVD || b == Faction.UGIBDD;
        return mvdA && mvdB;
    }

    /** Может ли игрок отдавать приказы этому NPC (прямой начальник по службе или старший в группировке). */
    public static boolean isSuperior(PlayerRecord r, CitizenEntity c) {
        Faction cf = c.getFaction();
        if (cf == Faction.CIVILIAN || r.faction == Faction.CIVILIAN) return false;
        if (!sameService(r.faction, cf)) return false;
        return grade(r.faction, r.rank) > grade(cf, c.rank);
    }

    public static boolean isSubordinateTo(CitizenEntity low, CitizenEntity high) {
        return sameService(low.getFaction(), high.getFaction())
                && grade(low.getFaction(), low.rank) < grade(high.getFaction(), high.rank);
    }

    /** Как NPC обращается к игроку. */
    public static String toPlayer(CitizenEntity npc, ServerPlayer p, PlayerRecord r) {
        Faction nf = npc.getFaction();
        Faction pf = r.faction;
        boolean visible = r.uniform || sameService(nf, pf); // своих знают в лицо
        if (pf == Faction.COURT && (visible || nf.isState())) return "Ваша честь";
        if (pf.isState() && visible) {
            if (nf.isCriminal()) return "гражданин начальник";
            if (nf == Faction.CIVILIAN) {
                return switch (pf) {
                    case ARMY -> "товарищ военный";
                    case UMVD, UGIBDD -> "товарищ полицейский";
                    case UFSIN -> "товарищ инспектор";
                    case GOV -> "уважаемый " + p.getName().getString();
                    default -> "товарищ " + Ranks.addressForm(pf.rankName(r.rank));
                };
            }
            return pf.address(r.rank);
        }
        if (pf.isCriminal()) {
            if (nf == pf) return pf.rankName(r.rank).toLowerCase().startsWith("вор") ? "уважаемый" : pf.rankName(r.rank);
            if (nf.isCriminal()) return "братан";
            if (nf.isState() && r.isWanted()) return "гражданин";
        }
        return switch (nf.type) {
            case CRIMINAL -> "фраерок";
            case STATE -> "гражданин";
            default -> npc.female ? "молодой человек" : "слышь, друг";
        };
    }

    /** Как один NPC обращается к другому. */
    public static String toNpc(CitizenEntity speaker, CitizenEntity listener) {
        Faction sf = speaker.getFaction();
        Faction lf = listener.getFaction();
        if (lf == Faction.COURT) return "Ваша честь";
        if (lf.isState() && lf.insignia && (sf.isState() || sf == Faction.CIVILIAN)) {
            if (sf == Faction.CIVILIAN) return lf == Faction.ARMY ? "служивый" : "товарищ начальник";
            return lf.address(listener.rank);
        }
        if (lf.isState()) return listener.politeName();
        if (lf.isCriminal()) {
            if (sf == lf) return listener.rank >= lf.topRank() - 1 ? "уважаемый" : listener.nickname;
            if (sf.canArrest()) return "гражданин " + listener.lastName;
            return sf.isCriminal() ? "братан" : "молодой человек";
        }
        // гражданские между собой — по отчеству, по-соседски
        return colloquial(listener);
    }

    /** «Сергеич», «Петрович», «Иваныч», для женщин — имя. */
    public static String colloquial(CitizenEntity c) {
        if (c.female || c.patronymic.isEmpty()) return c.firstName;
        String p = c.patronymic;
        if (p.endsWith("еевич")) return p.substring(0, p.length() - 5) + "еич";
        if (p.endsWith("ьевич")) return p.substring(0, p.length() - 5) + "ьич";
        if (p.endsWith("иевич")) return p.substring(0, p.length() - 5) + "ич";
        if (p.endsWith("анович")) return p.substring(0, p.length() - 6) + "аныч";
        if (p.endsWith("ович")) return p;
        return c.firstName;
    }

    private Address() {
    }
}
