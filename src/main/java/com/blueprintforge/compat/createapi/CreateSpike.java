package com.blueprintforge.compat.createapi;

import com.blueprintforge.BlueprintForge;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entry point of the Create-hook spike (task 1, Trello Agmz84v6). Throwaway scaffolding: it proves that a press and a
 * bulk-blasting fan can be observed and can rewrite a belt item without registering a Create recipe.
 */
public final class CreateSpike {
    private static final DeferredRegister<FanProcessingType> FAN_TYPES =
            DeferredRegister.create(CreateRegistries.FAN_PROCESSING_TYPE, BlueprintForge.MOD_ID);

    static {
        FAN_TYPES.register("spike_blasting", SpikeFanBlastingType::new);
    }

    private CreateSpike() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        SpikeComponents.COMPONENTS.register(modBus);
        FAN_TYPES.register(modBus);
        SpikePressHook.register(gameBus);
    }
}
