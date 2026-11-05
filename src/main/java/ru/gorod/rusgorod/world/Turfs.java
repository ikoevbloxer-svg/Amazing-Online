package ru.gorod.rusgorod.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.social.ChatResponder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Война за территорию.
 * <ul>
 *   <li>Улицы (гаражи, парки, стадион, вокзал…) делят байкеры и скинхеды.</li>
 *   <li>Бизнесы (рынок, кафе, ресторан, бар, сауна, ларьки, АЗС…) «крышуют» русская и кавказская ОПГ.</li>
 * </ul>
 * Захват: на точке есть люди нападающих и нет защитников — прогресс растёт. Защитники рядом — идёт стычка.
 * Полиция/ФСБ на бизнесе без бандитов — «освобождение» от рэкета. Владелец получает ежедневный доход.
 */
public final class Turfs {
    public static final int CAPTURE = 60;

    public static final class Turf {
        public String id;
        public String name;
        public boolean business;
        public BlockPos pos = BlockPos.ZERO;
        public int radius = 12;
        public Faction owner;
        public Faction attacker;
        public int progress;
        public int liberation;
        public int income;
        public long lastWar;

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("id", id);
            t.putString("name", name);
            t.putBoolean("biz", business);
            t.put("pos", NbtUtils.writeBlockPos(pos));
            t.putInt("radius", radius);
            t.putString("owner", owner == null ? "" : owner.id);
            t.putInt("income", income);
            return t;
        }

        public static Turf load(CompoundTag t) {
            Turf x = new Turf();
            x.id = t.getString("id");
            x.name = t.getString("name");
            x.business = t.getBoolean("biz");
            x.pos = NbtUtils.readBlockPos(t.getCompound("pos"));
            x.radius = t.getInt("radius");
            String o = t.getString("owner");
            x.owner = o.isEmpty() ? null : Faction.byId(o);
            x.income = t.getInt("income");
            return x;
        }

        public String ownerName() {
            return owner == null ? "ничья" : owner.title;
        }
    }

    public static boolean eligible(Turf t, Faction f) {
        return t.business ? (f == Faction.RUS_MAFIA || f == Faction.KAVKAZ_MAFIA) : (f == Faction.SKINHEADS || f == Faction.BIKERS);
    }

    /** Создать точки после постройки города (и для старых миров — при первом запуске). */
    public static void setup(GorodData d) {
        if (!d.turfs.isEmpty() || !d.generated) return;
        street(d, "garages", "Гаражи «Мотор»", Faction.SKINHEADS);
        street(d, "bikers", "Клубный двор у склада", Faction.BIKERS);
        street(d, "park", "Парк Победы", null);
        street(d, "park2", "Сквер у детсада", null);
        street(d, "fountain", "Сквер с фонтаном", null);
        street(d, "stadium", "Стадион «Труд»", Faction.SKINHEADS);
        street(d, "vokzal", "Привокзальная площадь", Faction.BIKERS);
        street(d, "warehouse", "Промзона у склада", Faction.BIKERS);
        biz(d, "restaurant", "Ресторан «Империя»", Faction.RUS_MAFIA, 6000);
        biz(d, "bar", "Рюмочная «Встреча»", Faction.RUS_MAFIA, 3000);
        biz(d, "sauna", "Сауна «Берёзка»", Faction.RUS_MAFIA, 5000);
        biz(d, "pawnshop", "Ломбард «Золотой»", Faction.RUS_MAFIA, 4000);
        biz(d, "cafe", "Кафе «Шашлычный двор»", Faction.KAVKAZ_MAFIA, 4000);
        biz(d, "market", "Рынок «Центральный»", Faction.KAVKAZ_MAFIA, 7000);
        biz(d, "kiosks", "Ларьки у школы", Faction.KAVKAZ_MAFIA, 2500);
        biz(d, "shawarma", "Шаурма «У Ашота»", Faction.KAVKAZ_MAFIA, 2500);
        biz(d, "shop", "Магазин «Продукты»", null, 4000);
        biz(d, "azs", "АЗС на выезде", null, 5000);
        biz(d, "autoservice", "Автосервис «Гараж»", null, 4000);
        d.setDirty();
    }

    private static void street(GorodData d, String key, String name, Faction owner) {
        if (!d.locs.containsKey(key)) return;
        Turf t = new Turf();
        t.id = key;
        t.name = name;
        t.business = false;
        t.pos = d.locs.get(key);
        t.radius = 14;
        t.owner = owner;
        t.income = 2000;
        d.turfs.add(t);
    }

    private static void biz(GorodData d, String key, String name, Faction owner, int income) {
        if (!d.locs.containsKey(key)) return;
        Turf t = new Turf();
        t.id = key;
        t.name = name;
        t.business = true;
        t.pos = d.locs.get(key);
        t.radius = 11;
        t.owner = owner;
        t.income = income;
        d.turfs.add(t);
    }

    public static Turf at(GorodData d, BlockPos p) {
        for (Turf t : d.turfs) if (t.pos.distSqr(p) < (double) t.radius * t.radius) return t;
        return null;
    }

    public static Turf byId(GorodData d, String id) {
        for (Turf t : d.turfs) if (t.id.equals(id)) return t;
        return null;
    }

    // ================================================================ тик

    public static void tick(ServerLevel level) {
        GorodData d = GorodData.get(level);
        if (!d.generated) return;
        setup(d);
        long now = level.getGameTime();
        if (now % 20 == 0) for (Turf t : d.turfs) tickTurf(level, d, t);
        if (now % 3600 == 1800) warAi(level, d);
    }

    private static void tickTurf(ServerLevel level, GorodData d, Turf t) {
        if (!level.isLoaded(t.pos)) return;
        AABB box = new AABB(t.pos).inflate(t.radius, 6, t.radius);
        int[] count = new int[Faction.values().length];
        List<ServerPlayer> players = new ArrayList<>();
        for (CitizenEntity c : level.getEntitiesOfClass(CitizenEntity.class, box, c -> c.isAlive() && !c.isJailed())) count[c.getFaction().ordinal()]++;
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box, p -> !p.isSpectator())) {
            PlayerRecord r = d.player(p.getUUID());
            if (r.isJailed(level.getGameTime())) continue;
            count[r.faction.ordinal()] += 2; // игрок весит больше
            players.add(p);
        }
        Faction[] sides = t.business ? new Faction[]{Faction.RUS_MAFIA, Faction.KAVKAZ_MAFIA} : new Faction[]{Faction.SKINHEADS, Faction.BIKERS};
        int ownerCount = t.owner == null ? 0 : count[t.owner.ordinal()];
        Faction challenger = null;
        int best = 0;
        for (Faction f : sides) {
            if (f == t.owner) continue;
            if (count[f.ordinal()] > best) {
                best = count[f.ordinal()];
                challenger = f;
            }
        }
        // освобождение бизнеса от рэкета силовиками
        int law = count[Faction.UMVD.ordinal()] + count[Faction.UFSB.ordinal()] + count[Faction.UGIBDD.ordinal()];
        if (t.business && t.owner != null && law > 0 && ownerCount == 0 && best == 0) {
            t.liberation += law;
            if (t.liberation >= CAPTURE) {
                Faction old = t.owner;
                t.owner = null;
                t.liberation = 0;
                d.addNews("Полиция: «" + t.name + "» освобождён от рэкета группировки «" + old.title + "».");
                CityManager.changeSatisfaction(d, 2);
                CrimeSystem.notifyFaction(level, old, "[Тревога] Менты забрали у нас «" + t.name + "»!", ChatFormatting.RED);
                for (ServerPlayer p : players) {
                    PlayerRecord r = d.player(p.getUUID());
                    if (r.faction.isLawEnforcement()) {
                        r.points += 20;
                        r.civRep += 3;
                        p.sendSystemMessage(Component.literal("Бизнес «" + t.name + "» освобождён от рэкета. +20 служебных баллов.").withStyle(ChatFormatting.GREEN));
                    }
                }
                d.setDirty();
            }
            return;
        }
        t.liberation = Math.max(0, t.liberation - 2);
        if (challenger == null) {
            t.progress = Math.max(0, t.progress - 3);
            if (t.progress == 0) t.attacker = null;
            return;
        }
        if (ownerCount > 0) {
            // стычка: стороны цепляются друг к другу
            if (level.random.nextInt(4) == 0) brawl(level, box, t.owner, challenger);
            for (ServerPlayer p : players) p.displayClientMessage(Component.literal("⚔ Стычка за «" + t.name + "»: " + t.ownerName() + " против «" + challenger.title + "»")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (t.attacker != challenger) {
            t.attacker = challenger;
            t.progress = 0;
        }
        t.progress += best;
        for (ServerPlayer p : players) {
            p.displayClientMessage(Component.literal("Захват «" + t.name + "»: " + Math.min(100, t.progress * 100 / CAPTURE) + "% («" + challenger.title + "»)")
                    .withStyle(ChatFormatting.GOLD), true);
        }
        if (t.progress >= CAPTURE) capture(level, d, t, challenger, players);
    }

    private static void capture(ServerLevel level, GorodData d, Turf t, Faction winner, List<ServerPlayer> present) {
        Faction old = t.owner;
        t.owner = winner;
        t.progress = 0;
        t.attacker = null;
        t.lastWar = level.getGameTime();
        String news = t.business ? "Криминал: «" + t.name + "» перешёл под «крышу» — «" + winner.title + "»." : "Район «" + t.name + "» теперь контролируют «" + winner.title + "».";
        d.addNews(news);
        CrimeSystem.notifyFaction(level, winner, "[Свои] «" + t.name + "» — наша! Доход с точки: " + t.income + " ₽ в сутки в общак.", ChatFormatting.GOLD);
        if (old != null) CrimeSystem.notifyFaction(level, old, "[Тревога] Мы потеряли «" + t.name + "». Отбить!", ChatFormatting.RED);
        CrimeSystem.notifyFaction(level, Faction.UFSB, "[ОРМ] Передел сфер влияния: " + news, ChatFormatting.GRAY);
        for (ServerPlayer p : present) {
            PlayerRecord r = d.player(p.getUUID());
            if (r.faction == winner) {
                r.balance += 2000;
                r.crimRep += 4;
                r.points += 15;
                p.sendSystemMessage(Component.literal("Точка взята! +2000 ₽, +4 авторитета, +15 баллов.").withStyle(ChatFormatting.GOLD));
                Network.syncPlayer(p);
            }
        }
        // бизнесмен недоволен новой «крышей»
        if (t.business) {
            for (CitizenEntity c : level.getEntitiesOfClass(CitizenEntity.class, new AABB(t.pos).inflate(t.radius), c -> c.getFaction() == Faction.CIVILIAN)) {
                c.mood = Math.max(0, c.mood - 15);
                c.say(level.random.nextBoolean() ? "Опять новые «хозяева»... Сколько можно платить?" : "Господи, хоть бы полиция что-то сделала.", true);
                break;
            }
        }
        d.setDirty();
    }

    private static void brawl(ServerLevel level, AABB box, Faction a, Faction b) {
        List<CitizenEntity> as = level.getEntitiesOfClass(CitizenEntity.class, box, c -> c.getFaction() == a && c.getTarget() == null && !c.isJailed());
        List<CitizenEntity> bs = level.getEntitiesOfClass(CitizenEntity.class, box, c -> c.getFaction() == b && !c.isJailed());
        if (as.isEmpty() || bs.isEmpty()) return;
        for (int i = 0; i < as.size(); i++) as.get(i).setTarget(bs.get(i % bs.size()));
    }

    // ================================================================ ИИ войны

    /** Раз в 3 минуты одна из группировок едет «забирать» точку, хозяева высылают защитников. */
    private static void warAi(ServerLevel level, GorodData d) {
        RandomSource r = level.random;
        if (d.turfs.isEmpty() || r.nextInt(3) == 0) return;
        Faction[] gangs = {Faction.SKINHEADS, Faction.BIKERS, Faction.RUS_MAFIA, Faction.KAVKAZ_MAFIA};
        Faction g = gangs[r.nextInt(gangs.length)];
        List<Turf> targets = new ArrayList<>();
        for (Turf t : d.turfs) if (eligible(t, g) && t.owner != g && level.isLoaded(t.pos) && level.getGameTime() - t.lastWar > 6000) targets.add(t);
        if (targets.isEmpty()) return;
        Turf t = targets.get(r.nextInt(targets.size()));
        startAssault(level, d, t, g, null);
    }

    /** Отправить отряд на точку. initiator — игрок, позвавший пацанов (может быть null). */
    public static boolean startAssault(ServerLevel level, GorodData d, Turf t, Faction g, ServerPlayer initiator) {
        int sent = send(level, d, t, g, 4);
        if (sent == 0 && initiator == null) return false;
        t.lastWar = level.getGameTime();
        String where = "«" + t.name + "» (X " + t.pos.getX() + ", Z " + t.pos.getZ() + ")";
        CrimeSystem.notifyFaction(level, g, "[Сходняк] " + (initiator != null ? initiator.getName().getString() + " собирает пацанов: " : "")
                + "едем забирать " + where + ". Подтягивайся! Бойцов в пути: " + sent, ChatFormatting.GOLD);
        if (t.owner != null) {
            int def = send(level, d, t, t.owner, 3);
            CrimeSystem.notifyFaction(level, t.owner, "[Тревога] На " + where + " наезжает «" + g.title + "»! Защитников в пути: " + def, ChatFormatting.RED);
        }
        if (level.random.nextBoolean()) {
            CrimeSystem.notifyFaction(level, Faction.UMVD, "[Дежурная часть] Граждане сообщают: у объекта " + where + " собираются агрессивные молодые люди.", ChatFormatting.AQUA);
        }
        return true;
    }

    private static int send(ServerLevel level, GorodData d, Turf t, Faction f, int max) {
        AABB city = new AABB(d.minX - 16, level.getMinBuildHeight(), d.minZ - 16, d.maxX + 16, level.getMaxBuildHeight(), d.maxZ + 16);
        List<CitizenEntity> free = level.getEntitiesOfClass(CitizenEntity.class, city,
                c -> c.getFaction() == f && !c.isJailed() && c.order == CitizenEntity.Order.NONE && c.getTarget() == null);
        free.sort(Comparator.comparingDouble(c -> c.distanceToSqr(t.pos.getX(), t.pos.getY(), t.pos.getZ())));
        int n = 0;
        long until = level.getGameTime() + 2400;
        for (CitizenEntity c : free) {
            if (n >= max) break;
            c.order = CitizenEntity.Order.ASSAULT;
            c.holdPos = t.pos.offset(level.random.nextInt(5) - 2, 0, level.random.nextInt(5) - 2);
            c.orderUntil = until;
            c.commander = null;
            if (n == 0) c.say(t.owner == f ? "Пацаны, наших бьют! Все на «" + t.name + "»!" : "Поехали, разберёмся с «" + t.name + "».", true);
            n++;
        }
        return n;
    }

    /** Строка для HUD и чата: «Район: Парк Победы — «Скинхеды»». */
    public static String describe(GorodData d, BlockPos p) {
        Turf t = at(d, p);
        if (t == null) return "";
        return (t.business ? "Точка: " : "Район: ") + t.name + " — " + t.ownerName()
                + (t.attacker != null && t.progress > 0 ? " (захват «" + t.attacker.title + "» " + Math.min(100, t.progress * 100 / CAPTURE) + "%)" : "");
    }

    /** Кто «крышует» место (для ответа NPC в чате). */
    public static String whoRules(GorodData d, BlockPos p) {
        Turf t = at(d, p);
        if (t == null) return null;
        if (t.owner == null) return t.business ? "Сейчас никто не крышует. Тьфу-тьфу." : "Ничья территория. Пока.";
        return t.business ? "Платим «" + t.owner.title + "». А что делать..." : "Тут «" + t.owner.title + "» хозяйничают. Чужим не советую.";
    }

    static String placeName(String key) {
        return ChatResponder.placeName(key);
    }

    private Turfs() {
    }
}
