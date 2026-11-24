package com.blueprintforge.research;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.slf4j.Logger;

import com.blueprintforge.BlueprintForge;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Reads {@code research_tier/} and {@code research_target/}, one entry per file, the id being the file path. A file
 * that does not parse or fails validation is skipped with an error in the log; the rest of the pack still loads and
 * the server keeps running. A target whose item is not in this modpack is skipped quietly: that is a pack choice.
 */
public final class ResearchDataLoader extends SimplePreparableReloadListener<ResearchDataLoader.RawData> {
    public static final String TIER_DIR = "research_tier";
    public static final String TARGET_DIR = "research_target";

    private static final Logger LOGGER = BlueprintForge.LOGGER;
    private static final Gson GSON = new Gson();
    private static volatile List<String> lastErrors = List.of();

    private final HolderLookup.Provider registries;

    public ResearchDataLoader(HolderLookup.Provider registries) {
        this.registries = registries;
    }

    public record RawData(Map<ResourceLocation, JsonElement> tiers, Map<ResourceLocation, JsonElement> targets, List<String> errors) {
    }

    public record Result(Map<ResourceLocation, ResearchTier> tiers, Map<ResourceLocation, ResearchTarget> targets,
                         Set<ResourceLocation> inactiveTargets, List<String> errors) {
    }

    /** Errors of the current load, kept for operators and tests. */
    public static List<String> lastErrors() {
        return lastErrors;
    }

    @Override
    protected RawData prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        List<String> errors = new ArrayList<>();
        return new RawData(readDirectory(resourceManager, TIER_DIR, errors), readDirectory(resourceManager, TARGET_DIR, errors), errors);
    }

    @Override
    protected void apply(RawData raw, ResourceManager resourceManager, ProfilerFiller profiler) {
        Result result = load(raw, RegistryOps.create(JsonOps.INSTANCE, registries), BuiltInRegistries.ITEM::containsKey);
        ResearchRegistry.replace(result.tiers(), result.targets());
        lastErrors = List.copyOf(result.errors());
        LOGGER.info("Loaded {} research tiers, {} research targets ({} inactive), {} errors",
                result.tiers().size(), result.targets().size(), result.inactiveTargets().size(), result.errors().size());
    }

    private static Map<ResourceLocation, JsonElement> readDirectory(ResourceManager resourceManager, String directory, List<String> errors) {
        FileToIdConverter converter = FileToIdConverter.json(directory);
        Map<ResourceLocation, JsonElement> out = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : converter.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation file = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                out.put(converter.fileToId(file), parseJson(reader));
            } catch (IOException | RuntimeException e) {
                String error = "Broken file data/" + file.getNamespace() + "/" + file.getPath()
                        + " in pack '" + entry.getValue().sourcePackId() + "': " + e.getMessage();
                LOGGER.error(error);
                errors.add(error);
            }
        }
        return out;
    }

    public static JsonElement parseJson(Reader reader) {
        JsonElement element = GsonHelper.fromNullableJson(GSON, reader, JsonElement.class, false);
        if (element == null) {
            throw new IllegalStateException("file is empty");
        }
        return element;
    }

    /** Pure part of the load, kept free of game state so it can be unit tested. */
    public static Result load(RawData raw, DynamicOps<JsonElement> ops, Predicate<ResourceLocation> itemExists) {
        List<String> errors = new ArrayList<>(raw.errors());

        Map<ResourceLocation, ResearchTier> tiers = new LinkedHashMap<>();
        raw.tiers().forEach((id, json) ->
                decode(ResearchTier.CODEC, ops, json, errors, path(TIER_DIR, id)).ifPresent(tier -> {
                    if (tier.attemptCost().pool().stream().anyMatch(entry -> !itemExists.test(entry.item()))) {
                        // A pool item missing from the pack is a pack choice, not a typo we can tell apart: keep the tier, say so.
                        LOGGER.warn("Research tier {} lists resource items that are not in this modpack", id);
                    }
                    tiers.put(id, tier);
                }));

        Map<ResourceLocation, ResearchTarget> targets = new LinkedHashMap<>();
        Set<ResourceLocation> inactive = new HashSet<>();
        raw.targets().forEach((id, json) ->
                decode(ResearchTarget.CODEC, ops, json, errors, path(TARGET_DIR, id)).ifPresent(target -> {
                    if (!itemExists.test(target.item())) {
                        LOGGER.debug("Research target {} is inactive: item {} is not in this modpack", id, target.item());
                        inactive.add(id);
                        return;
                    }
                    targets.put(id, target);
                }));

        return new Result(tiers, targets, inactive, errors);
    }

    private static String path(String directory, ResourceLocation id) {
        return "data/" + id.getNamespace() + "/" + directory + "/" + id.getPath() + ".json";
    }

    private static <T> Optional<T> decode(Codec<T> codec, DynamicOps<JsonElement> ops, JsonElement json, List<String> errors, String where) {
        DataResult<T> result;
        try {
            result = codec.parse(ops, json);
        } catch (RuntimeException e) {
            result = DataResult.error(() -> "unexpected " + e);
        }
        if (result.error().isPresent()) {
            String error = "Skipped " + where + ": " + result.error().get().message();
            LOGGER.error(error);
            errors.add(error);
            return Optional.empty();
        }
        return result.result();
    }
}
