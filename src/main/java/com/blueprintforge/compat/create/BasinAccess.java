package com.blueprintforge.compat.create;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;

@Mixin(BasinOperatingBlockEntity.class)
public interface BasinAccess {
    @Invoker("getBasin")
    Optional<BasinBlockEntity> blueprintforge$basin();
}
