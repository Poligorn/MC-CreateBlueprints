package com.blueprintforge.compat.createapi;

import java.util.List;

import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A fan processing type registered in Create's public {@code FAN_PROCESSING_TYPE} registry. It is valid exactly where
 * Create's own bulk blasting is valid (a fan behind lava) and wins by one priority point, so it is the type
 * {@link FanProcessingType#getAt} returns there. Everything except workpieces is delegated to bulk blasting, so
 * ordinary blasting recipes keep working. No recipe is registered, so JEI/EMI have nothing to show.
 */
public final class SpikeFanBlastingType implements FanProcessingType {
    private static FanProcessingType blasting() {
        return AllFanProcessingTypes.BLASTING;
    }

    @Override
    public boolean isValidAt(Level level, BlockPos pos) {
        return blasting().isValidAt(level, pos);
    }

    @Override
    public int getPriority() {
        return blasting().getPriority() + 1;
    }

    @Override
    public boolean canProcess(ItemStack stack, Level level) {
        return SpikeLine.isWorkpiece(stack) || blasting().canProcess(stack, level);
    }

    @Override
    public List<ItemStack> process(ItemStack stack, Level level) {
        if (SpikeLine.isWorkpiece(stack)) {
            return List.of(SpikeLine.withOperation(stack, SpikeLine.BLAST));
        }
        return blasting().process(stack, level);
    }

    @Override
    public void spawnProcessingParticles(Level level, Vec3 pos) {
        blasting().spawnProcessingParticles(level, pos);
    }

    @Override
    public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
        blasting().morphAirFlow(particleAccess, random);
    }

    @Override
    public void affectEntity(Entity entity, Level level) {
        blasting().affectEntity(entity, level);
    }
}
