package com.blueprintforge.terminal;

import com.blueprintforge.compat.createapi.SpikeFanBlastingType;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * STUB of the line scanner (open question 37): looks for the machines a chain needs near the terminal and nothing else.
 * It does not check order, belts or whether the machines are connected to each other or powered.
 * TODO(egor): the radius and the meaning of "what the line lacks" are proposals.
 */
public final class LineScanner {
    public static final int RADIUS = 16;
    public static final int PRESS = 1;
    public static final int BLAST = 2;
    public static final int DEPLOYER = 4;

    private LineScanner() {
    }

    /** Bits of the machines that are needed and not found. Unloaded chunks are never loaded by the scan. */
    public static int missing(Level level, BlockPos origin, boolean needPress, boolean needBlast) {
        return missing(level, origin, needPress, needBlast, false, RADIUS);
    }

    /** Same with the deployer a chain with hand-fed steps needs. */
    public static int missing(Level level, BlockPos origin, boolean needPress, boolean needBlast, boolean needDeployer) {
        return missing(level, origin, needPress, needBlast, needDeployer, RADIUS);
    }

    /** The radius is a parameter so a test can keep the scan inside its own scene. */
    public static int missing(Level level, BlockPos origin, boolean needPress, boolean needBlast, int radius) {
        return missing(level, origin, needPress, needBlast, false, radius);
    }

    public static int missing(Level level, BlockPos origin, boolean needPress, boolean needBlast, boolean needDeployer, int radius) {
        boolean press = !needPress;
        boolean blast = !needBlast;
        boolean deployer = !needDeployer;
        if (press && blast && deployer) {
            return 0;
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!press && AllBlocks.MECHANICAL_PRESS.has(state)) {
                press = true;
            } else if (!blast && AllBlocks.ENCASED_FAN.has(state) && blastsInFront(level, pos, state)) {
                blast = true;
            } else if (!deployer && AllBlocks.DEPLOYER.has(state)) {
                deployer = true;
            }
            if (press && blast && deployer) {
                return 0;
            }
        }
        return (press ? 0 : PRESS) | (blast ? 0 : BLAST) | (deployer ? 0 : DEPLOYER);
    }

    private static boolean blastsInFront(Level level, BlockPos fan, BlockState state) {
        Direction facing = state.getValue(BlockStateProperties.FACING);
        FanProcessingType type = FanProcessingType.getAt(level, fan.relative(facing));
        return type == AllFanProcessingTypes.BLASTING || type instanceof SpikeFanBlastingType;
    }
}
