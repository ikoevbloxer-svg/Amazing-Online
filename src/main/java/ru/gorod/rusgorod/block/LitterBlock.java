package ru.gorod.rusgorod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Мусор на тротуаре. Убирается дворником (работа гражданских). */
public class LitterBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 2, 15);

    public LitterBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
