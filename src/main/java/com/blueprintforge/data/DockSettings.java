package com.blueprintforge.data;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/** One {@code blueprint_dock} file. An empty {@code accepts} list means every tier. */
public record DockSettings(
        List<ResourceLocation> accepts,
        int slots,
        int swapTimeTicks,
        boolean dropOnBreak,
        int searchRadius
) {
    public static final DockSettings DEFAULT = new DockSettings(List.of(), 1, 100, true, 4);

    public static final Codec<DockSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("accepts", List.of()).forGetter(DockSettings::accepts),
            Codec.intRange(1, 1).optionalFieldOf("slots", 1).forGetter(DockSettings::slots),
            Codec.intRange(0, 1_000_000).optionalFieldOf("swap_time_ticks", 100).forGetter(DockSettings::swapTimeTicks),
            Codec.BOOL.optionalFieldOf("drop_on_break", true).forGetter(DockSettings::dropOnBreak),
            Codec.intRange(1, 16).optionalFieldOf("search_radius", 4).forGetter(DockSettings::searchRadius)
    ).apply(i, DockSettings::new));

    public boolean acceptsTier(ResourceLocation tier) {
        return accepts.isEmpty() || accepts.contains(tier);
    }
}
