package com.blueprintforge.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.DockSettingsRegistry;
import com.blueprintforge.data.LineDefinition;
import com.blueprintforge.data.LineMark;
import com.blueprintforge.data.LineRegistry;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.logic.LineProduction;
import com.blueprintforge.logic.ResearchPayment;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * One blueprint and the line's material buffer. Changeover counts down in game ticks with no shaft.
 * Breaking the block drops the document and the materials. A partial item is never created here.
 */
public class BlueprintDockBlockEntity extends BlockEntity implements DocumentHolder {
    public static final int MATERIAL_SLOTS = 9;

    private final ItemStackHandler blueprint = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return accepts(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                onDocumentChanged();
            }
        }
    };

    private final ItemStackHandler materials = new ItemStackHandler(MATERIAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private int swapRemaining;
    private UUID heldInstance;
    private boolean occupancyReady;
    private boolean adjusting;
    private boolean suppressSwap;

    public BlueprintDockBlockEntity(BlockPos pos, BlockState state) {
        super(BFBlocks.BLUEPRINT_DOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlueprintDockBlockEntity dock) {
        dock.tickServer();
    }

    private void tickServer() {
        if (!occupancyReady) {
            occupancyReady = true;
            syncOccupancy();
        }
        if (swapRemaining > 0) {
            swapRemaining--;
            if (swapRemaining % 20 == 0 || swapRemaining == 0) {
                setChanged();
            }
        }
    }

    public boolean isSwapping() {
        return swapRemaining > 0;
    }

    public int swapRemaining() {
        return swapRemaining;
    }

    public ItemStackHandler getBlueprintSlot() {
        return blueprint;
    }

    public ItemStackHandler getMaterials() {
        return materials;
    }

    public ItemStack getDocument() {
        return blueprint.getStackInSlot(0);
    }

    public boolean holdsLine(ResourceLocation lineId) {
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        if (data == null || isSwapping()) {
            return false;
        }
        return BlueprintRegistry.get(data.definitionId())
                .flatMap(BlueprintDefinition::line)
                .map(lineId::equals)
                .orElse(false);
    }

    /**
     * Pays the line and spends one copy run. The original stays. Returns empty when the stamp, the tier,
     * the changeover or the material buffer refuses the step.
     */
    public Optional<LineMark> stamp(ResourceLocation lineId, LineDefinition line, ItemStack stamp) {
        if (isSwapping() || level == null) {
            return Optional.empty();
        }
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        if (data == null || !line.blueprint().equals(data.definitionId()) || !LineProduction.canSpendRuns(data)) {
            return Optional.empty();
        }
        if (!LineProduction.stampMatches(stamp, line, data.definitionId())) {
            return Optional.empty();
        }
        if (!DockSettingsRegistry.get().acceptsTier(data.tierId())) {
            return Optional.empty();
        }
        List<BlueprintDefinition.CostEntry> cost = LineProduction.materialCost(line, data.materialEfficiency());
        if (ResearchPayment.hasFluid(cost) || !ResearchPayment.covers(cost, ResearchPayment.tally(materialStacks()))) {
            return Optional.empty();
        }
        if (!consume(cost)) {
            return Optional.empty();
        }
        BlueprintData next = LineProduction.afterStamp(data);
        suppressSwap = true;
        try {
            if (next.clazz() == BlueprintClass.COPY && next.runsRemaining() <= 0) {
                blueprint.setStackInSlot(0, ItemStack.EMPTY);
            } else if (next != data) {
                ItemStack updated = getDocument().copy();
                updated.set(BFComponents.BLUEPRINT.get(), next);
                blueprint.setStackInSlot(0, updated);
            }
        } finally {
            suppressSwap = false;
        }
        setChanged();
        return Optional.of(new LineMark(lineId, data.definitionId(), data.instanceId(),
                data.materialEfficiency(), data.flux(), data.potency()));
    }

    public boolean insertFrom(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!accepts(held)) {
            return false;
        }
        ItemStack leftover = blueprint.insertItem(0, held.copyWithCount(1), false);
        if (!leftover.isEmpty()) {
            return false;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        return true;
    }

    public void takeInto(Player player) {
        ItemStack stack = blueprint.getStackInSlot(0);
        if (stack.isEmpty()) {
            return;
        }
        blueprint.setStackInSlot(0, ItemStack.EMPTY);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    public static boolean accepts(ItemStack stack) {
        BlueprintData data = BlueprintItem.data(stack).orElse(null);
        if (data == null || data.isUnissued()) {
            return false;
        }
        if (data.clazz() != BlueprintClass.ORIGINAL && !(data.clazz() == BlueprintClass.COPY && data.runsRemaining() > 0)) {
            return false;
        }
        if (stack.is(BFItems.BLUEPRINT.get()) && !DockSettingsRegistry.get().acceptsTier(data.tierId())) {
            return false;
        }
        Optional<ResourceLocation> line = BlueprintRegistry.get(data.definitionId()).flatMap(BlueprintDefinition::line);
        return line.isPresent() && LineRegistry.get(line.get()).isPresent();
    }

    public void dropContents() {
        releaseHeld();
        if (level == null) {
            return;
        }
        ItemStack document = blueprint.getStackInSlot(0);
        if (!document.isEmpty()) {
            blueprint.setStackInSlot(0, ItemStack.EMPTY);
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), document);
        }
        for (int slot = 0; slot < materials.getSlots(); slot++) {
            ItemStack stack = materials.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                materials.setStackInSlot(slot, ItemStack.EMPTY);
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }

    private void onDocumentChanged() {
        if (!suppressSwap) {
            swapRemaining = getDocument().isEmpty() ? 0 : Math.max(0, DockSettingsRegistry.get().swapTimeTicks());
        }
        syncOccupancy();
    }

    private boolean consume(List<BlueprintDefinition.CostEntry> cost) {
        for (BlueprintDefinition.CostEntry entry : cost) {
            ResourceLocation item = entry.itemOrFluid().left().orElse(null);
            if (item == null) {
                return false;
            }
            int need = entry.amount();
            for (int slot = 0; slot < materials.getSlots() && need > 0; slot++) {
                ItemStack stack = materials.getStackInSlot(slot);
                if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)) {
                    continue;
                }
                int take = Math.min(need, stack.getCount());
                stack.shrink(take);
                need -= take;
            }
            if (need > 0) {
                return false;
            }
        }
        return true;
    }

    private List<ItemStack> materialStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < materials.getSlots(); slot++) {
            stacks.add(materials.getStackInSlot(slot));
        }
        return stacks;
    }

    @Override
    public boolean holdsInstance(UUID instanceId) {
        return BlueprintItem.data(getDocument()).map(data -> instanceId.equals(data.instanceId())).orElse(false);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Blueprint", blueprint.serializeNBT(registries));
        tag.put("Materials", materials.serializeNBT(registries));
        tag.putInt("Swap", swapRemaining);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Blueprint")) {
            blueprint.deserializeNBT(registries, tag.getCompound("Blueprint"));
        }
        if (tag.contains("Materials")) {
            materials.deserializeNBT(registries, tag.getCompound("Materials"));
        }
        swapRemaining = tag.getInt("Swap");
    }

    private void releaseHeld() {
        if (level instanceof ServerLevel server && heldInstance != null) {
            ArchiveOccupancy.get(server).release(heldInstance, server, worldPosition);
            heldInstance = null;
        }
    }

    private void syncOccupancy() {
        if (!(level instanceof ServerLevel server) || adjusting) {
            return;
        }
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        UUID now = data == null || data.isUnissued() ? null : data.instanceId();
        if (heldInstance != null && !heldInstance.equals(now)) {
            ArchiveOccupancy.get(server).release(heldInstance, server, worldPosition);
            heldInstance = null;
        }
        if (now == null || now.equals(heldInstance)) {
            return;
        }
        if (!ArchiveOccupancy.get(server).claim(server, now, worldPosition)) {
            adjusting = true;
            ItemStack rejected = getDocument().copy();
            blueprint.setStackInSlot(0, ItemStack.EMPTY);
            adjusting = false;
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), rejected);
            BlueprintForge.LOGGER.warn("Blueprint {} is already held by another block; dropped the duplicate at {}", now, worldPosition);
            return;
        }
        heldInstance = now;
    }
}
