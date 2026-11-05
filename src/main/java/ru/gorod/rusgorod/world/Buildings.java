package ru.gorod.rusgorod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import ru.gorod.rusgorod.data.GorodData;

/**
 * Здания города. Участок 24×24, (px, pz) — северо-западный угол, фасад обращён на юг
 * (к дороге на z = pz + 24). gy — уровень земли (верх блока покрытия).
 * В многоэтажных зданиях клетки x0+1..x0+6 × z0+1..z0+2 заняты лестницей — мебель туда не ставим.
 */
public final class Buildings {

    /** Результат постройки: дверь, внутренняя точка и габариты. */
    public record Info(int doorX, int frontZ, BlockPos inside, int x0, int z0, int x1, int z1) {
    }

    static BlockState topSlab(Block slab) {
        return slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    // =================================================================== общий фасад

    /**
     * Административное здание: цоколь, пилястры, большие окна с подоконниками, межэтажные пояса,
     * карниз, парапет, портал с козырьком и двустворчатой дверью, двухмаршевая лестница, освещение.
     */
    static Info office(B b, int x0, int z0, int w, int dep, int floors, int gy,
                       Block wall, Block trim, Block floor, Block roof, Block door, String[]... signs) {
        int x1 = x0 + w - 1, z1 = z0 + dep - 1;
        int top = gy + 4 * floors;
        b.air(x0 - 1, gy + 1, z0 - 1, x1 + 1, top + 3, z1 + 3);
        for (int i = 0; i <= floors; i++) {
            int y = gy + 4 * i;
            b.fill(x0, y, z0, x1, y, z1, i == floors ? roof : floor);
            if (i > 0) b.walls(x0, y, z0, x1, y, z1, trim);
        }
        for (int i = 0; i < floors; i++) {
            int y = gy + 4 * i;
            b.walls(x0, y + 1, z0, x1, y + 3, z1, wall);
            // пилястры
            for (int x = x0; x <= x1; x += 4) {
                b.fill(x, y + 1, z0, x, y + 3, z0, trim);
                b.fill(x, y + 1, z1, x, y + 3, z1, trim);
            }
            b.fill(x1, y + 1, z0, x1, y + 3, z0, trim);
            b.fill(x1, y + 1, z1, x1, y + 3, z1, trim);
            b.fill(x0, y + 1, z0, x0, y + 3, z1, wall);
            b.fill(x0, y + 1, z0, x0, y + 3, z0, trim);
            b.fill(x0, y + 1, z1, x0, y + 3, z1, trim);
            // окна на длинных фасадах
            for (int x = x0 + 1; x < x1; x++) {
                if ((x - x0) % 4 == 0) continue;
                b.set(x, y + 2, z0, Blocks.GLASS_PANE);
                b.set(x, y + 3, z0, Blocks.GLASS_PANE);
                b.set(x, y + 2, z1, Blocks.GLASS_PANE);
                b.set(x, y + 3, z1, Blocks.GLASS_PANE);
                if (i > 0) {
                    b.set(x, y + 1, z1 + 1, topSlab(Blocks.SMOOTH_STONE_SLAB));
                    b.set(x, y + 1, z0 - 1, topSlab(Blocks.SMOOTH_STONE_SLAB));
                }
            }
            // окна на торцах
            for (int z = z0 + 2; z <= z1 - 2; z++) {
                if ((z - z0) % 3 == 0) continue;
                b.set(x0, y + 2, z, Blocks.GLASS_PANE);
                b.set(x1, y + 2, z, Blocks.GLASS_PANE);
            }
            // освещение
            for (int x = x0 + 3; x <= x1 - 2; x += 5)
                for (int z = z0 + 4; z <= z1 - 2; z += 4)
                    b.hangingLantern(x, y + 3, z);
        }
        // цоколь
        b.walls(x0, gy + 1, z0, x1, gy + 1, z1, Blocks.POLISHED_ANDESITE);
        for (int x = x0 + 1; x < x1; x++) {
            if ((x - x0) % 4 == 0) continue;
            b.set(x, gy + 1, z0, Blocks.POLISHED_ANDESITE);
            b.set(x, gy + 1, z1, Blocks.POLISHED_ANDESITE);
        }
        // карниз и парапет
        b.cornice(x0, z0, x1, z1, top, Blocks.SMOOTH_STONE_SLAB);
        b.walls(x0, top + 1, z0, x1, top + 1, z1, trim);
        // лестница
        if (floors > 1) b.stairwell(x0 + 1, z0 + 1, gy, floors, 4, Blocks.SPRUCE_STAIRS);
        // портал
        int cx = x0 + w / 2 - 1;
        b.fill(cx - 1, gy + 1, z1, cx + 2, gy + 3, z1, trim);
        b.doubleDoor(cx, gy + 1, z1, Direction.SOUTH, door);
        b.air(cx, gy + 1, z1 - 1, cx + 1, gy + 2, z1 - 1);
        b.fill(cx - 1, gy, z1 + 1, cx + 2, gy, z1 + 3, Blocks.SMOOTH_STONE);
        b.fill(cx - 2, gy + 4, z1 + 1, cx + 3, gy + 4, z1 + 2, topSlab(Blocks.SMOOTH_STONE_SLAB));
        for (int y = gy + 1; y <= gy + 3; y++) {
            b.set(cx - 2, y, z1 + 2, Blocks.QUARTZ_PILLAR);
            b.set(cx + 3, y, z1 + 2, Blocks.QUARTZ_PILLAR);
        }
        b.hangingLantern(cx - 1, gy + 3, z1 + 1);
        b.hangingLantern(cx + 2, gy + 3, z1 + 1);
        // интерьер первого этажа: ковровая дорожка, цветы
        b.fill(cx, gy + 1, z1 - 5, cx + 1, gy + 1, z1 - 1, Blocks.RED_CARPET);
        b.plant(x0 + 1, gy + 1, z1 - 1, 0);
        b.plant(x1 - 1, gy + 1, z1 - 1, 1);
        // вывески над порталом
        int sx = cx - (signs.length - 1) / 2;
        int sy = floors > 1 ? gy + 5 : gy + 5;
        for (String[] s : signs) {
            b.set(sx, sy, z1, trim);
            b.sign(sx, sy, z1 + 1, Direction.SOUTH, s);
            sx++;
        }
        return new Info(cx, z1, new BlockPos(cx, gy + 1, z1 - 3), x0, z0, x1, z1);
    }

    /** Площадка перед зданием: тротуарная плитка, газон, фонари, урны. */
    static void forecourt(B b, int px, int pz, int frontZ, int gy, Block paving) {
        b.fill(px, gy, frontZ + 1, px + 23, gy, pz + 23, paving);
        b.fill(px, gy, frontZ + 4, px + 3, gy, pz + 21, Blocks.GRASS_BLOCK);
        b.fill(px + 20, gy, frontZ + 4, px + 23, gy, pz + 21, Blocks.GRASS_BLOCK);
        b.streetLamp(px + 1, gy + 1, pz + 22);
        b.streetLamp(px + 22, gy + 1, pz + 22);
        b.set(px + 4, gy + 1, pz + 22, Blocks.CAULDRON);
        b.set(px + 19, gy + 1, pz + 22, Blocks.CAULDRON);
    }

    /** Книжные шкафы вдоль задней стены на этаже y (вне лестницы). */
    static void archive(B b, Info i, int y) {
        for (int x = i.x0() + 8; x < i.x1(); x++) b.set(x, y, i.z0() + 1, Blocks.BOOKSHELF);
    }

    static void desks(B b, Info i, int y, int z) {
        for (int x = i.x0() + 2; x <= i.x1() - 2; x += 3) {
            if (z <= i.z0() + 3 && x <= i.x0() + 7) continue;
            if (x >= i.doorX() - 1 && x <= i.doorX() + 2) continue; // проход от входа
            b.desk(x, y, z);
        }
    }

    // =================================================================== жильё

    /** Панельная «хрущёвка» (5 этажей) или «девятиэтажка». */
    public static void panelHouse(B b, GorodData d, int px, int pz, int gy, int floors, boolean nine, RandomSource r) {
        int x0 = px + 1, z0 = pz + 7, w = 22, dep = 10;
        int x1 = x0 + w - 1, z1 = z0 + dep - 1;
        int top = gy + 3 * floors;
        Block wall = nine ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE;
        Block seam = nine ? Blocks.LIGHT_BLUE_CONCRETE : Blocks.GRAY_CONCRETE;
        b.air(x0, gy + 1, z0 - 1, x1, top + 3, z1 + 3);
        for (int i = 0; i <= floors; i++) {
            int y = gy + 3 * i;
            b.fill(x0, y, z0, x1, y, z1, i == floors ? Blocks.GRAY_CONCRETE : Blocks.SMOOTH_STONE);
        }
        for (int i = 0; i < floors; i++) {
            int y = gy + 3 * i;
            b.walls(x0, y + 1, z0, x1, y + 2, z1, wall);
            b.walls(x0, y + 3, z0, x1, y + 3, z1, seam);
            for (int x = x0 + 1; x < x1; x++) {
                int k = (x - x0) % 4;
                if (k == 1 || k == 2) {
                    b.set(x, y + 2, z0, Blocks.GLASS_PANE);
                    b.set(x, y + 2, z1, Blocks.GLASS_PANE);
                }
                // балконы на фасаде, местами застеклённые
                if (i > 0 && k == 1 && (x - x0) % 8 == 1) {
                    b.set(x, y, z1 + 1, Blocks.SMOOTH_STONE_SLAB);
                    b.set(x + 1, y, z1 + 1, Blocks.SMOOTH_STONE_SLAB);
                    boolean glazed = r.nextInt(3) == 0;
                    b.set(x, y + 1, z1 + 1, glazed ? Blocks.WHITE_STAINED_GLASS_PANE : Blocks.IRON_BARS);
                    b.set(x + 1, y + 1, z1 + 1, glazed ? Blocks.WHITE_STAINED_GLASS_PANE : Blocks.IRON_BARS);
                    if (r.nextInt(4) == 0) b.plant(x, y + 1, z0 + 1, r.nextInt(5));
                }
            }
            for (int x = x0 + 4; x < x1 - 2; x += 6) b.hangingLantern(x, y + 2, z0 + 5);
        }
        b.walls(x0, top + 1, z0, x1, top + 1, z1, seam);
        // будки выхода на крышу и антенны
        b.fill(x0 + 4, top + 1, z0 + 3, x0 + 6, top + 2, z0 + 5, seam);
        b.fill(x1 - 6, top + 1, z0 + 3, x1 - 4, top + 2, z0 + 5, seam);
        for (int y = top + 1; y <= top + 4; y++) b.set(x0 + 11, y, z0 + 5, Blocks.IRON_BARS);
        b.set(x0 + 10, top + 4, z0 + 5, Blocks.IRON_BARS);
        b.set(x0 + 12, top + 4, z0 + 5, Blocks.IRON_BARS);
        // подъезды
        int[] entrances = {x0 + 5, x0 + 16};
        for (int n = 0; n < entrances.length; n++) {
            int ex = entrances[n];
            b.stairwell(ex - 2, z0 + 1, gy, floors, 3, Blocks.STONE_BRICK_STAIRS);
            b.set(ex + 1, gy + 2, z1, wall);
            b.door(ex, gy + 1, z1, Direction.SOUTH, Blocks.SPRUCE_DOOR);
            b.set(ex, gy + 3, z1, wall);
            b.fill(ex - 1, gy + 3, z1 + 1, ex + 1, gy + 3, z1 + 2, Blocks.SMOOTH_STONE_SLAB);
            b.fill(ex - 1, gy, z1 + 1, ex + 1, gy, z1 + 3, Blocks.SMOOTH_STONE);
            b.sign(ex + 1, gy + 2, z1 + 1, Direction.SOUTH, "Подъезд", "№" + (n + 1), "кв. " + (n * floors * 4 + 1) + "-" + (n + 1) * floors * 4);
            b.hangingLantern(ex, gy + 2, z1 + 1);
            // квартиры первого этажа
            b.bed(ex - 4, gy + 1, z0 + 2, Direction.NORTH, Blocks.RED_BED);
            b.bed(ex + 4, gy + 1, z0 + 2, Direction.NORTH, Blocks.WHITE_BED);
            b.desk(ex - 3, gy + 1, z1 - 3);
            b.set(ex + 3, gy + 1, z1 - 2, Blocks.CRAFTING_TABLE);
            b.set(ex - 4, gy + 1, z1 - 1, Blocks.FURNACE);
            d.homes.add(new BlockPos(ex, gy + 1, z1 - 2));
            d.homes.add(new BlockPos(ex - 3, gy + 1, z0 + 5));
            d.homes.add(new BlockPos(ex + 3, gy + 1, z0 + 5));
        }
        // двор: лавочки у подъездов, детская площадка, «ракушки»
        b.fill(px, gy, z1 + 4, px + 23, gy, pz + 23, Blocks.GRASS_BLOCK);
        b.fill(px, gy, z1 + 1, px + 23, gy, z1 + 1, Blocks.SMOOTH_STONE);
        b.bench(entrances[0] + 2, gy + 1, z1 + 3, Direction.NORTH, 2);
        b.bench(entrances[1] + 2, gy + 1, z1 + 3, Direction.NORTH, 2);
        for (int x : new int[]{px + 2, px + 4}) {
            b.set(x, gy + 1, pz + 1, Blocks.OAK_FENCE);
            b.set(x, gy + 2, pz + 1, Blocks.OAK_FENCE);
        }
        b.fill(px + 2, gy + 3, pz + 1, px + 4, gy + 3, pz + 1, Blocks.OAK_SLAB);
        b.fill(px + 2, gy, pz + 3, px + 5, gy, pz + 5, Blocks.SAND);
        b.tree(px + 21, gy + 1, pz + 2, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        b.fill(px + 8, gy + 1, pz + 1, px + 9, gy + 1, pz + 2, Blocks.GREEN_TERRACOTTA);
        b.fill(px + 11, gy + 1, pz + 1, px + 12, gy + 1, pz + 2, Blocks.GRAY_TERRACOTTA);
        b.fill(px + 14, gy + 1, pz + 1, px + 15, gy + 1, pz + 2, Blocks.BLUE_TERRACOTTA);
        // бельевые верёвки
        b.set(px + 18, gy + 1, pz + 4, Blocks.OAK_FENCE);
        b.set(px + 18, gy + 2, pz + 4, Blocks.OAK_FENCE);
        b.set(px + 18, gy + 2, pz + 5, Blocks.WHITE_CARPET);
        b.streetLamp(px + 11, gy + 1, pz + 22);
        b.tree(px + 2, gy + 1, pz + 21, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
        d.roadSpots.add(new BlockPos(px + 12, gy + 1, pz + 22));
    }

    /** «Сталинка»: кирпичный дом с рустованным цоколем, эркерами-балконами, высоким карнизом и вальмовой крышей. */
    public static void stalinka(B b, GorodData d, int px, int pz, int gy) {
        int x0 = px + 1, z0 = pz + 5, w = 22, dep = 11, floors = 5;
        int x1 = x0 + w - 1, z1 = z0 + dep - 1;
        int top = gy + 4 * floors;
        Block wall = Blocks.SMOOTH_SANDSTONE, trim = Blocks.WHITE_TERRACOTTA;
        b.air(x0 - 1, gy + 1, z0 - 1, x1 + 1, top + 6, z1 + 3);
        for (int i = 0; i <= floors; i++) b.fill(x0, gy + 4 * i, z0, x1, gy + 4 * i, z1, i == floors ? Blocks.STONE_BRICKS : Blocks.OAK_PLANKS);
        for (int i = 0; i < floors; i++) {
            int y = gy + 4 * i;
            b.walls(x0, y + 1, z0, x1, y + 3, z1, i == 0 ? Blocks.STONE_BRICKS : wall);
            if (i > 0) b.walls(x0, y, z0, x1, y, z1, trim);
            for (int x = x0 + 2; x < x1 - 1; x += 3) {
                b.set(x, y + 2, z0, Blocks.GLASS_PANE);
                b.set(x, y + 3, z0, Blocks.GLASS_PANE);
                b.set(x, y + 2, z1, Blocks.GLASS_PANE);
                b.set(x, y + 3, z1, Blocks.GLASS_PANE);
                if (i > 0) b.set(x, y + 4 <= top ? y + 4 : y + 3, z1 + 1, topSlab(Blocks.SANDSTONE_SLAB));
            }
            // балконы с балясинами
            if (i > 0 && i < floors) {
                for (int x : new int[]{x0 + 5, x0 + 14}) {
                    b.fill(x, y, z1 + 1, x + 2, y, z1 + 1, Blocks.SMOOTH_SANDSTONE_SLAB);
                    b.fill(x, y + 1, z1 + 2, x + 2, y + 1, z1 + 2, Blocks.SANDSTONE_WALL);
                    b.set(x + 1, y + 2, z1, Blocks.GLASS_PANE);
                }
            }
            for (int x = x0 + 4; x < x1 - 2; x += 6) b.hangingLantern(x, y + 3, z0 + 5);
        }
        // высокий карниз и шатровая кровля
        b.cornice(x0, z0, x1, z1, top, Blocks.SANDSTONE_SLAB);
        for (int k = 0; k < 4; k++) {
            b.walls(x0 + k, top + 1 + k, z0 + k, x1 - k, top + 1 + k, z1 - k, Blocks.BRICKS);
        }
        b.fill(x0 + 4, top + 5, z0 + 4, x1 - 4, top + 5, z1 - 4, Blocks.BRICK_SLAB);
        // арка во двор и подъезды
        b.air(x0 + 10, gy + 1, z0, x0 + 11, gy + 3, z1);
        b.fill(x0 + 10, gy + 4, z0, x0 + 11, gy + 4, z1, Blocks.STONE_BRICKS);
        for (int ex : new int[]{x0 + 4, x0 + 17}) {
            b.stairwell(ex - 2, z0 + 1, gy, floors, 4, Blocks.OAK_STAIRS);
            b.door(ex, gy + 1, z1, Direction.SOUTH, Blocks.DARK_OAK_DOOR);
            b.set(ex, gy + 3, z1, trim);
            b.fill(ex - 1, gy + 4, z1 + 1, ex + 1, gy + 4, z1 + 1, topSlab(Blocks.SANDSTONE_SLAB));
            b.bed(ex + 3, gy + 1, z0 + 4, Direction.NORTH, Blocks.ORANGE_BED);
            d.homes.add(new BlockPos(ex, gy + 1, z1 - 3));
            d.homes.add(new BlockPos(ex + 2, gy + 1, z0 + 6));
        }
        b.sign(x0 + 9, gy + 3, z1 + 1, Direction.SOUTH, "ул. Ленина, 12", "Дом построен", "в 1954 году");
        b.set(x0 + 9, gy + 3, z1, Blocks.STONE_BRICKS);
        // двор
        b.fill(px, gy, pz, px + 23, gy, z0 - 2, Blocks.GRASS_BLOCK);
        b.tree(px + 4, gy + 1, pz + 1, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
        b.tree(px + 19, gy + 1, pz + 1, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
        b.bench(px + 10, gy + 1, pz + 2, Direction.SOUTH, 3);
        b.fill(px, gy, z1 + 3, px + 23, gy, pz + 23, Blocks.SMOOTH_STONE);
        b.streetLamp(px + 2, gy + 1, pz + 22);
        b.streetLamp(px + 21, gy + 1, pz + 22);
        d.roadSpots.add(new BlockPos(px + 12, gy + 1, pz + 22));
    }

    /** Частный сектор: четыре деревянных дома с двускатными крышами, заборами, огородами и сараями. */
    public static void privateHouses(B b, GorodData d, int px, int pz, int gy, RandomSource r) {
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.GRASS_BLOCK);
        b.fill(px, gy, pz + 11, px + 23, gy, pz + 12, Blocks.DIRT_PATH);
        Block[] walls = {Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.OAK_PLANKS, Blocks.DARK_OAK_PLANKS};
        Block[] roofs = {Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.BRICK_STAIRS, Blocks.MUD_BRICK_STAIRS};
        int n = 0;
        for (int hx : new int[]{px + 1, px + 13})
            for (int hz : new int[]{pz + 1, pz + 14}) {
                Block wall = walls[(n + r.nextInt(2)) % walls.length];
                Block roof = roofs[n % roofs.length];
                int x0 = hx + 1, z0 = hz + 1, x1 = hx + 7, z1 = hz + 5;
                // забор
                b.walls(hx, gy + 1, hz, hx + 9, gy + 1, hz + 8, Blocks.SPRUCE_FENCE);
                boolean southGate = hz > pz + 5;
                int gz = southGate ? hz : hz + 8;
                b.set(hx + 4, gy + 1, gz, Blocks.SPRUCE_FENCE_GATE.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, Direction.SOUTH));
                // сруб на каменном фундаменте
                b.fill(x0, gy, z0, x1, gy, z1, Blocks.COBBLESTONE);
                b.walls(x0, gy + 1, z0, x1, gy + 3, z1, wall);
                b.fill(x0, gy + 1, z0, x0, gy + 3, z0, Blocks.SPRUCE_LOG);
                b.fill(x1, gy + 1, z0, x1, gy + 3, z0, Blocks.SPRUCE_LOG);
                b.fill(x0, gy + 1, z1, x0, gy + 3, z1, Blocks.SPRUCE_LOG);
                b.fill(x1, gy + 1, z1, x1, gy + 3, z1, Blocks.SPRUCE_LOG);
                b.fill(x0 + 1, gy, z0 + 1, x1 - 1, gy, z1 - 1, Blocks.SPRUCE_PLANKS);
                b.set(x0 + 2, gy + 2, z0, Blocks.GLASS_PANE);
                b.set(x1 - 2, gy + 2, z0, Blocks.GLASS_PANE);
                b.set(x0 + 2, gy + 2, z1, Blocks.GLASS_PANE);
                b.set(x1 - 2, gy + 2, z1, Blocks.GLASS_PANE);
                // резные наличники
                b.set(x0 + 2, gy + 3, z1 + 1, Blocks.WHITE_CARPET);
                int dz = southGate ? z0 : z1;
                b.door(x0 + 3, gy + 1, dz, southGate ? Direction.NORTH : Direction.SOUTH, Blocks.SPRUCE_DOOR);
                b.gableRoof(x0, z0, x1, z1, gy + 4, roof, wall);
                b.set(x1 - 1, gy + 6, z0 + 2, Blocks.BRICKS);
                b.set(x1 - 1, gy + 7, z0 + 2, Blocks.BRICKS);
                // печь и кровать
                b.set(x1 - 1, gy + 1, z0 + 1, Blocks.FURNACE);
                b.bed(x0 + 1, gy + 1, z0 + 2, Direction.NORTH, Blocks.RED_BED);
                b.set(x0 + 4, gy + 1, z0 + 1, Blocks.CHEST);
                b.hangingLantern(x0 + 3, gy + 3, z0 + 2);
                // огород и сарай
                int gx0 = hx + 1, gz0 = southGate ? hz + 6 : hz + 6;
                b.fill(gx0, gy, gz0, gx0 + 3, gy, gz0 + 1, Blocks.FARMLAND);
                b.fill(gx0, gy + 1, gz0, gx0 + 3, gy + 1, gz0 + 1, r.nextBoolean() ? Blocks.POTATOES : Blocks.CARROTS);
                b.set(gx0 + 1, gy, gz0, Blocks.WATER);
                b.set(gx0 + 1, gy + 1, gz0, Blocks.AIR);
                b.fill(hx + 6, gy + 1, gz0, hx + 8, gy + 2, gz0 + 1, Blocks.SPRUCE_PLANKS);
                b.fill(hx + 6, gy + 3, gz0, hx + 8, gy + 3, gz0 + 1, Blocks.SPRUCE_SLAB);
                d.homes.add(new BlockPos(x0 + 3, gy + 1, z0 + 2));
                n++;
            }
        b.spruce(px + 11, gy + 1, pz + 4);
        b.streetLamp(px + 11, gy + 1, pz + 20);
    }

    /** Общежитие (бывшее рабочее). */
    public static void hostel(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 2, pz + 4, 20, 12, 4, gy, Blocks.BRICKS, Blocks.WHITE_TERRACOTTA, Blocks.OAK_PLANKS,
                Blocks.GRAY_CONCRETE, Blocks.SPRUCE_DOOR,
                new String[]{"ОБЩЕЖИТИЕ"}, new String[]{"Вахта", "круглосуточно"});
        for (int f = 0; f < 4; f++) {
            int y = gy + 1 + 4 * f;
            for (int x = i.x0() + 8; x < i.x1() - 1; x += 3) {
                b.bed(x, y, i.z0() + 2, Direction.NORTH, f % 2 == 0 ? Blocks.BLUE_BED : Blocks.GREEN_BED);
                b.set(x + 1, y, i.z0() + 1, Blocks.BARREL);
            }
            d.homes.add(new BlockPos(i.x0() + 12, y, i.z0() + 5));
        }
        b.fill(i.x1() - 3, gy + 1, i.z1() - 3, i.x1() - 1, gy + 1, i.z1() - 3, topSlab(Blocks.SPRUCE_SLAB));
        b.sign(i.x1() - 2, gy + 2, i.z1() - 3, Direction.SOUTH, "Вахта", "Пропуск", "обязателен");
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        d.locs.put("hostel", i.inside());
    }

    // =================================================================== госструктуры

    public static void police(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 2, pz + 4, 20, 14, 3, gy, Blocks.WHITE_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.POLISHED_ANDESITE,
                Blocks.GRAY_CONCRETE, Blocks.SPRUCE_DOOR,
                new String[]{"ОТДЕЛ", "МВД РОССИИ", "ПО ГОРОДУ"}, new String[]{"ПОЛИЦИЯ"}, new String[]{"Тел. 102", "Единый", "номер 112"});
        int y = gy + 1;
        // дежурная часть за стеклом
        b.fill(px + 4, y, pz + 12, px + 8, y, pz + 12, topSlab(Blocks.SMOOTH_STONE_SLAB));
        b.fill(px + 4, y + 1, pz + 12, px + 8, y + 1, pz + 12, Blocks.GLASS_PANE);
        b.sign(px + 6, y + 2, pz + 13, Direction.SOUTH, "ДЕЖУРНАЯ", "ЧАСТЬ");
        b.set(px + 6, y + 2, pz + 12, Blocks.BLUE_CONCRETE);
        b.desk(px + 5, y, pz + 10);
        b.desk(px + 7, y, pz + 10);
        // ИВС — камеры у задней стены
        for (int k = 0; k < 3; k++) {
            int cx0 = px + 10 + k * 4;
            b.walls(cx0, y, pz + 5, cx0 + 3, y + 2, pz + 8, Blocks.STONE_BRICKS);
            b.fill(cx0 + 1, y, pz + 8, cx0 + 2, y + 2, pz + 8, Blocks.IRON_BARS);
            b.air(cx0 + 1, y, pz + 6, cx0 + 2, y + 2, pz + 7);
            b.set(cx0 + 1, y, pz + 6, topSlab(Blocks.STONE_SLAB));
            d.ivsCells.add(new BlockPos(cx0 + 2, y, pz + 7));
        }
        b.sign(px + 13, y + 2, pz + 9, Direction.SOUTH, "ИВС", "Изолятор", "временного", "содержания");
        b.set(px + 13, y + 2, pz + 8, Blocks.STONE_BRICKS);
        // кабинеты на 2–3 этажах, тир не нужен — оружейная комната
        desks(b, i, gy + 5, pz + 9);
        desks(b, i, gy + 9, pz + 9);
        archive(b, i, gy + 5);
        b.fill(i.x1() - 4, gy + 9, i.z0() + 1, i.x1() - 1, gy + 10, i.z0() + 1, Blocks.IRON_BLOCK);
        b.sign(i.x1() - 3, gy + 10, i.z0() + 2, Direction.SOUTH, "КХО", "оружейная");
        b.russianFlag(px + 3, gy + 13, pz + 15, 4);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.POLISHED_ANDESITE);
        // служебная парковка: «УАЗы»
        for (int x = px + 5; x <= px + 17; x += 6) {
            b.fill(x, gy + 1, pz + 20, x + 2, gy + 1, pz + 21, Blocks.WHITE_CONCRETE);
            b.set(x + 1, gy + 2, pz + 20, Blocks.BLUE_STAINED_GLASS);
        }
        d.locs.put("police", i.inside());
    }

    public static void gibdd(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 4, pz + 6, 16, 12, 2, gy, Blocks.WHITE_CONCRETE, Blocks.LIME_CONCRETE, Blocks.POLISHED_ANDESITE,
                Blocks.GRAY_CONCRETE, Blocks.SPRUCE_DOOR,
                new String[]{"ГОСАВТО-", "ИНСПЕКЦИЯ"}, new String[]{"ОГИБДД", "ОМВД России", "по городу"});
        for (int x = px + 12; x <= px + 17; x += 2) b.desk(x, gy + 1, pz + 13);
        b.sign(px + 14, gy + 2, pz + 12, Direction.SOUTH, "Регистрация", "ТС и выдача", "в/у: окна 1-3");
        b.set(px + 14, gy + 2, pz + 11, Blocks.WHITE_CONCRETE);
        desks(b, i, gy + 5, pz + 11);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        // учебная площадка «автодром»
        b.fill(px + 1, gy, pz + 1, px + 22, gy, pz + 4, Blocks.GRAY_CONCRETE);
        for (int x = px + 3; x <= px + 20; x += 3) b.set(x, gy + 1, pz + 2, Blocks.ORANGE_CARPET);
        d.locs.put("gibdd", i.inside());
    }

    /** Стационарный пост ДПС на въезде в город. */
    public static void dpsPost(B b, GorodData d, int x, int z, int gy) {
        b.fill(x, gy, z, x + 3, gy, z + 3, Blocks.SMOOTH_STONE);
        b.walls(x, gy + 1, z, x + 3, gy + 1, z + 3, Blocks.WHITE_CONCRETE);
        b.walls(x, gy + 2, z, x + 3, gy + 2, z + 3, Blocks.GLASS);
        b.fill(x, gy + 3, z, x + 3, gy + 3, z + 3, Blocks.BLUE_CONCRETE);
        b.air(x + 1, gy + 1, z + 1, x + 2, gy + 2, z + 2);
        b.door(x + 1, gy + 1, z + 3, Direction.SOUTH, Blocks.SPRUCE_DOOR);
        b.set(x + 2, gy + 4, z + 3, Blocks.WHITE_CONCRETE);
        b.sign(x + 2, gy + 4, z + 4, Direction.SOUTH, "ДПС");
        b.hangingLantern(x + 2, gy + 2, z + 1);
        b.set(x + 4, gy + 1, z + 1, Blocks.RED_CONCRETE);
        b.fill(x + 5, gy + 1, z + 1, x + 8, gy + 1, z + 1, Blocks.WHITE_CONCRETE);
        d.locs.put("gibdd_post", new BlockPos(x + 2, gy + 1, z + 5));
    }

    public static void sk(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 3, pz + 5, 18, 13, 3, gy, Blocks.GRAY_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.POLISHED_ANDESITE,
                Blocks.BLACK_CONCRETE, Blocks.DARK_OAK_DOOR,
                new String[]{"СЛЕДСТВЕННЫЙ", "КОМИТЕТ"}, new String[]{"РОССИЙСКОЙ", "ФЕДЕРАЦИИ"}, new String[]{"Следственный", "отдел по", "городу"});
        desks(b, i, gy + 1, pz + 10);
        desks(b, i, gy + 5, pz + 10);
        desks(b, i, gy + 9, pz + 10);
        archive(b, i, gy + 5);
        archive(b, i, gy + 9);
        b.set(i.x1() - 2, gy + 1, i.z0() + 1, Blocks.LECTERN);
        b.set(i.x1() - 3, gy + 1, i.z0() + 1, Blocks.BREWING_STAND);
        b.sign(i.x1() - 2, gy + 2, i.z0() + 2, Direction.SOUTH, "Криминалисти-", "ческая", "лаборатория");
        b.russianFlag(px + 4, gy + 13, pz + 16, 4);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.POLISHED_ANDESITE);
        d.locs.put("sk", i.inside());
    }

    public static void prokuratura(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 3, pz + 5, 18, 13, 3, gy, Blocks.SMOOTH_QUARTZ, Blocks.BLUE_CONCRETE, Blocks.POLISHED_ANDESITE,
                Blocks.GRAY_CONCRETE, Blocks.DARK_OAK_DOOR,
                new String[]{"ПРОКУРАТУРА", "ГОРОДА"}, new String[]{"Приём граждан", "пн–пт", "9:00–18:00"});
        desks(b, i, gy + 1, pz + 10);
        desks(b, i, gy + 5, pz + 10);
        desks(b, i, gy + 9, pz + 10);
        archive(b, i, gy + 1);
        archive(b, i, gy + 9);
        b.fill(px + 6, gy + 1, pz + 15, px + 10, gy + 1, pz + 15, Blocks.SPRUCE_STAIRS);
        b.sign(px + 8, gy + 2, pz + 14, Direction.SOUTH, "Зал ожидания", "приёма");
        b.set(px + 8, gy + 2, pz + 13, Blocks.SMOOTH_QUARTZ);
        b.russianFlag(px + 4, gy + 13, pz + 16, 4);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.POLISHED_ANDESITE);
        d.locs.put("prok", i.inside());
    }

    public static void fsb(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 2, pz + 3, 20, 15, 4, gy, Blocks.SMOOTH_SANDSTONE, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE,
                Blocks.STONE_BRICKS, Blocks.DARK_OAK_DOOR,
                new String[]{"УПРАВЛЕНИЕ", "ФЕДЕРАЛЬНОЙ"}, new String[]{"СЛУЖБЫ", "БЕЗОПАСНОСТИ"}, new String[]{"РОССИЙСКОЙ", "ФЕДЕРАЦИИ"});
        // гранитный цоколь
        b.walls(i.x0(), gy + 1, i.z0(), i.x1(), gy + 1, i.z1(), Blocks.POLISHED_GRANITE);
        b.doubleDoor(i.doorX(), gy + 1, i.frontZ(), Direction.SOUTH, Blocks.DARK_OAK_DOOR);
        desks(b, i, gy + 5, pz + 10);
        desks(b, i, gy + 9, pz + 10);
        desks(b, i, gy + 13, pz + 10);
        archive(b, i, gy + 9);
        b.fill(i.x1() - 5, gy + 1, i.z0() + 1, i.x1() - 1, gy + 1, i.z0() + 1, Blocks.IRON_BLOCK);
        b.russianFlag(px + 3, gy + 17, pz + 15, 4);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.POLISHED_ANDESITE);
        // забор с калиткой — режимный объект
        for (int x = px; x <= px + 23; x++) if (Math.abs(x - i.doorX()) > 2) b.set(x, gy + 1, pz + 23, Blocks.IRON_BARS);
        d.locs.put("fsb", i.inside());
    }

    /** Администрация городского округа + площадь для митингов. */
    public static void admin(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 1, pz + 1, 22, 11, 3, gy, Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ, Blocks.POLISHED_ANDESITE,
                Blocks.SMOOTH_QUARTZ, Blocks.DARK_OAK_DOOR,
                new String[]{"АДМИНИСТРАЦИЯ"}, new String[]{"ГОРОДСКОГО", "ОКРУГА"}, new String[]{"Приём граждан:", "вт, чт", "10:00–13:00"});
        // колоннада
        for (int x = px + 3; x <= px + 21; x += 3) {
            for (int y = gy + 1; y <= gy + 7; y++) b.set(x, y, pz + 14, Blocks.QUARTZ_PILLAR);
        }
        b.fill(px + 2, gy + 8, pz + 12, px + 22, gy + 8, pz + 15, Blocks.SMOOTH_QUARTZ);
        b.fill(px + 2, gy + 9, pz + 15, px + 22, gy + 9, pz + 15, Blocks.QUARTZ_STAIRS);
        b.russianFlag(px + 11, gy + 13, pz + 6, 5);
        // площадь
        b.fill(px, gy, pz + 12, px + 23, gy, pz + 23, Blocks.POLISHED_ANDESITE);
        for (int x = px + 1; x <= px + 22; x += 3) b.set(x, gy, pz + 18, Blocks.STONE_BRICKS);
        b.fill(px + 9, gy, pz + 17, px + 14, gy, pz + 20, Blocks.STONE_BRICKS);
        b.fill(px + 10, gy + 1, pz + 18, px + 13, gy + 1, pz + 19, Blocks.STONE_BRICK_WALL);
        b.fill(px + 11, gy, pz + 18, px + 12, gy, pz + 19, Blocks.WATER);
        b.air(px + 11, gy + 1, pz + 18, px + 12, gy + 1, pz + 19);
        b.bench(px + 3, gy + 1, pz + 21, Direction.NORTH, 3);
        b.bench(px + 18, gy + 1, pz + 21, Direction.NORTH, 3);
        b.streetLamp(px + 1, gy + 1, pz + 16);
        b.streetLamp(px + 22, gy + 1, pz + 16);
        b.streetLamp(px + 1, gy + 1, pz + 22);
        b.streetLamp(px + 22, gy + 1, pz + 22);
        // кабинеты, зал заседаний Думы
        desks(b, i, gy + 5, pz + 6);
        archive(b, i, gy + 9);
        for (int x = px + 9; x <= px + 19; x++) b.stair(x, gy + 9, pz + 7, Blocks.DARK_OAK_STAIRS, Direction.NORTH);
        b.fill(px + 12, gy + 9, pz + 4, px + 16, gy + 9, pz + 4, topSlab(Blocks.DARK_OAK_SLAB));
        b.sign(px + 14, gy + 10, pz + 3, Direction.SOUTH, "Дума", "городского", "округа");
        d.locs.put("admin", i.inside());
        d.locs.put("square", new BlockPos(px + 12, gy + 1, pz + 16));
    }

    /** Городской суд: портик с колоннами, зал судебных заседаний с клеткой для подсудимого. */
    public static void court(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 1, pz + 2, 22, 14, 2, gy, Blocks.SMOOTH_STONE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_ANDESITE,
                Blocks.SMOOTH_STONE, Blocks.DARK_OAK_DOOR,
                new String[]{"ГОРОДСКОЙ"}, new String[]{"СУД"}, new String[]{"Канцелярия:", "пн–пт", "9:00–18:00"});
        // портик
        for (int x = px + 3; x <= px + 21; x += 3) for (int y = gy + 1; y <= gy + 7; y++) b.set(x, y, pz + 18, Blocks.QUARTZ_PILLAR);
        b.fill(px + 2, gy + 8, pz + 16, px + 22, gy + 8, pz + 18, Blocks.SMOOTH_QUARTZ);
        for (int k = 0; k < 3; k++) b.fill(px + 4 + k * 2, gy + 9 + k, pz + 18, px + 20 - k * 2, gy + 9 + k, pz + 18, Blocks.SMOOTH_QUARTZ);
        b.set(px + 12, gy + 10, pz + 19, Blocks.GOLD_BLOCK);
        b.fill(px + 2, gy, pz + 16, px + 22, gy, pz + 19, Blocks.POLISHED_DIORITE);
        // зал заседаний: возвышение судьи, герб, места сторон, клетка, скамьи для публики
        int y = gy + 1;
        b.fill(px + 8, y, pz + 3, px + 16, y, pz + 4, Blocks.DARK_OAK_PLANKS);
        b.fill(px + 9, y + 1, pz + 4, px + 15, y + 1, pz + 4, topSlab(Blocks.DARK_OAK_SLAB));
        b.stair(px + 12, y + 1, pz + 3, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
        b.set(px + 12, y + 2, pz + 2, Blocks.GOLD_BLOCK);
        b.set(px + 12, y + 3, pz + 2, Blocks.GOLD_BLOCK);
        b.sign(px + 12, y + 2, pz + 3, Direction.SOUTH, "ИМЕНЕМ", "РОССИЙСКОЙ", "ФЕДЕРАЦИИ");
        b.russianFlag(px + 15, y, pz + 3, 3);
        b.desk(px + 5, y, pz + 7);
        b.desk(px + 6, y, pz + 7);
        b.sign(px + 5, y + 1, pz + 6, Direction.SOUTH, "Гос.", "обвинитель");
        b.desk(px + 17, y, pz + 7);
        b.desk(px + 18, y, pz + 7);
        // клетка для подсудимого
        b.walls(px + 18, y, pz + 9, px + 21, y + 2, pz + 12, Blocks.IRON_BARS);
        b.fill(px + 18, y + 3, pz + 9, px + 21, y + 3, pz + 12, Blocks.IRON_BARS);
        b.air(px + 19, y, pz + 10, px + 20, y + 2, pz + 11);
        b.stair(px + 19, y, pz + 10, Blocks.SPRUCE_STAIRS, Direction.WEST);
        for (int z = pz + 9; z <= pz + 13; z += 2)
            for (int x = px + 7; x <= px + 16; x++) if (x != px + 11 && x != px + 12) b.stair(x, y, z, Blocks.SPRUCE_STAIRS, Direction.NORTH);
        // кабинеты судей — 2 этаж
        desks(b, i, gy + 5, pz + 8);
        archive(b, i, gy + 5);
        forecourt(b, px, pz, pz + 19, gy, Blocks.POLISHED_ANDESITE);
        d.locs.put("court", new BlockPos(px + 11, y, pz + 8));
        d.locs.put("court_dock", new BlockPos(px + 20, y, pz + 11));
    }

    /** СИЗО-1 УФСИН: периметр с колючей проволокой, корпус с камерами, вышка, КПП, прогулочный дворик. */
    public static void sizo(B b, GorodData d, int px, int pz, int gy) {
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.GRAVEL);
        b.walls(px, gy + 1, pz, px + 23, gy + 4, pz + 22, Blocks.STONE_BRICKS);
        b.walls(px, gy + 5, pz, px + 23, gy + 5, pz + 22, Blocks.IRON_BARS);
        b.walls(px, gy + 6, pz, px + 23, gy + 6, pz + 22, Blocks.COBWEB);
        int gx = px + 11;
        b.air(gx, gy + 1, pz + 22, gx + 1, gy + 3, pz + 22);
        b.fill(gx, gy + 1, pz + 22, gx + 1, gy + 3, pz + 22, Blocks.IRON_BARS);
        b.fill(gx - 1, gy + 4, pz + 22, gx + 2, gy + 4, pz + 22, Blocks.STONE_BRICKS);
        b.set(gx - 1, gy + 3, pz + 22, Blocks.STONE_BRICKS);
        b.sign(gx - 1, gy + 3, pz + 23, Direction.SOUTH, "ФКУ СИЗО-1", "УФСИН России", "Посторонним", "вход воспрещён");
        // КПП с дверью для персонала
        b.fill(gx + 3, gy + 1, pz + 22, gx + 3, gy + 2, pz + 22, Blocks.AIR);
        b.door(gx + 3, gy + 1, pz + 22, Direction.SOUTH, Blocks.SPRUCE_DOOR);
        int x0 = px + 3, z0 = pz + 3;
        b.fill(x0, gy, z0, x0 + 17, gy, z0 + 9, Blocks.POLISHED_ANDESITE);
        b.walls(x0, gy + 1, z0, x0 + 17, gy + 7, z0 + 9, Blocks.GRAY_CONCRETE);
        b.fill(x0, gy + 4, z0, x0 + 17, gy + 4, z0 + 9, Blocks.GRAY_CONCRETE);
        b.fill(x0, gy + 8, z0, x0 + 17, gy + 8, z0 + 9, Blocks.GRAY_CONCRETE);
        b.air(x0 + 1, gy + 1, z0 + 1, x0 + 16, gy + 3, z0 + 8);
        b.air(x0 + 1, gy + 5, z0 + 1, x0 + 16, gy + 7, z0 + 8);
        for (int k = 0; k < 4; k++) {
            int cx0 = x0 + 1 + k * 4;
            b.walls(cx0, gy + 1, z0 + 1, cx0 + 3, gy + 3, z0 + 5, Blocks.STONE_BRICKS);
            b.air(cx0 + 1, gy + 1, z0 + 2, cx0 + 2, gy + 3, z0 + 4);
            b.fill(cx0 + 1, gy + 1, z0 + 5, cx0 + 2, gy + 3, z0 + 5, Blocks.IRON_BARS);
            b.bed(cx0 + 1, gy + 1, z0 + 2, Direction.SOUTH, Blocks.GRAY_BED);
            b.set(cx0 + 2, gy + 1, z0 + 2, Blocks.CAULDRON);
            b.set(cx0 + 2, gy + 2, z0 + 1, Blocks.IRON_BARS);
            b.hangingLantern(cx0 + 2, gy + 3, z0 + 3);
            d.sizoCells.add(new BlockPos(cx0 + 2, gy + 1, z0 + 4));
        }
        for (int x = x0 + 2; x < x0 + 16; x += 4) {
            b.set(x, gy + 6, z0, Blocks.IRON_BARS);
            b.set(x, gy + 6, z0 + 9, Blocks.IRON_BARS);
        }
        b.hangingLantern(x0 + 8, gy + 3, z0 + 7);
        b.door(x0 + 9, gy + 1, z0 + 9, Direction.SOUTH, Blocks.SPRUCE_DOOR);
        b.set(x0 + 9, gy + 3, z0 + 9, Blocks.GRAY_CONCRETE);
        b.ladder(x0 + 16, gy + 1, gy + 7, z0 + 8, Direction.NORTH);
        // прогулочный дворик
        b.walls(px + 3, gy + 1, pz + 15, px + 9, gy + 3, pz + 20, Blocks.STONE_BRICKS);
        b.fill(px + 3, gy + 4, pz + 15, px + 9, gy + 4, pz + 20, Blocks.IRON_BARS);
        b.air(px + 4, gy + 1, pz + 16, px + 8, gy + 3, pz + 19);
        b.air(px + 6, gy + 1, pz + 15, px + 6, gy + 2, pz + 15);
        // вышка
        int tx = px + 20, tz = pz + 18;
        for (int[] o : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}})
            for (int y = gy + 1; y <= gy + 6; y++) b.set(tx + o[0], y, tz + o[1], Blocks.SPRUCE_FENCE);
        b.fill(tx - 1, gy + 7, tz - 1, tx + 3, gy + 7, tz + 3, Blocks.SPRUCE_PLANKS);
        b.walls(tx - 1, gy + 8, tz - 1, tx + 3, gy + 8, tz + 3, Blocks.SPRUCE_FENCE);
        b.fill(tx - 1, gy + 10, tz - 1, tx + 3, gy + 10, tz + 3, Blocks.SPRUCE_SLAB);
        for (int[] o : new int[][]{{-1, -1}, {3, -1}, {-1, 3}, {3, 3}}) b.set(tx + o[0], gy + 9, tz + o[1], Blocks.SPRUCE_FENCE);
        b.set(tx + 1, gy + 9, tz + 1, Blocks.LANTERN);
        for (int y = gy + 1; y <= gy + 6; y++) b.set(tx + 1, y, tz + 2, Blocks.SPRUCE_PLANKS);
        b.ladder(tx + 1, gy + 1, gy + 7, tz + 3, Direction.SOUTH);
        b.streetLamp(px + 2, gy + 1, pz + 13);
        b.streetLamp(px + 15, gy + 1, pz + 20);
        d.locs.put("sizo", new BlockPos(px + 13, gy + 1, pz + 16));
        d.locs.put("sizo_tower", new BlockPos(tx + 1, gy + 1, tz - 2));
        d.locs.put("sizo_exit", new BlockPos(gx, gy + 1, pz + 25));
    }

    public static void voenkomat(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 3, pz + 5, 18, 12, 2, gy, Blocks.WHITE_TERRACOTTA, Blocks.GREEN_CONCRETE, Blocks.POLISHED_ANDESITE,
                Blocks.GREEN_CONCRETE, Blocks.SPRUCE_DOOR,
                new String[]{"ВОЕННЫЙ"}, new String[]{"КОМИССАРИАТ"}, new String[]{"Отбор", "на контракт", "каб. 12"});
        desks(b, i, gy + 1, pz + 10);
        desks(b, i, gy + 5, pz + 10);
        b.fill(i.x1() - 5, gy + 1, i.z0() + 1, i.x1() - 1, gy + 2, i.z0() + 1, Blocks.WHITE_CONCRETE);
        b.sign(i.x1() - 3, gy + 2, i.z0() + 2, Direction.SOUTH, "Военно-", "врачебная", "комиссия");
        b.russianFlag(px + 4, gy + 9, pz + 16, 4);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        // плакат «Служба по контракту — твой выбор!»
        b.fill(px + 19, gy + 1, pz + 20, px + 19, gy + 3, pz + 20, Blocks.SPRUCE_FENCE);
        b.fill(px + 17, gy + 4, pz + 20, px + 21, gy + 5, pz + 20, Blocks.GREEN_WOOL);
        b.sign(px + 19, gy + 4, pz + 21, Direction.SOUTH, "Служба", "по контракту —", "достойный", "выбор!");
        d.locs.put("voenkomat", i.inside());
    }

    // =================================================================== криминальные точки и бизнес

    public static void restaurant(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 3, pz + 6, 18, 12, 2, gy, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GOLD_BLOCK, Blocks.DARK_OAK_PLANKS,
                Blocks.POLISHED_BLACKSTONE, Blocks.DARK_OAK_DOOR,
                new String[]{"Ресторан"}, new String[]{"«ИМПЕРИЯ»"}, new String[]{"Банкеты", "Караоке"});
        b.fill(i.doorX(), gy + 1, i.frontZ() + 1, i.doorX() + 1, gy + 1, pz + 22, Blocks.RED_CARPET);
        for (int x = px + 14; x <= px + 18; x += 4)
            for (int z = pz + 10; z <= pz + 14; z += 4) {
                b.set(x, gy + 1, z, Blocks.DARK_OAK_FENCE);
                b.set(x, gy + 2, z, Blocks.WHITE_CARPET);
                b.stair(x - 1, gy + 1, z, Blocks.DARK_OAK_STAIRS, Direction.EAST);
                b.stair(x + 1, gy + 1, z, Blocks.DARK_OAK_STAIRS, Direction.WEST);
            }
        // VIP-зал на втором этаже
        b.fill(px + 11, gy + 5, pz + 10, px + 16, gy + 5, pz + 11, topSlab(Blocks.DARK_OAK_SLAB));
        for (int x = px + 11; x <= px + 16; x++) {
            b.stair(x, gy + 5, pz + 9, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
            b.stair(x, gy + 5, pz + 12, Blocks.DARK_OAK_STAIRS, Direction.NORTH);
        }
        b.sign(px + 13, gy + 6, pz + 8, Direction.SOUTH, "VIP-зал", "Только", "для своих");
        b.set(px + 13, gy + 6, pz + 7, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(i.x1() - 1, gy + 5, i.z0() + 1, Blocks.CHEST);
        b.set(i.x1() - 1, gy + 1, i.z1() - 2, Blocks.JUKEBOX);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.POLISHED_BLACKSTONE);
        b.fill(px + 2, gy + 1, pz + 20, px + 4, gy + 1, pz + 21, Blocks.BLACK_CONCRETE);
        b.fill(px + 19, gy + 1, pz + 20, px + 21, gy + 1, pz + 21, Blocks.BLACK_CONCRETE);
        b.set(px + 3, gy + 2, pz + 20, Blocks.BLACK_STAINED_GLASS);
        b.set(px + 20, gy + 2, pz + 20, Blocks.BLACK_STAINED_GLASS);
        d.locs.put("restaurant", i.inside());
    }

    public static void cafe(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 4, pz + 7, 16, 11, 1, gy, Blocks.BRICKS, Blocks.ORANGE_TERRACOTTA, Blocks.SPRUCE_PLANKS,
                Blocks.RED_TERRACOTTA, Blocks.SPRUCE_DOOR,
                new String[]{"Кафе"}, new String[]{"«Шашлычный", "двор»"}, new String[]{"Шашлык", "Плов", "Лаваш"});
        for (int x = px + 6; x <= px + 17; x += 4) {
            b.set(x, gy + 1, pz + 12, Blocks.SPRUCE_FENCE);
            b.set(x, gy + 2, pz + 12, Blocks.RED_CARPET);
        }
        b.fill(px + 6, gy + 1, pz + 8, px + 12, gy + 1, pz + 8, topSlab(Blocks.SPRUCE_SLAB));
        b.set(px + 14, gy + 1, pz + 8, Blocks.SMOKER);
        b.set(px + 15, gy + 1, pz + 8, Blocks.SMOKER);
        b.fill(px + 2, gy, pz + 19, px + 21, gy, pz + 22, Blocks.SPRUCE_PLANKS);
        b.set(px + 3, gy + 1, pz + 21, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, false));
        for (int x = px + 6; x <= px + 18; x += 4) {
            b.set(x, gy + 1, pz + 21, Blocks.SPRUCE_FENCE);
            b.set(x, gy + 2, pz + 21, Blocks.WHITE_CARPET);
        }
        // навес над верандой
        for (int x = px + 2; x <= px + 21; x += 6) for (int y = gy + 1; y <= gy + 2; y++) b.set(x, y, pz + 22, Blocks.SPRUCE_FENCE);
        b.fill(px + 2, gy + 3, pz + 19, px + 21, gy + 3, pz + 22, Blocks.RED_WOOL);
        b.streetLamp(px + 1, gy + 1, pz + 18);
        b.streetLamp(px + 22, gy + 1, pz + 18);
        d.locs.put("cafe", i.inside());
    }

    /** Рюмочная «Встреча». */
    public static void bar(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 5, pz + 8, 14, 10, 1, gy, Blocks.BRICKS, Blocks.BROWN_TERRACOTTA, Blocks.SPRUCE_PLANKS,
                Blocks.GRAY_CONCRETE, Blocks.SPRUCE_DOOR,
                new String[]{"РЮМОЧНАЯ"}, new String[]{"«ВСТРЕЧА»"}, new String[]{"с 8:00", "до последнего", "клиента"});
        b.fill(px + 7, gy + 1, pz + 10, px + 16, gy + 1, pz + 10, topSlab(Blocks.DARK_OAK_SLAB));
        for (int x : new int[]{px + 7, px + 9, px + 14, px + 16}) b.set(x, gy + 1, pz + 12, Blocks.DARK_OAK_FENCE);
        b.fill(px + 7, gy + 1, pz + 9, px + 16, gy + 2, pz + 9, Blocks.BARREL);
        b.set(px + 17, gy + 1, pz + 15, Blocks.JUKEBOX);
        b.set(px + 6, gy + 1, pz + 15, Blocks.DARK_OAK_FENCE);
        b.set(px + 6, gy + 2, pz + 15, Blocks.BROWN_CARPET);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.COBBLESTONE);
        d.locs.put("bar", i.inside());
    }

    public static void market(B b, GorodData d, int px, int pz, int gy, RandomSource r) {
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.SMOOTH_STONE);
        Block[] roofs = {Blocks.RED_WOOL, Blocks.BLUE_WOOL, Blocks.GREEN_WOOL, Blocks.YELLOW_WOOL, Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL};
        Block[] goods = {Blocks.MELON, Blocks.PUMPKIN, Blocks.HAY_BLOCK, Blocks.BARREL, Blocks.COMPOSTER, Blocks.BEEHIVE};
        int n = 0;
        for (int sx = px + 2; sx <= px + 18; sx += 6)
            for (int sz = pz + 2; sz <= pz + 14; sz += 6) {
                for (int[] o : new int[][]{{0, 0}, {3, 0}, {0, 3}, {3, 3}})
                    for (int y = gy + 1; y <= gy + 2; y++) b.set(sx + o[0], y, sz + o[1], Blocks.OAK_FENCE);
                b.fill(sx, gy + 3, sz, sx + 3, gy + 3, sz + 3, roofs[n % roofs.length]);
                b.fill(sx + 1, gy + 1, sz + 3, sx + 2, gy + 1, sz + 3, topSlab(Blocks.OAK_SLAB));
                b.set(sx + 1, gy + 1, sz + 1, goods[(n + r.nextInt(3)) % goods.length]);
                b.set(sx + 2, gy + 1, sz + 1, goods[(n + 1) % goods.length]);
                n++;
            }
        // ворота рынка
        for (int y = gy + 1; y <= gy + 4; y++) {
            b.set(px + 9, y, pz + 22, Blocks.SPRUCE_LOG);
            b.set(px + 14, y, pz + 22, Blocks.SPRUCE_LOG);
        }
        b.fill(px + 9, gy + 5, pz + 22, px + 14, gy + 5, pz + 22, Blocks.SPRUCE_PLANKS);
        b.sign(px + 11, gy + 5, pz + 23, Direction.SOUTH, "РЫНОК");
        b.sign(px + 12, gy + 5, pz + 23, Direction.SOUTH, "«ЦЕНТРАЛЬНЫЙ»", "8:00–19:00");
        b.streetLamp(px + 1, gy + 1, pz + 21);
        b.streetLamp(px + 22, gy + 1, pz + 21);
        d.locs.put("market", new BlockPos(px + 11, gy + 1, pz + 13));
        d.roadSpots.add(new BlockPos(px + 6, gy + 1, pz + 20));
    }

    public static void shop(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 3, pz + 6, 18, 12, 2, gy, Blocks.BRICKS, Blocks.WHITE_CONCRETE, Blocks.WHITE_CONCRETE,
                Blocks.GRAY_CONCRETE, Blocks.OAK_DOOR,
                new String[]{"ПРОДУКТЫ"}, new String[]{"24 ЧАСА"}, new String[]{"АПТЕКА", "второй вход"});
        // витрины
        b.fill(px + 4, gy + 2, i.frontZ(), px + 7, gy + 3, i.frontZ(), Blocks.GLASS);
        b.fill(px + 16, gy + 2, i.frontZ(), px + 19, gy + 3, i.frontZ(), Blocks.GLASS);
        for (int z = pz + 10; z <= pz + 13; z += 3) b.fill(px + 14, gy + 1, z, px + 19, gy + 2, z, Blocks.BARREL);
        b.fill(px + 4, gy + 1, pz + 14, px + 8, gy + 1, pz + 14, topSlab(Blocks.SMOOTH_STONE_SLAB));
        b.set(px + 5, gy + 2, pz + 14, Blocks.GLASS_PANE);
        b.sign(px + 6, gy + 2, pz + 15, Direction.SOUTH, "Касса");
        b.set(px + 6, gy + 2, pz + 14, Blocks.WHITE_CONCRETE);
        // квартиры над магазином
        b.bed(px + 12, gy + 5, pz + 10, Direction.NORTH, Blocks.BLUE_BED);
        b.bed(px + 16, gy + 5, pz + 10, Direction.NORTH, Blocks.BLUE_BED);
        d.homes.add(new BlockPos(px + 14, gy + 5, pz + 13));
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        d.locs.put("shop", i.inside());
        d.roadSpots.add(new BlockPos(px + 6, gy + 1, pz + 21));
    }

    public static void bank(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 2, pz + 4, 20, 13, 2, gy, Blocks.POLISHED_GRANITE, Blocks.QUARTZ_BLOCK, Blocks.POLISHED_DIORITE,
                Blocks.SMOOTH_QUARTZ, Blocks.DARK_OAK_DOOR,
                new String[]{"БАНК"}, new String[]{"«НАРОДНЫЙ»"}, new String[]{"Лицензия ЦБ РФ", "№ 1481"});
        // операционные окна за стеклом
        b.fill(px + 9, gy + 1, pz + 10, px + 20, gy + 1, pz + 10, topSlab(Blocks.SMOOTH_QUARTZ_SLAB));
        b.fill(px + 9, gy + 2, pz + 10, px + 20, gy + 3, pz + 10, Blocks.GLASS_PANE);
        for (int x = px + 10; x <= px + 19; x += 3) b.set(x, gy + 2, pz + 10, Blocks.AIR);
        // хранилище
        b.walls(px + 15, gy + 1, pz + 5, px + 20, gy + 3, pz + 8, Blocks.IRON_BLOCK);
        b.fill(px + 16, gy + 1, pz + 6, px + 19, gy + 1, pz + 7, Blocks.GOLD_BLOCK);
        b.door(px + 17, gy + 1, pz + 8, Direction.SOUTH, Blocks.IRON_DOOR);
        b.set(px + 16, gy + 2, pz + 9, Blocks.STONE_BUTTON.defaultBlockState().setValue(net.minecraft.world.level.block.ButtonBlock.FACING, Direction.SOUTH));
        b.sign(px + 18, gy + 3, pz + 9, Direction.SOUTH, "ХРАНИЛИЩЕ");
        // банкомат на фасаде
        b.set(i.x1() - 1, gy + 2, i.frontZ(), Blocks.DISPENSER);
        b.sign(i.x1() - 1, gy + 3, i.frontZ() + 1, Direction.SOUTH, "БАНКОМАТ");
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.POLISHED_GRANITE);
        d.locs.put("bank", i.inside());
    }

    public static void mfc(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 2, pz + 5, 20, 12, 2, gy, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.WHITE_CONCRETE, Blocks.SMOOTH_QUARTZ,
                Blocks.WHITE_CONCRETE, Blocks.BIRCH_DOOR,
                new String[]{"МФЦ"}, new String[]{"«МОИ", "ДОКУМЕНТЫ»"}, new String[]{"Госуслуги", "пн–сб", "8:00–20:00"});
        for (int x = px + 10; x <= px + 20; x += 2) {
            b.set(x, gy + 1, pz + 9, topSlab(Blocks.SMOOTH_QUARTZ_SLAB));
            b.stair(x, gy + 1, pz + 10, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        }
        b.sign(px + 15, gy + 2, pz + 8, Direction.SOUTH, "Окна 1–6", "Электронная", "очередь");
        b.set(px + 15, gy + 2, pz + 7, Blocks.WHITE_CONCRETE);
        // зал ожидания
        for (int x = px + 4; x <= px + 8; x++) b.stair(x, gy + 1, pz + 14, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        b.set(px + 9, gy + 1, pz + 14, Blocks.LECTERN);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        d.locs.put("mfc", i.inside());
    }

    public static void garages(B b, GorodData d, int px, int pz, int gy, RandomSource r) {
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.GRAVEL);
        Block[] graffiti = {Blocks.RED_CONCRETE, Blocks.LIME_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.BLACK_CONCRETE};
        for (int row = 0; row < 2; row++) {
            int z0 = row == 0 ? pz + 1 : pz + 13;
            Direction face = row == 0 ? Direction.SOUTH : Direction.NORTH;
            for (int k = 0; k < 5; k++) {
                int x0 = px + 1 + k * 4;
                b.walls(x0, gy + 1, z0, x0 + 4, gy + 3, z0 + 5, Blocks.BRICKS);
                b.fill(x0, gy + 4, z0, x0 + 4, gy + 4, z0 + 5, Blocks.GRAY_CONCRETE);
                b.air(x0 + 1, gy + 1, z0 + 1, x0 + 3, gy + 3, z0 + 4);
                int fz = face == Direction.SOUTH ? z0 + 5 : z0;
                b.air(x0 + 1, gy + 1, fz, x0 + 3, gy + 2, fz);
                if (k % 2 == 0) b.fill(x0 + 1, gy + 1, fz, x0 + 3, gy + 2, fz, Blocks.IRON_BLOCK);
                for (int g = 0; g < 3; g++)
                    b.set(x0 + r.nextInt(5), gy + 1 + r.nextInt(3), face == Direction.SOUTH ? z0 : z0 + 5, graffiti[r.nextInt(graffiti.length)]);
            }
        }
        b.set(px + 22, gy + 1, pz + 15, Blocks.BARREL);
        b.set(px + 21, gy + 1, pz + 17, Blocks.CHEST);
        b.sign(px + 12, gy + 1, pz + 12, Direction.SOUTH, "ГСК «Мотор»", "Председатель:", "гараж №7");
        b.set(px + 12, gy + 1, pz + 11, Blocks.BRICKS);
        b.streetLamp(px + 11, gy + 1, pz + 9);
        d.locs.put("garages", new BlockPos(px + 11, gy + 1, pz + 10));
        d.roadSpots.add(new BlockPos(px + 4, gy + 1, pz + 9));
    }

    public static void bikers(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 4, pz + 6, 16, 12, 1, gy, Blocks.DARK_OAK_PLANKS, Blocks.BLACK_CONCRETE, Blocks.SPRUCE_PLANKS,
                Blocks.BLACK_CONCRETE, Blocks.DARK_OAK_DOOR,
                new String[]{"МОТОКЛУБ"}, new String[]{"«ЖЕЛЕЗНЫЕ", "ПСЫ»"}, new String[]{"Только", "для членов", "клуба"});
        b.fill(px + 6, gy + 1, pz + 8, px + 13, gy + 1, pz + 8, topSlab(Blocks.DARK_OAK_SLAB));
        b.set(px + 15, gy + 1, pz + 8, Blocks.BARREL);
        b.set(px + 16, gy + 1, pz + 8, Blocks.BARREL);
        b.set(px + 17, gy + 1, pz + 13, Blocks.ANVIL);
        b.set(px + 6, gy + 1, pz + 13, Blocks.CHEST);
        b.set(px + 10, gy + 1, pz + 12, Blocks.DARK_OAK_FENCE);
        b.set(px + 10, gy + 2, pz + 12, Blocks.BLACK_CARPET);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.GRAY_CONCRETE);
        for (int x = px + 3; x <= px + 20; x += 3) {
            b.set(x, gy + 1, pz + 20, Blocks.BLACK_CONCRETE);
            b.set(x, gy + 1, pz + 21, Blocks.IRON_BARS);
        }
        d.locs.put("bikers", i.inside());
    }

    public static void warehouse(B b, GorodData d, int px, int pz, int gy) {
        int x0 = px + 2, z0 = pz + 3;
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.SMOOTH_STONE);
        b.walls(x0, gy + 1, z0, x0 + 19, gy + 6, z0 + 15, Blocks.LIGHT_GRAY_CONCRETE);
        b.walls(x0, gy + 1, z0, x0 + 19, gy + 1, z0 + 15, Blocks.GRAY_CONCRETE);
        for (int k = 0; k < 6; k++) b.fill(x0 - 1 + k, gy + 7 + k / 2, z0, x0 + 20 - k, gy + 7 + k / 2, z0 + 15, Blocks.IRON_BLOCK);
        b.air(x0 + 1, gy + 1, z0 + 1, x0 + 18, gy + 6, z0 + 14);
        b.air(x0 + 8, gy + 1, z0 + 15, x0 + 11, gy + 4, z0 + 15);
        for (int x = x0 + 2; x <= x0 + 17; x += 3)
            for (int z = z0 + 2; z <= z0 + 10; z += 4) {
                b.fill(x, gy + 1, z, x + 1, gy + 1 + Math.floorMod(x, 3), z + 1, Blocks.BARREL);
            }
        for (int x = x0 + 3; x <= x0 + 16; x += 5) b.hangingLantern(x, gy + 6, z0 + 7);
        b.set(x0 + 7, gy + 5, z0 + 15, Blocks.LIGHT_GRAY_CONCRETE);
        b.sign(x0 + 7, gy + 5, z0 + 16, Direction.SOUTH, "СКЛАД №3", "ООО «Логистик»", "Приём грузов", "8:00–20:00");
        // погрузочная рампа и фура
        b.fill(x0 + 7, gy + 1, z0 + 16, x0 + 12, gy + 1, z0 + 17, Blocks.SMOOTH_STONE_SLAB);
        b.fill(x0 + 13, gy + 1, z0 + 17, x0 + 18, gy + 3, z0 + 19, Blocks.WHITE_CONCRETE);
        b.fill(x0 + 13, gy + 1, z0 + 19, x0 + 14, gy + 2, z0 + 19, Blocks.BLUE_CONCRETE);
        b.streetLamp(px + 1, gy + 1, pz + 22);
        d.locs.put("warehouse", new BlockPos(x0 + 9, gy + 1, z0 + 12));
    }

    /** Машиностроительный завод: цех со станками, кран-балка, дымящая труба, проходная. */
    public static void factory(B b, GorodData d, int px, int pz, int gy) {
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.GRAY_CONCRETE);
        int x0 = px + 1, z0 = pz + 1, x1 = px + 22, z1 = pz + 16;
        b.walls(x0, gy + 1, z0, x1, gy + 8, z1, Blocks.BRICKS);
        b.air(x0 + 1, gy + 1, z0 + 1, x1 - 1, gy + 8, z1 - 1);
        // пилообразная крыша с фонарями
        for (int x = x0; x <= x1; x++) {
            int k = (x - x0) % 4;
            b.fill(x, gy + 9 + (k == 3 ? 0 : k), z0, x, gy + 9 + (k == 3 ? 0 : k), z1, k == 3 ? Blocks.GLASS : Blocks.GRAY_CONCRETE);
            if (k > 0 && k < 3) b.fill(x, gy + 9, z0, x, gy + 8 + k, z1, Blocks.AIR);
        }
        for (int x = x0 + 2; x < x1; x += 3) b.set(x, gy + 5, z0, Blocks.GLASS_PANE);
        for (int x = x0 + 2; x < x1; x += 3) b.set(x, gy + 5, z1, Blocks.GLASS_PANE);
        // станки
        Block[] machines = {Blocks.BLAST_FURNACE, Blocks.SMITHING_TABLE, Blocks.STONECUTTER, Blocks.ANVIL, Blocks.GRINDSTONE};
        int n = 0;
        for (int x = x0 + 3; x <= x1 - 3; x += 3)
            for (int z = z0 + 4; z <= z1 - 4; z += 5) b.set(x, gy + 1, z, machines[n++ % machines.length]);
        // кран-балка
        b.fill(x0 + 1, gy + 7, z0 + 7, x1 - 1, gy + 7, z0 + 7, Blocks.YELLOW_CONCRETE);
        b.set(x0 + 9, gy + 6, z0 + 7, Blocks.CHAIN);
        b.set(x0 + 9, gy + 5, z0 + 7, Blocks.CHAIN);
        b.set(x0 + 9, gy + 4, z0 + 7, Blocks.IRON_BLOCK);
        for (int x = x0 + 4; x <= x1 - 4; x += 6) b.hangingLantern(x, gy + 8, z0 + 10);
        // ворота цеха
        b.air(x0 + 9, gy + 1, z1, x0 + 12, gy + 4, z1);
        // труба
        for (int y = gy + 1; y <= gy + 22; y++) {
            b.fill(x1 - 3, y, z0 - 0, x1 - 2, y, z0 + 1, y % 6 < 3 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
        }
        b.set(x1 - 3, gy + 23, z0, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
        // проходная
        b.walls(px + 2, gy + 1, pz + 19, px + 6, gy + 3, pz + 22, Blocks.WHITE_CONCRETE);
        b.fill(px + 2, gy + 4, pz + 19, px + 6, gy + 4, pz + 22, Blocks.GRAY_CONCRETE);
        b.door(px + 4, gy + 1, pz + 22, Direction.SOUTH, Blocks.SPRUCE_DOOR);
        b.door(px + 4, gy + 1, pz + 19, Direction.NORTH, Blocks.SPRUCE_DOOR);
        b.set(px + 4, gy + 4, pz + 23, Blocks.AIR);
        b.sign(px + 5, gy + 3, pz + 23, Direction.SOUTH, "ПРОХОДНАЯ");
        b.set(px + 9, gy + 6, z1 + 0, Blocks.BRICKS);
        b.sign(x0 + 10, gy + 6, z1 + 1, Direction.SOUTH, "АО «БЕРЁЗОВСКИЙ", "МАШИНО-", "СТРОИТЕЛЬНЫЙ", "ЗАВОД»");
        b.set(x0 + 10, gy + 6, z1, Blocks.BRICKS);
        b.streetLamp(px + 22, gy + 1, pz + 22);
        d.locs.put("factory", new BlockPos(x0 + 10, gy + 1, z0 + 10));
    }

    /** АЗС: навес на опорах, колонки, магазин-павильон. */
    public static void azs(B b, GorodData d, int px, int pz, int gy) {
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.GRAY_CONCRETE);
        for (int[] o : new int[][]{{4, 6}, {19, 6}, {4, 15}, {19, 15}})
            for (int y = gy + 1; y <= gy + 4; y++) b.set(px + o[0], y, pz + o[1], Blocks.WHITE_CONCRETE);
        b.fill(px + 3, gy + 5, pz + 5, px + 20, gy + 5, pz + 16, Blocks.WHITE_CONCRETE);
        b.walls(px + 3, gy + 5, pz + 5, px + 20, gy + 5, pz + 16, Blocks.RED_CONCRETE);
        for (int x = px + 6; x <= px + 6 + 12; x += 6) {
            b.set(x, gy + 1, pz + 10, Blocks.CAULDRON);
            b.set(x, gy + 2, pz + 10, Blocks.IRON_BARS);
            b.set(x, gy + 1, pz + 11, Blocks.RED_CONCRETE);
            b.set(x, gy + 2, pz + 11, Blocks.REDSTONE_LAMP);
        }
        for (int x = px + 5; x <= px + 18; x += 4) b.hangingLantern(x, gy + 4, pz + 8);
        // павильон
        b.walls(px + 8, gy + 1, pz + 18, px + 15, gy + 3, pz + 22, Blocks.WHITE_CONCRETE);
        b.fill(px + 8, gy + 4, pz + 18, px + 15, gy + 4, pz + 22, Blocks.RED_CONCRETE);
        b.fill(px + 9, gy + 2, pz + 18, px + 14, gy + 2, pz + 18, Blocks.GLASS_PANE);
        b.door(px + 11, gy + 1, pz + 18, Direction.NORTH, Blocks.OAK_DOOR);
        b.fill(px + 9, gy + 1, pz + 21, px + 14, gy + 1, pz + 21, topSlab(Blocks.SMOOTH_STONE_SLAB));
        // стела с ценами
        for (int y = gy + 1; y <= gy + 6; y++) b.set(px + 1, y, pz + 2, Blocks.RED_CONCRETE);
        b.set(px + 1, gy + 5, pz + 3, Blocks.WHITE_CONCRETE);
        b.sign(px + 2, gy + 5, pz + 2, Direction.EAST, "АЗС", "АИ-92  55,90", "АИ-95  61,20", "ДТ     68,40");
        d.locs.put("azs", new BlockPos(px + 11, gy + 1, pz + 13));
    }

    // =================================================================== социальные объекты

    public static void park(B b, GorodData d, int px, int pz, int gy, RandomSource r, boolean main) {
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.GRASS_BLOCK);
        b.fill(px + 11, gy, pz, px + 12, gy, pz + 23, Blocks.DIRT_PATH);
        b.fill(px, gy, pz + 11, px + 23, gy, pz + 12, Blocks.DIRT_PATH);
        if (main) {
            // памятник — стела Победы с Вечным огнём
            b.fill(px + 9, gy, pz + 9, px + 14, gy, pz + 14, Blocks.POLISHED_GRANITE);
            b.fill(px + 11, gy + 1, pz + 11, px + 12, gy + 6, pz + 12, Blocks.POLISHED_ANDESITE);
            b.set(px + 11, gy + 7, pz + 11, Blocks.GOLD_BLOCK);
            b.set(px + 11, gy + 1, pz + 13, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, false));
            b.sign(px + 12, gy + 2, pz + 13, Direction.SOUTH, "Защитникам", "Отечества", "1941–1945", "Вечная память");
            b.set(px + 12, gy + 2, pz + 12, Blocks.POLISHED_ANDESITE);
            b.plant(px + 10, gy + 1, pz + 13, 2);
            b.plant(px + 13, gy + 1, pz + 13, 2);
        } else {
            // аллея с беседкой
            for (int[] o : new int[][]{{9, 9}, {14, 9}, {9, 14}, {14, 14}})
                for (int y = gy + 1; y <= gy + 3; y++) b.set(px + o[0], y, pz + o[1], Blocks.OAK_FENCE);
            b.fill(px + 8, gy + 4, pz + 8, px + 15, gy + 4, pz + 15, Blocks.OAK_SLAB);
            b.fill(px + 10, gy + 5, pz + 10, px + 13, gy + 5, pz + 13, Blocks.OAK_SLAB);
            b.bench(px + 10, gy + 1, pz + 10, Direction.SOUTH, 3);
        }
        for (int k = 0; k < 10; k++) {
            int x = px + 2 + r.nextInt(20), z = pz + 2 + r.nextInt(20);
            if (Math.abs(x - (px + 11)) < 4 || Math.abs(z - (pz + 11)) < 4) continue;
            if (r.nextInt(3) == 0) b.spruce(x, gy + 1, z);
            else b.tree(x, gy + 1, z, r.nextBoolean() ? Blocks.BIRCH_LOG : Blocks.OAK_LOG, r.nextBoolean() ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES);
        }
        // цветники
        for (int x = px + 2; x <= px + 21; x += 4) {
            b.set(x, gy + 1, pz + 10, Blocks.POPPY);
            b.set(x + 1, gy + 1, pz + 13, Blocks.DANDELION);
        }
        b.bench(px + 8, gy + 1, pz + 10, Direction.SOUTH, 2);
        b.bench(px + 14, gy + 1, pz + 13, Direction.NORTH, 2);
        b.streetLamp(px + 10, gy + 1, pz + 2);
        b.streetLamp(px + 13, gy + 1, pz + 21);
        b.streetLamp(px + 2, gy + 1, pz + 13);
        b.streetLamp(px + 21, gy + 1, pz + 10);
        // детская площадка
        b.fill(px + 16, gy, pz + 16, px + 21, gy, pz + 21, Blocks.SAND);
        b.set(px + 17, gy + 1, pz + 17, Blocks.OAK_FENCE);
        b.set(px + 17, gy + 2, pz + 17, Blocks.OAK_FENCE);
        b.set(px + 20, gy + 1, pz + 17, Blocks.OAK_FENCE);
        b.set(px + 20, gy + 2, pz + 17, Blocks.OAK_FENCE);
        b.fill(px + 17, gy + 3, pz + 17, px + 20, gy + 3, pz + 17, Blocks.OAK_SLAB);
        if (main) d.locs.put("park", new BlockPos(px + 11, gy + 1, pz + 16));
        else d.locs.put("park2", new BlockPos(px + 11, gy + 1, pz + 16));
        d.roadSpots.add(new BlockPos(px + 11, gy + 1, pz + 5));
        d.roadSpots.add(new BlockPos(px + 5, gy + 1, pz + 12));
    }

    /** Сквер с фонтаном. */
    public static void fountainSquare(B b, GorodData d, int px, int pz, int gy) {
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.POLISHED_ANDESITE);
        for (int x = px; x <= px + 23; x++)
            for (int z = pz; z <= pz + 23; z++)
                if ((x + z) % 4 == 0) b.set(x, gy, z, Blocks.STONE_BRICKS);
        // фонтан
        int cx = px + 11, cz = pz + 11;
        for (int dx = -4; dx <= 5; dx++)
            for (int dz = -4; dz <= 5; dz++) {
                double rr = Math.hypot(dx - 0.5, dz - 0.5);
                if (rr < 5.0 && rr >= 4.0) b.set(cx + dx, gy + 1, cz + dz, Blocks.SMOOTH_STONE_SLAB);
                else if (rr < 4.0) b.set(cx + dx, gy, cz + dz, Blocks.WATER);
            }
        b.fill(cx, gy, cz, cx + 1, gy + 3, cz + 1, Blocks.QUARTZ_PILLAR);
        b.set(cx, gy + 4, cz, Blocks.WATER);
        // клумбы и лавки по периметру
        for (int k = 0; k < 4; k++) {
            int x = k % 2 == 0 ? px + 3 : px + 19, z = k < 2 ? pz + 3 : pz + 19;
            b.fill(x - 1, gy, z - 1, x + 1, gy, z + 1, Blocks.GRASS_BLOCK);
            b.set(x, gy + 1, z, Blocks.ALLIUM);
            b.tree(x, gy + 1, z + (k < 2 ? 3 : -3), Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        }
        b.bench(px + 9, gy + 1, pz + 4, Direction.SOUTH, 6);
        b.bench(px + 9, gy + 1, pz + 19, Direction.NORTH, 6);
        b.streetLamp(px + 6, gy + 1, pz + 6);
        b.streetLamp(px + 17, gy + 1, pz + 6);
        b.streetLamp(px + 6, gy + 1, pz + 17);
        b.streetLamp(px + 17, gy + 1, pz + 17);
        d.locs.put("fountain", new BlockPos(px + 11, gy + 1, pz + 18));
    }

    /** Православный храм с колокольней. */
    public static void church(B b, GorodData d, int px, int pz, int gy) {
        int x0 = px + 6, z0 = pz + 4, w = 12, dep = 15;
        int x1 = x0 + w - 1, z1 = z0 + dep - 1;
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.GRASS_BLOCK);
        b.fill(x0 - 1, gy, z0 - 1, x1 + 1, gy, z1 + 3, Blocks.SMOOTH_STONE);
        b.air(x0, gy + 1, z0, x1, gy + 26, z1);
        b.walls(x0, gy + 1, z0, x1, gy + 9, z1, Blocks.WHITE_CONCRETE);
        b.fill(x0, gy + 10, z0, x1, gy + 10, z1, Blocks.WHITE_CONCRETE);
        b.walls(x0, gy + 10, z0, x1, gy + 10, z1, Blocks.SMOOTH_QUARTZ);
        for (int z = z0 + 2; z <= z1 - 2; z += 3) {
            b.fill(x0, gy + 4, z, x0, gy + 7, z, Blocks.GLASS_PANE);
            b.fill(x1, gy + 4, z, x1, gy + 7, z, Blocks.GLASS_PANE);
        }
        // закомары (полукруглые завершения стен)
        for (int z = z0; z <= z1; z += 3) {
            b.set(x0, gy + 11, z, Blocks.SMOOTH_QUARTZ_SLAB);
            b.set(x1, gy + 11, z, Blocks.SMOOTH_QUARTZ_SLAB);
        }
        int cx = x0 + w / 2, cz = z0 + dep / 2;
        b.walls(cx - 2, gy + 11, cz - 2, cx + 2, gy + 14, cz + 2, Blocks.WHITE_CONCRETE);
        for (int y = gy + 12; y <= gy + 13; y++) {
            b.set(cx, y, cz - 2, Blocks.GLASS_PANE);
            b.set(cx, y, cz + 2, Blocks.GLASS_PANE);
        }
        int[] radii = {3, 3, 3, 2, 2, 1, 1, 0};
        for (int k = 0; k < radii.length; k++) {
            int rr = radii[k];
            for (int dx = -rr; dx <= rr; dx++)
                for (int dz = -rr; dz <= rr; dz++)
                    if (dx * dx + dz * dz <= rr * rr + 1) b.set(cx + dx, gy + 15 + k, cz + dz, Blocks.GOLD_BLOCK);
        }
        int ct = gy + 15 + radii.length;
        for (int y = ct; y < ct + 4; y++) b.set(cx, y, cz, Blocks.GOLD_BLOCK);
        b.set(cx - 1, ct + 2, cz, Blocks.GOLD_BLOCK);
        b.set(cx + 1, ct + 2, cz, Blocks.GOLD_BLOCK);
        // колокольня над входом
        int bz = z1 + 1;
        b.walls(cx - 2, gy + 1, bz, cx + 2, gy + 15, bz + 3, Blocks.WHITE_CONCRETE);
        b.air(cx - 1, gy + 1, bz + 1, cx + 1, gy + 15, bz + 2);
        b.air(cx - 1, gy + 11, bz, cx + 1, gy + 13, bz + 3);
        b.air(cx - 2, gy + 11, bz + 1, cx + 2, gy + 13, bz + 2);
        b.fill(cx - 1, gy + 10, bz + 1, cx + 1, gy + 10, bz + 2, Blocks.SPRUCE_PLANKS);
        b.set(cx, gy + 11, bz + 1, Blocks.BELL);
        b.fill(cx - 2, gy + 16, bz, cx + 2, gy + 16, bz + 3, Blocks.WHITE_CONCRETE);
        for (int y = gy + 17; y <= gy + 19; y++) b.set(cx, y, bz + 1, Blocks.GOLD_BLOCK);
        b.set(cx, gy + 20, bz + 1, Blocks.GOLD_BLOCK);
        b.door(cx, gy + 1, bz + 3, Direction.SOUTH, Blocks.SPRUCE_DOOR);
        b.air(cx, gy + 1, z1, cx, gy + 2, z1);
        b.set(cx, gy + 3, bz + 3, Blocks.WHITE_CONCRETE);
        // иконостас и подсвечники
        b.fill(x0 + 2, gy + 1, z0 + 1, x1 - 2, gy + 1, z0 + 1, Blocks.GOLD_BLOCK);
        b.fill(x0 + 2, gy + 2, z0 + 1, x1 - 2, gy + 5, z0 + 1, Blocks.YELLOW_TERRACOTTA);
        for (int x = x0 + 3; x <= x1 - 3; x += 2) b.set(x, gy + 3, z0 + 1, Blocks.ORANGE_TERRACOTTA);
        for (int z = z0 + 4; z < z1 - 1; z += 3) {
            b.hangingLantern(cx, gy + 9, z);
            b.set(x0 + 2, gy + 1, z, Blocks.CANDLE);
            b.set(x1 - 2, gy + 1, z, Blocks.CANDLE);
        }
        b.set(cx + 3, gy + 3, bz + 3, Blocks.WHITE_CONCRETE);
        b.sign(cx + 3, gy + 3, bz + 4, Direction.SOUTH, "Храм", "Святителя", "Николая", "Чудотворца");
        b.spruce(px + 2, gy + 1, pz + 18);
        b.spruce(px + 21, gy + 1, pz + 18);
        b.streetLamp(cx - 4, gy + 1, z1 + 5);
        d.locs.put("church", new BlockPos(cx, gy + 1, z1 - 3));
    }

    /** Юридический институт: колонны, большая аудитория 101 с амфитеатром, библиотека, деканат. */
    public static void university(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 1, pz + 3, 22, 14, 3, gy, Blocks.BRICKS, Blocks.SMOOTH_QUARTZ, Blocks.OAK_PLANKS,
                Blocks.GRAY_CONCRETE, Blocks.DARK_OAK_DOOR,
                new String[]{"ЮРИДИЧЕСКИЙ"}, new String[]{"ИНСТИТУТ"}, new String[]{"Приёмная", "комиссия", "ауд. 101"});
        // аудитория 101: амфитеатр от кафедры к входу
        int y = gy + 1;
        b.fill(px + 9, y + 1, pz + 4, px + 20, y + 2, pz + 4, Blocks.GREEN_CONCRETE);
        b.sign(px + 14, y + 3, pz + 5, Direction.SOUTH, "Аудитория 101");
        b.set(px + 14, y + 3, pz + 4, Blocks.BRICKS);
        b.set(px + 14, y, pz + 6, Blocks.LECTERN);
        for (int row = 0; row < 3; row++) {
            int z = pz + 9 + row * 2;
            for (int x = px + 8; x <= px + 21; x++) {
                if (x == px + 11 || x == px + 12) continue;
                b.stair(x, y, z, Blocks.OAK_STAIRS, Direction.NORTH);
                b.set(x, y, z - 1, topSlab(Blocks.OAK_SLAB));
            }
        }
        // библиотека и деканат
        archive(b, i, gy + 5);
        archive(b, i, gy + 9);
        desks(b, i, gy + 5, pz + 9);
        desks(b, i, gy + 9, pz + 9);
        b.sign(i.x1() - 3, gy + 6, i.z0() + 2, Direction.SOUTH, "Библиотека");
        for (int x = px + 4; x <= px + 19; x += 5) {
            b.set(x, gy + 1, pz + 22, Blocks.QUARTZ_PILLAR);
            b.set(x, gy + 2, pz + 22, Blocks.QUARTZ_PILLAR);
        }
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        b.sign(px + 12, gy + 2, pz + 23, Direction.SOUTH, "Поступление", "G → «Юрфак»");
        b.set(px + 12, gy + 2, pz + 22, Blocks.QUARTZ_PILLAR);
        b.set(px + 12, gy + 1, pz + 22, Blocks.QUARTZ_PILLAR);
        d.locs.put("university", i.inside());
        d.locs.put("auditorium", new BlockPos(px + 12, y, pz + 10));
    }

    public static void school(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 1, pz + 2, 22, 12, 3, gy, Blocks.YELLOW_TERRACOTTA, Blocks.WHITE_CONCRETE, Blocks.OAK_PLANKS,
                Blocks.GRAY_CONCRETE, Blocks.OAK_DOOR,
                new String[]{"МБОУ «СОШ"}, new String[]{"№ 1»"}, new String[]{"Школа", "им. Гагарина"});
        for (int f = 0; f < 3; f++) {
            int y = gy + 1 + 4 * f;
            b.fill(i.x0() + 9, y + 1, i.z0() + 1, i.x1() - 2, y + 2, i.z0() + 1, Blocks.GREEN_CONCRETE);
            desks(b, i, y, i.z0() + 5);
            desks(b, i, y, i.z0() + 8);
        }
        b.fill(px + 1, gy, pz + 16, px + 22, gy, pz + 23, Blocks.RED_TERRACOTTA);
        b.fill(px + 3, gy, pz + 18, px + 20, gy, pz + 21, Blocks.GRASS_BLOCK);
        b.fill(px + 3, gy + 1, pz + 19, px + 3, gy + 2, pz + 20, Blocks.WHITE_CONCRETE);
        b.fill(px + 20, gy + 1, pz + 19, px + 20, gy + 2, pz + 20, Blocks.WHITE_CONCRETE);
        b.russianFlag(px + 2, gy + 1, pz + 15, 8);
        d.locs.put("school", i.inside());
    }

    public static void kindergarten(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 3, pz + 3, 18, 10, 2, gy, Blocks.WHITE_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.OAK_PLANKS,
                Blocks.RED_CONCRETE, Blocks.OAK_DOOR,
                new String[]{"Детский сад"}, new String[]{"№ 5", "«Солнышко»"});
        for (int x = i.x0() + 8; x < i.x1(); x += 2) b.bed(x, gy + 5, i.z0() + 3, Direction.NORTH, Blocks.YELLOW_BED);
        b.fill(px + 1, gy, pz + 15, px + 22, gy, pz + 22, Blocks.GRASS_BLOCK);
        b.fill(px + 3, gy, pz + 16, px + 7, gy, pz + 20, Blocks.SAND);
        for (int x = px + 12; x <= px + 18; x += 3) {
            b.set(x, gy + 1, pz + 18, Blocks.OAK_FENCE);
            b.set(x, gy + 2, pz + 18, Blocks.OAK_FENCE);
            b.set(x, gy + 3, pz + 18, Blocks.RED_WOOL);
        }
        b.walls(px, gy + 1, pz + 14, px + 23, gy + 1, pz + 23, Blocks.OAK_FENCE);
        b.set(px + 11, gy + 1, pz + 23, Blocks.AIR);
        d.locs.put("kindergarten", i.inside());
    }

    public static void post(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 4, pz + 7, 16, 11, 2, gy, Blocks.WHITE_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.SMOOTH_STONE,
                Blocks.GRAY_CONCRETE, Blocks.SPRUCE_DOOR,
                new String[]{"ПОЧТА"}, new String[]{"РОССИИ"}, new String[]{"Индекс", "123456"});
        b.fill(px + 12, gy + 1, pz + 13, px + 18, gy + 1, pz + 13, topSlab(Blocks.SMOOTH_STONE_SLAB));
        b.fill(px + 12, gy + 1, pz + 10, px + 18, gy + 2, pz + 10, Blocks.BARREL);
        b.bed(px + 13, gy + 5, pz + 11, Direction.NORTH, Blocks.LIGHT_BLUE_BED);
        d.homes.add(new BlockPos(px + 15, gy + 5, pz + 13));
        b.set(px + 20, gy + 1, pz + 19, Blocks.BLUE_CONCRETE);
        b.sign(px + 20, gy + 2, pz + 19, Direction.SOUTH, "Почтовый", "ящик");
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        d.locs.put("post", i.inside());
    }

    /** Городская больница: красный крест на фасаде, приёмный покой, палаты, «скорая» у входа. */
    public static void hospital(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 1, pz + 2, 22, 14, 3, gy, Blocks.WHITE_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.SMOOTH_QUARTZ,
                Blocks.LIGHT_GRAY_CONCRETE, Blocks.BIRCH_DOOR,
                new String[]{"ГОРОДСКАЯ"}, new String[]{"БОЛЬНИЦА"}, new String[]{"Приёмный", "покой", "24/7"});
        // красный крест
        int cy = gy + 9, cx = i.x1() - 4;
        b.fill(cx - 1, cy, i.frontZ() + 1, cx + 1, cy, i.frontZ() + 1, Blocks.RED_CONCRETE);
        b.fill(cx, cy - 1, i.frontZ() + 1, cx, cy + 1, i.frontZ() + 1, Blocks.RED_CONCRETE);
        // приёмный покой
        b.fill(px + 3, gy + 1, pz + 12, px + 8, gy + 1, pz + 12, topSlab(Blocks.SMOOTH_QUARTZ_SLAB));
        b.sign(px + 5, gy + 2, pz + 13, Direction.SOUTH, "Регистратура");
        b.set(px + 5, gy + 2, pz + 12, Blocks.WHITE_CONCRETE);
        for (int x = px + 15; x <= px + 20; x++) b.stair(x, gy + 1, pz + 12, Blocks.QUARTZ_STAIRS, Direction.NORTH);
        b.set(i.x1() - 1, gy + 1, i.z0() + 1, Blocks.BREWING_STAND);
        // палаты на 2–3 этажах
        for (int f = 1; f < 3; f++) {
            int y = gy + 1 + 4 * f;
            for (int x = i.x0() + 8; x < i.x1() - 1; x += 3) {
                b.bed(x, y, i.z0() + 3, Direction.NORTH, Blocks.WHITE_BED);
                b.set(x + 1, y, i.z0() + 1, Blocks.CAULDRON);
            }
        }
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        // «скорая»
        b.fill(px + 16, gy + 1, pz + 20, px + 19, gy + 2, pz + 21, Blocks.WHITE_CONCRETE);
        b.set(px + 17, gy + 3, pz + 20, Blocks.RED_STAINED_GLASS);
        b.set(px + 18, gy + 3, pz + 20, Blocks.BLUE_STAINED_GLASS);
        b.set(px + 16, gy + 2, pz + 20, Blocks.RED_CONCRETE);
        d.locs.put("hospital", i.inside());
    }

    /** Дом культуры: портик, зрительный зал со сценой и красным занавесом, афиши. */
    public static void dk(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 1, pz + 2, 22, 15, 2, gy, Blocks.SMOOTH_SANDSTONE, Blocks.WHITE_TERRACOTTA, Blocks.OAK_PLANKS,
                Blocks.SMOOTH_SANDSTONE, Blocks.DARK_OAK_DOOR,
                new String[]{"ДОМ"}, new String[]{"КУЛЬТУРЫ"}, new String[]{"«Металлург»"});
        int y = gy + 1;
        // сцена
        b.fill(px + 8, y, pz + 3, px + 21, y, pz + 6, Blocks.SPRUCE_PLANKS);
        b.fill(px + 8, y + 1, pz + 3, px + 21, y + 2, pz + 3, Blocks.RED_WOOL);
        b.fill(px + 8, y + 1, pz + 6, px + 9, y + 2, pz + 6, Blocks.RED_WOOL);
        b.fill(px + 20, y + 1, pz + 6, px + 21, y + 2, pz + 6, Blocks.RED_WOOL);
        b.set(px + 14, y + 1, pz + 4, Blocks.NOTE_BLOCK);
        b.set(px + 15, y + 1, pz + 4, Blocks.JUKEBOX);
        // зрительный зал
        for (int z = pz + 9; z <= pz + 14; z += 2)
            for (int x = px + 8; x <= px + 21; x++) {
                if (x == px + 11 || x == px + 12) continue;
                b.stair(x, y, z, Blocks.SPRUCE_STAIRS, Direction.NORTH);
            }
        archive(b, i, gy + 5);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        // афиши
        for (int x : new int[]{px + 3, px + 20}) {
            b.fill(x, gy + 1, pz + 20, x, gy + 2, pz + 20, Blocks.DARK_OAK_FENCE);
            b.set(x, gy + 3, pz + 20, Blocks.WHITE_WOOL);
        }
        b.sign(px + 3, gy + 3, pz + 21, Direction.SOUTH, "АФИША", "Сб — концерт", "хора ветеранов");
        b.sign(px + 20, gy + 3, pz + 21, Direction.SOUTH, "КИНО", "Вс 18:00", "«Брат»");
        d.locs.put("dk", i.inside());
    }

    /** Железнодорожный вокзал с часовой башней и платформой (пути — вдоль южной окраины). */
    public static void vokzal(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 2, pz + 2, 20, 12, 2, gy, Blocks.YELLOW_TERRACOTTA, Blocks.WHITE_CONCRETE, Blocks.POLISHED_ANDESITE,
                Blocks.WHITE_CONCRETE, Blocks.DARK_OAK_DOOR,
                new String[]{"ВОКЗАЛ"}, new String[]{"НОВОБЕРЁЗОВСК"}, new String[]{"Кассы", "дальнего", "следования"});
        // часовая башня
        int tx = i.x0() + 9;
        b.walls(tx, gy + 9, i.z0() + 4, tx + 3, gy + 15, i.z0() + 7, Blocks.YELLOW_TERRACOTTA);
        b.fill(tx, gy + 16, i.z0() + 4, tx + 3, gy + 16, i.z0() + 7, Blocks.WHITE_CONCRETE);
        b.fill(tx + 1, gy + 17, i.z0() + 5, tx + 2, gy + 18, i.z0() + 6, Blocks.GREEN_CONCRETE);
        b.set(tx + 1, gy + 19, i.z0() + 5, Blocks.LIGHTNING_ROD);
        b.fill(tx + 1, gy + 12, i.z0() + 8, tx + 2, gy + 13, i.z0() + 8, Blocks.WHITE_CONCRETE);
        b.set(tx + 1, gy + 13, i.z0() + 8, Blocks.BLACK_CONCRETE);
        // кассы и зал ожидания
        b.fill(px + 11, gy + 1, pz + 6, px + 19, gy + 1, pz + 6, topSlab(Blocks.SMOOTH_STONE_SLAB));
        b.fill(px + 11, gy + 2, pz + 6, px + 19, gy + 3, pz + 6, Blocks.GLASS_PANE);
        for (int z = pz + 9; z <= pz + 11; z += 2)
            for (int x = px + 14; x <= px + 20; x++) b.stair(x, gy + 1, z, Blocks.OAK_STAIRS, Direction.NORTH);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.POLISHED_ANDESITE);
        b.sign(px + 6, gy + 2, pz + 22, Direction.SOUTH, "К поездам", "→ платформа", "на юг");
        b.set(px + 6, gy + 2, pz + 21, Blocks.DARK_OAK_FENCE);
        b.set(px + 6, gy + 1, pz + 21, Blocks.DARK_OAK_FENCE);
        d.locs.put("vokzal", i.inside());
    }

    /** Пути и платформа вдоль южной окраины города. */
    public static void railway(B b, GorodData d, int minX, int maxX, int z, int gy, int platformX) {
        for (int x = minX - 4; x <= maxX + 4; x++) {
            b.set(x, gy, z, Blocks.GRAVEL);
            b.set(x, gy, z + 1, Blocks.GRAVEL);
            b.set(x, gy + 1, z, Blocks.RAIL);
            b.set(x, gy + 1, z + 1, Blocks.AIR);
            if (x % 3 == 0) b.set(x, gy, z, Blocks.SPRUCE_PLANKS);
        }
        for (int x = platformX - 10; x <= platformX + 10; x++) {
            b.set(x, gy + 1, z - 1, Blocks.SMOOTH_STONE_SLAB);
            b.set(x, gy + 1, z - 2, Blocks.SMOOTH_STONE_SLAB);
            if ((x - platformX) % 5 == 0) b.streetLamp(x, gy + 2, z - 3);
        }
        b.set(platformX, gy + 2, z - 3, Blocks.SPRUCE_FENCE);
        b.sign(platformX + 1, gy + 2, z - 3, Direction.SOUTH, "Платформа 1", "Новоберёзовск");
    }

    // =================================================================== бизнесы (за них воюют ОПГ)

    /** Ряд ларьков: шаурма, табак, цветы, ремонт обуви. */
    public static void kiosks(B b, GorodData d, int px, int pz, int gy) {
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.SMOOTH_STONE);
        Block[] colors = {Blocks.BLUE_CONCRETE, Blocks.RED_CONCRETE, Blocks.GREEN_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.WHITE_CONCRETE};
        String[][] names = {{"ТАБАК", "ПРЕССА"}, {"ЦВЕТЫ"}, {"ОВОЩИ", "ФРУКТЫ"}, {"РЕМОНТ", "ОБУВИ"}, {"СВЯЗНОЙ", "SIM-карты"}};
        for (int k = 0; k < 5; k++) {
            int x0 = px + 1 + k * 4, z0 = pz + 9;
            b.walls(x0, gy + 1, z0, x0 + 3, gy + 3, z0 + 4, colors[k]);
            b.fill(x0, gy + 4, z0, x0 + 3, gy + 4, z0 + 4, Blocks.SMOOTH_STONE_SLAB);
            b.air(x0 + 1, gy + 1, z0 + 1, x0 + 2, gy + 3, z0 + 3);
            b.fill(x0 + 1, gy + 2, z0 + 4, x0 + 2, gy + 2, z0 + 4, Blocks.GLASS_PANE);
            b.set(x0 + 1, gy + 1, z0 + 4, topSlab(Blocks.SMOOTH_STONE_SLAB));
            b.door(x0 + 1, gy + 1, z0, Direction.NORTH, Blocks.OAK_DOOR);
            b.set(x0 + 2, gy + 1, z0 + 2, Blocks.BARREL);
            b.sign(x0 + 2, gy + 3, z0 + 5, Direction.SOUTH, names[k]);
        }
        b.bench(px + 4, gy + 1, pz + 20, Direction.NORTH, 3);
        b.bench(px + 15, gy + 1, pz + 20, Direction.NORTH, 3);
        b.streetLamp(px + 11, gy + 1, pz + 18);
        b.tree(px + 3, gy + 1, pz + 3, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        b.tree(px + 20, gy + 1, pz + 3, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        d.locs.put("kiosks", new BlockPos(px + 11, gy + 1, pz + 16));
        d.roadSpots.add(new BlockPos(px + 9, gy + 1, pz + 21));
    }

    public static void shawarma(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 5, pz + 8, 14, 10, 1, gy, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_CONCRETE, Blocks.SPRUCE_PLANKS,
                Blocks.ORANGE_CONCRETE, Blocks.SPRUCE_DOOR,
                new String[]{"ШАУРМА"}, new String[]{"«У Ашота»"}, new String[]{"24/7"});
        b.fill(px + 7, gy + 1, pz + 10, px + 16, gy + 1, pz + 10, topSlab(Blocks.SMOOTH_STONE_SLAB));
        b.set(px + 14, gy + 1, pz + 9, Blocks.SMOKER);
        b.set(px + 15, gy + 1, pz + 9, Blocks.FURNACE);
        for (int x : new int[]{px + 7, px + 15}) {
            b.set(x, gy + 1, pz + 14, Blocks.SPRUCE_FENCE);
            b.set(x, gy + 2, pz + 14, Blocks.WHITE_CARPET);
        }
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        d.locs.put("shawarma", i.inside());
    }

    /** Автосервис: подъёмники, яма, покрышки. */
    public static void autoservice(B b, GorodData d, int px, int pz, int gy) {
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.GRAY_CONCRETE);
        int x0 = px + 2, z0 = pz + 3, x1 = px + 21, z1 = pz + 14;
        b.walls(x0, gy + 1, z0, x1, gy + 5, z1, Blocks.LIGHT_GRAY_CONCRETE);
        b.fill(x0, gy + 6, z0, x1, gy + 6, z1, Blocks.IRON_BLOCK);
        b.air(x0 + 1, gy + 1, z0 + 1, x1 - 1, gy + 5, z1 - 1);
        for (int k = 0; k < 3; k++) {
            int gx = x0 + 2 + k * 6;
            b.air(gx, gy + 1, z1, gx + 3, gy + 4, z1);
            b.fill(gx, gy + 1, z0 + 3, gx + 3, gy + 1, z0 + 8, Blocks.GRAY_CONCRETE);
            b.fill(gx + 1, gy, z0 + 4, gx + 2, gy, z0 + 7, Blocks.AIR);
            b.set(gx, gy + 2, z0 + 2, Blocks.CHAIN);
            b.set(gx, gy + 3, z0 + 2, Blocks.CHAIN);
        }
        b.fill(x1 - 3, gy + 1, z0 + 1, x1 - 1, gy + 2, z0 + 1, Blocks.BLACK_CONCRETE);
        b.set(x1 - 1, gy + 1, z0 + 9, Blocks.ANVIL);
        b.set(x1 - 2, gy + 1, z0 + 9, Blocks.SMITHING_TABLE);
        for (int x = x0 + 3; x <= x1 - 3; x += 6) b.hangingLantern(x, gy + 5, z0 + 6);
        b.set(px + 11, gy + 6, z1 + 1, Blocks.AIR);
        b.sign(px + 11, gy + 5, z1 + 1, Direction.SOUTH, "АВТОСЕРВИС", "«ГАРАЖ»", "шиномонтаж");
        b.set(px + 11, gy + 5, z1, Blocks.LIGHT_GRAY_CONCRETE);
        // покрышки
        for (int x = px + 2; x <= px + 6; x++) b.set(x, gy + 1, pz + 20, Blocks.BLACK_CONCRETE);
        b.streetLamp(px + 22, gy + 1, pz + 22);
        d.locs.put("autoservice", new BlockPos(px + 11, gy + 1, pz + 17));
    }

    /** Сауна «Берёзка» — точка русской ОПГ. */
    public static void sauna(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 3, pz + 6, 18, 12, 1, gy, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS,
                Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_DOOR,
                new String[]{"САУНА"}, new String[]{"«БЕРЁЗКА»"}, new String[]{"Почасово", "Бильярд"});
        // бассейн, парилка, бильярд
        b.fill(px + 12, gy, pz + 8, px + 18, gy, pz + 11, Blocks.WATER);
        b.walls(px + 5, gy + 1, pz + 7, px + 9, gy + 3, pz + 10, Blocks.SPRUCE_PLANKS);
        b.air(px + 6, gy + 1, pz + 8, px + 8, gy + 3, pz + 9);
        b.door(px + 7, gy + 1, pz + 10, Direction.SOUTH, Blocks.SPRUCE_DOOR);
        b.set(px + 6, gy + 1, pz + 8, Blocks.MAGMA_BLOCK);
        b.fill(px + 7, gy + 1, pz + 8, px + 8, gy + 1, pz + 8, topSlab(Blocks.SPRUCE_SLAB));
        b.fill(px + 14, gy + 1, pz + 14, px + 16, gy + 1, pz + 15, Blocks.GREEN_WOOL);
        b.set(px + 19, gy + 1, pz + 15, Blocks.CHEST);
        b.sign(px + 7, gy + 3, pz + 11, Direction.SOUTH, "Парная");
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SPRUCE_PLANKS);
        b.fill(px + 2, gy + 1, pz + 20, px + 4, gy + 1, pz + 21, Blocks.BLACK_CONCRETE);
        d.locs.put("sauna", i.inside());
    }

    /** Ломбард «Золотой». */
    public static void pawnshop(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 4, pz + 7, 16, 11, 2, gy, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GOLD_BLOCK, Blocks.DARK_OAK_PLANKS,
                Blocks.POLISHED_BLACKSTONE, Blocks.DARK_OAK_DOOR,
                new String[]{"ЛОМБАРД"}, new String[]{"«ЗОЛОТОЙ»"}, new String[]{"Скупка", "золота", "и техники"});
        b.fill(px + 13, gy + 1, pz + 12, px + 18, gy + 1, pz + 12, topSlab(Blocks.SMOOTH_QUARTZ_SLAB));
        b.fill(px + 13, gy + 2, pz + 12, px + 18, gy + 3, pz + 12, Blocks.IRON_BARS);
        b.fill(px + 14, gy + 1, pz + 9, px + 18, gy + 1, pz + 9, Blocks.CHEST);
        b.set(px + 16, gy + 2, pz + 9, Blocks.GOLD_BLOCK);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.POLISHED_BLACKSTONE);
        d.locs.put("pawnshop", i.inside());
    }

    /** Стадион «Труд»: поле, ворота, трибуны, прожекторы. */
    public static void stadium(B b, GorodData d, int px, int pz, int gy) {
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.RED_TERRACOTTA);
        b.fill(px + 3, gy, pz + 3, px + 20, gy, pz + 16, Blocks.GRASS_BLOCK);
        for (int x = px + 3; x <= px + 20; x++) {
            b.set(x, gy, pz + 3, Blocks.WHITE_CONCRETE);
            b.set(x, gy, pz + 16, Blocks.WHITE_CONCRETE);
        }
        for (int z = pz + 3; z <= pz + 16; z++) {
            b.set(px + 3, gy, z, Blocks.WHITE_CONCRETE);
            b.set(px + 20, gy, z, Blocks.WHITE_CONCRETE);
            b.set(px + 11, gy, z, Blocks.WHITE_CONCRETE);
        }
        for (int z : new int[]{pz + 8, pz + 11}) {
            b.fill(px + 3, gy + 1, z, px + 3, gy + 2, z, Blocks.WHITE_CONCRETE);
            b.fill(px + 20, gy + 1, z, px + 20, gy + 2, z, Blocks.WHITE_CONCRETE);
        }
        b.fill(px + 3, gy + 3, pz + 8, px + 3, gy + 3, pz + 11, Blocks.WHITE_CONCRETE);
        b.fill(px + 20, gy + 3, pz + 8, px + 20, gy + 3, pz + 11, Blocks.WHITE_CONCRETE);
        // трибуны
        for (int k = 0; k < 3; k++) {
            for (int x = px + 2; x <= px + 21; x++) {
                b.stair(x, gy + 1 + k, pz + 18 + k, Blocks.STONE_BRICK_STAIRS, Direction.NORTH);
                if (k > 0) b.set(x, gy + k, pz + 18 + k, Blocks.STONE_BRICKS);
            }
        }
        b.fill(px + 2, gy + 1, pz + 21, px + 21, gy + 3, pz + 21, Blocks.STONE_BRICKS);
        b.air(px + 11, gy + 1, pz + 18, px + 12, gy + 3, pz + 21);
        // прожекторы
        for (int[] o : new int[][]{{1, 1}, {22, 1}}) {
            for (int y = gy + 1; y <= gy + 9; y++) b.set(px + o[0], y, pz + o[1], Blocks.IRON_BARS);
            b.set(px + o[0], gy + 10, pz + o[1], Blocks.GLOWSTONE);
        }
        b.sign(px + 13, gy + 3, pz + 22, Direction.SOUTH, "СТАДИОН", "«ТРУД»");
        d.locs.put("stadium", new BlockPos(px + 11, gy + 1, pz + 10));
    }

    private Buildings() {
    }
}
