package com.blueprintforge.data;

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
import com.blueprintforge.logic.ResearchSelection;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
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
 * Reads tiers, blueprint definitions, research profiles and blueprint sources, in that order. A record that
 * fails to parse or references something unknown is skipped with an error; the rest of the pack still loads.
 */
public final class BlueprintDataLoader extends SimplePreparableReloadListener<BlueprintDataLoader.RawData> {
    public static final String TIER_DIR = "tier";
    public static final String BLUEPRINT_DIR = "blueprint";
    public static final String SOURCE_DIR = "blueprint_source";
    public static final String RESEARCH_DIR = "blueprint_research";

    private static final Logger LOGGER = BlueprintForge.LOGGER;
    private static final Gson GSON = new Gson();

    private static volatile List<String> lastErrors = List.of();

    private final HolderLookup.Provider registries;

    public BlueprintDataLoader(HolderLookup.Provider registries) {
        this.registries = registries;
    }

    public record RawData(Map<ResourceLocation, JsonElement> tiers, Map<ResourceLocation, JsonElement> blueprints,
                          Map<ResourceLocation, JsonElement> sources, Map<ResourceLocation, JsonElement> research,
                          List<String> errors) {
    }

    public record Result(Map<ResourceLocation, TierDefinition> tiers, Map<ResourceLocation, BlueprintDefinition> blueprints,
                         Map<ResourceLocation, SourceDefinition> sources, Set<ResourceLocation> inactiveBlueprints,
                         Map<ResourceLocation, ResearchProfile> research, Map<ResourceLocation, ResourceLocation> researchForBlueprint,
                         List<String> errors) {
    }

    /** Errors of the current load, kept for operators. */
    public static List<String> lastErrors() {
        return lastErrors;
    }

    @Override
    protected RawData prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        List<String> errors = new ArrayList<>();
        return new RawData(
                readDirectory(resourceManager, TIER_DIR, errors),
                readDirectory(resourceManager, BLUEPRINT_DIR, errors),
                readDirectory(resourceManager, SOURCE_DIR, errors),
                readDirectory(resourceManager, RESEARCH_DIR, errors),
                errors);
    }

    @Override
    protected void apply(RawData raw, ResourceManager resourceManager, ProfilerFiller profiler) {
        Result result = load(raw, RegistryOps.create(JsonOps.INSTANCE, registries), BuiltInRegistries.ITEM::containsKey);
        publish(result);
        LOGGER.info("Loaded {} tiers, {} blueprints ({} inactive), {} blueprint sources, {} research profiles, {} errors",
                result.tiers().size(), result.blueprints().size(), result.inactiveBlueprints().size(),
                result.sources().size(), result.research().size(), result.errors().size());
    }

    public static void publish(Result result) {
        TierRegistry.replace(result.tiers());
        BlueprintRegistry.replace(result.blueprints());
        SourceRegistry.replace(result.sources());
        ResearchRegistry.replace(result.research(), result.researchForBlueprint());
        lastErrors = List.copyOf(result.errors());
    }

    /** Applies definitions received from a remote server. Sources never leave the server. */
    public static void acceptSynced(Map<ResourceLocation, TierDefinition> tiers, Map<ResourceLocation, BlueprintDefinition> blueprints,
                                    Map<ResourceLocation, ResearchProfile> research, Map<ResourceLocation, ResourceLocation> researchForBlueprint) {
        TierRegistry.replace(tiers);
        BlueprintRegistry.replace(blueprints);
        ResearchRegistry.replace(research, researchForBlueprint);
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

    public static Result load(RawData raw, DynamicOps<JsonElement> ops, Predicate<ResourceLocation> itemExists) {
        List<String> errors = new ArrayList<>(raw.errors());

        Map<ResourceLocation, TierDefinition> tiers = new LinkedHashMap<>();
        raw.tiers().forEach((fileId, json) -> {
            String where = path(TIER_DIR, fileId);
            if (!(json instanceof JsonObject object)) {
                error(errors, where, "expected an object of tier id -> tier");
                return;
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                DataResult<ResourceLocation> id = ResourceLocation.read(entry.getKey());
                if (id.error().isPresent()) {
                    error(errors, where + "#" + entry.getKey(), id.error().get().message());
                    continue;
                }
                decode(TierDefinition.CODEC, ops, entry.getValue(), errors, where + "#" + entry.getKey())
                        .ifPresent(tier -> tiers.put(id.getOrThrow(), tier));
            }
        });

        Map<ResourceLocation, BlueprintDefinition> blueprints = new LinkedHashMap<>();
        Set<ResourceLocation> inactive = new HashSet<>();
        raw.blueprints().forEach((id, json) -> {
            String where = path(BLUEPRINT_DIR, id);
            decode(BlueprintDefinition.CODEC, ops, json, errors, where).ifPresent(def -> {
                if (!tiers.containsKey(def.tier())) {
                    error(errors, where, "unknown tier " + def.tier());
                    return;
                }
                if (def.target().isPresent() && !itemExists.test(def.target().get())) {
                    LOGGER.debug("Blueprint {} is inactive: target item {} is not in this modpack", id, def.target().get());
                    inactive.add(id);
                    return;
                }
                blueprints.put(id, def);
            });
        });

        Map<ResourceLocation, ResearchProfile> research = new LinkedHashMap<>();
        raw.research().forEach((id, json) -> {
            String where = path(RESEARCH_DIR, id);
            decode(ResearchProfile.CODEC, ops, json, errors, where).ifPresent(profile -> {
                List<ResourceLocation> unknown = profile.appliesTo().stream()
                        .filter(target -> !target.tag())
                        .map(ResearchProfile.AppliesTo::id)
                        .filter(blueprint -> !blueprints.containsKey(blueprint) && !inactive.contains(blueprint))
                        .toList();
                if (!unknown.isEmpty()) {
                    error(errors, where, "unknown blueprint " + unknown);
                    return;
                }
                research.put(id, profile);
            });
        });

        Map<ResourceLocation, ResourceLocation> researchForBlueprint = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, BlueprintDefinition> entry : blueprints.entrySet()) {
            ResearchSelection.Choice choice = ResearchSelection.choose(entry.getKey(), entry.getValue().tags(), research);
            if (choice.ambiguous()) {
                LOGGER.warn("Research profiles for {} are equally specific; using {}", entry.getKey(), choice.profileId().orElseThrow());
            }
            choice.profileId().ifPresent(profileId -> researchForBlueprint.put(entry.getKey(), profileId));
        }

        Map<ResourceLocation, SourceDefinition> sources = new LinkedHashMap<>();
        raw.sources().forEach((id, json) -> {
            String where = path(SOURCE_DIR, id);
            decode(SourceDefinition.Raw.CODEC, ops, json, errors, where).ifPresent(source -> {
                if (inactive.contains(source.blueprint())) {
                    LOGGER.debug("Blueprint source {} is inactive: blueprint {} is inactive", id, source.blueprint());
                    return;
                }
                if (!blueprints.containsKey(source.blueprint())) {
                    error(errors, where, "unknown blueprint " + source.blueprint());
                    return;
                }
                List<SourceDefinition.LootInjection> injections = new ArrayList<>();
                List<String> unsupported = new ArrayList<>();
                for (int index = 0; index < source.sources().size(); index++) {
                    Dynamic<?> entry = source.sources().get(index);
                    String type = entry.get("type").asString().result().orElse("<missing>");
                    if (SourceDefinition.LootInjection.TYPE.equals(type)) {
                        decode(SourceDefinition.LootInjection.CODEC, entry, errors, where + "#sources[" + index + "]")
                                .ifPresent(injections::add);
                    } else {
                        unsupported.add(type);
                    }
                }
                if (!unsupported.isEmpty()) {
                    error(errors, where, "source types " + unsupported + " are not supported by this version, skipped");
                }
                if (!injections.isEmpty()) {
                    sources.put(id, new SourceDefinition(source.blueprint(), source.weight(), source.conditions(), List.copyOf(injections)));
                }
            });
        });

        return new Result(tiers, blueprints, sources, inactive, research, researchForBlueprint, errors);
    }

    private static String path(String directory, ResourceLocation id) {
        return "data/" + id.getNamespace() + "/" + directory + "/" + id.getPath() + ".json";
    }

    private static <T> Optional<T> decode(Codec<T> codec, DynamicOps<JsonElement> ops, JsonElement json, List<String> errors, String where) {
        return decode(codec, new Dynamic<>(ops, json), errors, where);
    }

    private static <T> Optional<T> decode(Codec<T> codec, Dynamic<?> input, List<String> errors, String where) {
        DataResult<T> result = codec.parse(input);
        if (result.error().isPresent()) {
            error(errors, where, result.error().get().message());
            return Optional.empty();
        }
        return result.result();
    }

    private static void error(List<String> errors, String where, String message) {
        String error = "Skipped " + where + ": " + message;
        LOGGER.error(error);
        errors.add(error);
    }
}
