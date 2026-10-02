package com.blueprintforge.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.AssemblyRecipe;
import com.blueprintforge.data.AssemblyRegistry;
import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.ResearchProfile;
import com.blueprintforge.data.ResearchRegistry;
import com.blueprintforge.data.TierDefinition;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.logic.ArchiveRefusal;
import com.blueprintforge.logic.AssemblyMatching;
import com.blueprintforge.logic.CopyRunWarning;
import com.blueprintforge.logic.EfficiencyMath;
import com.blueprintforge.logic.ResearchAxis;
import com.blueprintforge.logic.ResearchPayment;
import com.blueprintforge.logic.ResearchRefusal;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Blueprint Laboratory. Research, copying and fragment assembly each last a datapack number of game ticks,
 * and only while the shaft in the roof is turning. Stress is applied only during that work.
 * Forged items are not made here: they come off a sequenced assembly line.
 */
public class ProjectBureauBlockEntity extends KineticBlockEntity implements MenuProvider, DocumentHolder {
    private static final String BLUEPRINT_TAG = "Blueprint";
    /** Sentinel: the replacement is already queued, so a second tick must not spend another run. */
    public static final int MATERIAL_SLOTS = 16;
    public static final int DATA_ME = 0;
    public static final int DATA_ME_MAX = 1;
    public static final int DATA_FLUX = 2;
    public static final int DATA_FLUX_MAX = 3;
    public static final int DATA_ME_NEXT = 4;
    public static final int DATA_FLUX_NEXT = 5;
    public static final int DATA_PROGRESS = 6;
    public static final int DATA_TOTAL = 7;
    public static final int DATA_AXIS = 8;
    public static final int DATA_ME_REFUSAL = 9;
    public static final int DATA_FLUX_REFUSAL = 10;
    public static final int DATA_STRESS = 11;
    public static final int DATA_COPY_RUNS = 12;
    public static final int DATA_COPY_MAX = 13;
    public static final int DATA_COPY_REFUSAL = 14;
    public static final int DATA_JOB = 15;
    public static final int DATA_PRESSING = 16;
    public static final int DATA_PRESS_PROGRESS = 17;
    public static final int DATA_PRESS_TOTAL = 18;
    public static final int DATA_POTENCY = 19;
    public static final int DATA_POTENCY_MAX = 20;
    public static final int DATA_POTENCY_NEXT = 21;
    public static final int DATA_POTENCY_REFUSAL = 22;
    public static final int DATA_SPEED = 23;
    public static final int DATA_COUNT = 24;
    private static final String MATERIALS_TAG = "Materials";
    private static final String RESEARCH_AXIS_TAG = "ResearchAxis";

    private final ItemStackHandler blueprintSlot = new DocumentSlot() {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                onDocumentChanged();
            }
        }
    };

    private final ItemStackHandler materials = new MaterialSlot();
    private final ItemStackHandler output = new OutputSlot();

    /** Null when no research step is running. Materials are spent only when the step finishes. */
    private ResearchAxis researchAxis;
    private int researchProgress;
    private int researchTotal;
    private UUID researchInstance;
    private UUID researchPlayer;
    private String researchPlayerName;
    private boolean copying;
    private boolean assembling;
    private int jobProgress;
    private int jobTotal;
    private UUID jobInstance;
    private UUID copyPlayer;
    private String copyPlayerName;
    private int selectedRuns = 1;
    private final List<ItemStack> reserved = new ArrayList<>();
    private ResourceLocation assemblyId;
    private UUID heldInstance;
    private boolean occupancyReady;

    public ProjectBureauBlockEntity(BlockPos pos, BlockState state) {
        super(BFBlocks.PROJECT_BUREAU_ENTITY.get(), pos, state);
    }

    @Override
    public void addBehaviours(java.util.List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        tickServer();
    }

    @Override
    public float calculateStressApplied() {
        float applied = appliedStress();
        this.lastStressApplied = applied;
        return applied;
    }

    private boolean shaftTurns() {
        return Math.abs(getSpeed()) > 0.0F;
    }

    private float appliedStress() {
        if (researchAxis != null) {
            return profile().map(profile -> (float) profile.stressPerStep()).orElse(0.0F);
        }
        if (copying) {
            return profile().map(profile -> (float) profile.copyStress()).orElse(0.0F);
        }
        if (assembling && assemblyId != null) {
            return AssemblyRegistry.get(assemblyId).map(recipe -> (float) recipe.stress()).orElse(0.0F);
        }
        return 0.0F;
    }

    private void refreshStress() {
        if (level != null && !level.isClientSide && hasNetwork()) {
            getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        }
    }

    /**
     * The document slot takes an issued original or a copy that still has runs. Blanks, fragments, ancient
     * blueprints, spent copies and creative templates stay out.
     */
    public static boolean acceptsDocument(ItemStack stack) {
        return BlueprintItem.data(stack)
                .filter(data -> !data.isUnissued())
                .map(data -> data.clazz() == BlueprintClass.ORIGINAL
                        || (data.clazz() == BlueprintClass.COPY && data.runsRemaining() > 0))
                .orElse(false);
    }

    public ItemStackHandler getBlueprintSlot() {
        return blueprintSlot;
    }

    public ItemStack getDocument() {
        return blueprintSlot.getStackInSlot(0);
    }

    public ItemStackHandler getMaterials() {
        return materials;
    }

    public boolean isResearching() {
        return researchAxis != null;
    }

    /**
     * Starts one ME, Flux or Potency step. Materials stay in the slots until the step finishes.
     * The step lasts {@code time_per_step_ticks} game ticks and only advances while the shaft turns.
     */
    public ResearchRefusal tryStart(ResearchAxis axis, Player player) {
        ResearchRefusal refusal = refusal(axis);
        if (refusal != ResearchRefusal.OK || level == null || level.isClientSide) {
            return refusal != ResearchRefusal.OK ? refusal : ResearchRefusal.BUSY;
        }
        ResearchProfile profile = profile().orElseThrow();
        researchAxis = axis;
        researchProgress = 0;
        researchTotal = Math.max(1, profile.timePerStepTicks());
        researchInstance = BlueprintItem.data(getDocument()).orElseThrow().instanceId();
        researchPlayer = player.getUUID();
        researchPlayerName = player.getName().getString();
        setChanged();
        refreshStress();
        return ResearchRefusal.OK;
    }

    public ResearchRefusal refusal(ResearchAxis axis) {
        if (isBusy()) {
            return ResearchRefusal.BUSY;
        }
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        if (data == null) {
            return ResearchRefusal.NO_DOCUMENT;
        }
        ResearchProfile profile = profile().orElse(null);
        if (data.clazz() == BlueprintClass.COPY && (profile == null || !profile.allowResearchOnCopy())) {
            return ResearchRefusal.COPY_FORBIDDEN;
        }
        if (data.clazz() != BlueprintClass.ORIGINAL && data.clazz() != BlueprintClass.COPY) {
            return ResearchRefusal.COPY_FORBIDDEN;
        }
        if (profile == null) {
            return ResearchRefusal.NO_PROFILE;
        }
        BlueprintDefinition definition = BlueprintRegistry.get(data.definitionId()).orElse(null);
        if (definition == null) {
            return ResearchRefusal.NO_PROFILE;
        }
        BlueprintDefinition.EfficiencyRange range = definition.range(axis);
        int current = axisValue(data, axis);
        if (EfficiencyMath.nextStep(current, range.max(), range.step()).isEmpty()) {
            return ResearchRefusal.AT_CAP;
        }
        List<BlueprintDefinition.CostEntry> cost = axisCost(profile, definition, axis);
        if (ResearchPayment.hasFluid(cost)) {
            return ResearchRefusal.FLUID_COST;
        }
        if (!ResearchPayment.covers(cost, ResearchPayment.tally(materialStacks()))) {
            return ResearchRefusal.MISSING_COST;
        }
        if (!shaftTurns()) {
            return ResearchRefusal.NO_ROTATION;
        }
        return ResearchRefusal.OK;
    }

    public ContainerData researchData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return researchDatum(index);
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    private void tickServer() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!occupancyReady) {
            occupancyReady = true;
            syncOccupancy();
        }
        tickJob();
        if (researchAxis == null) {
            return;
        }
        if (refusalWhileRunning() != ResearchRefusal.OK) {
            cancelResearch();
            return;
        }
        if (!shaftTurns()) {
            return;
        }
        researchProgress++;
        if (researchProgress % 20 == 0) {
            setChanged();
        }
        if (researchProgress >= researchTotal) {
            finishResearch();
        }
    }

    private void onDocumentChanged() {
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        if (researchAxis != null && (data == null || researchInstance == null || !researchInstance.equals(data.instanceId()))) {
            cancelResearch();
        }
        if (copying && (data == null || jobInstance == null || !jobInstance.equals(data.instanceId()))) {
            cancelJob(true);
        }
        selectedRuns = copyRules(data).map(BlueprintDefinition.CopyRules::defaultRuns).orElse(1);
        syncOccupancy();
    }

    /** Running step, ignoring the busy flag and the rotation pause. Missing items or a removed document abort it. */
    private ResearchRefusal refusalWhileRunning() {
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        if (data == null || researchInstance == null || !researchInstance.equals(data.instanceId()) || researchAxis == null) {
            return ResearchRefusal.NO_DOCUMENT;
        }
        ResearchProfile profile = profile().orElse(null);
        if (profile == null) {
            return ResearchRefusal.NO_PROFILE;
        }
        if (data.clazz() == BlueprintClass.COPY && !profile.allowResearchOnCopy()) {
            return ResearchRefusal.COPY_FORBIDDEN;
        }
        BlueprintDefinition definition = BlueprintRegistry.get(data.definitionId()).orElse(null);
        if (definition == null) {
            return ResearchRefusal.NO_PROFILE;
        }
        BlueprintDefinition.EfficiencyRange range = definition.range(researchAxis);
        int current = axisValue(data, researchAxis);
        if (EfficiencyMath.nextStep(current, range.max(), range.step()).isEmpty()) {
            return ResearchRefusal.AT_CAP;
        }
        List<BlueprintDefinition.CostEntry> cost = cost(profile, definition, researchAxis);
        if (ResearchPayment.hasFluid(cost) || !ResearchPayment.covers(cost, ResearchPayment.tally(materialStacks()))) {
            return ResearchRefusal.MISSING_COST;
        }
        return ResearchRefusal.OK;
    }

    private void finishResearch() {
        ResearchAxis axis = researchAxis;
        ResearchProfile profile = profile().orElse(null);
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        BlueprintDefinition definition = data == null ? null : BlueprintRegistry.get(data.definitionId()).orElse(null);
        if (axis == null || profile == null || data == null || definition == null) {
            cancelResearch();
            return;
        }
        BlueprintDefinition.EfficiencyRange range = definition.range(axis);
        int current = axisValue(data, axis);
        int next = EfficiencyMath.nextStep(current, range.max(), range.step()).orElse(-1);
        List<BlueprintDefinition.CostEntry> cost = cost(profile, definition, axis);
        if (next < 0 || !consume(cost)) {
            cancelResearch();
            return;
        }
        int me = axis == ResearchAxis.MATERIAL ? next : data.materialEfficiency();
        int flux = axis == ResearchAxis.FLUX ? next : data.flux();
        int potency = axis == ResearchAxis.POTENCY ? next : data.potency();
        researchAxis = null;
        researchProgress = 0;
        researchTotal = 0;
        ItemStack updated = getDocument().copy();
        updated.set(BFComponents.BLUEPRINT.get(), data.withResearch(me, flux, potency,
                Optional.ofNullable(researchPlayer), Optional.ofNullable(researchPlayerName)));
        blueprintSlot.setStackInSlot(0, updated);
        setChanged();
        refreshStress();
    }

    private void cancelResearch() {
        researchAxis = null;
        researchProgress = 0;
        researchTotal = 0;
        researchInstance = null;
        setChanged();
        refreshStress();
    }

    private boolean consume(List<BlueprintDefinition.CostEntry> cost) {
        if (!ResearchPayment.covers(cost, ResearchPayment.tally(materialStacks()))) {
            return false;
        }
        for (BlueprintDefinition.CostEntry entry : cost) {
            ResourceLocation item = entry.itemOrFluid().left().orElseThrow();
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

    private Optional<ResearchProfile> profile() {
        return BlueprintItem.data(getDocument()).flatMap(data -> ResearchRegistry.forBlueprint(data.definitionId()));
    }

    private static List<BlueprintDefinition.CostEntry> cost(ResearchProfile profile, BlueprintDefinition definition, ResearchAxis axis) {
        return scaled(axisCost(profile, definition, axis), 1.0);
    }

    public static List<BlueprintDefinition.CostEntry> axisCost(ResearchProfile profile, BlueprintDefinition definition, ResearchAxis axis) {
        List<BlueprintDefinition.CostEntry> base = switch (axis) {
            case MATERIAL -> profile.meStepCost();
            case FLUX -> profile.fluxStepCost();
            case POTENCY -> profile.potencyStepCost();
        };
        return scaled(base, definition.range(axis).costMultiplier());
    }

    private static List<BlueprintDefinition.CostEntry> scaled(List<BlueprintDefinition.CostEntry> cost, double multiplier) {
        if (multiplier == 1.0) {
            return cost;
        }
        List<BlueprintDefinition.CostEntry> scaled = new ArrayList<>();
        for (BlueprintDefinition.CostEntry entry : cost) {
            int amount = (int) Math.ceil(entry.amount() * multiplier - 1.0E-9);
            if (amount > 0) {
                scaled.add(new BlueprintDefinition.CostEntry(entry.itemOrFluid(), amount));
            }
        }
        return scaled;
    }

    private static int axisValue(BlueprintData data, ResearchAxis axis) {
        return switch (axis) {
            case MATERIAL -> data.materialEfficiency();
            case FLUX -> data.flux();
            case POTENCY -> data.potency();
        };
    }

    private List<ItemStack> materialStacks() {
        List<ItemStack> stacks = new ArrayList<>(materials.getSlots());
        for (int slot = 0; slot < materials.getSlots(); slot++) {
            stacks.add(materials.getStackInSlot(slot));
        }
        return stacks;
    }

    private int researchDatum(int index) {
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        BlueprintDefinition definition = data == null ? null : BlueprintRegistry.get(data.definitionId()).orElse(null);
        int me = data == null ? 0 : data.materialEfficiency();
        int flux = data == null ? 0 : data.flux();
        int potency = data == null ? 0 : data.potency();
        int meMax = definition == null ? 0 : definition.materialEfficiencyOrFixed().max();
        int fluxMax = definition == null ? 0 : definition.fluxOrFixed().max();
        int potencyMax = definition == null ? 0 : definition.potencyOrFixed().max();
        int meStep = definition == null ? 0 : definition.materialEfficiencyOrFixed().step();
        int fluxStep = definition == null ? 0 : definition.fluxOrFixed().step();
        int potencyStep = definition == null ? 0 : definition.potencyOrFixed().step();
        return switch (index) {
            case DATA_ME -> me;
            case DATA_ME_MAX -> meMax;
            case DATA_FLUX -> flux;
            case DATA_FLUX_MAX -> fluxMax;
            case DATA_ME_NEXT -> EfficiencyMath.nextStep(me, meMax, meStep).orElse(-1);
            case DATA_FLUX_NEXT -> EfficiencyMath.nextStep(flux, fluxMax, fluxStep).orElse(-1);
            case DATA_PROGRESS -> researchProgress;
            case DATA_TOTAL -> researchTotal;
            case DATA_AXIS -> researchAxis == null ? 0 : researchAxis.ordinal() + 1;
            case DATA_ME_REFUSAL -> refusal(ResearchAxis.MATERIAL).ordinal();
            case DATA_FLUX_REFUSAL -> refusal(ResearchAxis.FLUX).ordinal();
            case DATA_STRESS -> (int) appliedStress();
            case DATA_COPY_RUNS -> selectedRuns;
            case DATA_COPY_MAX -> copyRules(data).map(BlueprintDefinition.CopyRules::maxRuns).orElse(1);
            case DATA_COPY_REFUSAL -> getDocument().isEmpty() ? assemblyRefusal().ordinal() : copyRefusal().ordinal();
            case DATA_JOB -> copying ? 2 : assembling ? 3 : researchAxis != null ? 1 : 0;
            case DATA_PRESSING -> isWorking() ? 1 : 0;
            case DATA_PRESS_PROGRESS -> operationProgress();
            case DATA_PRESS_TOTAL -> operationTotal();
            case DATA_POTENCY -> potency;
            case DATA_POTENCY_MAX -> potencyMax;
            case DATA_POTENCY_NEXT -> EfficiencyMath.nextStep(potency, potencyMax, potencyStep).orElse(-1);
            case DATA_POTENCY_REFUSAL -> refusal(ResearchAxis.POTENCY).ordinal();
            case DATA_SPEED -> Math.round(Math.abs(getSpeed()));
            default -> 0;
        };
    }

    /** Break drops the document, leftover materials and anything already in the output. A partial copy is not created. */
    public void dropContents() {
        cancelResearch();
        cancelJob(true);
        releaseHeld();
        if (level == null) {
            return;
        }
        ItemStack document = blueprintSlot.getStackInSlot(0);
        if (!document.isEmpty()) {
            blueprintSlot.setStackInSlot(0, ItemStack.EMPTY);
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), document);
        }
        for (int slot = 0; slot < materials.getSlots(); slot++) {
            ItemStack stack = materials.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                materials.setStackInSlot(slot, ItemStack.EMPTY);
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
        ItemStack printed = output.getStackInSlot(0);
        if (!printed.isEmpty()) {
            output.setStackInSlot(0, ItemStack.EMPTY);
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), printed);
        }
    }

    private void releaseHeld() {
        if (level instanceof ServerLevel server && heldInstance != null) {
            ArchiveOccupancy.get(server).release(heldInstance, server, worldPosition);
            heldInstance = null;
        }
    }

    private void writeContents(CompoundTag compound, HolderLookup.Provider registries) {
        compound.put(BLUEPRINT_TAG, blueprintSlot.serializeNBT(registries));
        compound.put(MATERIALS_TAG, materials.serializeNBT(registries));
        compound.put("Output", output.serializeNBT(registries));
        compound.putInt("SelectedRuns", selectedRuns);
        compound.putBoolean("Copying", copying);
        compound.putBoolean("Assembling", assembling);
        compound.putInt("JobProgress", jobProgress);
        compound.putInt("JobTotal", jobTotal);
        if (jobInstance != null) {
            compound.putUUID("JobInstance", jobInstance);
        }
        if (assemblyId != null) {
            compound.putString("Assembly", assemblyId.toString());
        }
        net.minecraft.nbt.ListTag reservedTag = new net.minecraft.nbt.ListTag();
        for (ItemStack stack : reserved) {
            reservedTag.add(stack.save(registries));
        }
        compound.put("Reserved", reservedTag);
        if (researchAxis != null) {
            compound.putString(RESEARCH_AXIS_TAG, researchAxis.name());
            compound.putInt("ResearchProgress", researchProgress);
            compound.putInt("ResearchTotal", researchTotal);
            if (researchInstance != null) {
                compound.putUUID("ResearchInstance", researchInstance);
            }
            if (researchPlayer != null) {
                compound.putUUID("ResearchPlayer", researchPlayer);
            }
            if (researchPlayerName != null) {
                compound.putString("ResearchPlayerName", researchPlayerName);
            }
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeContents(tag, registries);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        readContents(tag, registries);
    }

    private void readContents(CompoundTag compound, HolderLookup.Provider registries) {
        blueprintSlot.deserializeNBT(registries, compound.getCompound(BLUEPRINT_TAG));
        materials.deserializeNBT(registries, compound.getCompound(MATERIALS_TAG));
        if (compound.contains("Output")) {
            output.deserializeNBT(registries, compound.getCompound("Output"));
        }
        selectedRuns = Math.max(1, compound.getInt("SelectedRuns"));
        copying = compound.getBoolean("Copying");
        assembling = compound.getBoolean("Assembling");
        jobProgress = compound.getInt("JobProgress");
        jobTotal = compound.getInt("JobTotal");
        jobInstance = compound.hasUUID("JobInstance") ? compound.getUUID("JobInstance") : null;
        assemblyId = compound.contains("Assembly") ? ResourceLocation.tryParse(compound.getString("Assembly")) : null;
        reserved.clear();
        for (net.minecraft.nbt.Tag entry : compound.getList("Reserved", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, entry).ifPresent(reserved::add);
        }
        researchAxis = null;
        if (compound.contains(RESEARCH_AXIS_TAG)) {
            try {
                researchAxis = ResearchAxis.valueOf(compound.getString(RESEARCH_AXIS_TAG));
            } catch (IllegalArgumentException ignored) {
                researchAxis = null;
            }
        }
        researchProgress = compound.getInt("ResearchProgress");
        researchTotal = compound.getInt("ResearchTotal");
        researchInstance = compound.hasUUID("ResearchInstance") ? compound.getUUID("ResearchInstance") : null;
        researchPlayer = compound.hasUUID("ResearchPlayer") ? compound.getUUID("ResearchPlayer") : null;
        researchPlayerName = compound.contains("ResearchPlayerName") ? compound.getString("ResearchPlayerName") : null;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.blueprintforge.blueprint_laboratory");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ProjectBureauMenu(containerId, inventory, this);
    }

    /** Single-slot handler shared by the block entity and the client-side menu mirror. */
    public static class DocumentSlot extends ItemStackHandler {
        public DocumentSlot() {
            super(1);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return acceptsDocument(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    }

    /** Items that pay for a research step. Blueprint documents stay in the document slot. */
    public static class MaterialSlot extends ItemStackHandler {
        public MaterialSlot() {
            super(MATERIAL_SLOTS);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (!stack.is(BFItems.BLUEPRINT.get())) {
                return true;
            }
            return BlueprintItem.data(stack).map(data -> data.clazz() == BlueprintClass.FRAGMENT).orElse(false);
        }
    }

    /** Printed copies and assembled originals are taken out, not pushed in. */
    public static class OutputSlot extends ItemStackHandler {
        public OutputSlot() {
            super(1);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    public ItemStackHandler getOutput() {
        return output;
    }

    public boolean holdsInstance(UUID instanceId) {
        return BlueprintItem.data(getDocument()).map(data -> instanceId.equals(data.instanceId())).orElse(false);
    }

    public boolean isBusy() {
        return researchAxis != null || copying || assembling;
    }

    /** True while a research step or a copy is in progress. Assembly is busy too, but it is not a redraw. */
    public boolean isWorking() {
        return researchAxis != null || copying || assembling;
    }

    public int operationProgress() {
        if (researchAxis != null) {
            return researchProgress;
        }
        return copying || assembling ? jobProgress : 0;
    }

    public int operationTotal() {
        if (researchAxis != null) {
            return researchTotal;
        }
        return copying || assembling ? jobTotal : 0;
    }

    public int selectedRuns() {
        return selectedRuns;
    }

    public boolean setCopyRuns(int runs, int checksum) {
        BlueprintDefinition.CopyRules rules = copyRules(BlueprintItem.data(getDocument()).orElse(null)).orElse(null);
        if (rules == null) {
            return false;
        }
        int clamped = EfficiencyMath.clampRuns(runs, rules.maxRuns());
        if (copyChecksum(rules, clamped) != checksum) {
            return false;
        }
        selectedRuns = clamped;
        setChanged();
        return true;
    }

    public void nudgeRuns(int delta) {
        BlueprintDefinition.CopyRules rules = copyRules(BlueprintItem.data(getDocument()).orElse(null)).orElse(null);
        int max = rules == null ? 1 : rules.maxRuns();
        selectedRuns = EfficiencyMath.clampRuns(selectedRuns + delta, max);
        setChanged();
    }

    public static int copyChecksum(BlueprintDefinition.CopyRules rules, int runs) {
        int sum = 0;
        for (BlueprintDefinition.CostEntry entry : rules.copyCost()) {
            if (entry.itemOrFluid().left().isPresent()) {
                sum += EfficiencyMath.copyCost(entry.amount(), runs, rules.defaultRuns(), rules.costScaling());
            }
        }
        return sum;
    }

    public ArchiveRefusal tryCopy(Player player) {
        ArchiveRefusal refusal = copyRefusal();
        if (refusal != ArchiveRefusal.OK || level == null || level.isClientSide) {
            return refusal != ArchiveRefusal.OK ? refusal : ArchiveRefusal.BUSY;
        }
        BlueprintData data = BlueprintItem.data(getDocument()).orElseThrow();
        BlueprintDefinition.CopyRules rules = copyRules(data).orElseThrow();
        ResearchProfile profile = profile().orElseThrow();
        int runs = EfficiencyMath.clampRuns(selectedRuns, rules.maxRuns());
        int perRun = Math.max(1, profile.copyTimePerRunTicks());
        if (!reserve(scaledCost(rules, runs))) {
            return ArchiveRefusal.MISSING_COST;
        }
        copying = true;
        jobProgress = 0;
        jobTotal = perRun * runs;
        jobInstance = data.instanceId();
        copyPlayer = player.getUUID();
        copyPlayerName = player.getName().getString();
        selectedRuns = runs;
        setChanged();
        refreshStress();
        return ArchiveRefusal.OK;
    }

    public ArchiveRefusal copyRefusal() {
        if (isBusy()) {
            return ArchiveRefusal.BUSY;
        }
        if (!output.getStackInSlot(0).isEmpty()) {
            return ArchiveRefusal.OUTPUT_FULL;
        }
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        if (data == null) {
            return ArchiveRefusal.NO_DOCUMENT;
        }
        BlueprintDefinition.CopyRules rules = copyRules(data).orElse(null);
        if (rules == null || !rules.enabled()) {
            return ArchiveRefusal.DISABLED;
        }
        if (data.clazz() == BlueprintClass.COPY && !rules.allowFromCopy()) {
            return ArchiveRefusal.COPY_FORBIDDEN;
        }
        if (data.clazz() != BlueprintClass.ORIGINAL && data.clazz() != BlueprintClass.COPY) {
            return ArchiveRefusal.COPY_FORBIDDEN;
        }
        ResearchProfile profile = profile().orElse(null);
        if (profile == null) {
            return ArchiveRefusal.NO_PROFILE;
        }
        List<BlueprintDefinition.CostEntry> cost = scaledCost(rules, EfficiencyMath.clampRuns(selectedRuns, rules.maxRuns()));
        if (ResearchPayment.hasFluid(cost)) {
            return ArchiveRefusal.FLUID_COST;
        }
        if (!ResearchPayment.covers(cost, ResearchPayment.tally(materialStacks()))) {
            return ArchiveRefusal.MISSING_COST;
        }
        if (!shaftTurns()) {
            return ArchiveRefusal.NO_ROTATION;
        }
        return ArchiveRefusal.OK;
    }

    public ArchiveRefusal tryAssemble(Player player) {
        ArchiveRefusal refusal = assemblyRefusal();
        if (refusal != ArchiveRefusal.OK || !(level instanceof ServerLevel)) {
            return refusal != ArchiveRefusal.OK ? refusal : ArchiveRefusal.BUSY;
        }
        Map.Entry<ResourceLocation, AssemblyRecipe> match = assemblyMatch().orElseThrow();
        if (!reserveAssembly(match.getValue())) {
            return ArchiveRefusal.ASSEMBLY_SHORT;
        }
        assembling = true;
        assemblyId = match.getKey();
        jobProgress = 0;
        jobTotal = Math.max(1, match.getValue().processTimeTicks());
        setChanged();
        refreshStress();
        return ArchiveRefusal.OK;
    }

    public ArchiveRefusal assemblyRefusal() {
        if (isBusy()) {
            return ArchiveRefusal.BUSY;
        }
        if (!getDocument().isEmpty()) {
            return ArchiveRefusal.NO_DOCUMENT;
        }
        if (!output.getStackInSlot(0).isEmpty()) {
            return ArchiveRefusal.OUTPUT_FULL;
        }
        Map.Entry<ResourceLocation, AssemblyRecipe> match = assemblyMatch().orElse(null);
        if (match == null) {
            return ArchiveRefusal.ASSEMBLY_SHORT;
        }
        if (level instanceof ServerLevel server && match.getValue().oneTimePerChunk()) {
            String key = AssemblyClaims.key(match.getKey(), server.dimension(), worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
            if (AssemblyClaims.get(server).claimed(key)) {
                return ArchiveRefusal.ALREADY_DONE;
            }
        }
        if (ResearchPayment.hasFluid(match.getValue().extraIngredients())) {
            return ArchiveRefusal.FLUID_COST;
        }
        if (!shaftTurns()) {
            return ArchiveRefusal.NO_ROTATION;
        }
        return ArchiveRefusal.OK;
    }

    private void tickJob() {
        if (!copying && !assembling) {
            return;
        }
        if (copying) {
            BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
            if (data == null || jobInstance == null || !jobInstance.equals(data.instanceId())) {
                cancelJob(true);
                return;
            }
        } else if (!getDocument().isEmpty()) {
            cancelJob(true);
            return;
        }
        if (!shaftTurns()) {
            return;
        }
        jobProgress++;
        if (jobProgress >= jobTotal) {
            if (copying) {
                finishCopy();
            } else {
                finishAssembly();
            }
        }
    }

    private void finishCopy() {
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        BlueprintDefinition.CopyRules rules = copyRules(data).orElse(null);
        if (data == null || rules == null || !output.getStackInSlot(0).isEmpty()) {
            cancelJob(true);
            return;
        }
        int runs = EfficiencyMath.clampRuns(selectedRuns, rules.maxRuns());
        int me = EfficiencyMath.penalized(data.materialEfficiency(), rules.mePenalty());
        int flux = EfficiencyMath.penalized(data.flux(), rules.fluxPenalty());
        ItemStack copy = new ItemStack(BFItems.BLUEPRINT.get());
        copy.set(BFComponents.BLUEPRINT.get(), data.printedCopy(UUID.randomUUID(), runs, me, flux, data.potency(),
                Optional.ofNullable(copyPlayer), Optional.ofNullable(copyPlayerName)));
        reserved.clear();
        copying = false;
        output.setStackInSlot(0, copy);
        setChanged();
        refreshStress();
    }

    private void finishAssembly() {
        AssemblyRecipe recipe = assemblyId == null ? null : AssemblyRegistry.get(assemblyId).orElse(null);
        BlueprintDefinition definition = recipe == null ? null : BlueprintRegistry.get(recipe.outputBlueprint()).orElse(null);
        if (recipe == null || definition == null || !output.getStackInSlot(0).isEmpty()) {
            cancelJob(true);
            return;
        }
        ItemStack original = BlueprintItem.createInstance(recipe.outputBlueprint(), definition, UUID.randomUUID());
        reserved.clear();
        assembling = false;
        output.setStackInSlot(0, original);
        if (recipe.oneTimePerChunk() && level instanceof ServerLevel server) {
            AssemblyClaims.get(server).claim(AssemblyClaims.key(assemblyId, server.dimension(), worldPosition.getX() >> 4, worldPosition.getZ() >> 4));
        }
        if (recipe.announceToServer() && level instanceof ServerLevel server && server.getServer() != null) {
            Component name = definition.display().name();
            Component tier = TierRegistry.get(definition.tier()).map(TierDefinition::display).orElseGet(() -> Component.translatable("tooltip.blueprintforge.tier.unknown"));
            server.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.blueprintforge.assembly", name, tier), false);
        }
        assemblyId = null;
        setChanged();
        refreshStress();
    }

    private void cancelJob(boolean refund) {
        copying = false;
        assembling = false;
        jobProgress = 0;
        jobTotal = 0;
        jobInstance = null;
        assemblyId = null;
        if (refund) {
            refundReserved();
        } else {
            reserved.clear();
        }
        setChanged();
        refreshStress();
    }

    private boolean reserve(List<BlueprintDefinition.CostEntry> cost) {
        if (!ResearchPayment.covers(cost, ResearchPayment.tally(materialStacks()))) {
            return false;
        }
        List<ItemStack> taken = new ArrayList<>();
        for (BlueprintDefinition.CostEntry entry : cost) {
            ResourceLocation item = entry.itemOrFluid().left().orElse(null);
            if (item == null) {
                return false;
            }
            int need = entry.amount();
            ItemStack pile = new ItemStack(BuiltInRegistries.ITEM.get(item), 0);
            for (int slot = 0; slot < materials.getSlots() && need > 0; slot++) {
                ItemStack stack = materials.getStackInSlot(slot);
                if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)) {
                    continue;
                }
                int take = Math.min(need, stack.getCount());
                stack.shrink(take);
                pile.grow(take);
                need -= take;
            }
            if (need > 0 || pile.isEmpty()) {
                refundStacks(taken);
                return false;
            }
            taken.add(pile);
        }
        reserved.addAll(taken);
        return true;
    }

    private boolean reserveAssembly(AssemblyRecipe recipe) {
        int need = recipe.fragmentsRequired();
        List<ItemStack> taken = new ArrayList<>();
        for (int slot = 0; slot < materials.getSlots() && need > 0; slot++) {
            ItemStack stack = materials.getStackInSlot(slot);
            BlueprintData data = BlueprintItem.data(stack).orElse(null);
            if (data == null || data.clazz() != BlueprintClass.FRAGMENT || !recipe.fragmentBlueprint().equals(data.definitionId())) {
                continue;
            }
            taken.add(stack.copy());
            materials.setStackInSlot(slot, ItemStack.EMPTY);
            need--;
        }
        if (need > 0) {
            refundStacks(taken);
            return false;
        }
        if (!reserve(recipe.extraIngredients())) {
            refundStacks(taken);
            return false;
        }
        reserved.addAll(0, taken);
        return true;
    }

    private void refundReserved() {
        List<ItemStack> stacks = new ArrayList<>(reserved);
        reserved.clear();
        refundStacks(stacks);
    }

    private void refundStacks(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ItemStack left = stack.copy();
            for (int slot = 0; slot < materials.getSlots() && !left.isEmpty(); slot++) {
                left = materials.insertItem(slot, left, false);
            }
            if (!left.isEmpty() && level != null) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), left);
            }
        }
    }

    private List<BlueprintDefinition.CostEntry> scaledCost(BlueprintDefinition.CopyRules rules, int runs) {
        List<BlueprintDefinition.CostEntry> scaled = new ArrayList<>();
        for (BlueprintDefinition.CostEntry entry : rules.copyCost()) {
            int amount = EfficiencyMath.copyCost(entry.amount(), runs, rules.defaultRuns(), rules.costScaling());
            if (amount > 0) {
                scaled.add(new BlueprintDefinition.CostEntry(entry.itemOrFluid(), amount));
            }
        }
        return scaled;
    }

    private Optional<BlueprintDefinition.CopyRules> copyRules(BlueprintData data) {
        if (data == null) {
            return Optional.empty();
        }
        return BlueprintRegistry.get(data.definitionId()).flatMap(BlueprintDefinition::copy);
    }

    private Optional<Map.Entry<ResourceLocation, AssemblyRecipe>> assemblyMatch() {
        Optional<ResourceLocation> id = AssemblyMatching.match(AssemblyRegistry.all(), materialStacks());
        return id.flatMap(key -> AssemblyRegistry.get(key).map(recipe -> Map.entry(key, recipe)));
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
            blueprintSlot.setStackInSlot(0, ItemStack.EMPTY);
            adjusting = false;
            heldInstance = null;
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), rejected);
            BlueprintForge.LOGGER.warn("Blueprint {} is already held by another block; dropped the duplicate at {}", now, worldPosition);
            return;
        }
        heldInstance = now;
    }

    private boolean adjusting;
}
