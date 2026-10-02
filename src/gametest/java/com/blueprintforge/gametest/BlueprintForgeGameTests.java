package com.blueprintforge.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import com.blueprintforge.BFConfig;
import com.blueprintforge.compat.viewer.ViewerCatalog;
import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDataLoader;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.ForgedItemData;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.logic.ArchiveRefusal;
import com.blueprintforge.logic.EnchantPolicy;
import com.blueprintforge.logic.ResearchAxis;
import com.blueprintforge.logic.ResearchRefusal;
import com.blueprintforge.data.LineMark;
import com.blueprintforge.data.StampData;
import com.blueprintforge.logic.LineProduction;
import com.blueprintforge.machine.BlueprintArchiveBlockEntity;
import com.blueprintforge.machine.BlueprintDockBlockEntity;
import com.blueprintforge.machine.ProjectBureauBlockEntity;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFComponents;
import com.simibubi.create.AllBlocks;
import com.blueprintforge.event.CreativeIssueHandler;
import com.blueprintforge.registry.BFCreativeTabs;
import com.blueprintforge.registry.BFItems;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.phys.AABB;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BlueprintForge.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlueprintForgeGameTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation GUILD_BLADE = BlueprintForge.id("guild_blade");
    private static final ResourceLocation TIER2 = BlueprintForge.id("tier2");

    private BlueprintForgeGameTests() {
    }

    private static ItemStack guildBladeOriginal(UUID uuid) {
        BlueprintDefinition definition = BlueprintRegistry.get(GUILD_BLADE).orElseThrow(() -> new IllegalStateException("guild_blade not loaded"));
        return BlueprintItem.createInstance(GUILD_BLADE, definition, uuid);
    }

    private static ItemStack forgedT2Sword() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(BFComponents.FORGED.get(), new ForgedItemData(GUILD_BLADE, TIER2, List.of()));
        return sword;
    }

    @GameTest(template = EMPTY)
    public static void referencePackLoadsAndBrokenFilesAreSkipped(GameTestHelper helper) {
        helper.assertTrue(TierRegistry.get(TIER2).map(t -> t.requiresBlueprint()).orElse(false), "tier2 must require a blueprint");
        helper.assertTrue(TierRegistry.get(BlueprintForge.id("tier1")).map(t -> !t.requiresBlueprint()).orElse(false), "tier1 must be free");
        BlueprintDefinition blade = BlueprintRegistry.get(GUILD_BLADE).orElse(null);
        helper.assertTrue(blade != null && blade.clazz() == BlueprintClass.ORIGINAL, "guild_blade original must load");

        List<String> errors = BlueprintDataLoader.lastErrors();
        helper.assertTrue(errors.stream().anyMatch(e -> e.contains("blueprintforge_test/blueprint/broken_syntax.json")),
                "syntax error must be reported: " + errors);
        helper.assertTrue(errors.stream().anyMatch(e -> e.contains("blueprintforge_test/blueprint/unknown_tier.json")),
                "unknown tier must be reported: " + errors);
        helper.assertTrue(errors.stream().anyMatch(e -> e.contains("blueprintforge_test/blueprint_source/future_type.json")),
                "future source type must be reported: " + errors);
        helper.assertTrue(errors.stream().anyMatch(e -> e.contains("blueprintforge_test/blueprint_research/broken.json")),
                "broken research file must be reported: " + errors);
        helper.assertTrue(com.blueprintforge.data.ResearchRegistry.forBlueprint(GUILD_BLADE).isPresent(),
                "the reference research profile must load");
        helper.assertFalse(BlueprintRegistry.get(ResourceLocation.fromNamespaceAndPath("blueprintforge_test", "unknown_tier")).isPresent(),
                "blueprint with unknown tier must be skipped");
        helper.succeed();
    }

    private static final BlockPos BELT_START = new BlockPos(0, 1, 2);
    private static final BlockPos BELT_END = new BlockPos(4, 1, 2);
    private static final BlockPos ARCHIVE = new BlockPos(2, 2, 2);
    private static final BlockPos BUREAU = new BlockPos(2, 2, 4);

    private static BlueprintArchiveBlockEntity placeArchive(GameTestHelper helper) {
        helper.setBlock(ARCHIVE, BFBlocks.BLUEPRINT_ARCHIVE.get().defaultBlockState());
        return helper.getBlockEntity(ARCHIVE);
    }

    private static ProjectBureauBlockEntity placeLab(GameTestHelper helper) {
        helper.setBlock(BUREAU, BFBlocks.PROJECT_BUREAU.get().defaultBlockState());
        return helper.getBlockEntity(BUREAU);
    }

    private static void withShaft(GameTestHelper helper, java.util.function.Consumer<ProjectBureauBlockEntity> next) {
        ProjectBureauBlockEntity lab = placeLab(helper);
        helper.setBlock(BUREAU.above(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(Math.abs(lab.getSpeed()) > 1.0F, "laboratory shaft is turning, speed=" + lab.getSpeed());
            next.accept(lab);
        });
    }

    @GameTest(template = EMPTY)
    public static void archiveStandsWithoutABeltAndKeepsItsDocument(GameTestHelper helper) {
        helper.setBlock(ARCHIVE.below(), Blocks.STONE);
        BlueprintArchiveBlockEntity be = placeArchive(helper);
        UUID uuid = UUID.randomUUID();
        helper.assertTrue(be.getDocumentSlot().insertItem(0, guildBladeOriginal(uuid), false).isEmpty(), "original must fit");
        helper.destroyBlock(ARCHIVE.below());
        helper.assertBlockPresent(BFBlocks.BLUEPRINT_ARCHIVE.get(), ARCHIVE);
        helper.assertTrue(BlueprintItem.data(be.getDocument()).map(d -> d.instanceId().equals(uuid)).orElse(false),
                "removing the block below does not break the archive or its document");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void laboratoryDoesNotStartWithoutAShaft(GameTestHelper helper) {
        ProjectBureauBlockEntity lab = placeLab(helper);
        lab.getBlueprintSlot().insertItem(0, guildBladeOriginal(UUID.randomUUID()), false);
        lab.getMaterials().insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(lab.tryStart(ResearchAxis.MATERIAL, player) == ResearchRefusal.NO_ROTATION, "a stopped shaft does not start");
        helper.assertTrue(lab.getMaterials().getStackInSlot(0).getCount() == 4, "iron is not spent");
        helper.assertTrue(!lab.isWorking(), "the laboratory stays idle");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void beltDoesNotForgeASword(GameTestHelper helper) {
        BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        helper.setBlock(BELT_START, shaft);
        helper.setBlock(BELT_END, shaft);
        BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(BELT_START), helper.absolutePos(BELT_END));
        helper.setBlock(BELT_START.north(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        ProjectBureauBlockEntity bureau = placeLab(helper);
        UUID uuid = UUID.randomUUID();
        helper.assertTrue(bureau.getBlueprintSlot().insertItem(0, guildBladeOriginal(uuid), false).isEmpty(), "original must fit");
        float[] direction = new float[1];
        helper.runAfterDelay(20, () -> {
            BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
            helper.assertTrue(controller != null && controller.getDirectionAwareBeltMovementSpeed() != 0, "belt must be running");
            direction[0] = Math.signum(controller.getDirectionAwareBeltMovementSpeed());
            TransportedItemStack item = new TransportedItemStack(new ItemStack(Items.IRON_SWORD));
            item.beltPosition = direction[0] > 0 ? 0.5F : controller.beltLength - 0.5F;
            controller.getInventory().addItem(item);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(direction[0] != 0, "sword not placed yet");
            BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
            float center = controller.beltLength / 2.0F;
            boolean plainPast = controller.getInventory().getTransportedItems().stream().anyMatch(stack ->
                    stack.stack.is(Items.IRON_SWORD) && stack.stack.get(BFComponents.FORGED.get()) == null
                            && (stack.beltPosition - center) * direction[0] > 1.0F);
            boolean ejectedPlain = controller.getInventory().getTransportedItems().stream().noneMatch(stack -> stack.stack.is(Items.IRON_SWORD))
                    && !helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BELT_END)).inflate(4),
                    entity -> entity.getItem().is(Items.IRON_SWORD) && entity.getItem().get(BFComponents.FORGED.get()) == null).isEmpty();
            helper.assertTrue(plainPast || ejectedPlain, "a sword on the belt stays an unforged iron sword");
            helper.assertTrue(BlueprintItem.data(bureau.getDocument()).map(d -> d.isOriginal() && d.instanceId().equals(uuid)).orElse(false),
                    "the original stays in the bureau");
        });
    }

    @GameTest(template = EMPTY)
    public static void archiveKeepsOneDocumentAcrossSaveAndDropsItWhenBroken(GameTestHelper helper) {
        BlueprintArchiveBlockEntity be = placeArchive(helper);
        UUID uuid = UUID.randomUUID();

        helper.assertTrue(be.getDocumentSlot().insertItem(0, guildBladeOriginal(uuid), false).isEmpty(), "original must fit");
        helper.assertFalse(be.getDocumentSlot().insertItem(0, guildBladeOriginal(UUID.randomUUID()), false).isEmpty(),
                "a second document must not fit");

        ServerLevel level = helper.getLevel();
        CompoundTag saved = be.saveWithFullMetadata(level.registryAccess());
        BlockState state = level.getBlockState(helper.absolutePos(ARCHIVE));
        BlueprintArchiveBlockEntity reloaded = new BlueprintArchiveBlockEntity(helper.absolutePos(ARCHIVE), state);
        reloaded.loadWithComponents(saved, level.registryAccess());
        helper.assertTrue(BlueprintItem.data(reloaded.getDocument()).map(BlueprintData::instanceId).filter(uuid::equals).isPresent(),
                "document must survive save/load with its UUID");

        helper.destroyBlock(ARCHIVE);
        assertSingleDroppedOriginal(helper, uuid);
        helper.succeed();
    }


    private static void assertSingleDroppedOriginal(GameTestHelper helper, UUID uuid) {
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, ARCHIVE, 3.0).stream()
                .filter(entity -> BlueprintItem.data(entity.getItem()).map(d -> d.instanceId().equals(uuid)).orElse(false))
                .toList();
        helper.assertTrue(drops.size() == 1, "exactly one dropped original expected, got " + drops.size());
        helper.assertTrue(BlueprintItem.data(drops.getFirst().getItem()).map(BlueprintData::isOriginal).orElse(false), "drop is the original");
    }

    @GameTest(template = EMPTY)
    public static void archiveRejectsBlanksFragmentsTemplatesAndOtherItems(GameTestHelper helper) {
        BlueprintArchiveBlockEntity be = placeArchive(helper);

        ItemStack fragment = guildBladeOriginal(UUID.randomUUID());
        BlueprintData data = BlueprintItem.data(fragment).orElseThrow();
        fragment.set(BFComponents.BLUEPRINT.get(), new BlueprintData(data.instanceId(), data.definitionId(), BlueprintClass.FRAGMENT,
                data.tierId(), data.target(), 1, 0, 0, 0, 0L, data.researcherUuid(), data.researcherName(), data.copierUuid(),
                data.copierName(), data.ownerUuid(), data.ownerName(), data.roll()));
        ItemStack template = guildBladeOriginal(BlueprintData.UNISSUED);

        ItemStack spentCopy = guildBladeCopy(UUID.randomUUID(), 0);
        for (ItemStack stack : List.of(new ItemStack(BFItems.BLUEPRINT.get()), fragment, template, spentCopy, new ItemStack(Items.PAPER))) {
            helper.assertFalse(be.getDocumentSlot().insertItem(0, stack, false).isEmpty(), "must reject " + stack);
        }
        helper.assertTrue(be.getDocument().isEmpty(), "slot stays empty");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void creativeCopiesBelongToThePlayerWhoTakesThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CreativeModeTab tab = BFCreativeTabs.MAIN.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), false, level.registryAccess()));
        ItemStack template = tab.getDisplayItems().stream()
                .filter(stack -> BlueprintItem.data(stack).map(d -> d.definitionId().equals(GUILD_BLADE)).orElse(false))
                .findFirst().orElseThrow(() -> new IllegalStateException("guild_blade missing from the creative tab"));
        helper.assertTrue(BlueprintItem.data(template).orElseThrow().isUnissued(), "creative tab holds an unissued template");

        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getInventory().setItem(0, template.copy());
        player.getInventory().setItem(1, template.copy());
        helper.assertTrue(CreativeIssueHandler.issueInventory(player) == 2, "both taken copies must be issued");

        BlueprintData first = BlueprintItem.data(player.getInventory().getItem(0)).orElseThrow();
        BlueprintData second = BlueprintItem.data(player.getInventory().getItem(1)).orElseThrow();
        for (BlueprintData issued : List.of(first, second)) {
            helper.assertFalse(issued.isUnissued(), "issued copy needs a real instance id");
            helper.assertTrue(issued.ownerUuid().filter(player.getUUID()::equals).isPresent(), "owner is the player who took it");
            helper.assertTrue(issued.ownerName().filter(player.getGameProfile().getName()::equals).isPresent(), "owner name is recorded");
            helper.assertTrue(issued.isOriginal() && issued.runsRemaining() == -1, "class and runs are kept");
        }
        helper.assertFalse(first.instanceId().equals(second.instanceId()), "two copies from the tab never share a UUID");
        helper.assertTrue(BlueprintItem.data(template).orElseThrow().isUnissued(), "the tab template itself stays unissued");
        helper.assertTrue(CreativeIssueHandler.issueInventory(player) == 0, "issued copies are not re-issued");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void creativeCopyThrownOutOfTheInventoryBelongsToTheThrower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2)));
        ItemEntity thrown = new ItemEntity(level, at.x, at.y, at.z, guildBladeOriginal(BlueprintData.UNISSUED));
        thrown.setThrower(player);
        level.addFreshEntity(thrown);

        BlueprintData issued = BlueprintItem.data(thrown.getItem()).orElseThrow();
        helper.assertFalse(issued.isUnissued(), "thrown template must be issued when it enters the world");
        helper.assertTrue(issued.ownerUuid().filter(player.getUUID()::equals).isPresent(), "owner is the thrower");
        helper.succeed();
    }

    private static List<ItemStack> rollMineshaftChest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.ABANDONED_MINESHAFT);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                .create(LootContextParamSets.CHEST);
        return table.getRandomItems(params);
    }

    @GameTest(template = EMPTY)
    public static void referenceChestCanYieldTheOriginal(GameTestHelper helper) {
        double previous = BFConfig.RARITY_MULTIPLIER.get();
        try {
            BFConfig.RARITY_MULTIPLIER.set(1000.0);
            List<BlueprintData> found = rollMineshaftChest(helper).stream()
                    .map(BlueprintItem::data).flatMap(java.util.Optional::stream).toList();
            helper.assertTrue(found.size() == 1, "one injected entry with chance clamped to 1, got " + found.size());
            BlueprintData original = found.getFirst();
            helper.assertTrue(original.isOriginal() && original.runsRemaining() == -1, "must be an original with unlimited runs");
            helper.assertTrue(original.definitionId().equals(GUILD_BLADE) && original.tierId().equals(TIER2), "must be the T2 guild blade");
        } finally {
            BFConfig.RARITY_MULTIPLIER.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void zeroRarityMultiplierDisablesInjection(GameTestHelper helper) {
        double previous = BFConfig.RARITY_MULTIPLIER.get();
        try {
            BFConfig.RARITY_MULTIPLIER.set(0.0);
            for (int roll = 0; roll < 20; roll++) {
                helper.assertFalse(rollMineshaftChest(helper).stream().anyMatch(s -> BlueprintItem.data(s).isPresent()),
                        "no blueprint may appear with multiplier 0");
            }
        } finally {
            BFConfig.RARITY_MULTIPLIER.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void enchantingTableRefusesForgedT2AndAcceptsVanillaIronSword(GameTestHelper helper) {
        helper.assertTrue(BFConfig.enchantingMode() == com.blueprintforge.logic.EnchantPolicy.Mode.RESTRICTED, "default mode must be restricted");
        BlockPos table = new BlockPos(2, 1, 2);
        helper.setBlock(table, Blocks.ENCHANTING_TABLE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        EnchantmentMenu menu = new EnchantmentMenu(0, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));

        menu.getSlot(0).set(forgedT2Sword());
        for (int row = 0; row < 3; row++) {
            helper.assertTrue(menu.costs[row] == 0, "forged T2 must get no offer in row " + row);
        }

        menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(menu.costs[0] > 0, "vanilla iron sword must get an offer");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void anvilRefusesEnchantedBookOnForgedT2(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Enchantment> sharpness = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AnvilMenu menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.create(level, helper.absolutePos(BlockPos.ZERO)));

        menu.getSlot(0).set(forgedT2Sword());
        menu.getSlot(1).set(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(sharpness, 1)));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "anvil must not enchant a forged T2 item");

        menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
        helper.assertFalse(menu.getSlot(2).getItem().isEmpty(), "anvil must still enchant a vanilla sword");
        helper.succeed();
    }

    private static ItemStack guildBladeCopy(UUID uuid, int runs) {
        ItemStack stack = guildBladeOriginal(uuid);
        BlueprintData data = BlueprintItem.data(stack).orElseThrow();
        stack.set(BFComponents.BLUEPRINT.get(), new BlueprintData(data.instanceId(), data.definitionId(), BlueprintClass.COPY,
                data.tierId(), data.target(), runs, data.materialEfficiency(), data.flux(), data.potency(), data.foundGameTime(),
                data.researcherUuid(), data.researcherName(), data.copierUuid(), data.copierName(),
                data.ownerUuid(), data.ownerName(), data.roll()));
        return stack;
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void laboratoryResearchesMaterialEfficiencyAndKeepsTheOriginal(GameTestHelper helper) {
        withShaft(helper, bureau -> {
            UUID uuid = UUID.randomUUID();
            helper.assertTrue(bureau.getBlueprintSlot().insertItem(0, guildBladeOriginal(uuid), false).isEmpty(), "original must fit");
            helper.assertTrue(bureau.getMaterials().insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false).isEmpty(), "iron must fit");
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            helper.assertTrue(bureau.tryStart(ResearchAxis.MATERIAL, player) == ResearchRefusal.OK, "the ME step must start");
            helper.assertTrue(bureau.isWorking(), "the laboratory is researching");
            helper.assertTrue(bureau.operationTotal() == 100, "duration is 100 game ticks");
            helper.assertTrue(bureau.calculateStressApplied() == 256.0F, "stress is applied while the step runs");
            helper.assertTrue(bureau.getMaterials().getStackInSlot(0).getCount() == 4, "iron stays until the step finishes");
            helper.succeedWhen(() -> {
                BlueprintData data = BlueprintItem.data(bureau.getDocument()).orElse(null);
                helper.assertTrue(data != null && data.materialEfficiency() == 3 && data.flux() == 0 && data.potency() == 0,
                        "ME advances by one step of 3");
                helper.assertTrue(data.isOriginal() && data.runsRemaining() == -1 && data.instanceId().equals(uuid), "the original stays");
                helper.assertTrue(data.researcherUuid().isPresent(), "the player who started the step is recorded");
                helper.assertTrue(bureau.getMaterials().getStackInSlot(0).isEmpty(), "the four iron are spent when the step finishes");
                helper.assertTrue(!bureau.isWorking(), "the laboratory is idle when the step ends");
                helper.assertTrue(bureau.calculateStressApplied() == 0.0F, "stress drops when the step ends");
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void laboratoryResearchesFluxAndPotency(GameTestHelper helper) {
        withShaft(helper, bureau -> {
            UUID uuid = UUID.randomUUID();
            bureau.getBlueprintSlot().insertItem(0, guildBladeOriginal(uuid), false);
            bureau.getMaterials().insertItem(0, new ItemStack(Items.LAPIS_LAZULI, 8), false);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            helper.assertTrue(bureau.tryStart(ResearchAxis.FLUX, player) == ResearchRefusal.OK, "the flux step must start");
            helper.succeedWhen(() -> {
                BlueprintData data = BlueprintItem.data(bureau.getDocument()).orElseThrow();
                helper.assertTrue(data.flux() == 10 && data.materialEfficiency() == 0 && data.potency() == 0, "flux advances by 10");
                helper.assertTrue(bureau.getMaterials().getStackInSlot(0).isEmpty(), "the lapis is spent");
                helper.assertTrue(data.instanceId().equals(uuid) && data.runsRemaining() == -1, "the original is not consumed");
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void laboratoryResearchesPotency(GameTestHelper helper) {
        withShaft(helper, bureau -> {
            bureau.getBlueprintSlot().insertItem(0, guildBladeOriginal(UUID.randomUUID()), false);
            bureau.getMaterials().insertItem(0, new ItemStack(Items.EXPERIENCE_BOTTLE, 4), false);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            helper.assertTrue(bureau.tryStart(ResearchAxis.POTENCY, player) == ResearchRefusal.OK, "the potency step must start");
            helper.succeedWhen(() -> {
                BlueprintData data = BlueprintItem.data(bureau.getDocument()).orElseThrow();
                helper.assertTrue(data.potency() == 1 && data.flux() == 0, "potency advances by 1");
                helper.assertTrue(bureau.getMaterials().getStackInSlot(0).isEmpty(), "the bottles are spent");
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void laboratoryRefusesToResearchACopyOrAShortPayment(GameTestHelper helper) {
        ProjectBureauBlockEntity bureau = placeLab(helper);
        UUID uuid = UUID.randomUUID();
        bureau.getBlueprintSlot().insertItem(0, guildBladeCopy(uuid, 5), false);
        bureau.getMaterials().insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false);
        helper.runAfterDelay(1, () -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            helper.assertTrue(bureau.tryStart(ResearchAxis.MATERIAL, player) == ResearchRefusal.COPY_FORBIDDEN, "a copy is not researched");
            helper.assertTrue(bureau.getMaterials().getStackInSlot(0).getCount() == 4, "a refused step does not take the iron");
            bureau.getBlueprintSlot().setStackInSlot(0, ItemStack.EMPTY);
            helper.assertTrue(bureau.getBlueprintSlot().insertItem(0, guildBladeOriginal(uuid), false).isEmpty(), "original must fit");
            bureau.getMaterials().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
            helper.assertTrue(bureau.tryStart(ResearchAxis.MATERIAL, player) == ResearchRefusal.MISSING_COST, "three iron do not pay for four");
            helper.assertTrue(BlueprintItem.data(bureau.getDocument()).orElseThrow().materialEfficiency() == 0, "ME stays at the start");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void laboratoryDoesNotResearchPastTheCeilingWithoutAShaft(GameTestHelper helper) {
        ProjectBureauBlockEntity bureau = placeLab(helper);
        UUID uuid = UUID.randomUUID();
        ItemStack capped = guildBladeOriginal(uuid);
        BlueprintData data = BlueprintItem.data(capped).orElseThrow();
        capped.set(BFComponents.BLUEPRINT.get(), data.withResearch(30, 80, 3, data.researcherUuid(), data.researcherName()));
        bureau.getBlueprintSlot().insertItem(0, capped, false);
        bureau.getMaterials().insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(bureau.tryStart(ResearchAxis.MATERIAL, player) == ResearchRefusal.AT_CAP, "ME at 30 does not start");
        helper.assertTrue(bureau.tryStart(ResearchAxis.FLUX, player) == ResearchRefusal.AT_CAP, "flux at 80 does not start");
        helper.assertTrue(bureau.tryStart(ResearchAxis.POTENCY, player) == ResearchRefusal.AT_CAP, "potency at 3 does not start");
        helper.assertTrue(bureau.getMaterials().getStackInSlot(0).getCount() == 4, "a ceiling does not take materials");
        ItemStack fresh = guildBladeOriginal(UUID.randomUUID());
        bureau.getBlueprintSlot().setStackInSlot(0, fresh);
        helper.assertTrue(bureau.tryStart(ResearchAxis.MATERIAL, player) == ResearchRefusal.NO_ROTATION, "a laboratory with no shaft does not start");
        helper.assertTrue(bureau.getMaterials().getStackInSlot(0).getCount() == 4, "iron is not spent");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void laboratoryPrintsACopyForDatapackTicksTimesRuns(GameTestHelper helper) {
        withShaft(helper, bureau -> {
        UUID uuid = UUID.randomUUID();
        ItemStack original = guildBladeOriginal(uuid);
        BlueprintData seeded = BlueprintItem.data(original).orElseThrow().withResearch(20, 25, 2, Optional.empty(), Optional.empty());
        original.set(BFComponents.BLUEPRINT.get(), seeded);
        bureau.getBlueprintSlot().insertItem(0, original, false);
        bureau.getMaterials().insertItem(0, new ItemStack(Items.PAPER, 8), false);
        bureau.getMaterials().insertItem(1, new ItemStack(Items.INK_SAC, 2), false);
        BlueprintDefinition definition = BlueprintRegistry.get(GUILD_BLADE).orElseThrow();
        BlueprintDefinition.CopyRules rules = definition.copy().orElseThrow();
        helper.assertTrue(bureau.setCopyRuns(1, ProjectBureauBlockEntity.copyChecksum(rules, 1)), "one run must be quoted");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(bureau.tryCopy(player) == ArchiveRefusal.OK, "printing must start");
        helper.assertTrue(bureau.operationTotal() == 80, "one run lasts 80 game ticks, got " + bureau.operationTotal());
        helper.assertTrue(bureau.isWorking(), "the table is printing");
        helper.succeedWhen(() -> {
            ItemStack printed = ItemStack.EMPTY;
            for (int slot = 0; slot < bureau.getOutput().getSlots(); slot++) {
                ItemStack stack = bureau.getOutput().getStackInSlot(slot);
                if (BlueprintItem.data(stack).isPresent()) {
                    printed = stack;
                }
            }
            BlueprintData copy = BlueprintItem.data(printed).orElse(null);
            helper.assertTrue(copy != null && copy.clazz() == BlueprintClass.COPY && copy.runsRemaining() == 1,
                    "one run was printed");
            helper.assertTrue(!copy.instanceId().equals(uuid), "the copy has its own UUID");
            helper.assertTrue(copy.materialEfficiency() == 10 && copy.flux() == 15 && copy.potency() == 2,
                    "ME and flux take the penalty, potency is copied whole");
            helper.assertTrue(BlueprintItem.data(bureau.getDocument()).map(d -> d.isOriginal() && d.instanceId().equals(uuid)).orElse(false),
                    "the original stays");
            helper.assertTrue(!bureau.isWorking(), "the laboratory is idle when printing ends");
            helper.succeed();
        });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void lineStampsAnIngotAndRejectsAFinishedSword(GameTestHelper helper) {
        BlockPos dockPos = new BlockPos(2, 2, 2);
        helper.setBlock(dockPos, BFBlocks.BLUEPRINT_DOCK.get().defaultBlockState());
        BlueprintDockBlockEntity dock = helper.getBlockEntity(dockPos);
        ItemStack copy = guildBladeCopy(UUID.randomUUID(), 1);
        copy.set(BFComponents.BLUEPRINT.get(), BlueprintItem.data(copy).orElseThrow().withResearch(30, 0, 1, Optional.empty(), Optional.empty()));
        helper.assertTrue(dock.getBlueprintSlot().insertItem(0, copy, false).isEmpty(), "copy must fit the dock");
        dock.getMaterials().insertItem(0, new ItemStack(Items.IRON_INGOT, 8), false);
        ItemStack stamp = new ItemStack(BFItems.BLUEPRINT_STAMP.get());
        stamp.set(BFComponents.STAMP.get(), new StampData(GUILD_BLADE));
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(dockPos);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(!dock.isSwapping(), "changeover has finished");
            helper.assertTrue(LineProduction.tryStamp(level, absolute, new ItemStack(Items.IRON_SWORD), stamp).isEmpty(),
                    "a finished sword is not a line input");
            helper.assertTrue(dock.getMaterials().getStackInSlot(0).getCount() == 8, "a rejected sword does not spend iron");
            var marked = LineProduction.tryStamp(level, absolute, new ItemStack(Items.IRON_INGOT), stamp);
            helper.assertTrue(marked.isPresent(), "an ingot stamps");
            helper.assertTrue(dock.getMaterials().getStackInSlot(0).getCount() == 6, "ME 30 spends 2 of the 4-iron price");
            helper.assertTrue(dock.getDocument().isEmpty(), "the last run of a copy is destroyed");
            ItemStack forged = LineProduction.finish(level, marked.get(), 0.9F);
            helper.assertTrue(isGuildForged(forged), "the roll forges the target");
            helper.assertTrue(forged.get(DataComponents.CUSTOM_NAME) == null, "the sword is not renamed");
            helper.assertTrue(forged.getHoverName().getString().equals(new ItemStack(Items.IRON_SWORD).getHoverName().getString()),
                    "the iron sword keeps its name");
            helper.assertTrue(forged.getMaxDamage() == 316, "durability is 250 × 1.10 × 1.15, got " + forged.getMaxDamage());
            Holder<Enchantment> sharpness = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
            helper.assertTrue(forged.getEnchantmentLevel(sharpness) == 1, "potency 1 writes Sharpness I");
            helper.assertTrue(LineProduction.finish(level, marked.get(), 0.0F).is(Items.IRON_NUGGET), "a low roll is scrap");
            ItemStack original = guildBladeOriginal(UUID.randomUUID());
            helper.assertTrue(dock.getBlueprintSlot().insertItem(0, original, false).isEmpty(), "original must fit");
            helper.runAfterDelay(100, () -> {
                var again = LineProduction.tryStamp(level, absolute, new ItemStack(Items.IRON_INGOT), stamp);
                helper.assertTrue(again.isPresent(), "an original stamps");
                helper.assertTrue(BlueprintItem.data(dock.getDocument()).orElseThrow().isOriginal(), "the original stays in the dock");
                helper.assertTrue(dock.getMaterials().getStackInSlot(0).getCount() == 2, "ME 0 spends the full 4 iron");
                helper.succeed();
            });
        });
    }

    private static boolean isGuildForged(ItemStack stack) {
        ForgedItemData forged = stack.get(BFComponents.FORGED.get());
        return stack.is(Items.IRON_SWORD) && forged != null && forged.blueprintId().equals(GUILD_BLADE) && forged.tierId().equals(TIER2);
    }

    @GameTest(template = EMPTY)
    public static void tierNamesAreLabels(GameTestHelper helper) {
        for (int number = 1; number <= 5; number++) {
            var tier = TierRegistry.get(BlueprintForge.id("tier" + number)).orElse(null);
            helper.assertTrue(tier != null, "tier " + number + " must load");
            helper.assertTrue(tier.display().getContents() instanceof TranslatableContents contents
                            && contents.getKey().equals("tier.blueprintforge.tier" + number),
                    "tier " + number + " uses its label key");
        }
        helper.assertFalse(BlueprintRegistry.get(GUILD_BLADE).orElseThrow().namesOutput(), "the guild blade does not name its output");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void scaledEnchantingFollowsTheTierCurve(GameTestHelper helper) {
        String previous = BFConfig.ENCHANTING_MODE.get();
        BFConfig.ENCHANTING_MODE.set("scaled");
        try {
            helper.assertTrue(BFConfig.enchantingMode() == EnchantPolicy.Mode.SCALED, "mode must be scaled");
            BlockPos table = new BlockPos(2, 1, 2);
            helper.setBlock(table, Blocks.ENCHANTING_TABLE);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            EnchantmentMenu menu = new EnchantmentMenu(0, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
            helper.assertTrue(menu.costs[0] > 0, "a vanilla sword can still be enchanted in scaled");
            menu.getSlot(0).set(forgedT2Sword());
            helper.assertTrue(menu.costs[0] == 0, "a forged T2 sword is not enchanted at the table");

            ServerLevel level = helper.getLevel();
            Holder<Enchantment> sharpness = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
            AnvilMenu anvil = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(level, helper.absolutePos(BlockPos.ZERO)));
            anvil.getSlot(0).set(forgedT2Sword());
            anvil.getSlot(1).set(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(sharpness, 1)));
            helper.assertTrue(anvil.getSlot(2).getItem().isEmpty(), "a forged sword does not take a book");
            anvil.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
            helper.assertFalse(anvil.getSlot(2).getItem().isEmpty(), "a vanilla sword can take a book in scaled");
            ItemStack damaged = new ItemStack(Items.IRON_SWORD);
            damaged.setDamageValue(20);
            anvil.getSlot(0).set(damaged);
            anvil.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
            helper.assertFalse(anvil.getSlot(2).getItem().isEmpty(), "a repair without enchantments still works");
        } finally {
            BFConfig.ENCHANTING_MODE.set(previous);
        }
        helper.succeed();
    }

    private static ItemStack forgedSword(ResourceLocation tier) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(BFComponents.FORGED.get(), new ForgedItemData(GUILD_BLADE, tier, List.of()));
        return sword;
    }

    public static void viewerPagesMatchTheReferencePack(GameTestHelper helper) {
        List<ViewerCatalog.ResearchStep> research = ViewerCatalog.researchSteps();
        ViewerCatalog.ResearchStep me = research.stream()
                .filter(step -> step.id().getPath().equals("research/blueprintforge_guild_blade_me"))
                .findFirst().orElse(null);
        helper.assertTrue(me != null, "guild blade ME step must be listed");
        helper.assertTrue(me.cost().size() == 1 && me.cost().getFirst().is(Items.IRON_INGOT) && me.cost().getFirst().getCount() == 4,
                "ME step costs 4 iron");
        helper.assertTrue(BlueprintItem.data(me.result()).orElseThrow().materialEfficiency() == 3, "the shown step lands on 3");
        ViewerCatalog.ResearchStep flux = research.stream()
                .filter(step -> step.id().getPath().equals("research/blueprintforge_guild_blade_flux"))
                .findFirst().orElse(null);
        helper.assertTrue(flux != null && flux.cost().size() == 1 && flux.cost().getFirst().is(Items.LAPIS_LAZULI) && flux.cost().getFirst().getCount() == 8,
                "flux step costs 8 lapis");

        ViewerCatalog.CopyPrint print = ViewerCatalog.copyPrints().stream()
                .filter(page -> page.id().getPath().equals("copy/blueprintforge_guild_blade"))
                .findFirst().orElse(null);
        helper.assertTrue(print != null, "guild blade copy must be listed");
        helper.assertTrue(print.cost().stream().anyMatch(stack -> stack.is(Items.PAPER) && stack.getCount() == 8), "10 runs cost 8 paper");
        helper.assertTrue(print.cost().stream().anyMatch(stack -> stack.is(Items.INK_SAC) && stack.getCount() == 2), "10 runs cost 2 ink");
        BlueprintData copy = BlueprintItem.data(print.copy()).orElseThrow();
        helper.assertTrue(copy.clazz() == BlueprintClass.COPY && copy.runsRemaining() == 10, "the print is a 10-run copy");
        helper.assertTrue(copy.materialEfficiency() == 0 && copy.flux() == 0 && copy.potency() == 0, "a fresh original prints at the floor");

        List<ViewerCatalog.TierPage> tiers = ViewerCatalog.tiers();
        helper.assertTrue(tiers.size() == 5, "five tier pages");
        ViewerCatalog.TierPage handmade = tiers.stream().filter(page -> page.id().getPath().equals("tier/blueprintforge_tier1")).findFirst().orElseThrow();
        ViewerCatalog.TierPage crafted = tiers.stream().filter(page -> page.id().getPath().equals("tier/blueprintforge_tier2")).findFirst().orElseThrow();
        helper.assertTrue(!handmade.requiresBlueprint() && handmade.enchantLine() == null && handmade.durability().equals("1"),
                "handmade needs no blueprint and the table still enchants it");
        helper.assertTrue(crafted.requiresBlueprint() && crafted.durability().equals("1.1") && crafted.enchantLine() != null,
                "crafted needs a blueprint and the table is not its path");
        helper.assertTrue(ViewerCatalog.productionDocuments().stream().noneMatch(stack ->
                BlueprintItem.data(stack).orElseThrow().clazz() == BlueprintClass.FRAGMENT), "fragments are not slot documents");
        helper.assertFalse(ViewerCatalog.enchantNote().isEmpty(), "restricted mode says the table is not the forged path");
        helper.succeed();
    }
}
