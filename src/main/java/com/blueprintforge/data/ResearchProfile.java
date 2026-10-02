package com.blueprintforge.data;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * One {@code blueprint_research} file: the price of an ME, Flux or Potency step.
 * {@code copyTimePerRunTicks} is one printed run, in game ticks, and only advances while the shaft turns.
 * Stress is applied only while a step, a copy or an assembly is running.
 */
public record ResearchProfile(
        List<AppliesTo> appliesTo,
        List<BlueprintDefinition.CostEntry> meStepCost,
        List<BlueprintDefinition.CostEntry> fluxStepCost,
        List<BlueprintDefinition.CostEntry> potencyStepCost,
        int stressPerStep,
        int timePerStepTicks,
        boolean allowResearchOnCopy,
        int copyTimePerRunTicks,
        int copyStress
) {
    public static final Codec<ResearchProfile> CODEC = RecordCodecBuilder.<ResearchProfile>create(i -> i.group(
            AppliesTo.CODEC.listOf().fieldOf("applies_to").forGetter(ResearchProfile::appliesTo),
            BlueprintDefinition.CostEntry.CODEC.listOf().optionalFieldOf("me_step_cost", List.of()).forGetter(ResearchProfile::meStepCost),
            BlueprintDefinition.CostEntry.CODEC.listOf().optionalFieldOf("flux_step_cost", List.of()).forGetter(ResearchProfile::fluxStepCost),
            BlueprintDefinition.CostEntry.CODEC.listOf().optionalFieldOf("potency_step_cost", List.of()).forGetter(ResearchProfile::potencyStepCost),
            Codec.intRange(0, 1_000_000).fieldOf("stress_per_step").forGetter(ResearchProfile::stressPerStep),
            Codec.intRange(1, 1_000_000).fieldOf("time_per_step_ticks").forGetter(ResearchProfile::timePerStepTicks),
            Codec.BOOL.optionalFieldOf("allow_research_on_copy", false).forGetter(ResearchProfile::allowResearchOnCopy),
            Codec.intRange(1, 1_000_000).optionalFieldOf("copy_time_per_run_ticks", 80).forGetter(ResearchProfile::copyTimePerRunTicks),
            Codec.intRange(0, 1_000_000).optionalFieldOf("copy_stress", 256).forGetter(ResearchProfile::copyStress)
    ).apply(i, ResearchProfile::new)).validate(profile -> profile.appliesTo.isEmpty()
            ? DataResult.error(() -> "applies_to is empty")
            : DataResult.success(profile));

    /** A blueprint id, or a definition tag when {@code tag} is true ({@code #namespace:path} in JSON). */
    public record AppliesTo(ResourceLocation id, boolean tag) {
        public static final Codec<AppliesTo> CODEC = Codec.STRING.comapFlatMap(AppliesTo::parse, AppliesTo::format);

        private static DataResult<AppliesTo> parse(String raw) {
            boolean tag = raw.startsWith("#");
            String body = tag ? raw.substring(1) : raw;
            if (body.isEmpty()) {
                return DataResult.error(() -> "empty applies_to entry");
            }
            return ResourceLocation.read(body).map(id -> new AppliesTo(id, tag));
        }

        private String format() {
            return tag ? "#" + id : id.toString();
        }
    }
}
