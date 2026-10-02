package com.blueprintforge.command;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.item.BlueprintItem;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** The /bf log journal, plus the in-memory confirmation that has not been accepted yet. */
public final class TransferLog extends SavedData {
    public record Entry(String time, UUID from, String fromName, UUID to, String toName, ResourceLocation blueprintId,
                        UUID instanceId, int materialEfficiency, int flux, int potency) {
    }

    public record Pending(UUID from, UUID to, UUID instanceId, ResourceLocation blueprintId, int materialEfficiency,
                          int flux, int potency, long expiresAt) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    public static TransferLog get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(TransferLog::new, TransferLog::load), "blueprintforge_transfer_log");
    }

    public static PendingBox pending(ServerLevel level) {
        return new PendingBox();
    }

    public static final class PendingBox {
        public void offer(UUID from, UUID to, ItemStack stack, UUID instanceId, long expiresAt) {
            BlueprintData data = BlueprintItem.data(stack).orElseThrow();
            PENDING.put(to, new Pending(from, to, instanceId, data.definitionId(), data.materialEfficiency(), data.flux(), data.potency(), expiresAt));
        }

        public Pending take(UUID to) {
            Pending pending = PENDING.remove(to);
            if (pending == null || pending.expiresAt() < System.currentTimeMillis()) {
                return null;
            }
            return pending;
        }
    }

    private static TransferLog load(CompoundTag tag, HolderLookup.Provider registries) {
        TransferLog log = new TransferLog();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation blueprint = ResourceLocation.tryParse(entry.getString("Blueprint"));
            if (blueprint == null || !entry.hasUUID("From") || !entry.hasUUID("To") || !entry.hasUUID("Instance")) {
                continue;
            }
            int flux = entry.contains("Flux") ? entry.getInt("Flux") : entry.getInt("TE");
            log.entries.add(new Entry(entry.getString("Time"), entry.getUUID("From"), entry.getString("FromName"),
                    entry.getUUID("To"), entry.getString("ToName"), blueprint, entry.getUUID("Instance"),
                    entry.getInt("ME"), flux, entry.getInt("Potency")));
        }
        return log;
    }

    public void append(UUID from, String fromName, UUID to, String toName, ResourceLocation blueprint, UUID instance,
                       int materialEfficiency, int flux, int potency) {
        entries.add(new Entry(java.time.Instant.now().toString(), from, fromName, to, toName, blueprint, instance,
                materialEfficiency, flux, potency));
        setDirty();
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag row = new CompoundTag();
            row.putString("Time", entry.time());
            row.putUUID("From", entry.from());
            row.putString("FromName", entry.fromName());
            row.putUUID("To", entry.to());
            row.putString("ToName", entry.toName());
            row.putString("Blueprint", entry.blueprintId().toString());
            row.putUUID("Instance", entry.instanceId());
            row.putInt("ME", entry.materialEfficiency());
            row.putInt("Flux", entry.flux());
            row.putInt("Potency", entry.potency());
            list.add(row);
        }
        tag.put("Entries", list);
        return tag;
    }

    /** Drops expired offers so a stale confirmation cannot fire later. */
    public static void purge() {
        long now = System.currentTimeMillis();
        Iterator<Pending> it = PENDING.values().iterator();
        while (it.hasNext()) {
            if (it.next().expiresAt() < now) {
                it.remove();
            }
        }
    }
}
