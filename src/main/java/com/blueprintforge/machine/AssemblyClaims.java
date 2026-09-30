package com.blueprintforge.machine;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Chunks that have already assembled a recipe whose {@code one_time_per_chunk} flag is set. */
public final class AssemblyClaims extends SavedData {
    private final Set<String> done = new HashSet<>();

    public static AssemblyClaims get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(AssemblyClaims::new, AssemblyClaims::load), "blueprintforge_assembly_claims");
    }

    private static AssemblyClaims load(CompoundTag tag, HolderLookup.Provider registries) {
        AssemblyClaims data = new AssemblyClaims();
        ListTag list = tag.getList("Done", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            data.done.add(list.getString(i));
        }
        return data;
    }

    public static String key(ResourceLocation assembly, ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return assembly + "@" + dimension.location() + "@" + chunkX + "," + chunkZ;
    }

    public boolean claimed(String key) {
        return done.contains(key);
    }

    public void claim(String key) {
        if (done.add(key)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String key : done) {
            list.add(StringTag.valueOf(key));
        }
        tag.put("Done", list);
        return tag;
    }
}
