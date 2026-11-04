package ru.gorod.rusgorod.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.gorod.rusgorod.RusGorod;
import ru.gorod.rusgorod.block.LitterBlock;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RusGorod.MODID);

    /** Мусор на улицах — работа дворника. */
    public static final RegistryObject<Block> LITTER = BLOCKS.register("litter",
            () -> new LitterBlock(BlockBehaviour.Properties.of().strength(0.1f).noCollission().noOcclusion().sound(SoundType.WOOL)));
}
