package com.blueprintforge.machine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which Archive currently holds a blueprint instance. A duplicate UUID cannot start a second operation.
 * Chunk unload does not release the claim: the item is still inside the unloaded block.
 */
public final class ArchiveOccupancy extends SavedData {
    private final Map<UUID, GlobalPos> holders = new HashMap<>();

    public static ArchiveOccupancy get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ArchiveOccupancy::new, ArchiveOccupancy::load), "blueprintforge_archive_occupancy");
    }

    public ArchiveOccupancy() {
    }

    private static ArchiveOccupancy load(CompoundTag tag, HolderLookup.Provider registries) {
        ArchiveOccupancy data = new ArchiveOccupancy();
        ListTag list = tag.getList("Holders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Id")) {
                continue;
            }
            GlobalPos pos = GlobalPos.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, entry.get("Pos")).result().orElse(null);
            if (pos != null) {
                data.holders.put(entry.getUUID("Id"), pos);
            }
        }
        return data;
    }

    /**
     * @return true when this position is the holder. A loaded archive that no longer has the item loses the claim.
     */
    public boolean claim(ServerLevel level, UUID instanceId, BlockPos pos) {
        GlobalPos here = GlobalPos.of(level.dimension(), pos);
        GlobalPos existing = holders.get(instanceId);
        if (existing == null || existing.equals(here) || !stillHolds(level, existing, instanceId)) {
            holders.put(instanceId, here);
            setDirty();
            return true;
        }
        return false;
    }

    public void release(UUID instanceId, ServerLevel level, BlockPos pos) {
        GlobalPos here = GlobalPos.of(level.dimension(), pos);
        if (here.equals(holders.get(instanceId))) {
            holders.remove(instanceId);
            setDirty();
        }
    }

    private static boolean stillHolds(ServerLevel level, GlobalPos pos, UUID instanceId) {
        if (!pos.dimension().equals(level.dimension()) || !level.isLoaded(pos.pos())) {
            return true;
        }
        BlockEntity be = level.getBlockEntity(pos.pos());
        return be instanceof BlueprintArchiveBlockEntity archive && archive.holdsInstance(instanceId);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        holders.forEach((id, pos) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            GlobalPos.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, pos).result().ifPresent(encoded -> entry.put("Pos", encoded));
            list.add(entry);
        });
        tag.put("Holders", list);
        return tag;
    }
}
