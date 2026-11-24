package com.blueprintforge.registry;

import java.util.function.Supplier;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.terminal.TerminalBlock;
import com.blueprintforge.terminal.TerminalBlockEntity;

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

    public static final DeferredBlock<TerminalBlock> TERMINAL = BLOCKS.register("terminal",
            () -> new TerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<TerminalBlockEntity>> TERMINAL_ENTITY = BLOCK_ENTITIES.register("terminal",
            () -> BlockEntityType.Builder.of(TerminalBlockEntity::new, TERMINAL.get()).build(null));

    private BFBlocks() {
    }
}
