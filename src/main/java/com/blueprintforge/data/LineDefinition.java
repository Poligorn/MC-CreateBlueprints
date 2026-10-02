package com.blueprintforge.data;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * One {@code blueprint_line} file. The Create sequenced-assembly recipe stays a normal Create recipe;
 * scrap, the stamp and the material price live here so Create's serializer never sees extra fields.
 */
public record LineDefinition(
        ResourceLocation recipe,
        ResourceLocation blueprint,
        ResourceLocation stampTag,
        double scrapChance,
        ResourceLocation scrapItem,
        ResourceLocation ingredient,
        int dockRadius,
        List<BlueprintDefinition.CostEntry> materials
) {
    public static final Codec<LineDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("recipe").forGetter(LineDefinition::recipe),
            ResourceLocation.CODEC.fieldOf("blueprint").forGetter(LineDefinition::blueprint),
            ResourceLocation.CODEC.fieldOf("stamp").forGetter(LineDefinition::stampTag),
            Codec.doubleRange(0.0, 1.0).fieldOf("scrap_chance").forGetter(LineDefinition::scrapChance),
            ResourceLocation.CODEC.fieldOf("scrap_item").forGetter(LineDefinition::scrapItem),
            ResourceLocation.CODEC.fieldOf("ingredient").forGetter(LineDefinition::ingredient),
            Codec.intRange(1, 16).optionalFieldOf("dock_radius", 4).forGetter(LineDefinition::dockRadius),
            BlueprintDefinition.CostEntry.CODEC.listOf().optionalFieldOf("materials", List.of()).forGetter(LineDefinition::materials)
    ).apply(i, LineDefinition::new));
}
