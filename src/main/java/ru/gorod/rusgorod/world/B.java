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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

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

    /** Двухблочная дверь. facing — куда «смотрит» дверь (наружу). */
    public void door(int x, int y, int z, Direction facing, Block door) {
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upper = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        set(x, y, z, lower);
        set(x, y + 1, z, upper);
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

    public void bed(int x, int y, int z, Direction facing, Block bed) {
        set(x, y, z, bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT));
        int hx = x + facing.getStepX(), hz = z + facing.getStepZ();
        set(hx, y, hz, bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.HEAD));
    }

    public void hangingLantern(int x, int y, int z) {
        set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    /** Уличный фонарь. */
    public void streetLamp(int x, int y, int z) {
        for (int i = 0; i < 4; i++) set(x, y + i, z, Blocks.DARK_OAK_FENCE);
        set(x, y + 4, z, Blocks.LANTERN);
    }

    public void tree(int x, int y, int z, Block log, Block leaves) {
        for (int i = 0; i < 5; i++) set(x, y + i, z, log);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 3; dy <= 6; dy++) {
                    int dist = Math.abs(dx) + Math.abs(dz) + Math.max(0, dy - 5) * 2;
                    if (dist <= (dy >= 5 ? 2 : 3) && !(dx == 0 && dz == 0 && dy < 5))
                        set(x + dx, y + dy, z + dz, leaves.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
                }
    }

    public void bench(int x, int y, int z, Direction facing, int len) {
        for (int i = 0; i < len; i++) {
            int bx = x + (facing.getAxis() == Direction.Axis.Z ? i : 0);
            int bz = z + (facing.getAxis() == Direction.Axis.X ? i : 0);
            stair(bx, y, bz, Blocks.SPRUCE_STAIRS, facing.getOpposite());
        }
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
}
