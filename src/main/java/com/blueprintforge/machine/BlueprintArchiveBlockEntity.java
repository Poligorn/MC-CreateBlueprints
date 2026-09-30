package com.blueprintforge.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.ResearchProfile;
import com.blueprintforge.data.ResearchRegistry;
import com.blueprintforge.data.TierDefinition;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.item.BlueprintItem;
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
    public static final int MATERIAL_SLOTS = 4;
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
    public static final int DATA_COUNT = 12;
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
        if (researchAxis != null) {
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
        if (stressDirty) {
            pushStress();
            stressDirty = false;
        }
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
        float impact = researchAxis == null ? 0.0F : researchStress;
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
            jobs.put(transported, new Job(offer.definitionId(), ticks));
            return ProcessingResult.HOLD;
        }

        Job job = jobs.get(transported);
        if (job == null || !job.definitionId().equals(offer.definitionId())) {
            int ticks = RemakeMath.processingTicks(Math.abs(getSpeed()), offer.processingTime());
            job = new Job(offer.definitionId(), ticks > 0 ? ticks : 1);
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
            jobs.put(transported, new Job(job.definitionId(), left));
            return ProcessingResult.HOLD;
        }

        ItemStack forged = BlueprintOutputApplicator.apply(transported.stack, offer.definitionId(), offer.definition(), offer.tier(),
                level.registryAccess());
        TransportedItemStack replacement = transported.getSimilar();
        replacement.stack = forged;
        replacement.locked = false;
        handler.handleProcessingOnItem(transported, TransportedResult.convertTo(replacement));
        jobs.put(transported, new Job(job.definitionId(), FINISHED));
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
        if (researchAxis == null) {
            return;
        }
        BlueprintData data = BlueprintItem.data(getDocument()).orElse(null);
        if (data == null || researchInstance == null || !researchInstance.equals(data.instanceId())) {
            cancelResearch();
        }
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
            default -> 0;
        };
    }

    @Override
    public void destroy() {
        super.destroy();
        cancelResearch();
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
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.put(BLUEPRINT_TAG, blueprintSlot.serializeNBT(registries));
        compound.put(MATERIALS_TAG, materials.serializeNBT(registries));
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
            return !stack.is(BFItems.BLUEPRINT.get());
        }
    }

    private record Job(ResourceLocation definitionId, int remaining) {
    }

    private record Offer(ResourceLocation definitionId, BlueprintDefinition definition, TierDefinition tier, int processingTime) {
    }
}
