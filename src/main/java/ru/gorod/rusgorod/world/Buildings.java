package ru.gorod.rusgorod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import ru.gorod.rusgorod.data.GorodData;

/**
 * Здания города. Участок 24×24, (px, pz) — северо-западный угол, фасад обращён на юг
 * (к дороге на z = pz + 24). gy — уровень земли (верх блока покрытия).
 */
public final class Buildings {

    // =================================================================== общие

    /** Результат постройки типового здания: дверь и внутренняя точка. */
    public record Info(int doorX, int frontZ, BlockPos inside) {
    }

    static BlockState topSlab(Block slab) {
        return slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    /** Типовое административное здание с окнами, перекрытиями, лестницей, светом, вывеской. */
    static Info office(B b, int x0, int z0, int w, int dep, int floors, int gy,
                       Block wall, Block trim, Block floor, Block roof, Block door, String[]... signs) {
        int x1 = x0 + w - 1, z1 = z0 + dep - 1;
        int top = gy + 4 * floors;
        b.air(x0, gy + 1, z0, x1, top + 2, z1);
        for (int i = 0; i <= floors; i++) {
            int y = gy + 4 * i;
            b.fill(x0, y, z0, x1, y, z1, i == floors ? roof : floor);
            if (i > 0) b.walls(x0, y, z0, x1, y, z1, trim);
        }
        for (int i = 0; i < floors; i++) {
            int y = gy + 4 * i;
            b.walls(x0, y + 1, z0, x1, y + 3, z1, wall);
            // окна
            for (int x = x0 + 2; x <= x1 - 2; x++) {
                if ((x - x0) % 3 == 0) continue;
                b.set(x, y + 2, z0, Blocks.GLASS_PANE);
                b.set(x, y + 3, z0, Blocks.GLASS_PANE);
                b.set(x, y + 2, z1, Blocks.GLASS_PANE);
                b.set(x, y + 3, z1, Blocks.GLASS_PANE);
            }
            for (int z = z0 + 2; z <= z1 - 2; z++) {
                if ((z - z0) % 3 == 0) continue;
                b.set(x0, y + 2, z, Blocks.GLASS_PANE);
                b.set(x1, y + 2, z, Blocks.GLASS_PANE);
            }
            // освещение
            for (int x = x0 + 3; x <= x1 - 2; x += 5)
                for (int z = z0 + 3; z <= z1 - 2; z += 5)
                    b.hangingLantern(x, y + 3, z);
        }
        // парапет
        b.walls(x0, top + 1, z0, x1, top + 1, z1, trim);
        // лестница между этажами
        if (floors > 1) b.ladder(x0 + 1, gy + 1, top - 1, z0 + 1, Direction.SOUTH);
        // вход
        int cx = x0 + w / 2;
        b.fill(cx - 2, gy + 1, z1, cx + 2, gy + 3, z1, wall);
        b.door(cx, gy + 1, z1, Direction.SOUTH, door);
        b.set(cx, gy + 3, z1, trim);
        b.fill(cx - 1, gy, z1 + 1, cx + 1, gy, z1 + 2, Blocks.SMOOTH_STONE);
        b.hangingLantern(cx + 2, gy + 3, z1 + 1);
        b.set(cx + 2, gy + 4, z1 + 1, trim);
        // вывески над входом
        int sx = cx - signs.length / 2;
        for (String[] s : signs) {
            b.set(sx, gy + 4, z1, trim);
            b.sign(sx, gy + 4, z1 + 1, Direction.SOUTH, s);
            sx++;
        }
        return new Info(cx, z1, new BlockPos(cx, gy + 1, z1 - 3));
    }

    /** Площадка перед зданием: тротуарная плитка, лавочки, фонари. */
    static void forecourt(B b, int px, int pz, int frontZ, int gy, Block paving) {
        b.fill(px, gy, frontZ + 1, px + 23, gy, pz + 23, paving);
        b.streetLamp(px + 1, gy + 1, pz + 22);
        b.streetLamp(px + 22, gy + 1, pz + 22);
    }

    static void desk(B b, int x, int y, int z) {
        b.set(x, y, z, topSlab(Blocks.SPRUCE_SLAB));
        b.stair(x, y, z + 1, Blocks.SPRUCE_STAIRS, Direction.NORTH);
    }

    static void deskRow(B b, int x0, int x1, int y, int z) {
        for (int x = x0; x <= x1; x += 3) desk(b, x, y, z);
    }

    // =================================================================== жильё

    /** Панельная «хрущёвка» (5 этажей) или «девятиэтажка». */
    public static void panelHouse(B b, GorodData d, int px, int pz, int gy, int floors, boolean nine, RandomSource r) {
        int x0 = px + 1, z0 = pz + 7, w = 22, dep = 10;
        int x1 = x0 + w - 1, z1 = z0 + dep - 1;
        int top = gy + 3 * floors;
        Block wall = nine ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE;
        Block seam = nine ? Blocks.LIGHT_BLUE_CONCRETE : Blocks.GRAY_CONCRETE;
        b.air(x0, gy + 1, z0 - 1, x1, top + 2, z1 + 2);
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
                // балконы на фасаде
                if (i > 0 && k == 1 && (x - x0) % 8 == 1) {
                    b.set(x, y, z1 + 1, Blocks.SMOOTH_STONE_SLAB);
                    b.set(x + 1, y, z1 + 1, Blocks.SMOOTH_STONE_SLAB);
                    b.set(x, y + 1, z1 + 1, Blocks.IRON_BARS);
                    b.set(x + 1, y + 1, z1 + 1, Blocks.IRON_BARS);
                }
            }
            for (int x = x0 + 4; x < x1 - 2; x += 6) b.hangingLantern(x, y + 2, z0 + 4);
        }
        b.walls(x0, top + 1, z0, x1, top + 1, z1, seam);
        // подъезды
        int[] entrances = {x0 + 5, x0 + 16};
        for (int ex : entrances) {
            b.fill(ex, gy + 1, z0, ex, top, z0, wall);
            b.set(ex + 1, gy + 2, z1, wall);
            b.door(ex, gy + 1, z1, Direction.SOUTH, Blocks.SPRUCE_DOOR);
            b.set(ex, gy + 3, z1, wall);
            b.fill(ex - 1, gy + 3, z1 + 1, ex + 1, gy + 3, z1 + 2, Blocks.SMOOTH_STONE_SLAB);
            b.fill(ex - 1, gy, z1 + 1, ex + 1, gy, z1 + 3, Blocks.SMOOTH_STONE);
            b.sign(ex + 1, gy + 2, z1 + 1, Direction.SOUTH, "Подъезд", ex == entrances[0] ? "№1" : "№2", "кв. " + (ex == entrances[0] ? "1-" + (floors * 4) : (floors * 4 + 1) + "-" + floors * 8));
            b.ladder(ex, gy + 1, top - 1, z0 + 1, Direction.SOUTH);
            // квартиры первого этажа: кровати и столы
            b.bed(ex - 3, gy + 1, z0 + 2, Direction.NORTH, Blocks.RED_BED);
            b.bed(ex + 3, gy + 1, z0 + 2, Direction.NORTH, Blocks.WHITE_BED);
            desk(b, ex - 3, gy + 1, z1 - 3);
            b.set(ex + 3, gy + 1, z1 - 2, Blocks.CRAFTING_TABLE);
            d.homes.add(new BlockPos(ex, gy + 1, z1 - 2));
            d.homes.add(new BlockPos(ex - 2, gy + 1, z0 + 4));
            d.homes.add(new BlockPos(ex + 2, gy + 1, z0 + 4));
        }
        // двор: лавочки у подъездов, детская площадка, «гаражи-ракушки»
        b.fill(px, gy, z1 + 4, px + 23, gy, pz + 23, Blocks.GRASS_BLOCK);
        b.fill(px, gy, z1 + 1, px + 23, gy, z1 + 1, Blocks.SMOOTH_STONE);
        b.bench(entrances[0] + 2, gy + 1, z1 + 3, Direction.NORTH, 2);
        b.bench(entrances[1] + 2, gy + 1, z1 + 3, Direction.NORTH, 2);
        b.set(px + 2, gy + 1, pz + 1, Blocks.OAK_FENCE);
        b.set(px + 2, gy + 2, pz + 1, Blocks.OAK_FENCE);
        b.set(px + 4, gy + 1, pz + 1, Blocks.OAK_FENCE);
        b.set(px + 4, gy + 2, pz + 1, Blocks.OAK_FENCE);
        b.fill(px + 2, gy + 3, pz + 1, px + 4, gy + 3, pz + 1, Blocks.OAK_SLAB);
        b.tree(px + 21, gy + 1, pz + 2, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        b.fill(px + 8, gy + 1, pz + 1, px + 9, gy + 1, pz + 1, Blocks.GREEN_TERRACOTTA);
        b.fill(px + 11, gy + 1, pz + 1, px + 12, gy + 1, pz + 1, Blocks.GRAY_TERRACOTTA);
        b.streetLamp(px + 11, gy + 1, pz + 22);
        d.roadSpots.add(new BlockPos(px + 12, gy + 1, pz + 22));
    }

    // =================================================================== госструктуры

    public static void police(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 2, pz + 4, 20, 14, 3, gy, Blocks.WHITE_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.POLISHED_ANDESITE,
                Blocks.GRAY_CONCRETE, Blocks.SPRUCE_DOOR,
                new String[]{"ОТДЕЛ", "МВД РОССИИ", "ПО ГОРОДУ"}, new String[]{"ПОЛИЦИЯ", "", "Дежурная", "часть 24/7"}, new String[]{"Тел. 102", "Единый", "номер 112"});
        int y = gy + 1;
        // дежурная часть
        b.fill(px + 8, y, pz + 12, px + 14, y, pz + 12, Blocks.SMOOTH_STONE_SLAB);
        b.fill(px + 8, y + 1, pz + 12, px + 14, y + 1, pz + 12, Blocks.GLASS_PANE);
        // ИВС — камеры у задней стены
        for (int k = 0; k < 3; k++) {
            int cx0 = px + 10 + k * 4;
            b.walls(cx0, y, pz + 5, cx0 + 3, y + 2, pz + 8, Blocks.STONE_BRICKS);
            b.fill(cx0 + 1, y, pz + 8, cx0 + 2, y + 2, pz + 8, Blocks.IRON_BARS);
            b.air(cx0 + 1, y, pz + 6, cx0 + 2, y + 2, pz + 7);
            d.ivsCells.add(new BlockPos(cx0 + 1, y, pz + 6));
        }
        b.set(px + 8, y + 2, pz + 7, Blocks.STONE_BRICKS);
        b.sign(px + 9, y + 2, pz + 7, Direction.EAST, "ИВС", "Изолятор", "временного", "содержания");
        // кабинеты на 2–3 этажах
        deskRow(b, px + 5, px + 18, gy + 5, pz + 8);
        deskRow(b, px + 5, px + 18, gy + 9, pz + 8);
        b.russianFlag(px + 3, gy + 13, pz + 15, 4);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.POLISHED_ANDESITE);
        // служебная парковка с разметкой
        for (int x = px + 2; x <= px + 22; x += 4) b.set(x, gy, pz + 21, Blocks.WHITE_CONCRETE);
        d.locs.put("police", i.inside());
    }

    public static void gibdd(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 5, pz + 6, 14, 12, 2, gy, Blocks.WHITE_CONCRETE, Blocks.LIME_CONCRETE, Blocks.POLISHED_ANDESITE,
                Blocks.GRAY_CONCRETE, Blocks.SPRUCE_DOOR,
                new String[]{"ГОСАВТО-", "ИНСПЕКЦИЯ"}, new String[]{"ОГИБДД", "ОМВД России", "по городу"});
        deskRow(b, px + 7, px + 16, gy + 1, pz + 10);
        b.sign(px + 7, gy + 2, pz + 7, Direction.SOUTH, "Регистрация", "ТС и выдача", "в/у: окно 1-3");
        b.set(px + 7, gy + 2, pz + 6, Blocks.WHITE_CONCRETE);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        // учебная площадка «автодром»
        b.fill(px + 2, gy, pz + 1, px + 21, gy, pz + 4, Blocks.GRAY_CONCRETE);
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
        b.set(x + 1, gy + 4, z + 3, Blocks.WHITE_CONCRETE);
        b.sign(x + 2, gy + 3, z + 4, Direction.SOUTH, "ДПС");
        b.hangingLantern(x + 2, gy + 2, z + 1);
        // шлагбаум и «лежачий полицейский»
        b.fill(x + 4, gy + 1, z + 1, x + 4, gy + 1, z + 1, Blocks.RED_CONCRETE);
        b.fill(x + 5, gy + 1, z + 1, x + 8, gy + 1, z + 1, Blocks.WHITE_CONCRETE);
        d.locs.put("gibdd_post", new BlockPos(x + 2, gy + 1, z + 5));
    }

    public static void sk(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 3, pz + 5, 18, 13, 3, gy, Blocks.GRAY_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.POLISHED_ANDESITE,
                Blocks.BLACK_CONCRETE, Blocks.DARK_OAK_DOOR,
                new String[]{"СЛЕДСТВЕННЫЙ", "КОМИТЕТ"}, new String[]{"РОССИЙСКОЙ", "ФЕДЕРАЦИИ"}, new String[]{"Следственный", "отдел по", "городу"});
        deskRow(b, px + 5, px + 18, gy + 1, pz + 8);
        deskRow(b, px + 5, px + 18, gy + 5, pz + 8);
        b.set(px + 5, gy + 1, pz + 13, Blocks.BOOKSHELF);
        b.set(px + 6, gy + 1, pz + 13, Blocks.BOOKSHELF);
        b.set(px + 7, gy + 1, pz + 13, Blocks.LECTERN);
        b.russianFlag(px + 4, gy + 13, pz + 16, 4);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.POLISHED_ANDESITE);
        d.locs.put("sk", i.inside());
    }

    public static void prokuratura(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 3, pz + 5, 18, 13, 3, gy, Blocks.SMOOTH_QUARTZ, Blocks.BLUE_CONCRETE, Blocks.POLISHED_ANDESITE,
                Blocks.GRAY_CONCRETE, Blocks.DARK_OAK_DOOR,
                new String[]{"ПРОКУРАТУРА", "ГОРОДА"}, new String[]{"Приём граждан", "пн–пт", "9:00–18:00"});
        deskRow(b, px + 5, px + 18, gy + 1, pz + 9);
        deskRow(b, px + 5, px + 18, gy + 5, pz + 9);
        b.fill(px + 5, gy + 1, pz + 6, px + 9, gy + 1, pz + 6, Blocks.BOOKSHELF);
        b.russianFlag(px + 4, gy + 13, pz + 16, 4);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.POLISHED_ANDESITE);
        d.locs.put("prok", i.inside());
    }

    public static void fsb(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 2, pz + 3, 20, 15, 4, gy, Blocks.SMOOTH_SANDSTONE, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE,
                Blocks.STONE_BRICKS, Blocks.DARK_OAK_DOOR,
                new String[]{"УПРАВЛЕНИЕ", "ФЕДЕРАЛЬНОЙ"}, new String[]{"СЛУЖБЫ", "БЕЗОПАСНОСТИ"}, new String[]{"РОССИЙСКОЙ", "ФЕДЕРАЦИИ"});
        // гранитный цоколь
        b.walls(px + 2, gy + 1, pz + 3, px + 21, gy + 1, pz + 17, Blocks.POLISHED_GRANITE);
        b.door(i.doorX(), gy + 1, i.frontZ(), Direction.SOUTH, Blocks.DARK_OAK_DOOR);
        deskRow(b, px + 5, px + 18, gy + 5, pz + 8);
        deskRow(b, px + 5, px + 18, gy + 9, pz + 8);
        b.fill(px + 4, gy + 1, pz + 5, px + 8, gy + 1, pz + 5, Blocks.IRON_BLOCK);
        b.russianFlag(px + 3, gy + 17, pz + 15, 4);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.POLISHED_ANDESITE);
        // забор с калиткой — режимный объект
        for (int x = px; x <= px + 23; x++) if (Math.abs(x - i.doorX()) > 1) b.set(x, gy + 1, pz + 23, Blocks.IRON_BARS);
        d.locs.put("fsb", i.inside());
    }

    /** Администрация городского округа + площадь для митингов. */
    public static void admin(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 1, pz + 1, 22, 11, 3, gy, Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ, Blocks.POLISHED_ANDESITE,
                Blocks.SMOOTH_QUARTZ, Blocks.DARK_OAK_DOOR,
                new String[]{"АДМИНИСТРАЦИЯ"}, new String[]{"ГОРОДСКОГО", "ОКРУГА"}, new String[]{"Приём граждан:", "вт, чт", "10:00–13:00"});
        // колоннада
        for (int x = px + 4; x <= px + 20; x += 4) {
            for (int y = gy + 1; y <= gy + 7; y++) b.set(x, y, pz + 13, Blocks.QUARTZ_PILLAR);
        }
        b.fill(px + 3, gy + 8, pz + 12, px + 21, gy + 8, pz + 13, Blocks.SMOOTH_QUARTZ);
        b.russianFlag(px + 11, gy + 13, pz + 6, 5);
        // площадь
        b.fill(px, gy, pz + 12, px + 23, gy, pz + 23, Blocks.POLISHED_ANDESITE);
        for (int x = px + 1; x <= px + 22; x += 3) b.set(x, gy, pz + 18, Blocks.STONE_BRICKS);
        // клумба с фонтаном в центре
        b.fill(px + 9, gy, pz + 17, px + 14, gy, pz + 20, Blocks.STONE_BRICKS);
        b.fill(px + 10, gy + 1, pz + 18, px + 13, gy + 1, pz + 19, Blocks.STONE_BRICK_WALL);
        b.fill(px + 11, gy, pz + 18, px + 12, gy, pz + 19, Blocks.WATER);
        b.air(px + 11, gy + 1, pz + 18, px + 12, gy + 1, pz + 19);
        b.bench(px + 3, gy + 1, pz + 21, Direction.NORTH, 3);
        b.bench(px + 18, gy + 1, pz + 21, Direction.NORTH, 3);
        b.streetLamp(px + 1, gy + 1, pz + 15);
        b.streetLamp(px + 22, gy + 1, pz + 15);
        b.streetLamp(px + 1, gy + 1, pz + 22);
        b.streetLamp(px + 22, gy + 1, pz + 22);
        // кабинеты
        deskRow(b, px + 4, px + 20, gy + 5, pz + 5);
        deskRow(b, px + 4, px + 20, gy + 9, pz + 5);
        b.fill(px + 8, gy + 9, pz + 3, px + 15, gy + 9, pz + 3, Blocks.BOOKSHELF);
        d.locs.put("admin", i.inside());
        d.locs.put("square", new BlockPos(px + 12, gy + 1, pz + 16));
    }

    /** СИЗО-1 УФСИН: периметр, корпус с камерами, вышка, КПП. */
    public static void sizo(B b, GorodData d, int px, int pz, int gy) {
        // основное ограждение
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.GRAVEL);
        b.walls(px, gy + 1, pz, px + 23, gy + 4, pz + 22, Blocks.STONE_BRICKS);
        b.walls(px, gy + 5, pz, px + 23, gy + 5, pz + 22, Blocks.IRON_BARS);
        // КПП
        int gx = px + 11;
        b.air(gx, gy + 1, pz + 22, gx + 1, gy + 3, pz + 22);
        b.fill(gx - 1, gy + 4, pz + 22, gx + 2, gy + 4, pz + 22, Blocks.STONE_BRICKS);
        b.set(gx - 1, gy + 3, pz + 22, Blocks.STONE_BRICKS);
        b.sign(gx - 1, gy + 3, pz + 23, Direction.SOUTH, "ФКУ СИЗО-1", "УФСИН России", "Посторонним", "вход воспрещён");
        // корпус
        int x0 = px + 3, z0 = pz + 3;
        b.fill(x0, gy, z0, x0 + 17, gy, z0 + 9, Blocks.POLISHED_ANDESITE);
        b.walls(x0, gy + 1, z0, x0 + 17, gy + 7, z0 + 9, Blocks.GRAY_CONCRETE);
        b.fill(x0, gy + 4, z0, x0 + 17, gy + 4, z0 + 9, Blocks.GRAY_CONCRETE);
        b.fill(x0, gy + 8, z0, x0 + 17, gy + 8, z0 + 9, Blocks.GRAY_CONCRETE);
        b.air(x0 + 1, gy + 1, z0 + 1, x0 + 16, gy + 3, z0 + 8);
        b.air(x0 + 1, gy + 5, z0 + 1, x0 + 16, gy + 7, z0 + 8);
        // камеры (первый этаж), фасадом в коридор
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
        b.set(x0 + 16, gy + 4, z0 + 8, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH));
        // прогулочный дворик и вышка
        int tx = px + 20, tz = pz + 18;
        for (int[] o : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}})
            for (int y = gy + 1; y <= gy + 6; y++) b.set(tx + o[0], y, tz + o[1], Blocks.SPRUCE_FENCE);
        b.fill(tx - 1, gy + 7, tz - 1, tx + 3, gy + 7, tz + 3, Blocks.SPRUCE_PLANKS);
        b.walls(tx - 1, gy + 8, tz - 1, tx + 3, gy + 8, tz + 3, Blocks.SPRUCE_FENCE);
        b.set(tx + 1, gy + 9, tz + 1, Blocks.LANTERN);
        b.ladder(tx + 1, gy + 1, gy + 7, tz + 3, Direction.SOUTH);
        b.set(tx + 1, gy + 6, tz + 2, Blocks.SPRUCE_PLANKS);
        for (int y = gy + 1; y <= gy + 5; y++) b.set(tx + 1, y, tz + 2, Blocks.SPRUCE_PLANKS);
        b.streetLamp(px + 2, gy + 1, pz + 20);
        b.streetLamp(px + 15, gy + 1, pz + 20);
        d.locs.put("sizo", new BlockPos(px + 11, gy + 1, pz + 16));
        d.locs.put("sizo_tower", new BlockPos(tx + 1, gy + 1, tz - 2));
        d.locs.put("sizo_exit", new BlockPos(gx, gy + 1, pz + 25));
    }

    // =================================================================== криминальные точки и бизнес

    public static void restaurant(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 3, pz + 6, 18, 12, 2, gy, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GOLD_BLOCK, Blocks.DARK_OAK_PLANKS,
                Blocks.POLISHED_BLACKSTONE, Blocks.DARK_OAK_DOOR,
                new String[]{"Ресторан"}, new String[]{"«ИМПЕРИЯ»"}, new String[]{"Банкеты", "Караоке"});
        // красная дорожка
        b.fill(i.doorX(), gy + 1, i.frontZ() + 1, i.doorX(), gy + 1, pz + 22, Blocks.RED_CARPET);
        for (int x = px + 5; x <= px + 18; x += 4)
            for (int z = pz + 8; z <= pz + 13; z += 4) {
                b.set(x, gy + 1, z, Blocks.DARK_OAK_FENCE);
                b.set(x, gy + 2, z, Blocks.WHITE_CARPET);
                b.stair(x - 1, gy + 1, z, Blocks.DARK_OAK_STAIRS, Direction.EAST);
                b.stair(x + 1, gy + 1, z, Blocks.DARK_OAK_STAIRS, Direction.WEST);
            }
        b.fill(px + 5, gy + 1, pz + 7, px + 10, gy + 1, pz + 7, Blocks.DARK_OAK_PLANKS);
        b.sign(px + 7, gy + 2, pz + 8, Direction.SOUTH, "VIP-зал", "Только", "для своих");
        b.set(px + 7, gy + 2, pz + 7, Blocks.DARK_OAK_PLANKS);
        b.set(px + 18, gy + 1, pz + 7, Blocks.CHEST);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.POLISHED_BLACKSTONE);
        // чёрные «машины» на парковке
        b.fill(px + 2, gy + 1, pz + 20, px + 4, gy + 1, pz + 21, Blocks.BLACK_CONCRETE);
        b.fill(px + 19, gy + 1, pz + 20, px + 21, gy + 1, pz + 21, Blocks.BLACK_CONCRETE);
        d.locs.put("restaurant", i.inside());
    }

    public static void cafe(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 4, pz + 7, 16, 11, 1, gy, Blocks.BRICKS, Blocks.ORANGE_TERRACOTTA, Blocks.SPRUCE_PLANKS,
                Blocks.RED_TERRACOTTA, Blocks.SPRUCE_DOOR,
                new String[]{"Кафе"}, new String[]{"«Шашлычный", "двор»"}, new String[]{"Шашлык", "Плов", "Лаваш"});
        for (int x = px + 6; x <= px + 17; x += 4) {
            b.set(x, gy + 1, pz + 11, Blocks.SPRUCE_FENCE);
            b.set(x, gy + 2, pz + 11, Blocks.RED_CARPET);
        }
        b.fill(px + 6, gy + 1, pz + 8, px + 12, gy + 1, pz + 8, Blocks.SPRUCE_PLANKS);
        b.set(px + 14, gy + 1, pz + 8, Blocks.SMOKER);
        b.set(px + 15, gy + 1, pz + 8, Blocks.SMOKER);
        // летняя веранда с мангалом
        b.fill(px + 2, gy, pz + 19, px + 21, gy, pz + 22, Blocks.SPRUCE_PLANKS);
        b.set(px + 3, gy + 1, pz + 21, Blocks.SMOKER);
        for (int x = px + 6; x <= px + 18; x += 4) {
            b.set(x, gy + 1, pz + 21, Blocks.SPRUCE_FENCE);
            b.set(x, gy + 2, pz + 21, Blocks.WHITE_CARPET);
        }
        b.streetLamp(px + 1, gy + 1, pz + 22);
        b.streetLamp(px + 22, gy + 1, pz + 22);
        d.locs.put("cafe", i.inside());
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
                n++;
            }
        b.set(px + 11, gy + 1, pz + 22, Blocks.SPRUCE_FENCE);
        b.set(px + 11, gy + 2, pz + 22, Blocks.SPRUCE_FENCE);
        b.set(px + 11, gy + 3, pz + 22, Blocks.SPRUCE_PLANKS);
        b.sign(px + 11, gy + 3, pz + 23, Direction.SOUTH, "РЫНОК", "«ЦЕНТРАЛЬНЫЙ»", "8:00–19:00");
        b.streetLamp(px + 1, gy + 1, pz + 21);
        b.streetLamp(px + 22, gy + 1, pz + 21);
        d.locs.put("market", new BlockPos(px + 11, gy + 1, pz + 13));
        d.roadSpots.add(new BlockPos(px + 6, gy + 1, pz + 20));
    }

    public static void shop(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 3, pz + 6, 18, 12, 2, gy, Blocks.BRICKS, Blocks.WHITE_CONCRETE, Blocks.WHITE_CONCRETE,
                Blocks.GRAY_CONCRETE, Blocks.OAK_DOOR,
                new String[]{"ПРОДУКТЫ"}, new String[]{"24 ЧАСА"}, new String[]{"АПТЕКА", "второй вход"});
        // витрина
        b.fill(px + 5, gy + 1, i.frontZ(), px + 9, gy + 3, i.frontZ(), Blocks.GLASS);
        b.fill(px + 15, gy + 1, i.frontZ(), px + 18, gy + 3, i.frontZ(), Blocks.GLASS);
        // стеллажи
        for (int z = pz + 8; z <= pz + 13; z += 3) b.fill(px + 5, gy + 1, z, px + 12, gy + 2, z, Blocks.BARREL);
        b.fill(px + 14, gy + 1, pz + 14, px + 18, gy + 1, pz + 14, Blocks.SMOOTH_STONE_SLAB);
        b.set(px + 18, gy + 2, pz + 14, Blocks.GLASS_PANE);
        // квартиры над магазином
        b.bed(px + 6, gy + 5, pz + 9, Direction.NORTH, Blocks.BLUE_BED);
        b.bed(px + 16, gy + 5, pz + 9, Direction.NORTH, Blocks.BLUE_BED);
        d.homes.add(new BlockPos(px + 10, gy + 5, pz + 12));
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        d.locs.put("shop", i.inside());
        d.roadSpots.add(new BlockPos(px + 6, gy + 1, pz + 21));
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
        // «штаб» в крайнем гараже
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
        b.fill(px + 6, gy + 1, pz + 8, px + 13, gy + 1, pz + 8, Blocks.DARK_OAK_PLANKS);
        b.set(px + 15, gy + 1, pz + 8, Blocks.BARREL);
        b.set(px + 16, gy + 1, pz + 8, Blocks.BARREL);
        b.set(px + 17, gy + 1, pz + 13, Blocks.ANVIL);
        b.set(px + 6, gy + 1, pz + 13, Blocks.CHEST);
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.GRAY_CONCRETE);
        // мотоциклы у входа (декор)
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
        b.fill(x0, gy + 7, z0, x0 + 19, gy + 7, z0 + 15, Blocks.IRON_BLOCK);
        b.air(x0 + 1, gy + 1, z0 + 1, x0 + 18, gy + 6, z0 + 14);
        b.air(x0 + 8, gy + 1, z0 + 15, x0 + 11, gy + 4, z0 + 15);
        for (int x = x0 + 2; x <= x0 + 17; x += 3)
            for (int z = z0 + 2; z <= z0 + 10; z += 4) {
                b.fill(x, gy + 1, z, x + 1, gy + 1 + Math.floorMod(x, 3), z + 1, Blocks.BARREL);
            }
        for (int x = x0 + 3; x <= x0 + 16; x += 5) b.hangingLantern(x, gy + 6, z0 + 7);
        b.set(x0 + 7, gy + 5, z0 + 15, Blocks.LIGHT_GRAY_CONCRETE);
        b.sign(x0 + 7, gy + 5, z0 + 16, Direction.SOUTH, "СКЛАД №3", "ООО «Логистик»", "Приём грузов", "8:00–20:00");
        b.streetLamp(px + 1, gy + 1, pz + 22);
        d.locs.put("warehouse", new BlockPos(x0 + 9, gy + 1, z0 + 12));
    }

    // =================================================================== социальные объекты

    public static void park(B b, GorodData d, int px, int pz, int gy, RandomSource r) {
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.GRASS_BLOCK);
        b.fill(px + 11, gy, pz, px + 12, gy, pz + 23, Blocks.DIRT_PATH);
        b.fill(px, gy, pz + 11, px + 23, gy, pz + 12, Blocks.DIRT_PATH);
        // памятник в центре (стела)
        b.fill(px + 10, gy, pz + 10, px + 13, gy, pz + 13, Blocks.STONE_BRICKS);
        b.fill(px + 11, gy + 1, pz + 11, px + 12, gy + 4, pz + 12, Blocks.POLISHED_ANDESITE);
        b.set(px + 11, gy + 5, pz + 11, Blocks.GOLD_BLOCK);
        b.sign(px + 11, gy + 2, pz + 13, Direction.SOUTH, "Защитникам", "Отечества", "1941–1945");
        for (int k = 0; k < 8; k++) {
            int x = px + 2 + r.nextInt(20), z = pz + 2 + r.nextInt(20);
            if (Math.abs(x - (px + 11)) < 3 || Math.abs(z - (pz + 11)) < 3) continue;
            b.tree(x, gy + 1, z, r.nextBoolean() ? Blocks.BIRCH_LOG : Blocks.OAK_LOG, r.nextBoolean() ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES);
        }
        b.bench(px + 8, gy + 1, pz + 9, Direction.SOUTH, 2);
        b.bench(px + 14, gy + 1, pz + 14, Direction.NORTH, 2);
        b.bench(px + 8, gy + 1, pz + 14, Direction.NORTH, 2);
        b.bench(px + 14, gy + 1, pz + 9, Direction.SOUTH, 2);
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
        d.locs.put("park", new BlockPos(px + 11, gy + 1, pz + 15));
        d.roadSpots.add(new BlockPos(px + 11, gy + 1, pz + 5));
        d.roadSpots.add(new BlockPos(px + 5, gy + 1, pz + 12));
    }

    /** Православный храм. */
    public static void church(B b, GorodData d, int px, int pz, int gy) {
        int x0 = px + 6, z0 = pz + 4, w = 12, dep = 15;
        int x1 = x0 + w - 1, z1 = z0 + dep - 1;
        b.fill(px, gy, pz, px + 23, gy, pz + 23, Blocks.GRASS_BLOCK);
        b.fill(x0 - 1, gy, z0 - 1, x1 + 1, gy, z1 + 3, Blocks.SMOOTH_STONE);
        b.air(x0, gy + 1, z0, x1, gy + 22, z1);
        b.walls(x0, gy + 1, z0, x1, gy + 9, z1, Blocks.WHITE_CONCRETE);
        b.fill(x0, gy + 10, z0, x1, gy + 10, z1, Blocks.WHITE_CONCRETE);
        b.walls(x0, gy + 10, z0, x1, gy + 10, z1, Blocks.SMOOTH_QUARTZ);
        for (int z = z0 + 2; z <= z1 - 2; z += 3) {
            b.fill(x0, gy + 4, z, x0, gy + 7, z, Blocks.GLASS_PANE);
            b.fill(x1, gy + 4, z, x1, gy + 7, z, Blocks.GLASS_PANE);
        }
        // барабан и луковичный купол
        int cx = x0 + w / 2, cz = z0 + dep / 2;
        b.walls(cx - 2, gy + 11, cz - 2, cx + 2, gy + 14, cz + 2, Blocks.WHITE_CONCRETE);
        int[] radii = {3, 3, 3, 2, 2, 1, 1, 0};
        for (int k = 0; k < radii.length; k++) {
            int rr = radii[k];
            for (int dx = -rr; dx <= rr; dx++)
                for (int dz = -rr; dz <= rr; dz++)
                    if (dx * dx + dz * dz <= rr * rr + 1) b.set(cx + dx, gy + 15 + k, cz + dz, Blocks.GOLD_BLOCK);
        }
        // крест
        int ct = gy + 15 + radii.length;
        for (int y = ct; y < ct + 4; y++) b.set(cx, y, cz, Blocks.GOLD_BLOCK);
        b.set(cx - 1, ct + 2, cz, Blocks.GOLD_BLOCK);
        b.set(cx + 1, ct + 2, cz, Blocks.GOLD_BLOCK);
        // вход и интерьер
        b.door(cx, gy + 1, z1, Direction.SOUTH, Blocks.SPRUCE_DOOR);
        b.set(cx, gy + 3, z1, Blocks.WHITE_CONCRETE);
        b.fill(x0 + 2, gy + 1, z0 + 1, x1 - 2, gy + 1, z0 + 1, Blocks.GOLD_BLOCK);
        b.fill(x0 + 2, gy + 2, z0 + 1, x1 - 2, gy + 4, z0 + 1, Blocks.YELLOW_TERRACOTTA);
        for (int z = z0 + 4; z < z1 - 1; z += 3) {
            b.hangingLantern(cx, gy + 9, z);
            b.set(x0 + 2, gy + 1, z, Blocks.CANDLE);
            b.set(x1 - 2, gy + 1, z, Blocks.CANDLE);
        }
        b.set(cx + 2, gy + 4, z1, Blocks.WHITE_CONCRETE);
        b.sign(cx + 2, gy + 4, z1 + 1, Direction.SOUTH, "Храм", "Святителя", "Николая", "Чудотворца");
        b.tree(px + 2, gy + 1, pz + 18, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        b.tree(px + 21, gy + 1, pz + 18, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        b.streetLamp(cx - 3, gy + 1, z1 + 3);
        d.locs.put("church", new BlockPos(cx, gy + 1, z1 - 3));
    }

    public static void university(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 1, pz + 4, 22, 13, 3, gy, Blocks.BRICKS, Blocks.SMOOTH_QUARTZ, Blocks.OAK_PLANKS,
                Blocks.GRAY_CONCRETE, Blocks.OAK_DOOR,
                new String[]{"ЮРИДИЧЕСКИЙ"}, new String[]{"ИНСТИТУТ"}, new String[]{"Приёмная", "комиссия", "ауд. 101"});
        // лекционная аудитория — ступенчатые ряды
        for (int row = 0; row < 3; row++) {
            int z = pz + 7 + row * 2;
            for (int x = px + 4; x <= px + 19; x++) b.stair(x, gy + 1, z, Blocks.OAK_STAIRS, Direction.NORTH);
        }
        b.fill(px + 6, gy + 2, pz + 5, px + 17, gy + 3, pz + 5, Blocks.BLACK_CONCRETE);
        b.set(px + 11, gy + 1, pz + 6, Blocks.LECTERN);
        b.fill(px + 3, gy + 5, pz + 5, px + 20, gy + 6, pz + 5, Blocks.BOOKSHELF);
        for (int x = px + 4; x <= px + 19; x += 5) {
            b.set(x, gy + 1, pz + 22, Blocks.QUARTZ_PILLAR);
            b.set(x, gy + 2, pz + 22, Blocks.QUARTZ_PILLAR);
        }
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        d.locs.put("university", i.inside());
    }

    public static void school(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 1, pz + 2, 22, 12, 3, gy, Blocks.YELLOW_TERRACOTTA, Blocks.WHITE_CONCRETE, Blocks.OAK_PLANKS,
                Blocks.GRAY_CONCRETE, Blocks.OAK_DOOR,
                new String[]{"МБОУ «СОШ"}, new String[]{"№ 1»"}, new String[]{"Школа", "им. Гагарина"});
        deskRow(b, px + 4, px + 19, gy + 1, pz + 6);
        deskRow(b, px + 4, px + 19, gy + 5, pz + 6);
        b.fill(px + 6, gy + 2, pz + 3, px + 15, gy + 3, pz + 3, Blocks.GREEN_CONCRETE);
        // школьный стадион
        b.fill(px + 1, gy, pz + 16, px + 22, gy, pz + 23, Blocks.RED_TERRACOTTA);
        b.fill(px + 3, gy, pz + 18, px + 20, gy, pz + 21, Blocks.GRASS_BLOCK);
        b.fill(px + 3, gy + 1, pz + 19, px + 3, gy + 2, pz + 20, Blocks.WHITE_CONCRETE);
        b.fill(px + 20, gy + 1, pz + 19, px + 20, gy + 2, pz + 20, Blocks.WHITE_CONCRETE);
        b.russianFlag(px + 2, gy + 1, pz + 15, 8);
        d.locs.put("school", i.inside());
    }

    public static void post(B b, GorodData d, int px, int pz, int gy) {
        Info i = office(b, px + 4, pz + 7, 16, 11, 2, gy, Blocks.WHITE_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.SMOOTH_STONE,
                Blocks.GRAY_CONCRETE, Blocks.SPRUCE_DOOR,
                new String[]{"ПОЧТА"}, new String[]{"Отделение", "связи"}, new String[]{"Индекс", "123456"});
        b.fill(px + 6, gy + 1, pz + 12, px + 16, gy + 1, pz + 12, Blocks.SMOOTH_STONE_SLAB);
        b.fill(px + 6, gy + 1, pz + 9, px + 9, gy + 2, pz + 9, Blocks.BARREL);
        b.bed(px + 8, gy + 5, pz + 10, Direction.NORTH, Blocks.LIGHT_BLUE_BED);
        d.homes.add(new BlockPos(px + 12, gy + 5, pz + 12));
        forecourt(b, px, pz, i.frontZ(), gy, Blocks.SMOOTH_STONE);
        d.locs.put("post", i.inside());
    }

    private Buildings() {
    }
}
