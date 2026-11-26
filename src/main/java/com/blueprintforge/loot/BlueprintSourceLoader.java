package com.blueprintforge.loot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.research.ResearchDataLoader;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Reads {@code blueprint_source/}, one source per file. Same rule as tiers and targets: a file that does not parse or
 * fails validation is skipped with an error in the log, the rest still loads and the server keeps running.
 */
public final class BlueprintSourceLoader extends SimplePreparableReloadListener<BlueprintSourceLoader.RawData> {
    public static final String DIR = "blueprint_source";

    private static final Logger LOGGER = BlueprintForge.LOGGER;
    private static volatile List<String> lastErrors = List.of();

    public record RawData(Map<ResourceLocation, JsonElement> sources, List<String> errors) {
    }

    public record Result(Map<ResourceLocation, BlueprintSource> sources, List<String> errors) {
    }

    /** Errors of the current load, kept for operators and tests. */
    public static List<String> lastErrors() {
        return lastErrors;
    }

    @Override
    protected RawData prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        List<String> errors = new ArrayList<>();
        return new RawData(ResearchDataLoader.readDirectory(resourceManager, DIR, errors), errors);
    }

    @Override
    protected void apply(RawData raw, ResourceManager resourceManager, ProfilerFiller profiler) {
        Result result = load(raw, JsonOps.INSTANCE);
        BlueprintSourceRegistry.replace(result.sources());
        lastErrors = List.copyOf(result.errors());
        LOGGER.info("Loaded {} blueprint loot sources, {} errors", result.sources().size(), result.errors().size());
    }

    /** Pure part of the load, free of game state so it can be unit tested. */
    public static Result load(RawData raw, DynamicOps<JsonElement> ops) {
        List<String> errors = new ArrayList<>(raw.errors());
        Map<ResourceLocation, BlueprintSource> sources = new LinkedHashMap<>();
        raw.sources().forEach((id, json) -> {
            DataResult<BlueprintSource> parsed;
            try {
                parsed = BlueprintSource.CODEC.parse(ops, json);
            } catch (RuntimeException e) {
                parsed = DataResult.error(() -> "unexpected " + e);
            }
            if (parsed.error().isPresent()) {
                String error = "Skipped data/" + id.getNamespace() + "/" + DIR + "/" + id.getPath() + ".json: " + parsed.error().get().message();
                LOGGER.error(error);
                errors.add(error);
            } else {
                sources.put(id, parsed.result().orElseThrow());
            }
        });
        return new Result(sources, errors);
    }
}
