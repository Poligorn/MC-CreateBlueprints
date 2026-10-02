package com.blueprintforge.registry;

import java.util.function.Supplier;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.ForgedItemData;
import com.blueprintforge.data.LineMark;
import com.blueprintforge.data.StampData;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BFComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BlueprintForge.MOD_ID);

    public static final Supplier<DataComponentType<BlueprintData>> BLUEPRINT = COMPONENTS.registerComponentType("blueprint",
            builder -> builder.persistent(BlueprintData.CODEC).networkSynchronized(BlueprintData.STREAM_CODEC));

    public static final Supplier<DataComponentType<ForgedItemData>> FORGED = COMPONENTS.registerComponentType("forged",
            builder -> builder.persistent(ForgedItemData.CODEC).networkSynchronized(ForgedItemData.STREAM_CODEC));

    public static final Supplier<DataComponentType<LineMark>> LINE_MARK = COMPONENTS.registerComponentType("line_mark",
            builder -> builder.persistent(LineMark.CODEC).networkSynchronized(LineMark.STREAM_CODEC));

    public static final Supplier<DataComponentType<StampData>> STAMP = COMPONENTS.registerComponentType("stamp",
            builder -> builder.persistent(StampData.CODEC).networkSynchronized(StampData.STREAM_CODEC));

    private BFComponents() {
    }
}
