package com.blueprintforge.terminal;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.blueprintforge.compat.createapi.SpikeLine;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFItems;
import com.blueprintforge.research.ResearchBlueprint;
import com.blueprintforge.research.ResearchChain;
import com.blueprintforge.research.ResearchRegistry;
import com.blueprintforge.research.ResearchRun;
import com.blueprintforge.research.ResearchTier;
import com.blueprintforge.research.RunScore;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The terminal: holds a blueprint, turns the target item into a workpiece and scores the workpiece when it comes back.
 * Several rules here are proposals for open questions and carry a TODO(egor) where they are decided in code.
 */
public class TerminalBlockEntity extends BlockEntity implements MenuProvider {
    public static final int DATA_STEPS = 0;
    public static final int DATA_PRESS = 1;
    public static final int DATA_BLAST = 2;
    public static final int DATA_OTHER = 3;
    public static final int DATA_PERCENT = 4;
    public static final int DATA_MATCHED = 5;
    public static final int DATA_TOTAL = 6;
    public static final int DATA_MISSING = 7;
    public static final int DATA_FLAGS = 8;
    public static final int DATA_SPRITE_TIER = 9;
    public static final int DATA_COUNT = 10;

    public static final int FLAG_BLUEPRINT = 1;
    public static final int FLAG_UNKNOWN_TIER = 2;
    public static final int FLAG_NO_SEED = 4;

    private static final String PRESS = "press";
    private static final String BLAST = "blast";

    private final TerminalSlots inventory = new TerminalSlots() {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == TARGET) {
                return acceptsTarget(stack);
            }
            return super.isItemValid(slot, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            onInventoryChanged(slot);
        }
    };

    private final IItemHandler outputView = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(TerminalSlots.OUTPUT);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inventory.extractItem(TerminalSlots.OUTPUT, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    private int steps;
    private int press;
    private int blast;
    private int other;
    private int flags;
    private int missing;
    private int lastPercent = -1;
    private int lastMatched;
    private int lastTotal;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_STEPS -> steps;
                case DATA_PRESS -> press;
                case DATA_BLAST -> blast;
                case DATA_OTHER -> other;
                case DATA_PERCENT -> lastPercent;
                case DATA_MATCHED -> lastMatched;
                case DATA_TOTAL -> lastTotal;
                case DATA_MISSING -> missing;
                case DATA_FLAGS -> flags;
                case DATA_SPRITE_TIER -> blueprint().map(ResearchBlueprint::spriteTier).orElse(0);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(BFBlocks.TERMINAL_ENTITY.get(), pos, state);
    }

    public TerminalSlots inventory() {
        return inventory;
    }

    /** The side an automation block can take the workpiece from: the one with the brass port. */
    @Nullable
    public IItemHandler handlerFor(@Nullable Direction side) {
        return side != null && side == getBlockState().getValue(TerminalBlock.FACING).getClockWise() ? outputView : null;
    }

    private Optional<ResearchBlueprint> blueprint() {
        return Optional.ofNullable(inventory.getStackInSlot(TerminalSlots.BLUEPRINT).get(BFComponents.RESEARCH_BLUEPRINT.get()));
    }

    private boolean acceptsTarget(ItemStack stack) {
        // A workpiece is the target item with a log on it, and it must never be taken for a fresh target.
        return !SpikeLine.isWorkpiece(stack)
                && blueprint().map(bp -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(bp.item())).orElse(false);
    }

    private void onInventoryChanged(int slot) {
        boolean server = level != null && !level.isClientSide;
        if (server && slot == TerminalSlots.BLUEPRINT) {
            assignSeedIfAbsent();
        }
        refresh();
        if (server && slot == TerminalSlots.BLUEPRINT) {
            updateScreenState();
            scanLine();
        }
        setChanged();
    }

    /** The seed is given the first time a blueprint goes into a terminal, as the design says. */
    public void assignSeedIfAbsent() {
        ItemStack stack = inventory.getStackInSlot(TerminalSlots.BLUEPRINT);
        ResearchBlueprint bp = stack.get(BFComponents.RESEARCH_BLUEPRINT.get());
        if (bp != null && bp.seed().isEmpty() && level != null) {
            stack.set(BFComponents.RESEARCH_BLUEPRINT.get(), bp.withSeed(level.random.nextLong()));
        }
    }

    private void updateScreenState() {
        TerminalScreenState wanted = blueprint()
                .map(bp -> bp.spriteTier() >= 4 ? TerminalScreenState.ACTIVE_T4 : TerminalScreenState.ACTIVE_T2)
                .orElse(TerminalScreenState.IDLE);
        BlockState state = getBlockState();
        if (level != null && state.getValue(TerminalBlock.SCREEN) != wanted) {
            level.setBlock(worldPosition, state.setValue(TerminalBlock.SCREEN, wanted), 3);
        }
    }

    /** The chain of the blueprint in the slot, empty while there is no blueprint, no seed or no known tier. */
    public List<ResourceLocation> chain() {
        ResearchBlueprint bp = blueprint().orElse(null);
        if (bp == null || bp.seed().isEmpty()) {
            return List.of();
        }
        ResearchTier tier = ResearchRegistry.tier(bp.tier()).orElse(null);
        if (tier == null) {
            return List.of();
        }
        try {
            return ResearchChain.generate(bp.seed().get(), tier, bp.generatorVersion());
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    private void refresh() {
        ResearchBlueprint bp = blueprint().orElse(null);
        flags = 0;
        steps = press = blast = other = 0;
        if (bp == null) {
            missing = 0;
            return;
        }
        flags |= FLAG_BLUEPRINT;
        if (ResearchRegistry.tier(bp.tier()).isEmpty()) {
            flags |= FLAG_UNKNOWN_TIER;
        }
        if (bp.seed().isEmpty()) {
            flags |= FLAG_NO_SEED;
        }
        List<ResourceLocation> chain = chain();
        steps = chain.size();
        for (ResourceLocation operation : chain) {
            switch (operation.getPath()) {
                case PRESS -> press++;
                case BLAST -> blast++;
                default -> other++;
            }
        }
    }

    private void scanLine() {
        if (level == null || level.isClientSide) {
            return;
        }
        missing = blueprint().isPresent() ? LineScanner.missing(level, worldPosition, press > 0, blast > 0) : 0;
    }

    /** Called by the open menu on the server every tick; the scan itself runs twice a second at most. */
    public void tickLine() {
        if (level != null && level.getGameTime() % 40 == 0) {
            int before = missing;
            scanLine();
            if (before != missing) {
                setChanged();
            }
        }
    }

    /** @return {@code null} on success, otherwise why nothing happened */
    @Nullable
    public Component createWorkpiece() {
        ResearchBlueprint bp = blueprint().orElse(null);
        if (bp == null) {
            return Component.translatable("gui.blueprintforge.terminal.error.no_blueprint");
        }
        if (ResearchRegistry.tier(bp.tier()).isEmpty()) {
            return Component.translatable("gui.blueprintforge.terminal.error.unknown_tier");
        }
        if (chain().isEmpty()) {
            return Component.translatable("gui.blueprintforge.terminal.error.no_chain");
        }
        ItemStack target = inventory.getStackInSlot(TerminalSlots.TARGET);
        if (target.isEmpty()) {
            return Component.translatable("gui.blueprintforge.terminal.error.no_target");
        }
        if (!inventory.getStackInSlot(TerminalSlots.OUTPUT).isEmpty()) {
            return Component.translatable("gui.blueprintforge.terminal.error.output_busy");
        }
        // The target item is spent when the workpiece is issued. TODO(egor): proposal for open question 41.
        // Enchantments and durability stay on the workpiece, which is the item itself, and are not carried to the result
        // (open question 5, temporary choice).
        ItemStack workpiece = SpikeLine.workpiece(target.copyWithCount(1));
        workpiece.set(BFComponents.RESEARCH_RUN.get(), new ResearchRun(bp.tier(), bp.target(), bp.item(), bp.seed().orElseThrow(), bp.generatorVersion()));
        inventory.setStackInSlot(TerminalSlots.TARGET, ItemStack.EMPTY);
        inventory.setStackInSlot(TerminalSlots.OUTPUT, workpiece);
        return null;
    }

    /** @return {@code null} on success, otherwise why nothing happened */
    @Nullable
    public Component checkRun() {
        ItemStack workpiece = inventory.getStackInSlot(TerminalSlots.RETURN);
        ResearchRun run = workpiece.get(BFComponents.RESEARCH_RUN.get());
        if (run == null) {
            return Component.translatable("gui.blueprintforge.terminal.error.no_workpiece");
        }
        if (!inventory.getStackInSlot(TerminalSlots.OUTPUT).isEmpty()) {
            return Component.translatable("gui.blueprintforge.terminal.error.output_busy");
        }
        ResearchTier tier = ResearchRegistry.tier(run.tier()).orElse(null);
        if (tier == null) {
            return Component.translatable("gui.blueprintforge.terminal.error.unknown_tier");
        }
        List<String> expected;
        try {
            expected = ResearchChain.generate(run.seed(), tier, run.generatorVersion()).stream().map(ResourceLocation::getPath).toList();
        } catch (IllegalArgumentException e) {
            return Component.translatable("gui.blueprintforge.terminal.error.no_chain");
        }
        RunScore score = RunScore.of(expected, SpikeLine.operationsOf(workpiece));
        lastPercent = score.percent();
        lastMatched = score.matched();
        lastTotal = score.total();

        // TODO(egor): stubs. 100% should give the enhanced item (task 9) and anything less the scrap (task 10, open question 3).
        // Here 100% gives the target item with a gold name, a glint and a tier line, anything less gives a placeholder scrap item.
        ItemStack result;
        if (score.complete()) {
            Item item = BuiltInRegistries.ITEM.get(run.item());
            result = new ItemStack(item);
            // Gold name, enchantment glint and a tier line mark an item made from a blueprint (decision of 24 November 2026).
            result.set(DataComponents.CUSTOM_NAME, Component.translatable(item.getDescriptionId())
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)));
            result.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("tooltip.blueprintforge.result.from",
                    Component.translatable("research.tier." + run.tier().getNamespace() + "." + run.tier().getPath()))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tier.color())).withItalic(false)))));
            result.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            result = new ItemStack(BFItems.SCRAP.get());
        }
        inventory.setStackInSlot(TerminalSlots.RETURN, ItemStack.EMPTY);
        inventory.setStackInSlot(TerminalSlots.OUTPUT, result);
        setChanged();
        return null;
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < TerminalSlots.COUNT; slot++) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, inventory.getStackInSlot(slot));
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("LastPercent", lastPercent);
        tag.putInt("LastMatched", lastMatched);
        tag.putInt("LastTotal", lastTotal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        lastPercent = tag.contains("LastPercent") ? tag.getInt("LastPercent") : -1;
        lastMatched = tag.getInt("LastMatched");
        lastTotal = tag.getInt("LastTotal");
        refresh();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.blueprintforge.terminal");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new TerminalMenu(containerId, inventory, this);
    }
}
