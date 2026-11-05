package ru.gorod.rusgorod.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.gorod.rusgorod.Config;
import ru.gorod.rusgorod.RusGorod;
import ru.gorod.rusgorod.data.GorodData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Пошаговая генерация города и воинской части рядом со спавном.
 * Сетка 7×7 кварталов по 24 блока, между ними дороги шириной 6 (4 полосы асфальта + 2 тротуара).
 * Точка появления оказывается на дороге между 1-й и 2-й колонной кварталов; центр города — примерно в 75 блоках.
 */
public final class CityGenerator {
    public static final int PLOT = 24, ROAD = 6, PERIOD = PLOT + ROAD, GRID = 9;
    public static final int SIZE = GRID * PERIOD + ROAD; // 276
    public static final String CITY_NAME = "Новоберёзовск";

    private static final Deque<Consumer<ServerLevel>> TASKS = new ArrayDeque<>();
    private static final List<long[]> FORCED = new ArrayList<>();
    private static boolean running;
    private static int total;

    enum Plot {
        PANEL5, PANEL9, STALINKA, PRIVATE, HOSTEL, SCHOOL, KINDERGARTEN, GARAGES, POLICE, GIBDD, SK, BIKERS, MARKET, PROK, ADMIN, FSB,
        COURT, WAREHOUSE, CAFE, SHOP, PARK, PARK2, FOUNTAIN, CHURCH, SIZO, RESTAURANT, UNIVERSITY, POST, HOSPITAL, BANK, MFC,
        VOENKOMAT, VOKZAL, FACTORY, DK, AZS, BAR, KIOSKS, SHAWARMA, AUTOSERVICE, SAUNA, PAWNSHOP, STADIUM
    }

    static final Plot[][] LAYOUT = {
            {Plot.PRIVATE, Plot.PRIVATE, Plot.PANEL5, Plot.KIOSKS, Plot.PANEL5, Plot.SCHOOL, Plot.PANEL5, Plot.GARAGES, Plot.AZS},
            {Plot.PRIVATE, Plot.PANEL9, Plot.PANEL5, Plot.SHAWARMA, Plot.POLICE, Plot.GIBDD, Plot.SK, Plot.PANEL5, Plot.BIKERS},
            {Plot.PRIVATE, Plot.STALINKA, Plot.MARKET, Plot.PROK, Plot.ADMIN, Plot.FSB, Plot.COURT, Plot.AUTOSERVICE, Plot.WAREHOUSE},
            {Plot.PANEL9, Plot.PANEL5, Plot.CAFE, Plot.SHOP, Plot.PARK, Plot.CHURCH, Plot.SIZO, Plot.PANEL5, Plot.FACTORY},
            {Plot.PANEL9, Plot.SAUNA, Plot.RESTAURANT, Plot.UNIVERSITY, Plot.FOUNTAIN, Plot.BANK, Plot.MFC, Plot.HOSTEL, Plot.PANEL9},
            {Plot.PANEL5, Plot.BAR, Plot.HOSPITAL, Plot.DK, Plot.POST, Plot.VOENKOMAT, Plot.PAWNSHOP, Plot.PANEL5, Plot.STALINKA},
            {Plot.STALINKA, Plot.PANEL5, Plot.KINDERGARTEN, Plot.PARK2, Plot.STADIUM, Plot.PANEL9, Plot.PANEL5, Plot.PANEL9, Plot.PRIVATE},
            {Plot.PANEL5, Plot.PANEL9, Plot.PANEL5, Plot.PANEL9, Plot.PANEL5, Plot.STALINKA, Plot.PANEL5, Plot.PANEL5, Plot.PRIVATE},
            {Plot.PRIVATE, Plot.PRIVATE, Plot.PANEL5, Plot.PANEL9, Plot.VOKZAL, Plot.STALINKA, Plot.PANEL5, Plot.PRIVATE, Plot.PRIVATE},
    };

    public static boolean isRunning() {
        return running;
    }

    public static void reset() {
        TASKS.clear();
        FORCED.clear();
        running = false;
        total = 0;
    }

    public static int progressPercent() {
        return total == 0 ? 0 : (int) (100 - 100.0 * TASKS.size() / total);
    }

    public static void start(ServerLevel level, GorodData d) {
        if (running) return;
        BlockPos spawn = level.getSharedSpawnPos();
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        int gy = Mth.clamp(h - 1, level.getSeaLevel(), 110);
        RandomSource r = level.random;

        d.groundY = gy;
        // точка спавна — на асфальте вертикальной дороги между 1-й и 2-й колонной
        d.minX = spawn.getX() - PERIOD - 3 + Config.CITY_OFFSET_X.get() - 8;
        d.minZ = spawn.getZ() - SIZE / 2;
        d.maxX = d.minX + SIZE - 1;
        d.maxZ = d.minZ + SIZE - 1;
        int centerZ = d.minZ + PERIOD * 5 + 3; // дорога между 5-м и 6-м рядом — выезд к воинской части
        d.baseMinX = d.maxX + Config.BASE_DISTANCE.get();
        d.baseMinZ = centerZ - 31;
        d.baseMaxX = d.baseMinX + MilitaryBase.SIZE - 1;
        d.baseMaxZ = d.baseMinZ + MilitaryBase.SIZE - 1;
        d.locs.clear();
        d.homes.clear();
        d.nodes.clear();
        d.sizoCells.clear();
        d.ivsCells.clear();
        d.barracks.clear();
        d.roadSpots.clear();
        d.turfs.clear();
        for (int i = 0; i <= GRID; i++)
            for (int j = 0; j <= GRID; j++)
                d.nodes.add(new BlockPos(d.minX + 3 + PERIOD * i, gy + 1, d.minZ + 3 + PERIOD * j));

        final int minX = d.minX, minZ = d.minZ, maxX = d.maxX, maxZ = d.maxZ;
        final int bx = d.baseMinX, bz = d.baseMinZ;
        TASKS.clear();

        // 0. Игроков в зоне стройки — на безопасную дорогу
        final BlockPos safe = new BlockPos(minX + PERIOD + 3, gy + 1, spawn.getZ());
        TASKS.add(l -> {
            for (ServerPlayer p : l.players()) {
                if (p.getX() >= minX - 8 && p.getX() <= maxX + 8 && p.getZ() >= minZ - 8 && p.getZ() <= maxZ + 8) {
                    p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 1200, 0));
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1200, 4));
                }
            }
        });
        // 1. Выравнивание территории города (с запасом по краям)
        for (int cx = minX - 6; cx <= maxX + 6; cx += 16)
            for (int cz = minZ - 6; cz <= maxZ + 6; cz += 16) {
                final int x0 = cx, z0 = cz;
                TASKS.add(l -> flatten(l, x0, z0, Math.min(x0 + 15, maxX + 6), Math.min(z0 + 15, maxZ + 6), gy));
            }
        // 2. Коридор дороги к воинской части
        for (int cx = maxX + 7; cx < bx; cx += 16) {
            final int x0 = cx;
            TASKS.add(l -> flatten(l, x0, centerZ - 10, Math.min(x0 + 15, bx - 1), centerZ + 10, gy));
        }
        // 3. Территория части
        for (int cx = bx - 4; cx <= bx + MilitaryBase.SIZE + 3; cx += 16)
            for (int cz = bz - 4; cz <= bz + MilitaryBase.SIZE + 3; cz += 16) {
                final int x0 = cx, z0 = cz;
                TASKS.add(l -> flatten(l, x0, z0, Math.min(x0 + 15, bx + MilitaryBase.SIZE + 3), Math.min(z0 + 15, bz + MilitaryBase.SIZE + 3), gy));
            }
        // 4. Дороги города
        for (int i = 0; i <= GRID; i++) {
            final int xs = minX + PERIOD * i;
            TASKS.add(l -> roads(l, d, xs, Math.min(xs + PERIOD - 1, maxX), minZ, maxZ, gy));
        }
        // 5. Застройка кварталов
        for (int row = 0; row < GRID; row++)
            for (int col = 0; col < GRID; col++) {
                final Plot plot = LAYOUT[row][col];
                final int px = minX + ROAD + col * PERIOD, pz = minZ + ROAD + row * PERIOD;
                TASKS.add(l -> buildPlot(l, d, plot, px, pz, gy, r));
            }
        // 6. Железная дорога вдоль южной окраины
        TASKS.add(l -> Buildings.railway(new B(l), d, minX, maxX, maxZ + 3, gy, minX + ROAD + 4 * PERIOD + 11));
        TASKS.add(l -> Turfs.setup(d));
        // 7. Дорога к части, въездная стела и пост ДПС
        TASKS.add(l -> {
            B b = new B(l);
            for (int x = maxX + 1; x < bx; x++) {
                for (int dz = 0; dz <= 5; dz++)
                    b.set(x, gy, centerZ - 3 + dz, dz == 0 || dz == 5 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());
                if (x % 4 == 0) b.set(x, gy, centerZ - 1, Blocks.WHITE_CONCRETE);
                if ((x - maxX) % 20 == 0) b.streetLamp(x, gy + 1, centerZ - 4);
            }
            Buildings.dpsPost(b, d, maxX + 6, centerZ + 4, gy);
            // стела «Новоберёзовск»
            for (int y = gy + 1; y <= gy + 5; y++) b.set(maxX + 3, y, centerZ - 5, Blocks.WHITE_CONCRETE);
            b.fill(maxX + 2, gy + 6, centerZ - 5, maxX + 4, gy + 6, centerZ - 5, Blocks.RED_CONCRETE);
            b.sign(maxX + 3, gy + 4, centerZ - 4, net.minecraft.core.Direction.SOUTH, CITY_NAME.toUpperCase(), "основан", "в 1932 году");
            b.sign(maxX + 3, gy + 3, centerZ - 4, net.minecraft.core.Direction.SOUTH, "Добро", "пожаловать!");
        });
        // 8. Воинская часть
        TASKS.add(l -> MilitaryBase.perimeter(new B(l), d, bx, bz, gy));
        TASKS.add(l -> MilitaryBase.hqAndParade(new B(l), d, bx, bz, gy));
        TASKS.add(l -> MilitaryBase.barracks(new B(l), d, bx, bz, gy));
        TASKS.add(l -> MilitaryBase.messAndDepot(new B(l), d, bx, bz, gy));
        TASKS.add(l -> MilitaryBase.range(new B(l), d, bx, bz, gy));
        // 9. Заселение и завершение
        TASKS.add(l -> CityManager.spawnInitial(l, d));
        TASKS.add(l -> finish(l, d, safe));

        total = TASKS.size();
        running = true;
        RusGorod.LOGGER.info("[RusGorod] Генерация города {} у {}, {} (уровень {}), задач: {}", CITY_NAME, minX, minZ, gy, total);
        broadcast(level, "§6[РусГород] §fНачинается строительство города «" + CITY_NAME + "» вокруг точки появления. Это займёт пару минут...");
    }

    public static void tick(ServerLevel level) {
        if (!running) return;
        int n = Config.BLOCKS_PER_TICK_TASKS.get();
        for (int i = 0; i < n && !TASKS.isEmpty(); i++) {
            Consumer<ServerLevel> task = TASKS.poll();
            try {
                task.accept(level);
            } catch (Exception e) {
                RusGorod.LOGGER.error("[RusGorod] Ошибка задачи генерации", e);
            }
        }
        if (level.getGameTime() % 100 == 0 && !TASKS.isEmpty()) {
            for (var p : level.players())
                p.displayClientMessage(Component.literal("Строительство города: " + progressPercent() + "%").withStyle(ChatFormatting.GOLD), true);
        }
        if (TASKS.isEmpty()) running = false;
    }

    private static void force(ServerLevel l, int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        for (long[] f : FORCED) if (f[0] == cx && f[1] == cz) return;
        l.setChunkForced(cx, cz, true);
        FORCED.add(new long[]{cx, cz});
    }

    static void flatten(ServerLevel l, int x0, int z0, int x1, int z1, int gy) {
        force(l, x0, z0);
        force(l, x1, z1);
        B b = new B(l);
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                int top = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                for (int y = gy + 1; y <= Math.max(top, gy + 1); y++) {
                    if (!b.get(x, y, z).isAir()) b.set(x, y, z, air);
                }
                b.set(x, gy, z, grass);
                for (int y = gy - 1; y >= gy - 3; y--) b.set(x, y, z, dirt);
                for (int y = gy - 4; y >= gy - 30; y--) {
                    BlockState s = b.get(x, y, z);
                    if (s.isAir() || !s.getFluidState().isEmpty()) b.set(x, y, z, stone);
                    else break;
                }
            }
    }

    static void roads(ServerLevel l, GorodData d, int xa, int xb, int minZ, int maxZ, int gy) {
        B b = new B(l);
        BlockState asphalt = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState walk = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState curb = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState();
        for (int x = xa; x <= xb; x++)
            for (int z = minZ; z <= maxZ; z++) {
                int rx = Math.floorMod(x - d.minX, PERIOD), rz = Math.floorMod(z - d.minZ, PERIOD);
                boolean inV = rx < ROAD, inH = rz < ROAD;
                if (!inV && !inH) continue;
                boolean asph = (inV && rx >= 1 && rx <= 4) || (inH && rz >= 1 && rz <= 4);
                BlockState s = asph ? asphalt : walk;
                // бордюр по краю тротуара
                if (!asph && ((inV && !inH && (rx == 0 || rx == 5)) && (rz % 2 == 0))) s = curb;
                // «зебра» у перекрёстков
                if (inV && !inH && asph && (rz == 7 || rz == PERIOD - 2) && rx % 2 == 1) s = white;
                if (inH && !inV && asph && (rx == 7 || rx == PERIOD - 2) && rz % 2 == 1) s = white;
                // осевая разметка
                if (inV && !inH && (rx == 2) && rz % 4 == 0 && rz > 8 && rz < PERIOD - 3) s = white;
                if (inH && !inV && (rz == 2) && rx % 4 == 0 && rx > 8 && rx < PERIOD - 3) s = white;
                b.set(x, gy, z, s);
                if (rx == 0 && rz == 0) b.streetLamp(x, gy + 1, z);
                if (rx == 5 && rz == 5) b.streetLamp(x, gy + 1, z);
                if (inV && !inH && rx == 0 && rz == PERIOD / 2) d.roadSpots.add(new BlockPos(x, gy + 1, z));
            }
    }

    static void buildPlot(ServerLevel l, GorodData d, Plot plot, int px, int pz, int gy, RandomSource r) {
        B b = new B(l);
        switch (plot) {
            case PANEL5 -> Buildings.panelHouse(b, d, px, pz, gy, 5, false, r);
            case PANEL9 -> Buildings.panelHouse(b, d, px, pz, gy, 9, true, r);
            case STALINKA -> Buildings.stalinka(b, d, px, pz, gy);
            case PRIVATE -> Buildings.privateHouses(b, d, px, pz, gy, r);
            case HOSTEL -> Buildings.hostel(b, d, px, pz, gy);
            case SCHOOL -> Buildings.school(b, d, px, pz, gy);
            case KINDERGARTEN -> Buildings.kindergarten(b, d, px, pz, gy);
            case GARAGES -> Buildings.garages(b, d, px, pz, gy, r);
            case POLICE -> Buildings.police(b, d, px, pz, gy);
            case GIBDD -> Buildings.gibdd(b, d, px, pz, gy);
            case SK -> Buildings.sk(b, d, px, pz, gy);
            case BIKERS -> Buildings.bikers(b, d, px, pz, gy);
            case MARKET -> Buildings.market(b, d, px, pz, gy, r);
            case PROK -> Buildings.prokuratura(b, d, px, pz, gy);
            case ADMIN -> Buildings.admin(b, d, px, pz, gy);
            case FSB -> Buildings.fsb(b, d, px, pz, gy);
            case COURT -> Buildings.court(b, d, px, pz, gy);
            case WAREHOUSE -> Buildings.warehouse(b, d, px, pz, gy);
            case CAFE -> Buildings.cafe(b, d, px, pz, gy);
            case SHOP -> Buildings.shop(b, d, px, pz, gy);
            case PARK -> Buildings.park(b, d, px, pz, gy, r, true);
            case PARK2 -> Buildings.park(b, d, px, pz, gy, r, false);
            case FOUNTAIN -> Buildings.fountainSquare(b, d, px, pz, gy);
            case CHURCH -> Buildings.church(b, d, px, pz, gy);
            case SIZO -> Buildings.sizo(b, d, px, pz, gy);
            case RESTAURANT -> Buildings.restaurant(b, d, px, pz, gy);
            case UNIVERSITY -> Buildings.university(b, d, px, pz, gy);
            case POST -> Buildings.post(b, d, px, pz, gy);
            case HOSPITAL -> Buildings.hospital(b, d, px, pz, gy);
            case BANK -> Buildings.bank(b, d, px, pz, gy);
            case MFC -> Buildings.mfc(b, d, px, pz, gy);
            case VOENKOMAT -> Buildings.voenkomat(b, d, px, pz, gy);
            case VOKZAL -> Buildings.vokzal(b, d, px, pz, gy);
            case FACTORY -> Buildings.factory(b, d, px, pz, gy);
            case DK -> Buildings.dk(b, d, px, pz, gy);
            case AZS -> Buildings.azs(b, d, px, pz, gy);
            case BAR -> Buildings.bar(b, d, px, pz, gy);
            case KIOSKS -> Buildings.kiosks(b, d, px, pz, gy);
            case SHAWARMA -> Buildings.shawarma(b, d, px, pz, gy);
            case AUTOSERVICE -> Buildings.autoservice(b, d, px, pz, gy);
            case SAUNA -> Buildings.sauna(b, d, px, pz, gy);
            case PAWNSHOP -> Buildings.pawnshop(b, d, px, pz, gy);
            case STADIUM -> Buildings.stadium(b, d, px, pz, gy);
        }
    }

    private static void finish(ServerLevel l, GorodData d, BlockPos safe) {
        for (long[] f : FORCED) l.setChunkForced((int) f[0], (int) f[1], false);
        FORCED.clear();
        d.generated = true;
        d.lastDay = l.getDayTime() / 24000L;
        d.addNews("Город «" + CITY_NAME + "» живёт своей жизнью. Глава округа провёл планёрку.");
        d.setDirty();
        BlockPos sq = d.loc("square");
        l.setDefaultSpawnPos(sq, 0.0f);
        // игроки, оказавшиеся внутри стен, — на площадь
        for (ServerPlayer p : l.players()) {
            BlockPos pp = p.blockPosition();
            if (d.inCity(pp) && (!l.getBlockState(pp).isAir() || !l.getBlockState(pp.above()).isAir() || pp.getY() < d.groundY)) {
                p.teleportTo(l, sq.getX() + 0.5, sq.getY(), sq.getZ() + 0.5, p.getYRot(), p.getXRot());
            }
        }
        broadcast(l, "§6[РусГород] §aГород «" + CITY_NAME + "» построен! §fПлощадь: X " + sq.getX() + ", Z " + sq.getZ()
                + ". Воинская часть: X " + (d.baseMinX + 32) + ", Z " + (d.baseMinZ + 32) + ". §eКлавиша G — меню. Пишите жителям в чат — они ответят.");
        RusGorod.LOGGER.info("[RusGorod] Город построен.");
    }

    private static void broadcast(ServerLevel level, String s) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(s), false);
    }

    private CityGenerator() {
    }
}
