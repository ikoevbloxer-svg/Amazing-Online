package ru.gorod.rusgorod.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import ru.gorod.rusgorod.Config;
import ru.gorod.rusgorod.data.GorodData;
import ru.gorod.rusgorod.data.PlayerRecord;
import ru.gorod.rusgorod.entity.CitizenEntity;
import ru.gorod.rusgorod.entity.Profession;
import ru.gorod.rusgorod.entity.ai.ActivityPlanner;
import ru.gorod.rusgorod.faction.Faction;
import ru.gorod.rusgorod.faction.FactionService;
import ru.gorod.rusgorod.law.Article;
import ru.gorod.rusgorod.law.CrimeSystem;
import ru.gorod.rusgorod.net.Network;
import ru.gorod.rusgorod.registry.ModBlocks;
import ru.gorod.rusgorod.registry.ModEntities;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Жизнь города: сутки, бюджет, зарплаты, настроение, митинги, пополнение фракций, мусор. */
public final class CityManager {

    public static void tick(ServerLevel level) {
        GorodData d = GorodData.get(level);
        if (!d.generated) {
            if (CityGenerator.isRunning()) {
                CityGenerator.tick(level);
            } else if (Config.GENERATE_CITY.get() && level.getGameTime() > 60 && !level.players().isEmpty()) {
                CityGenerator.start(level, d);
            }
            return;
        }
        long gt = level.getGameTime();
        long dayTime = level.getDayTime();
        long day = dayTime / 24000L;
        long t = dayTime % 24000L;

        if (gt % 20 == 0 && day != d.lastDay) {
            d.lastDay = day;
            daily(level, d, day);
        }
        if (gt % 600 == 0) CrimeSystem.tickCases(level, d);
        if (gt % 1200 == 0) litter(level, d);
        if (gt % 2400 == 0) maintainPopulation(level, d);
        if (gt % 20 == 0 && t >= 4000 && t < 4020) rallyCheck(level, d);
        if (gt % 40 == 0) {
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) Network.syncPlayer(p);
        }
        if (gt % 600 == 300) Network.broadcastRanks(level.getServer());
    }

    public static void changeSatisfaction(GorodData d, double delta) {
        d.satisfaction = Math.max(0, Math.min(100, d.satisfaction + delta));
        d.setDirty();
    }

    // ============================================================ сутки

    private static void daily(ServerLevel level, GorodData d, long day) {
        int civilians = d.npcCount(Faction.CIVILIAN);
        long taxes = 30000 + civilians * 600L;
        taxes = Math.round(taxes * (0.5 + d.satisfaction / 200.0));
        d.budget += taxes;
        int stateNpc = 0;
        for (Faction f : Faction.values()) if (f == Faction.GOV) stateNpc += d.npcCount(f);
        d.budget -= stateNpc * 1500L; // муниципальные служащие — из местного бюджета; остальные — федеральный

        for (Map.Entry<UUID, PlayerRecord> e : d.players.entrySet()) {
            PlayerRecord r = e.getValue();
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            // Денежное довольствие и доля из общака
            if (r.faction.isState()) {
                long pay = FactionService.dailyIncome(r);
                boolean municipal = r.faction == Faction.GOV;
                if (!municipal || d.budget > 0) {
                    r.balance += pay;
                    if (municipal) d.budget -= pay;
                    r.points += 2; // выслуга
                    if (p != null) p.sendSystemMessage(Component.literal("Начислено денежное довольствие: " + pay + " ₽ ("
                            + (municipal ? "местный бюджет" : "федеральный бюджет") + ")").withStyle(ChatFormatting.GREEN));
                } else if (p != null) {
                    p.sendSystemMessage(Component.literal("Задержка зарплаты: в местном бюджете нет средств (ст. 236 ТК РФ — компенсация за задержку).").withStyle(ChatFormatting.RED));
                }
                if (r.faction.isLegalProfession()) r.legalExpDays++;
            } else if (r.faction.isCriminal()) {
                long funds = d.fundsOf(r.faction);
                long share = Math.min(funds, Math.round(funds * 0.04 * (r.rank + 1)));
                if (share > 0) {
                    r.balance += share;
                    d.addFunds(r.faction, -share);
                    if (p != null) p.sendSystemMessage(Component.literal("Твоя доля: " + share + " ₽").withStyle(ChatFormatting.GOLD));
                }
            }
            // Неуплата штрафа в срок — ст. 20.25 КоАП
            if (r.fineDebt > 0 && r.fineDueDay >= 0 && day > r.fineDueDay && !r.wanted.contains(Article.KOAP_20_25)) {
                r.wanted.add(Article.KOAP_20_25);
                r.fineDebt *= 2;
                if (p != null) p.sendSystemMessage(Component.literal("Штраф не оплачен в срок: сумма удвоена (" + r.fineDebt + " ₽), вы в розыске по ст. 20.25 КоАП РФ. Оплатите и сдайтесь.").withStyle(ChatFormatting.RED));
            }
        }

        // доход с территорий и «крыши» в общак
        for (Turfs.Turf t : d.turfs) {
            if (t.owner == null) continue;
            d.addFunds(t.owner, t.income);
            if (t.business) changeSatisfaction(d, -0.3);
        }
        if (d.budget < 0) {
            changeSatisfaction(d, -6);
            d.addNews("Бюджет: дефицит. Бюджетникам задерживают зарплату. Горожане недовольны.");
        }
        changeSatisfaction(d, 1.5 - d.crimeIndex / 15.0);
        d.crimeIndex *= 0.75;
        d.addNews("Сводка за сутки " + day + ": бюджет " + d.budget + " ₽, индекс преступности " + Math.round(d.crimeIndex)
                + ", доверие к власти " + Math.round(d.satisfaction) + "%.");
        d.setDirty();
    }

    // ============================================================ митинги (ФЗ № 54-ФЗ)

    private static void rallyCheck(ServerLevel level, GorodData d) {
        if (d.rallyUntil > level.getGameTime()) return;
        if (d.satisfaction >= 45) return;
        if (level.random.nextDouble() > (50 - d.satisfaction) / 30.0) return;
        d.rallyNotified = level.random.nextDouble() < 0.6;
        d.rallyUntil = level.getGameTime() + 8000;
        BlockPos sq = d.loc("square");
        List<CitizenEntity> civs = level.getEntitiesOfClass(CitizenEntity.class, new AABB(sq).inflate(200),
                c -> c.getFaction() == Faction.CIVILIAN && !c.isJailed());
        civs.sort((a, b) -> Integer.compare(a.mood, b.mood)); // первыми выходят самые недовольные
        int n = Math.min(civs.size(), 6 + level.random.nextInt(9));
        for (int i = 0; i < n; i++) civs.get(i).protestUntil = d.rallyUntil;
        String news = d.rallyNotified
                ? "У здания администрации проходит митинг (уведомление подано в порядке ст. 7 ФЗ № 54-ФЗ). Полиция обеспечивает порядок."
                : "У здания администрации — несогласованная акция! Полиция требует разойтись (ст. 20.2 КоАП РФ).";
        d.addNews(news);
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("[Новости] " + news).withStyle(ChatFormatting.YELLOW), false);
        CrimeSystem.notifyFaction(level, Faction.GOV, "[Администрация] Горожане вышли на площадь. Выйдите к людям — задание «Согласование публичного мероприятия».", ChatFormatting.GOLD);
        CrimeSystem.notifyFaction(level, Faction.UMVD, "[Дежурная часть] Всем свободным нарядам — на площадь у администрации.", ChatFormatting.AQUA);
        d.setDirty();
    }

    // ============================================================ население

    public static int target(Faction f) {
        return Math.max(1, (int) Math.round(f.defaultNpcCount * Config.POPULATION_MULTIPLIER.get()));
    }

    public static CitizenEntity spawnNpc(ServerLevel level, GorodData d, Faction f, int rank, BlockPos pos) {
        CitizenEntity e = ModEntities.CITIZEN.get().create(level);
        if (e == null) return null;
        e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        e.setup(f, rank, level.random, d);
        level.addFreshEntity(e);
        d.npcs.put(e.getUUID(), f);
        d.setDirty();
        return e;
    }

    /** Пирамида званий: чем выше звание, тем меньше людей. */
    private static int randomRank(Faction f, RandomSource r) {
        int max = f.rankCount() - 1;
        if (max <= 0) return 0;
        float x = r.nextFloat();
        return Math.min(max - 1, (int) (x * x * max));
    }

    private static BlockPos spawnPos(GorodData d, Faction f, RandomSource r) {
        if (f == Faction.CIVILIAN) return d.randomHome(r);
        if (f == Faction.ARMY) return d.barracks.isEmpty() ? d.loc("army_plac") : d.barracks.get(r.nextInt(d.barracks.size()));
        return ActivityPlanner.around(d.loc(f.hqKey), r, 2);
    }

    public static void spawnInitial(ServerLevel level, GorodData d) {
        RandomSource r = level.random;
        for (Faction f : Faction.values()) {
            int n = target(f);
            for (int i = 0; i < n; i++) {
                // Руководитель — один, заместитель — один, остальные — пирамида званий
                int rank;
                if (f == Faction.CIVILIAN) rank = 0;
                else if (i == 0) rank = f == Faction.UMVD || f == Faction.UFSIN || f == Faction.UGIBDD || f == Faction.UFSB || f == Faction.SK || f == Faction.ARMY || f == Faction.PROK || f == Faction.COURT
                        ? f.topRank() - 1 : f.topRank(); // генеральские должности — в области, в городе — полковник
                else if (i == 1 && n > 3) rank = Math.max(0, f.topRank() - 2);
                else rank = randomRank(f, r);
                BlockPos pos = spawnPos(d, f, r);
                level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                CitizenEntity c = spawnNpc(level, d, f, rank, pos);
                if (c != null && f == Faction.CIVILIAN && i < Profession.KEY.length) c.setProfession(Profession.KEY[i], d);
            }
        }
    }

    /** Пополнение фракций: найм жителей (на госслужбу — проверенных, в банды — склонных к криминалу) или «переезд» новых. */
    private static void maintainPopulation(ServerLevel level, GorodData d) {
        RandomSource r = level.random;
        for (Faction f : Faction.values()) {
            int target = target(f);
            int cur = d.npcCount(f);
            if (cur >= target) continue;
            if (f != Faction.CIVILIAN && f != Faction.ARMY && f != Faction.COURT && r.nextBoolean()) {
                CitizenEntity cand = ActivityPlanner.findNear(level, d.loc("square"), 150, c -> c.getFaction() == Faction.CIVILIAN
                        && !c.isJailed() && !c.isWanted() && !c.isProtesting() && c.profession != Profession.PROFESSOR && c.profession != Profession.DOCTOR
                        && (f.isState() ? (!c.everConvicted && c.criminality < 0.3f) : (c.everConvicted || c.criminality > 0.55f || c.mood < 20)));
                if (cand != null) {
                    cand.changeFaction(f, 0, d);
                    d.addNews(f.isState() ? "Кадры: житель " + cand.fullName() + " поступил на службу — " + f.title + "."
                            : "Слухи: " + cand.fullName() + " связался с «" + f.title + "».");
                    continue;
                }
            }
            BlockPos pos = spawnPos(d, f, r);
            if (!level.isLoaded(pos)) continue;
            spawnNpc(level, d, f, f == Faction.CIVILIAN ? 0 : randomRank(f, r), pos);
            if (f == Faction.CIVILIAN) d.addNews("В город переехала новая семья.");
        }
    }

    private static void litter(ServerLevel level, GorodData d) {
        if (d.roadSpots.isEmpty()) return;
        for (int i = 0; i < 4; i++) {
            BlockPos p = d.roadSpots.get(level.random.nextInt(d.roadSpots.size()));
            if (level.isLoaded(p) && level.getBlockState(p).isAir() && level.random.nextFloat() < 0.6f) {
                level.setBlock(p, ModBlocks.LITTER.get().defaultBlockState(), 3);
            }
        }
    }

    private CityManager() {
    }
}
