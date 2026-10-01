package com.blueprintforge.registry;

import java.util.function.Supplier;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.machine.BlueprintArchiveBlock;
import com.blueprintforge.machine.BlueprintArchiveBlockEntity;
import com.blueprintforge.machine.ProjectBureauBlock;
import com.blueprintforge.machine.ProjectBureauBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BFBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BlueprintForge.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BlueprintForge.MOD_ID);

    public static final DeferredBlock<BlueprintArchiveBlock> BLUEPRINT_ARCHIVE = BLOCKS.register("blueprint_archive",
            () -> new BlueprintArchiveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<ProjectBureauBlock> PROJECT_BUREAU = BLOCKS.register("project_bureau",
            () -> new ProjectBureauBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.WOOD)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<BlueprintArchiveBlockEntity>> BLUEPRINT_ARCHIVE_ENTITY = BLOCK_ENTITIES.register("blueprint_archive",
            () -> BlockEntityType.Builder.of(BlueprintArchiveBlockEntity::new, BLUEPRINT_ARCHIVE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<ProjectBureauBlockEntity>> PROJECT_BUREAU_ENTITY = BLOCK_ENTITIES.register("project_bureau",
            () -> BlockEntityType.Builder.of(ProjectBureauBlockEntity::new, PROJECT_BUREAU.get()).build(null));

    private BFBlocks() {
    }
}
