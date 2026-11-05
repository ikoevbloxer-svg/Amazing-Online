package ru.gorod.rusgorod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import ru.gorod.rusgorod.data.GorodData;

/**
 * Войсковая часть (64×64): ограждение, КПП, штаб, плац, казармы, столовая, склад РАВ, стрельбище.
 * (bx, bz) — северо-западный угол, въезд с запада (со стороны города).
 */
public final class MilitaryBase {
    public static final int SIZE = 64;

    public static void perimeter(B b, GorodData d, int bx, int bz, int gy) {
        int x1 = bx + SIZE - 1, z1 = bz + SIZE - 1;
        b.fill(bx, gy, bz, x1, gy, z1, Blocks.COARSE_DIRT);
        b.walls(bx, gy + 1, bz, x1, gy + 3, z1, Blocks.LIGHT_GRAY_CONCRETE);
        b.walls(bx, gy + 4, bz, x1, gy + 4, z1, Blocks.IRON_BARS);
        // въезд и КПП
        int gz = bz + 30;
        b.air(bx, gy + 1, gz, bx, gy + 4, gz + 3);
        b.fill(bx - 3, gy, gz, bx + 10, gy, gz + 3, Blocks.GRAY_CONCRETE);
        b.walls(bx + 2, gy + 1, gz - 5, bx + 6, gy + 3, gz - 1, Blocks.WHITE_CONCRETE);
        b.fill(bx + 2, gy + 4, gz - 5, bx + 6, gy + 4, gz - 1, Blocks.GREEN_CONCRETE);
        b.air(bx + 3, gy + 1, gz - 4, bx + 5, gy + 3, gz - 2);
        b.fill(bx + 3, gy + 2, gz - 1, bx + 5, gy + 2, gz - 1, Blocks.GLASS_PANE);
        b.door(bx + 4, gy + 1, gz - 5, Direction.NORTH, Blocks.SPRUCE_DOOR);
        b.hangingLantern(bx + 4, gy + 3, gz - 3);
        b.set(bx - 1, gy + 3, gz - 1, Blocks.LIGHT_GRAY_CONCRETE);
        b.set(bx - 1, gy + 2, gz - 1, Blocks.LIGHT_GRAY_CONCRETE);
        b.set(bx - 1, gy + 1, gz - 1, Blocks.LIGHT_GRAY_CONCRETE);
        b.sign(bx - 2, gy + 2, gz - 1, Direction.WEST, "ВОЙСКОВАЯ", "ЧАСТЬ 12345", "Стой!", "Запретная зона");
        // шлагбаум
        b.fill(bx + 1, gy + 1, gz + 4, bx + 1, gy + 1, gz + 4, Blocks.RED_CONCRETE);
        b.fill(bx + 1, gy + 2, gz + 1, bx + 1, gy + 2, gz + 3, Blocks.WHITE_CONCRETE);
        b.streetLamp(bx + 1, gy + 1, gz - 1);
        b.streetLamp(bx + 1, gy + 1, gz + 5);
        d.locs.put("army_kpp", new BlockPos(bx + 3, gy + 1, gz + 1));
        // внутренние дороги
        b.fill(bx + 1, gy, gz, x1 - 1, gy, gz + 3, Blocks.GRAY_CONCRETE);
        b.fill(bx + 26, gy, bz + 1, bx + 29, gy, z1 - 1, Blocks.GRAY_CONCRETE);
        for (int x = bx + 8; x < x1; x += 12) b.streetLamp(x, gy + 1, gz + 4);
    }

    public static void hqAndParade(B b, GorodData d, int bx, int bz, int gy) {
        Buildings.Info hq = Buildings.office(b, bx + 6, bz + 3, 18, 10, 2, gy, Blocks.WHITE_CONCRETE, Blocks.GREEN_CONCRETE,
                Blocks.POLISHED_ANDESITE, Blocks.GREEN_CONCRETE, Blocks.SPRUCE_DOOR,
                new String[]{"ШТАБ"}, new String[]{"в/ч 12345"}, new String[]{"Дежурный", "по части"});
        b.deskRow(bx + 17, bx + 21, gy + 1, bz + 6);
        b.deskRow(bx + 14, bx + 21, gy + 5, bz + 7);
        b.fill(bx + 14, gy + 5, bz + 4, bx + 22, gy + 5, bz + 4, Blocks.BOOKSHELF);
        b.sign(bx + 18, gy + 6, bz + 5, Direction.SOUTH, "Кабинет", "командира", "части");
        b.russianFlag(bx + 14, gy + 1, bz + 15, 9);
        b.fill(bx + 12, gy, bz + 14, bx + 16, gy, bz + 16, Blocks.STONE_BRICKS);
        d.locs.put("army_hq", hq.inside());

        // плац
        int px0 = bx + 32, pz0 = bz + 3;
        b.fill(px0, gy, pz0, px0 + 26, gy, pz0 + 18, Blocks.GRAY_CONCRETE);
        for (int x = px0 + 2; x <= px0 + 24; x += 2) {
            b.set(x, gy, pz0 + 6, Blocks.WHITE_CONCRETE);
            b.set(x, gy, pz0 + 12, Blocks.WHITE_CONCRETE);
        }
        // трибуна
        b.fill(px0 + 9, gy + 1, pz0, px0 + 17, gy + 1, pz0 + 2, Blocks.SMOOTH_QUARTZ);
        b.fill(px0 + 9, gy + 2, pz0, px0 + 17, gy + 2, pz0, Blocks.RED_CONCRETE);
        b.set(px0 + 13, gy + 2, pz0 + 1, Blocks.QUARTZ_PILLAR);
        b.set(px0 + 13, gy + 3, pz0 + 1, Blocks.QUARTZ_PILLAR);
        b.set(px0 + 13, gy + 4, pz0, Blocks.SMOOTH_QUARTZ);
        b.sign(px0 + 13, gy + 4, pz0 + 1, Direction.SOUTH, "Служу", "России!");
        b.streetLamp(px0, gy + 1, pz0 + 19);
        b.streetLamp(px0 + 26, gy + 1, pz0 + 19);
        d.locs.put("army_plac", new BlockPos(px0 + 13, gy + 1, pz0 + 10));
    }

    public static void barracks(B b, GorodData d, int bx, int bz, int gy) {
        for (int k = 0; k < 2; k++) {
            int x0 = bx + 3, z0 = bz + 37 + k * 13;
            Buildings.Info info = Buildings.office(b, x0, z0, 20, 10, 2, gy, Blocks.WHITE_TERRACOTTA, Blocks.GREEN_CONCRETE,
                    Blocks.SPRUCE_PLANKS, Blocks.GREEN_CONCRETE, Blocks.SPRUCE_DOOR,
                    new String[]{"КАЗАРМА"}, new String[]{"Рота №" + (k + 1)});
            for (int f = 0; f < 2; f++) {
                int y = gy + 1 + 4 * f;
                for (int x = x0 + 8; x <= x0 + 18; x += 2) {
                    b.bed(x, y, z0 + 2, Direction.NORTH, Blocks.GREEN_BED);
                    b.set(x, y, z0 + 4, Blocks.BARREL);
                }
                d.barracks.add(new BlockPos(x0 + 9, y, z0 + 6));
                d.barracks.add(new BlockPos(x0 + 14, y, z0 + 6));
            }
            d.barracks.add(info.inside());
        }
    }

    public static void messAndDepot(B b, GorodData d, int bx, int bz, int gy) {
        Buildings.Info mess = Buildings.office(b, bx + 33, bz + 37, 14, 9, 1, gy, Blocks.WHITE_TERRACOTTA, Blocks.GREEN_CONCRETE,
                Blocks.SMOOTH_STONE, Blocks.GREEN_CONCRETE, Blocks.SPRUCE_DOOR,
                new String[]{"СТОЛОВАЯ"}, new String[]{"Завтрак 7:00", "Обед 13:00", "Ужин 19:00"});
        for (int x = bx + 35; x <= bx + 44; x += 3) {
            b.set(x, gy + 1, bz + 40, Buildings.topSlab(Blocks.SPRUCE_SLAB));
            b.set(x, gy + 1, bz + 41, Buildings.topSlab(Blocks.SPRUCE_SLAB));
        }
        b.set(bx + 45, gy + 1, bz + 38, Blocks.SMOKER);
        b.set(bx + 44, gy + 1, bz + 38, Blocks.CAULDRON);
        d.locs.put("army_mess", mess.inside());

        Buildings.office(b, bx + 50, bz + 37, 10, 8, 1, gy, Blocks.LIGHT_GRAY_CONCRETE, Blocks.GREEN_CONCRETE,
                Blocks.SMOOTH_STONE, Blocks.IRON_BLOCK, Blocks.SPRUCE_DOOR,
                new String[]{"СКЛАД", "РАВ"});
        b.fill(bx + 51, gy + 1, bz + 38, bx + 58, gy + 1, bz + 38, Blocks.BARREL);
    }

    public static void range(B b, GorodData d, int bx, int bz, int gy) {
        int x0 = bx + 33, z0 = bz + 48, x1 = bx + 61, z1 = bz + 61;
        b.fill(x0, gy, z0, x1, gy, z1, Blocks.SAND);
        // огневой рубеж — мешки с песком
        for (int x = x0 + 1; x <= x1 - 1; x++) if (x % 3 != 0) b.set(x, gy + 1, z0 + 1, Blocks.SANDSTONE_WALL);
        // мишени
        for (int x = x0 + 3; x <= x1 - 3; x += 5) {
            b.set(x, gy + 1, z1 - 1, Blocks.HAY_BLOCK);
            b.set(x, gy + 2, z1 - 1, Blocks.TARGET);
        }
        b.fill(x0, gy + 1, z1, x1, gy + 3, z1, Blocks.COARSE_DIRT);
        b.set(x0 + 1, gy + 1, z0 + 2, Blocks.LIGHT_GRAY_CONCRETE);
        b.sign(x0 + 1, gy + 1, z0 + 3, Direction.SOUTH, "СТРЕЛЬБИЩЕ", "Не стоять", "на линии огня!");
        b.streetLamp(x0, gy + 1, z0);
        d.locs.put("army_range", new BlockPos((x0 + x1) / 2, gy + 1, z0 + 3));
    }

    private MilitaryBase() {
    }
}
