package com.blueprintforge.data;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * One {@code blueprint_assembly} file. Fragments plus extra ingredients and machine time make one original.
 * An unsupported {@code machine} is rejected by the loader; it is not silently retargeted at the bureau.
 */
public record AssemblyRecipe(
        ResourceLocation outputBlueprint,
        int fragmentsRequired,
        ResourceLocation fragmentBlueprint,
        ResourceLocation machine,
        int processTimeTicks,
        List<BlueprintDefinition.CostEntry> extraIngredients,
        boolean oneTimePerChunk,
        boolean announceToServer,
        int stress
) {
    public static final ResourceLocation LABORATORY = ResourceLocation.fromNamespaceAndPath("blueprintforge", "blueprint_laboratory");

    public static final Codec<AssemblyRecipe> CODEC = RecordCodecBuilder.<AssemblyRecipe>create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("output_blueprint").forGetter(AssemblyRecipe::outputBlueprint),
            Codec.intRange(1, 64).fieldOf("fragments_required").forGetter(AssemblyRecipe::fragmentsRequired),
            ResourceLocation.CODEC.fieldOf("fragment_item").forGetter(AssemblyRecipe::fragmentBlueprint),
            ResourceLocation.CODEC.fieldOf("machine").forGetter(AssemblyRecipe::machine),
            Codec.intRange(1, 1_000_000).fieldOf("process_time_ticks").forGetter(AssemblyRecipe::processTimeTicks),
            BlueprintDefinition.CostEntry.CODEC.listOf().optionalFieldOf("extra_ingredients", List.of()).forGetter(AssemblyRecipe::extraIngredients),
            Codec.BOOL.optionalFieldOf("one_time_per_chunk", false).forGetter(AssemblyRecipe::oneTimePerChunk),
            Codec.BOOL.optionalFieldOf("announce_to_server", false).forGetter(AssemblyRecipe::announceToServer),
            Codec.intRange(0, 1_000_000).optionalFieldOf("stress", 0).forGetter(AssemblyRecipe::stress)
    ).apply(i, AssemblyRecipe::new)).validate(recipe -> recipe.machine.equals(LABORATORY)
            ? DataResult.success(recipe)
            : DataResult.error(() -> "unsupported assembly machine " + recipe.machine));
}
