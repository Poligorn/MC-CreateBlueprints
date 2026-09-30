package com.blueprintforge.data;

import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Research profiles loaded from the datapack, and the one profile chosen for each blueprint. */
public final class ResearchRegistry {
    private static volatile Map<ResourceLocation, ResearchProfile> byId = Map.of();
    private static volatile Map<ResourceLocation, ResourceLocation> byBlueprint = Map.of();

    private ResearchRegistry() {
    }

    public static void replace(Map<ResourceLocation, ResearchProfile> profiles, Map<ResourceLocation, ResourceLocation> forBlueprint) {
        byId = Map.copyOf(profiles);
        byBlueprint = Map.copyOf(forBlueprint);
    }

    public static Map<ResourceLocation, ResearchProfile> all() {
        return byId;
    }

    public static Map<ResourceLocation, ResourceLocation> assignments() {
        return byBlueprint;
    }

    public static Optional<ResearchProfile> get(ResourceLocation id) {
        return Optional.ofNullable(byId.get(id));
    }

    public static Optional<ResearchProfile> forBlueprint(ResourceLocation blueprintId) {
        ResourceLocation profileId = byBlueprint.get(blueprintId);
        return profileId == null ? Optional.empty() : Optional.ofNullable(byId.get(profileId));
    }
}
