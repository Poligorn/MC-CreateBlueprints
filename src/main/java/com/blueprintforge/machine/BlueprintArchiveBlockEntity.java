package com.blueprintforge.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

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
import com.blueprintforge.logic.ItemRemake;
import com.blueprintforge.logic.RemakeMath;
import com.blueprintforge.logic.ResearchAxis;
import com.blueprintforge.logic.ResearchPayment;
import com.blueprintforge.logic.ResearchRefusal;
import com.blueprintforge.recipe.BlueprintOutputApplicator;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Holds one blueprint document on a Create kinetic network. While the network turns, a matching item on the
 * belt under this tunnel is rewritten into the blueprint's forged output. The document stays in the block.
 */
public class BlueprintArchiveBlockEntity extends KineticBlockEntity implements MenuProvider {
    private static final String BLUEPRINT_TAG = "Blueprint";
    /** Sentinel: the replacement is already queued, so a second tick must not spend another run. */
    private static final int FINISHED = Integer.MIN_VALUE;
    public static final int MATERIAL_SLOTS = 16;
    public static final int DATA_ME = 0;
    public static final int DATA_ME_MAX = 1;
    public static final int DATA_TE = 2;
    public static final int DATA_TE_MAX = 3;
    public static final int DATA_ME_NEXT = 4;
    public static final int DATA_TE_NEXT = 5;
    public static final int DATA_PROGRESS = 6;
    public static final int DATA_TOTAL = 7;
    public static final int DATA_AXIS = 8;
    public static final int DATA_ME_REFUSAL = 9;
    public static final int DATA_TE_REFUSAL = 10;
    public static final int DATA_STRESS = 11;
    public static final int DATA_COPY_RUNS = 12;
    public static final int DATA_COPY_MAX = 13;
    public static final int DATA_COPY_REFUSAL = 14;
    public static final int DATA_JOB = 15;
    public static final int DATA_PRESSING = 16;
    public static final int DATA_PRESS_PROGRESS = 17;
    public static final int DATA_PRESS_TOTAL = 18;
    public static final int DATA_COUNT = 19;
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

    /** Countdown per belt item. Not saved: a reload restarts the hold and does not forge a partial result. */
    private final Map<TransportedItemStack, Job> jobs = new WeakHashMap<>();
    /** Null when no research step is running. Materials are spent only when the step finishes. */
    private ResearchAxis researchAxis;
    private int researchProgress;
    private int researchTotal;
    private int researchStress;
    private UUID researchInstance;
    private UUID researchPlayer;
    private String researchPlayerName;
    private boolean stressDirty;
    private boolean copying;
    private boolean assembling;
    private int jobProgress;
    private int jobTotal;
    private int jobStress;
    private UUID jobInstance;
    private UUID copyPlayer;
    private String copyPlayerName;
    private int selectedRuns = 1;
    private final List<ItemStack> reserved = new ArrayList<>();
    private ResourceLocation assemblyId;
    private UUID heldInstance;
    private boolean occupancyReady;
    private boolean clientPressing;
    private int clientPressProgress;
    private int clientPressTotal;
    private boolean pressSync;

    public BlueprintArchiveBlockEntity(BlockPos pos, BlockState state) {
        super(BFBlocks.BLUEPRINT_ARCHIVE_ENTITY.get(), pos, state);
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
     * Starts one ME or TE step. Materials stay in the slots until the step finishes. A copy is refused unless the
     * profile allows it. At the ceiling, with no rotation, or without the items, nothing is taken.
     */
    public ResearchRefusal tryStart(ResearchAxis axis, Player player) {
        ResearchRefusal refusal = refusal(axis);
        if (refusal != ResearchRefusal.OK || level == null || level.isClientSide) {
            return refusal == ResearchRefusal.OK ? ResearchRefusal.NO_ROTATION : refusal;
        }
        ResearchProfile profile = profile().orElseThrow();
        int ticks = EfficiencyMath.researchTicks(Math.abs(getSpeed()), profile.timePerStepTicks());
        if (ticks <= 0) {
            return ResearchRefusal.NO_ROTATION;
        }
        researchAxis = axis;
        researchProgress = 0;
        researchTotal = ticks;
        researchStress = profile.stressPerStep();
        researchInstance = BlueprintItem.data(getDocument()).orElseThrow().instanceId();
        researchPlayer = player.getUUID();
        researchPlayerName = player.getName().getString();
        pushStress();
        setChanged();
        sendData();
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
        BlueprintDefinition.EfficiencyRange range = axis == ResearchAxis.MATERIAL
                ? definition.materialEfficiencyOrFixed()
                : definition.timeEfficiencyOrFixed();
        int current = axis == ResearchAxis.MATERIAL ? data.materialEfficiency() : data.timeEfficiency();
        if (EfficiencyMath.nextStep(current, range.max(), range.step()).isEmpty()) {
            return ResearchRefusal.AT_CAP;
        }
        List<BlueprintDefinition.CostEntry> cost = axis == ResearchAxis.MATERIAL ? profile.meStepCost() : profile.teStepCost();
        if (ResearchPayment.hasFluid(cost)) {
            return ResearchRefusal.FLUID_COST;
        }
        if (!ResearchPayment.covers(cost, ResearchPayment.tally(materialStacks()))) {
            return ResearchRefusal.MISSING_COST;
        }
        if (getSpeed() == 0 || EfficiencyMath.researchTicks(Math.abs(getSpeed()), profile.timePerStepTicks()) <= 0) {
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

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        if (!occupancyReady) {
            occupancyReady = true;
            syncOccupancy();
        }
        if (stressDirty) {
            pushStress();
            stressDirty = false;
        }
        tickJob();
        syncPress();
        if (researchAxis == null) {
            return;
        }
        if (refusalWhileRunning() != ResearchRefusal.OK) {
            cancelResearch();
            return;
        }
        if (getSpeed() == 0) {
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

    @Override
    public float calculateStressApplied() {
        float impact = researchAxis != null ? researchStress : (copying || assembling ? jobStress : 0.0F);
        this.lastStressApplied = impact;
        return impact;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(new BeltProcessingBehaviour(this)
                .whenItemEnters((stack, handler) -> onBeltItem(stack, handler, false))
                .whileItemHeld((stack, handler) -> onBeltItem(stack, handler, true)));
    }

    private ProcessingResult onBeltItem(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, boolean held) {
        if (level == null || level.isClientSide) {
            return ProcessingResult.PASS;
        }
        Offer offer = offerFor(transported.stack).orElse(null);
        if (offer == null) {
            jobs.remove(transported);
            return ProcessingResult.PASS;
        }
        if (!held) {
            if (getSpeed() == 0) {
                return ProcessingResult.PASS;
            }
            int ticks = RemakeMath.processingTicks(Math.abs(getSpeed()), offer.processingTime());
            if (ticks <= 0) {
                return ProcessingResult.PASS;
            }
            jobs.put(transported, new Job(offer.definitionId(), ticks, ticks));
            return ProcessingResult.HOLD;
        }

        Job job = jobs.get(transported);
        if (job == null || !job.definitionId().equals(offer.definitionId())) {
            int ticks = RemakeMath.processingTicks(Math.abs(getSpeed()), offer.processingTime());
            job = new Job(offer.definitionId(), ticks > 0 ? ticks : 1, ticks > 0 ? ticks : 1);
            jobs.put(transported, job);
        }
        if (job.remaining() == FINISHED) {
            return ProcessingResult.HOLD;
        }
        if (getSpeed() == 0) {
            return ProcessingResult.HOLD;
        }
        int left = job.remaining() - 1;
        if (left > 0) {
            jobs.put(transported, new Job(job.definitionId(), left, job.total()));
            return ProcessingResult.HOLD;
        }

        ItemStack forged = BlueprintOutputApplicator.apply(transported.stack, offer.definitionId(), offer.definition(), offer.tier(),
                level.registryAccess());
        TransportedItemStack replacement = transported.getSimilar();
        replacement.stack = forged;
        replacement.locked = false;
        handler.handleProcessingOnItem(transported, TransportedResult.convertTo(replacement));
        jobs.put(transported, new Job(job.definitionId(), FINISHED, job.total()));
        spendCopyRun();
        return ProcessingResult.HOLD;
    }

    private java.util.Optional<Offer> offerFor(ItemStack stack) {
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        if (data == null || data.isUnissued()) {
            return java.util.Optional.empty();
        }
        if (data.clazz() != BlueprintClass.ORIGINAL && data.clazz() != BlueprintClass.COPY) {
            return java.util.Optional.empty();
        }
        if (data.clazz() == BlueprintClass.COPY && data.runsRemaining() <= 0) {
            return java.util.Optional.empty();
        }
        BlueprintDefinition definition = BlueprintRegistry.get(data.definitionId()).orElse(null);
        if (definition == null || definition.remake().isEmpty() || definition.target().isEmpty()) {
            return java.util.Optional.empty();
        }
        if (!ItemRemake.eligible(stack, definition.target().get())) {
            return java.util.Optional.empty();
        }
        TierDefinition tier = TierRegistry.get(definition.tier()).orElse(null);
        if (tier == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Offer(data.definitionId(), definition, tier, definition.remake().get().processingTime()));
    }

    /** Originals are never consumed. A copy loses one run when the forged item is queued, and the last run removes it. */
    private void spendCopyRun() {
        ItemStack document = getDocument();
        BlueprintData data = BlueprintItem.data(document).orElse(null);
        if (data == null || data.clazz() != BlueprintClass.COPY) {
            return;
        }
        int left = data.runsRemaining() - 1;
        if (left <= 0) {
            blueprintSlot.setStackInSlot(0, ItemStack.EMPTY);
        } else {
            ItemStack updated = document.copy();
            updated.set(BFComponents.BLUEPRINT.get(), data.withRuns(left));
            blueprintSlot.setStackInSlot(0, updated);
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
        BlueprintDefinition.EfficiencyRange range = researchAxis == ResearchAxis.MATERIAL
                ? definition.materialEfficiencyOrFixed()
                : definition.timeEfficiencyOrFixed();
        int current = researchAxis == ResearchAxis.MATERIAL ? data.materialEfficiency() : data.timeEfficiency();
        if (EfficiencyMath.nextStep(current, range.max(), range.step()).isEmpty()) {
            return ResearchRefusal.AT_CAP;
        }
        List<BlueprintDefinition.CostEntry> cost = cost(profile, researchAxis);
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
        BlueprintDefinition.EfficiencyRange range = axis == ResearchAxis.MATERIAL
                ? definition.materialEfficiencyOrFixed()
                : definition.timeEfficiencyOrFixed();
        int current = axis == ResearchAxis.MATERIAL ? data.materialEfficiency() : data.timeEfficiency();
        int next = EfficiencyMath.nextStep(current, range.max(), range.step()).orElse(-1);
        List<BlueprintDefinition.CostEntry> cost = cost(profile, axis);
        if (next < 0 || !consume(cost)) {
            cancelResearch();
            return;
        }
        int me = axis == ResearchAxis.MATERIAL ? next : data.materialEfficiency();
        int te = axis == ResearchAxis.TIME ? next : data.timeEfficiency();
        researchAxis = null;
        researchProgress = 0;
        researchTotal = 0;
        researchStress = 0;
        ItemStack updated = getDocument().copy();
        updated.set(BFComponents.BLUEPRINT.get(), data.withResearch(me, te,
                Optional.ofNullable(researchPlayer), Optional.ofNullable(researchPlayerName)));
        blueprintSlot.setStackInSlot(0, updated);
        pushStress();
        setChanged();
        sendData();
    }

    private void cancelResearch() {
        researchAxis = null;
        researchProgress = 0;
        researchTotal = 0;
        researchStress = 0;
        researchInstance = null;
        if (level != null && !level.isClientSide) {
            pushStress();
            sendData();
        }
        setChanged();
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

    private void pushStress() {
        float impact = calculateStressApplied();
        if (hasNetwork()) {
            getOrCreateNetwork().updateStressFor(this, impact);
        }
    }

    private Optional<ResearchProfile> profile() {
        return BlueprintItem.data(getDocument()).flatMap(data -> ResearchRegistry.forBlueprint(data.definitionId()));
    }

    private static List<BlueprintDefinition.CostEntry> cost(ResearchProfile profile, ResearchAxis axis) {
        return axis == ResearchAxis.MATERIAL ? profile.meStepCost() : profile.teStepCost();
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
        int te = data == null ? 0 : data.timeEfficiency();
        int meMax = definition == null ? 0 : definition.materialEfficiencyOrFixed().max();
        int teMax = definition == null ? 0 : definition.timeEfficiencyOrFixed().max();
        int meStep = definition == null ? 0 : definition.materialEfficiencyOrFixed().step();
        int teStep = definition == null ? 0 : definition.timeEfficiencyOrFixed().step();
        return switch (index) {
            case DATA_ME -> me;
            case DATA_ME_MAX -> meMax;
            case DATA_TE -> te;
            case DATA_TE_MAX -> teMax;
            case DATA_ME_NEXT -> EfficiencyMath.nextStep(me, meMax, meStep).orElse(-1);
            case DATA_TE_NEXT -> EfficiencyMath.nextStep(te, teMax, teStep).orElse(-1);
            case DATA_PROGRESS -> researchProgress;
            case DATA_TOTAL -> researchTotal;
            case DATA_AXIS -> researchAxis == null ? 0 : researchAxis.ordinal() + 1;
            case DATA_ME_REFUSAL -> refusal(ResearchAxis.MATERIAL).ordinal();
            case DATA_TE_REFUSAL -> refusal(ResearchAxis.TIME).ordinal();
            case DATA_STRESS -> researchAxis == null ? profile().map(ResearchProfile::stressPerStep).orElse(0) : researchStress;
            case DATA_COPY_RUNS -> selectedRuns;
            case DATA_COPY_MAX -> copyRules(data).map(BlueprintDefinition.CopyRules::maxRuns).orElse(1);
            case DATA_COPY_REFUSAL -> getDocument().isEmpty() ? assemblyRefusal().ordinal() : copyRefusal().ordinal();
            case DATA_JOB -> copying ? 2 : assembling ? 3 : researchAxis != null ? 1 : 0;
            case DATA_PRESSING -> isPressing() ? 1 : 0;
            case DATA_PRESS_PROGRESS -> pressProgress();
            case DATA_PRESS_TOTAL -> pressTotal();
            default -> 0;
        };
    }

    @Override
    public void destroy() {
        super.destroy();
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

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.put(BLUEPRINT_TAG, blueprintSlot.serializeNBT(registries));
        compound.put(MATERIALS_TAG, materials.serializeNBT(registries));
        compound.put("Output", output.serializeNBT(registries));
        compound.putInt("SelectedRuns", selectedRuns);
        compound.putBoolean("Copying", copying);
        compound.putBoolean("Assembling", assembling);
        compound.putInt("JobProgress", jobProgress);
        compound.putInt("JobTotal", jobTotal);
        compound.putInt("JobStress", jobStress);
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
        compound.putBoolean("Pressing", isPressing());
        compound.putInt("PressProgress", pressProgress());
        compound.putInt("PressTotal", pressTotal());
        if (researchAxis != null) {
            compound.putString(RESEARCH_AXIS_TAG, researchAxis.name());
            compound.putInt("ResearchProgress", researchProgress);
            compound.putInt("ResearchTotal", researchTotal);
            compound.putInt("ResearchStress", researchStress);
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
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        blueprintSlot.deserializeNBT(registries, compound.getCompound(BLUEPRINT_TAG));
        materials.deserializeNBT(registries, compound.getCompound(MATERIALS_TAG));
        if (compound.contains("Output")) {
            output.deserializeNBT(registries, compound.getCompound("Output"));
        }
        selectedRuns = Math.max(1, compound.getInt("SelectedRuns"));
        clientPressing = compound.getBoolean("Pressing");
        clientPressProgress = compound.getInt("PressProgress");
        clientPressTotal = compound.getInt("PressTotal");
        copying = compound.getBoolean("Copying");
        assembling = compound.getBoolean("Assembling");
        jobProgress = compound.getInt("JobProgress");
        jobTotal = compound.getInt("JobTotal");
        jobStress = compound.getInt("JobStress");
        jobInstance = compound.hasUUID("JobInstance") ? compound.getUUID("JobInstance") : null;
        assemblyId = compound.contains("Assembly") ? ResourceLocation.tryParse(compound.getString("Assembly")) : null;
        reserved.clear();
        for (net.minecraft.nbt.Tag entry : compound.getList("Reserved", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, entry).ifPresent(reserved::add);
        }
        if (copying || assembling) {
            stressDirty = true;
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
        researchStress = compound.getInt("ResearchStress");
        researchInstance = compound.hasUUID("ResearchInstance") ? compound.getUUID("ResearchInstance") : null;
        researchPlayer = compound.hasUUID("ResearchPlayer") ? compound.getUUID("ResearchPlayer") : null;
        researchPlayerName = compound.contains("ResearchPlayerName") ? compound.getString("ResearchPlayerName") : null;
        stressDirty = researchAxis != null;
        super.read(compound, registries, clientPacket);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.blueprintforge.blueprint_archive");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BlueprintArchiveMenu(containerId, inventory, this);
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

    /** The press moves only while a research step or a belt remake is working an item. Copying does not strike. */
    public boolean isPressing() {
        if (level != null && level.isClientSide) {
            return clientPressing;
        }
        return researchAxis != null || activeRemake() != null;
    }

    public int pressProgress() {
        if (level != null && level.isClientSide) {
            return clientPressProgress;
        }
        if (researchAxis != null) {
            return researchProgress;
        }
        Job job = activeRemake();
        return job == null ? 0 : Math.max(0, job.total() - Math.max(job.remaining(), 0));
    }

    public int pressTotal() {
        if (level != null && level.isClientSide) {
            return clientPressTotal;
        }
        if (researchAxis != null) {
            return researchTotal;
        }
        Job job = activeRemake();
        return job == null ? 0 : job.total();
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
            return refusal == ArchiveRefusal.OK ? ArchiveRefusal.NO_ROTATION : refusal;
        }
        BlueprintData data = BlueprintItem.data(getDocument()).orElseThrow();
        BlueprintDefinition.CopyRules rules = copyRules(data).orElseThrow();
        ResearchProfile profile = profile().orElseThrow();
        int runs = EfficiencyMath.clampRuns(selectedRuns, rules.maxRuns());
        int perRun = RemakeMath.processingTicks(Math.abs(getSpeed()), profile.copyTimePerRunTicks());
        if (perRun <= 0) {
            return ArchiveRefusal.NO_ROTATION;
        }
        if (!reserve(scaledCost(rules, runs))) {
            return ArchiveRefusal.MISSING_COST;
        }
        copying = true;
        jobProgress = 0;
        jobTotal = perRun * runs;
        jobStress = profile.copyStress();
        jobInstance = data.instanceId();
        copyPlayer = player.getUUID();
        copyPlayerName = player.getName().getString();
        selectedRuns = runs;
        pushStress();
        setChanged();
        sendData();
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
        if (getSpeed() == 0 || RemakeMath.processingTicks(Math.abs(getSpeed()), profile.copyTimePerRunTicks()) <= 0) {
            return ArchiveRefusal.NO_ROTATION;
        }
        return ArchiveRefusal.OK;
    }

    public ArchiveRefusal tryAssemble(Player player) {
        ArchiveRefusal refusal = assemblyRefusal();
        if (refusal != ArchiveRefusal.OK || !(level instanceof ServerLevel server)) {
            return refusal == ArchiveRefusal.OK ? ArchiveRefusal.NO_ROTATION : refusal;
        }
        Map.Entry<ResourceLocation, AssemblyRecipe> match = assemblyMatch().orElseThrow();
        if (!reserveAssembly(match.getValue())) {
            return ArchiveRefusal.ASSEMBLY_SHORT;
        }
        assembling = true;
        assemblyId = match.getKey();
        jobProgress = 0;
        jobTotal = RemakeMath.processingTicks(Math.abs(getSpeed()), match.getValue().processTimeTicks());
        jobStress = match.getValue().stress();
        if (jobTotal <= 0) {
            cancelJob(true);
            return ArchiveRefusal.NO_ROTATION;
        }
        pushStress();
        setChanged();
        sendData();
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
        if (getSpeed() == 0 || RemakeMath.processingTicks(Math.abs(getSpeed()), match.getValue().processTimeTicks()) <= 0) {
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
        if (getSpeed() == 0) {
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
        int te = EfficiencyMath.penalized(data.timeEfficiency(), rules.tePenalty());
        ItemStack copy = new ItemStack(BFItems.BLUEPRINT.get());
        copy.set(BFComponents.BLUEPRINT.get(), data.printedCopy(UUID.randomUUID(), runs, me, te,
                Optional.ofNullable(copyPlayer), Optional.ofNullable(copyPlayerName)));
        reserved.clear();
        copying = false;
        jobStress = 0;
        output.setStackInSlot(0, copy);
        pushStress();
        setChanged();
        sendData();
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
        jobStress = 0;
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
        pushStress();
        setChanged();
        sendData();
    }

    private void cancelJob(boolean refund) {
        copying = false;
        assembling = false;
        jobProgress = 0;
        jobTotal = 0;
        jobStress = 0;
        jobInstance = null;
        assemblyId = null;
        if (refund) {
            refundReserved();
        } else {
            reserved.clear();
        }
        if (level != null && !level.isClientSide) {
            pushStress();
            sendData();
        }
        setChanged();
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

    private Job activeRemake() {
        for (Job job : jobs.values()) {
            if (job.remaining() != FINISHED && job.remaining() > 0) {
                return job;
            }
        }
        return null;
    }

    private void syncPress() {
        boolean pressing = isPressing();
        if (pressing || pressSync) {
            sendData();
        }
        pressSync = pressing;
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
            BlueprintForge.LOGGER.warn("Blueprint {} is already held by another Archive; dropped the duplicate at {}", now, worldPosition);
            return;
        }
        heldInstance = now;
    }

    private boolean adjusting;

    private record Job(ResourceLocation definitionId, int remaining, int total) {
    }

    private record Offer(ResourceLocation definitionId, BlueprintDefinition definition, TierDefinition tier, int processingTime) {
    }
}
