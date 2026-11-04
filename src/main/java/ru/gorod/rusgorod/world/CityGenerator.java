package ru.gorod.rusgorod.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
 * Пошаговая (по несколько задач за тик) генерация города и воинской части рядом со спавном.
 * Сетка 5×5 участков по 24 блока, между ними дороги шириной 6 (4 полосы асфальта + 2 тротуара).
 */
public final class CityGenerator {
    public static final int PLOT = 24, ROAD = 6, PERIOD = PLOT + ROAD, GRID = 5;
    public static final int SIZE = GRID * PERIOD + ROAD; // 156
    public static final String CITY_NAME = "Новоберёзовск";

    private static final Deque<Consumer<ServerLevel>> TASKS = new ArrayDeque<>();
    private static final List<long[]> FORCED = new ArrayList<>();
    private static boolean running;
    private static int total;

    enum Plot {PANEL5, PANEL9, SCHOOL, GARAGES, POLICE, GIBDD, SK, BIKERS, MARKET, PROK, ADMIN, FSB, WAREHOUSE, CAFE, SHOP, PARK, CHURCH, SIZO, RESTAURANT, UNIVERSITY, POST}

    static final Plot[][] LAYOUT = {
            {Plot.PANEL9, Plot.PANEL5, Plot.SCHOOL, Plot.PANEL5, Plot.GARAGES},
            {Plot.PANEL5, Plot.POLICE, Plot.GIBDD, Plot.SK, Plot.BIKERS},
            {Plot.MARKET, Plot.PROK, Plot.ADMIN, Plot.FSB, Plot.WAREHOUSE},
            {Plot.CAFE, Plot.SHOP, Plot.PARK, Plot.CHURCH, Plot.SIZO},
            {Plot.PANEL9, Plot.RESTAURANT, Plot.UNIVERSITY, Plot.POST, Plot.PANEL5},
    };

    public static boolean isRunning() {
        return running;
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
        d.minX = spawn.getX() + Config.CITY_OFFSET_X.get();
        d.minZ = spawn.getZ() - SIZE / 2;
        d.maxX = d.minX + SIZE - 1;
        d.maxZ = d.minZ + SIZE - 1;
        int centerZ = d.minZ + SIZE / 2;
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
        for (int i = 0; i <= GRID; i++)
            for (int j = 0; j <= GRID; j++)
                d.nodes.add(new BlockPos(d.minX + 3 + PERIOD * i, gy + 1, d.minZ + 3 + PERIOD * j));

        final int minX = d.minX, minZ = d.minZ, maxX = d.maxX, maxZ = d.maxZ;
        final int bx = d.baseMinX, bz = d.baseMinZ;
        TASKS.clear();

        // 1. Выравнивание территории города (с запасом по краям)
        for (int cx = minX - 4; cx <= maxX + 4; cx += 16)
            for (int cz = minZ - 4; cz <= maxZ + 4; cz += 16) {
                final int x0 = cx, z0 = cz;
                TASKS.add(l -> flatten(l, x0, z0, Math.min(x0 + 15, maxX + 4), Math.min(z0 + 15, maxZ + 4), gy));
            }
        // 2. Коридор дороги к воинской части
        for (int cx = maxX + 5; cx < bx; cx += 16) {
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
        // 5. Застройка участков
        for (int row = 0; row < GRID; row++)
            for (int col = 0; col < GRID; col++) {
                final Plot plot = LAYOUT[row][col];
                final int px = minX + ROAD + col * PERIOD, pz = minZ + ROAD + row * PERIOD;
                TASKS.add(l -> buildPlot(l, d, plot, px, pz, gy, r));
            }
        // 6. Дорога к части и пост ДПС
        TASKS.add(l -> {
            B b = new B(l);
            for (int x = maxX + 1; x < bx; x++) {
                for (int dz = -3; dz <= 3; dz++)
                    b.set(x, gy, centerZ + dz, Math.abs(dz) == 3 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());
                if (x % 4 == 0) b.set(x, gy, centerZ, Blocks.WHITE_CONCRETE);
                if ((x - maxX) % 20 == 0) b.streetLamp(x, gy + 1, centerZ - 4);
            }
            Buildings.dpsPost(b, d, maxX + 6, centerZ + 4, gy);
            b.sign(maxX + 3, gy + 1, centerZ - 4, net.minecraft.core.Direction.SOUTH, CITY_NAME.toUpperCase(), "Добро", "пожаловать!");
            b.set(maxX + 3, gy + 1, centerZ - 5, Blocks.WHITE_CONCRETE);
        });
        // 7. Воинская часть
        TASKS.add(l -> MilitaryBase.perimeter(new B(l), d, bx, bz, gy));
        TASKS.add(l -> MilitaryBase.hqAndParade(new B(l), d, bx, bz, gy));
        TASKS.add(l -> MilitaryBase.barracks(new B(l), d, bx, bz, gy));
        TASKS.add(l -> MilitaryBase.messAndDepot(new B(l), d, bx, bz, gy));
        TASKS.add(l -> MilitaryBase.range(new B(l), d, bx, bz, gy));
        // 8. Заселение и завершение
        TASKS.add(l -> CityManager.spawnInitial(l, d));
        TASKS.add(l -> finish(l, d));

        total = TASKS.size();
        running = true;
        RusGorod.LOGGER.info("[RusGorod] Генерация города {} у {}, {} (уровень {}), задач: {}", CITY_NAME, minX, minZ, gy, total);
        l(level, "§6[РусГород] §fНачинается строительство города «" + CITY_NAME + "» рядом с точкой появления. Это займёт около минуты...");
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
        BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState();
        for (int x = xa; x <= xb; x++)
            for (int z = minZ; z <= maxZ; z++) {
                int rx = Math.floorMod(x - d.minX, PERIOD), rz = Math.floorMod(z - d.minZ, PERIOD);
                boolean inV = rx < ROAD, inH = rz < ROAD;
                if (!inV && !inH) continue;
                boolean asph = (inV && rx >= 1 && rx <= 4) || (inH && rz >= 1 && rz <= 4);
                BlockState s = asph ? asphalt : walk;
                // «зебра» у перекрёстков
                if (inV && !inH && asph && (rz == 7 || rz == PERIOD - 2) && rx % 2 == 1) s = white;
                if (inH && !inV && asph && (rx == 7 || rx == PERIOD - 2) && rz % 2 == 1) s = white;
                // осевая разметка
                if (inV && !inH && (rx == 2) && rz % 4 == 0 && rz > 8 && rz < PERIOD - 3) s = white;
                if (inH && !inV && (rz == 2) && rx % 4 == 0 && rx > 8 && rx < PERIOD - 3) s = white;
                b.set(x, gy, z, s);
                // фонари на углах перекрёстков, урны на тротуарах
                if (rx == 0 && rz == 0) b.streetLamp(x, gy + 1, z);
                if (inV && !inH && rx == 0 && rz == PERIOD / 2) {
                    d.roadSpots.add(new BlockPos(x, gy + 1, z));
                }
            }
    }

    static void buildPlot(ServerLevel l, GorodData d, Plot plot, int px, int pz, int gy, RandomSource r) {
        B b = new B(l);
        switch (plot) {
            case PANEL5 -> Buildings.panelHouse(b, d, px, pz, gy, 5, false, r);
            case PANEL9 -> Buildings.panelHouse(b, d, px, pz, gy, 9, true, r);
            case SCHOOL -> Buildings.school(b, d, px, pz, gy);
            case GARAGES -> Buildings.garages(b, d, px, pz, gy, r);
            case POLICE -> Buildings.police(b, d, px, pz, gy);
            case GIBDD -> Buildings.gibdd(b, d, px, pz, gy);
            case SK -> Buildings.sk(b, d, px, pz, gy);
            case BIKERS -> Buildings.bikers(b, d, px, pz, gy);
            case MARKET -> Buildings.market(b, d, px, pz, gy, r);
            case PROK -> Buildings.prokuratura(b, d, px, pz, gy);
            case ADMIN -> Buildings.admin(b, d, px, pz, gy);
            case FSB -> Buildings.fsb(b, d, px, pz, gy);
            case WAREHOUSE -> Buildings.warehouse(b, d, px, pz, gy);
            case CAFE -> Buildings.cafe(b, d, px, pz, gy);
            case SHOP -> Buildings.shop(b, d, px, pz, gy);
            case PARK -> Buildings.park(b, d, px, pz, gy, r);
            case CHURCH -> Buildings.church(b, d, px, pz, gy);
            case SIZO -> Buildings.sizo(b, d, px, pz, gy);
            case RESTAURANT -> Buildings.restaurant(b, d, px, pz, gy);
            case UNIVERSITY -> Buildings.university(b, d, px, pz, gy);
            case POST -> Buildings.post(b, d, px, pz, gy);
        }
    }

    private static void finish(ServerLevel l, GorodData d) {
        for (long[] f : FORCED) l.setChunkForced((int) f[0], (int) f[1], false);
        FORCED.clear();
        d.generated = true;
        d.lastDay = l.getDayTime() / 24000L;
        d.addNews("Город «" + CITY_NAME + "» живёт своей жизнью. Глава округа провёл планёрку.");
        d.setDirty();
        BlockPos sq = d.loc("square");
        l(l, "§6[РусГород] §aГород «" + CITY_NAME + "» построен! §fЦентр: X " + sq.getX() + ", Z " + sq.getZ()
                + ". Воинская часть: X " + (d.baseMinX + 32) + ", Z " + (d.baseMinZ + 32) + ". §eКлавиша G — меню фракций.");
        RusGorod.LOGGER.info("[RusGorod] Город построен.");
    }

    private static void l(ServerLevel level, String s) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(s), false);
    }

    private CityGenerator() {
    }
}
