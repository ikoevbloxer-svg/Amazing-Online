package ru.gorod.rusgorod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Примитивы строительства. Все координаты абсолютные. */
public class B {
    public final ServerLevel l;
    public final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    public B(ServerLevel l) {
        this.l = l;
    }

    public void set(int x, int y, int z, BlockState s) {
        m.set(x, y, z);
        l.setBlock(m, s, FLAGS);
    }

    public void set(int x, int y, int z, Block b) {
        set(x, y, z, b.defaultBlockState());
    }

    public BlockState get(int x, int y, int z) {
        return l.getBlockState(m.set(x, y, z));
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, s);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        fill(x1, y1, z1, x2, y2, z2, b.defaultBlockState());
    }

    /** Полая коробка: только стены (без пола и потолка). */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        fill(x1, y1, z1, x2, y2, z1, b);
        fill(x1, y1, z2, x2, y2, z2, b);
        fill(x1, y1, z1, x1, y2, z2, b);
        fill(x2, y1, z1, x2, y2, z2, b);
    }

    public void air(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, Blocks.AIR);
    }

    public static BlockState topSlab(Block slab) {
        return slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    public static BlockState bottomSlab(Block slab) {
        return slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** Двухблочная дверь. facing — куда «смотрит» дверь (наружу). */
    public void door(int x, int y, int z, Direction facing, Block door) {
        door(x, y, z, facing, door, DoorHingeSide.LEFT);
    }

    public void door(int x, int y, int z, Direction facing, Block door, DoorHingeSide hinge) {
        BlockState base = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge);
        set(x, y, z, base.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(x, y + 1, z, base.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Двустворчатая дверь вдоль оси X (x и x+1). */
    public void doubleDoor(int x, int y, int z, Direction facing, Block door) {
        door(x, y, z, facing, door, DoorHingeSide.LEFT);
        door(x + 1, y, z, facing, door, DoorHingeSide.RIGHT);
    }

    /** Табличка на стене. facing — направление лицевой стороны (стена с противоположной стороны). */
    public void sign(int x, int y, int z, Direction facing, String... lines) {
        set(x, y, z, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing));
        BlockEntity be = l.getBlockEntity(m.set(x, y, z));
        if (be instanceof SignBlockEntity sign) {
            SignText text = new SignText();
            for (int i = 0; i < Math.min(4, lines.length); i++) {
                text = text.setMessage(i, Component.literal(lines[i]));
            }
            sign.setText(text, true);
            sign.setChanged();
        }
    }

    public void ladder(int x, int y1, int y2, int z, Direction facing) {
        for (int y = y1; y <= y2; y++) set(x, y, z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
    }

    public void stair(int x, int y, int z, Block stair, Direction facing) {
        set(x, y, z, stair.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM));
    }

    public void stairTop(int x, int y, int z, Block stair, Direction facing) {
        set(x, y, z, stair.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.TOP));
    }

    /**
     * Двухмаршевая лестница между этажами высотой h (марш из h ступеней). Занимает клетки x..x+h+1 по X и две полосы z, z+1.
     * Чётные марши поднимаются на восток по полосе z, нечётные — на запад по полосе z+1; проёмы в перекрытиях прорезаются.
     */
    public void stairwell(int x, int z, int gy, int floors, int h, Block stairBlock) {
        for (int i = 0; i < floors - 1; i++) {
            int base = gy + h * i;
            int next = base + h;
            boolean even = i % 2 == 0;
            int lane = even ? z : z + 1;
            for (int k = 0; k < h; k++) {
                int sx = even ? x + 1 + k : x + h - k;
                int sy = base + 1 + k;
                // проём в перекрытии над маршем
                if (sy + 2 >= next && sy != next) set(sx, next, lane, Blocks.AIR);
                set(sx, sy + 1, lane, Blocks.AIR);
                if (sy + 2 != next) set(sx, sy + 2, lane, Blocks.AIR);
                stair(sx, sy, lane, stairBlock, even ? Direction.EAST : Direction.WEST);
            }
        }
    }

    public void bed(int x, int y, int z, Direction facing, Block bed) {
        set(x, y, z, bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT));
        int hx = x + facing.getStepX(), hz = z + facing.getStepZ();
        set(hx, y, hz, bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.HEAD));
    }

    public void hangingLantern(int x, int y, int z) {
        set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    /** Уличный фонарь с кронштейном. */
    public void streetLamp(int x, int y, int z) {
        for (int i = 0; i < 4; i++) set(x, y + i, z, Blocks.DARK_OAK_FENCE);
        set(x, y + 4, z, Blocks.DARK_OAK_FENCE);
        set(x, y + 5, z, Blocks.LANTERN);
    }

    public void tree(int x, int y, int z, Block log, Block leaves) {
        for (int i = 0; i < 5; i++) set(x, y + i, z, log);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 3; dy <= 6; dy++) {
                    int dist = Math.abs(dx) + Math.abs(dz) + Math.max(0, dy - 5) * 2;
                    if (dist <= (dy >= 5 ? 2 : 3) && !(dx == 0 && dz == 0 && dy < 5))
                        set(x + dx, y + dy, z + dz, leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
                }
    }

    /** Ель / туя — для частного сектора и сквера. */
    public void spruce(int x, int y, int z) {
        for (int i = 0; i < 6; i++) set(x, y + i, z, Blocks.SPRUCE_LOG);
        BlockState lv = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dy = 2; dy <= 7; dy++) {
            int rr = dy < 4 ? 2 : (dy < 6 ? 1 : 0);
            for (int dx = -rr; dx <= rr; dx++)
                for (int dz = -rr; dz <= rr; dz++)
                    if ((dx != 0 || dz != 0 || dy > 5) && Math.abs(dx) + Math.abs(dz) <= rr + 1) set(x + dx, y + dy, z + dz, lv);
        }
    }

    public void bench(int x, int y, int z, Direction facing, int len) {
        for (int i = 0; i < len; i++) {
            int bx = x + (facing.getAxis() == Direction.Axis.Z ? i : 0);
            int bz = z + (facing.getAxis() == Direction.Axis.X ? i : 0);
            stair(bx, y, bz, Blocks.SPRUCE_STAIRS, facing.getOpposite());
        }
    }

    /** Цветок в горшке. */
    public void plant(int x, int y, int z, int variant) {
        Block[] pots = {Blocks.POTTED_FERN, Blocks.POTTED_OAK_SAPLING, Blocks.POTTED_RED_TULIP, Blocks.POTTED_CACTUS, Blocks.POTTED_BIRCH_SAPLING};
        set(x, y, z, pots[Math.floorMod(variant, pots.length)]);
    }

    /** Стол со стулом: стол (верхняя плита) в (x,z), стул — ступенька к югу. */
    public void desk(int x, int y, int z) {
        set(x, y, z, topSlab(Blocks.SPRUCE_SLAB));
        stair(x, y, z + 1, Blocks.SPRUCE_STAIRS, Direction.NORTH);
    }

    public void deskRow(int x0, int x1, int y, int z) {
        for (int x = x0; x <= x1; x += 3) desk(x, y, z);
    }

    /** Флагшток с триколором. */
    public void russianFlag(int x, int y, int z, int poleHeight) {
        for (int i = 0; i < poleHeight; i++) set(x, y + i, z, Blocks.IRON_BARS);
        int top = y + poleHeight - 1;
        for (int i = 1; i <= 4; i++) {
            set(x + i, top, z, Blocks.WHITE_WOOL);
            set(x + i, top - 1, z, Blocks.BLUE_WOOL);
            set(x + i, top - 2, z, Blocks.RED_WOOL);
        }
    }

    /** Двускатная крыша вдоль X над прямоугольником (x0..x1, z0..z1), начиная с высоты y. */
    public void gableRoof(int x0, int z0, int x1, int z1, int y, Block stairs, Block full) {
        int depth = z1 - z0 + 1;
        int half = depth / 2;
        for (int k = 0; k <= half; k++) {
            int zn = z0 - 1 + k, zs = z1 + 1 - k;
            int yy = y + k;
            if (zn > zs) break;
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                if (zn == zs) {
                    set(x, yy, zn, bottomSlab(Blocks.SPRUCE_SLAB));
                } else {
                    stair(x, yy, zn, stairs, Direction.SOUTH);
                    stair(x, yy, zs, stairs, Direction.NORTH);
                    for (int zz = zn + 1; zz < zs; zz++) if (x == x0 - 1 || x == x1 + 1) set(x, yy, zz, Blocks.AIR);
                }
            }
            // фронтоны
            for (int zz = zn + 1; zz < zs; zz++) {
                set(x0, yy, zz, full);
                set(x1, yy, zz, full);
            }
        }
    }

    /** Карниз: кольцо верхних полублоков вокруг здания на высоте y (выступ на 1 блок). */
    public void cornice(int x0, int z0, int x1, int z1, int y, Block slab) {
        BlockState s = topSlab(slab);
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            set(x, y, z0 - 1, s);
            set(x, y, z1 + 1, s);
        }
        for (int z = z0; z <= z1; z++) {
            set(x0 - 1, y, z, s);
            set(x1 + 1, y, z, s);
        }
    }
}
