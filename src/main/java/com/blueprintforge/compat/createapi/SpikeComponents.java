package com.blueprintforge.compat.createapi;

import java.util.function.Supplier;

import com.blueprintforge.BlueprintForge;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

final class SpikeComponents {
    static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BlueprintForge.MOD_ID);

    static final Supplier<DataComponentType<SpikeLine>> LINE = COMPONENTS.registerComponentType("spike_line",
            builder -> builder.persistent(SpikeLine.CODEC).networkSynchronized(SpikeLine.STREAM_CODEC));

    private SpikeComponents() {
    }
}
