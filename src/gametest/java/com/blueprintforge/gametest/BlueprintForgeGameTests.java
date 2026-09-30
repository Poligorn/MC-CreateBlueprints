package com.blueprintforge.gametest;

import java.util.ArrayList;
import java.util.List;
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
import com.blueprintforge.logic.EfficiencyMath;
import com.blueprintforge.logic.ResearchAxis;
import com.blueprintforge.logic.ResearchRefusal;
import com.blueprintforge.machine.BlueprintArchiveBlockEntity;
import com.blueprintforge.registry.BFBlocks;
import com.blueprintforge.registry.BFComponents;
import com.simibubi.create.AllBlocks;
import com.blueprintforge.event.CreativeIssueHandler;
import com.blueprintforge.machine.BlueprintArchiveBlock;
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

    /**
     * A straight five-block belt along X with the Archive standing over its middle segment, like a Create tunnel.
     * Optionally a creative motor drives the belt pulley, and another one sits on the Archive roof.
     */
    private static BlueprintArchiveBlockEntity archiveOnBelt(GameTestHelper helper, boolean driveBelt, boolean driveArchive) {
        BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        helper.setBlock(BELT_START, shaft);
        helper.setBlock(BELT_END, shaft);
        BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(BELT_START), helper.absolutePos(BELT_END));
        if (driveBelt) {
            helper.setBlock(BELT_START.north(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        }
        helper.setBlock(ARCHIVE, BFBlocks.BLUEPRINT_ARCHIVE.get().defaultBlockState().setValue(BlueprintArchiveBlock.AXIS, Direction.Axis.X));
        if (driveArchive) {
            helper.setBlock(ARCHIVE.above(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        }
        return helper.getBlockEntity(ARCHIVE);
    }

    @GameTest(template = EMPTY)
    public static void archiveStandsOnlyOnAStraightBeltAlongItsAxis(GameTestHelper helper) {
        archiveOnBelt(helper, false, false);
        ServerLevel level = helper.getLevel();
        BlockState alongBelt = BFBlocks.BLUEPRINT_ARCHIVE.get().defaultBlockState().setValue(BlueprintArchiveBlock.AXIS, Direction.Axis.X);
        BlockState acrossBelt = alongBelt.setValue(BlueprintArchiveBlock.AXIS, Direction.Axis.Z);
        helper.assertTrue(alongBelt.canSurvive(level, helper.absolutePos(ARCHIVE)), "archive stands on the belt along its axis");
        helper.assertFalse(acrossBelt.canSurvive(level, helper.absolutePos(ARCHIVE)), "archive must follow the belt axis");
        helper.assertTrue(BlueprintArchiveBlock.beltAxisBelow(level, helper.absolutePos(ARCHIVE)) == Direction.Axis.X, "belt below runs along X");

        helper.setBlock(new BlockPos(2, 1, 4), Blocks.STONE);
        helper.assertFalse(alongBelt.canSurvive(level, helper.absolutePos(new BlockPos(2, 2, 4))), "archive needs a belt below");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void archiveTurnsFromAShaftThroughTheRoof(GameTestHelper helper) {
        archiveOnBelt(helper, false, true);
        helper.succeedWhen(() -> {
            BlueprintArchiveBlockEntity be = helper.getBlockEntity(ARCHIVE);
            helper.assertTrue(be.getSpeed() != 0, "archive must receive rotation from the shaft above, speed=" + be.getSpeed());
        });
    }

    @GameTest(template = EMPTY)
    public static void archiveDoesNotTakeRotationFromTheBelt(GameTestHelper helper) {
        archiveOnBelt(helper, true, false);
        helper.runAfterDelay(20, () -> {
            BeltBlockEntity belt = BeltHelper.getSegmentBE(helper.getLevel(), helper.absolutePos(ARCHIVE.below()));
            helper.assertTrue(belt != null && belt.getSpeed() != 0, "the belt itself must run");
            BlueprintArchiveBlockEntity be = helper.getBlockEntity(ARCHIVE);
            helper.assertTrue(be.getSpeed() == 0, "the Archive is powered only through its roof");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void beltCarriesItemsThroughTheArchive(GameTestHelper helper) {
        archiveOnBelt(helper, true, true);
        // Belt positions count from the controller end; the ingot starts on the segment before the Archive.
        float[] direction = new float[1];
        helper.runAfterDelay(20, () -> {
            BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
            helper.assertTrue(controller != null && controller.getDirectionAwareBeltMovementSpeed() != 0, "belt must be running");
            direction[0] = Math.signum(controller.getDirectionAwareBeltMovementSpeed());
            TransportedItemStack item = new TransportedItemStack(new ItemStack(Items.IRON_INGOT));
            item.beltPosition = direction[0] > 0 ? 0.5F : controller.beltLength - 0.5F;
            controller.getInventory().addItem(item);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(direction[0] != 0, "ingot not placed yet");
            BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
            float archiveSegmentCenter = controller.beltLength / 2.0F;
            List<TransportedItemStack> onBelt = controller.getInventory().getTransportedItems().stream()
                    .filter(stack -> stack.stack.is(Items.IRON_INGOT)).toList();
            boolean pastArchive = onBelt.stream()
                    .anyMatch(stack -> (stack.beltPosition - archiveSegmentCenter) * direction[0] > 1.0F);
            boolean ejectedAtFarEnd = onBelt.isEmpty() && !helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(helper.absolutePos(ARCHIVE)).inflate(6), entity -> entity.getItem().is(Items.IRON_INGOT)).isEmpty();
            helper.assertTrue(pastArchive || ejectedAtFarEnd, "the ingot must travel under the Archive and past it");
        });
    }

    @GameTest(template = EMPTY)
    public static void archiveKeepsOneDocumentAcrossSaveAndDropsItWhenBroken(GameTestHelper helper) {
        BlueprintArchiveBlockEntity be = archiveOnBelt(helper, false, false);
        UUID uuid = UUID.randomUUID();

        helper.assertTrue(be.getBlueprintSlot().insertItem(0, guildBladeOriginal(uuid), false).isEmpty(), "original must fit");
        helper.assertFalse(be.getBlueprintSlot().insertItem(0, guildBladeOriginal(UUID.randomUUID()), false).isEmpty(),
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

    @GameTest(template = EMPTY)
    public static void removingTheBeltBreaksTheArchiveAndDropsTheDocument(GameTestHelper helper) {
        BlueprintArchiveBlockEntity be = archiveOnBelt(helper, false, false);
        UUID uuid = UUID.randomUUID();
        be.getBlueprintSlot().insertItem(0, guildBladeOriginal(uuid), false);

        helper.destroyBlock(ARCHIVE.below());
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(BFBlocks.BLUEPRINT_ARCHIVE.get(), ARCHIVE);
            assertSingleDroppedOriginal(helper, uuid);
        });
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
        BlueprintArchiveBlockEntity be = archiveOnBelt(helper, false, false);

        ItemStack fragment = guildBladeOriginal(UUID.randomUUID());
        BlueprintData data = BlueprintItem.data(fragment).orElseThrow();
        fragment.set(BFComponents.BLUEPRINT.get(), new BlueprintData(data.instanceId(), data.definitionId(), BlueprintClass.FRAGMENT,
                data.tierId(), data.target(), 1, 0, 0, data.researcherUuid(), data.researcherName(), data.copierUuid(),
                data.copierName(), data.ownerUuid(), data.ownerName(), data.roll()));
        ItemStack template = guildBladeOriginal(BlueprintData.UNISSUED);

        ItemStack spentCopy = guildBladeCopy(UUID.randomUUID(), 0);
        for (ItemStack stack : List.of(new ItemStack(BFItems.BLUEPRINT.get()), fragment, template, spentCopy, new ItemStack(Items.PAPER))) {
            helper.assertFalse(be.getBlueprintSlot().insertItem(0, stack, false).isEmpty(), "must reject " + stack);
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
                data.tierId(), data.target(), runs, data.materialEfficiency(), data.timeEfficiency(),
                data.researcherUuid(), data.researcherName(), data.copierUuid(), data.copierName(),
                data.ownerUuid(), data.ownerName(), data.roll()));
        return stack;
    }

    private static void placeOnBelt(GameTestHelper helper, ItemStack stack) {
        BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
        helper.assertTrue(controller != null && controller.getInventory() != null && controller.getDirectionAwareBeltMovementSpeed() != 0,
                "belt must be running before an item is placed");
        float direction = Math.signum(controller.getDirectionAwareBeltMovementSpeed());
        TransportedItemStack transported = new TransportedItemStack(stack);
        transported.beltPosition = direction > 0 ? 0.5F : controller.beltLength - 0.5F;
        controller.getInventory().addItem(transported);
    }

    private static boolean isPastArchive(GameTestHelper helper, Predicate<ItemStack> match) {
        BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
        if (controller == null || controller.getInventory() == null) {
            return false;
        }
        float direction = Math.signum(controller.getDirectionAwareBeltMovementSpeed());
        float center = controller.beltLength / 2.0F;
        boolean onBelt = controller.getInventory().getTransportedItems().stream()
                .anyMatch(stack -> match.test(stack.stack) && (stack.beltPosition - center) * direction > 1.0F);
        boolean ejected = !helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(ARCHIVE)).inflate(8), entity -> match.test(entity.getItem())).isEmpty();
        return onBelt || ejected;
    }

    private static List<ItemStack> stacksAround(GameTestHelper helper, Predicate<ItemStack> match) {
        List<ItemStack> found = new ArrayList<>();
        BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
        if (controller != null && controller.getInventory() != null) {
            for (TransportedItemStack transported : controller.getInventory().getTransportedItems()) {
                if (match.test(transported.stack)) {
                    found.add(transported.stack);
                }
            }
        }
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(ARCHIVE)).inflate(8))) {
            if (match.test(entity.getItem())) {
                found.add(entity.getItem());
            }
        }
        return found;
    }

    private static boolean isGuildForged(ItemStack stack) {
        ForgedItemData forged = stack.get(BFComponents.FORGED.get());
        return stack.is(Items.IRON_SWORD) && forged != null && forged.blueprintId().equals(GUILD_BLADE) && forged.tierId().equals(TIER2);
    }

    @GameTest(template = EMPTY, timeoutTicks = 800)
    public static void archiveRemakesAPlainSwordAndKeepsTheOriginal(GameTestHelper helper) {
        BlueprintArchiveBlockEntity archive = archiveOnBelt(helper, true, true);
        UUID uuid = UUID.randomUUID();
        helper.assertTrue(archive.getBlueprintSlot().insertItem(0, guildBladeOriginal(uuid), false).isEmpty(), "original must fit");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(archive.getSpeed() != 0, "archive must be turning, speed=" + archive.getSpeed());
            placeOnBelt(helper, new ItemStack(Items.IRON_SWORD));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(isPastArchive(helper, BlueprintForgeGameTests::isGuildForged), "forged sword must leave the tunnel");
            helper.assertTrue(stacksAround(helper, stack -> stack.is(Items.IRON_SWORD) && !isGuildForged(stack)).isEmpty(),
                    "the plain sword must be replaced, not duplicated");
            ItemStack forged = stacksAround(helper, BlueprintForgeGameTests::isGuildForged).getFirst();
            helper.assertTrue(forged.getCount() == 1, "one sword in, one sword out");
            helper.assertTrue(forged.getMaxDamage() == 275, "durability is base 250 × tier 1.10, got " + forged.getMaxDamage());
            ItemAttributeModifiers modifiers = forged.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            boolean bonus = modifiers.modifiers().stream().anyMatch(entry ->
                    entry.modifier().id().getPath().startsWith("forged/generic.attack_damage")
                            && Math.abs(entry.modifier().amount() - 0.15D) < 1.0E-9
                            && entry.modifier().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            helper.assertTrue(bonus, "forged sword must carry the blueprint's attack modifier");
            BlueprintData document = BlueprintItem.data(archive.getDocument()).orElse(null);
            helper.assertTrue(document != null && document.isOriginal() && document.runsRemaining() == -1
                    && document.instanceId().equals(uuid), "the original stays in the Archive");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 800)
    public static void archiveSpendsTheLastCopyRun(GameTestHelper helper) {
        BlueprintArchiveBlockEntity archive = archiveOnBelt(helper, true, true);
        UUID uuid = UUID.randomUUID();
        helper.assertTrue(archive.getBlueprintSlot().insertItem(0, guildBladeCopy(uuid, 1), false).isEmpty(), "a copy with a run must fit");
        helper.runAfterDelay(20, () -> placeOnBelt(helper, new ItemStack(Items.IRON_SWORD)));
        helper.succeedWhen(() -> {
            helper.assertTrue(isPastArchive(helper, BlueprintForgeGameTests::isGuildForged), "the copy's last run must still forge the sword");
            helper.assertTrue(archive.getDocument().isEmpty(), "the copy is destroyed on its last run");
            helper.assertTrue(stacksAround(helper, stack -> BlueprintItem.data(stack).map(d -> d.instanceId().equals(uuid)).orElse(false)).isEmpty(),
                    "the spent copy is not dropped");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void archiveDoesNotRemakeWithoutRotation(GameTestHelper helper) {
        BlueprintArchiveBlockEntity archive = archiveOnBelt(helper, true, false);
        UUID uuid = UUID.randomUUID();
        archive.getBlueprintSlot().insertItem(0, guildBladeOriginal(uuid), false);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(archive.getSpeed() == 0, "this archive has no shaft");
            placeOnBelt(helper, new ItemStack(Items.IRON_SWORD));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(isPastArchive(helper, stack -> stack.is(Items.IRON_SWORD) && stack.get(BFComponents.FORGED.get()) == null),
                    "without rotation the sword passes through unchanged");
            helper.assertTrue(stacksAround(helper, BlueprintForgeGameTests::isGuildForged).isEmpty(), "an idle Archive must not forge");
            helper.assertTrue(BlueprintItem.data(archive.getDocument()).map(d -> d.instanceId().equals(uuid) && d.isOriginal()).orElse(false),
                    "the original is untouched");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 800)
    public static void archiveLetsAStackPass(GameTestHelper helper) {
        expectPassThrough(helper, new ItemStack(Items.IRON_SWORD, 2),
                stack -> stack.is(Items.IRON_SWORD) && stack.getCount() == 2 && stack.get(BFComponents.FORGED.get()) == null,
                "a stack of two must pass through whole");
    }

    @GameTest(template = EMPTY, timeoutTicks = 800)
    public static void archiveLetsAnEnchantedSwordPass(GameTestHelper helper) {
        Holder<Enchantment> sharpness = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        ItemStack enchanted = new ItemStack(Items.IRON_SWORD);
        enchanted.enchant(sharpness, 1);
        expectPassThrough(helper, enchanted,
                stack -> stack.is(Items.IRON_SWORD) && stack.isEnchanted() && stack.get(BFComponents.FORGED.get()) == null,
                "an enchanted sword must not be reforged");
    }

    @GameTest(template = EMPTY, timeoutTicks = 800)
    public static void archiveLetsADamagedSwordPass(GameTestHelper helper) {
        ItemStack damaged = new ItemStack(Items.IRON_SWORD);
        damaged.setDamageValue(10);
        helper.assertTrue(damaged.isDamaged() && damaged.getDamageValue() == 10, "the sword must start damaged");
        expectPassThrough(helper, damaged,
                stack -> stack.is(Items.IRON_SWORD) && stack.getDamageValue() == 10 && stack.get(BFComponents.FORGED.get()) == null,
                "a damaged sword must not be repaired by a remake");
    }

    private static void expectPassThrough(GameTestHelper helper, ItemStack input, Predicate<ItemStack> stillInput, String message) {
        BlueprintArchiveBlockEntity archive = archiveOnBelt(helper, true, true);
        UUID uuid = UUID.randomUUID();
        archive.getBlueprintSlot().insertItem(0, guildBladeOriginal(uuid), false);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(archive.getSpeed() != 0, "archive must be turning");
            placeOnBelt(helper, input);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(isPastArchive(helper, stillInput), message + "; nearby=" + describeSwords(helper));
            helper.assertTrue(stacksAround(helper, BlueprintForgeGameTests::isGuildForged).isEmpty(), "unsuitable items are not forged");
            helper.assertTrue(BlueprintItem.data(archive.getDocument()).map(d -> d.runsRemaining() == -1).orElse(false),
                    "passing items do not spend the original");
            helper.succeed();
        });
    }

    private static String describeSwords(GameTestHelper helper) {
        return stacksAround(helper, stack -> stack.is(Items.IRON_SWORD)).stream()
                .map(stack -> "n=" + stack.getCount()
                        + " dmg=" + stack.getDamageValue()
                        + " ench=" + stack.isEnchanted()
                        + " forged=" + (stack.get(BFComponents.FORGED.get()) != null))
                .toList()
                .toString();
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void breakingTheArchiveMidRemakeDoesNotSpendTheCopy(GameTestHelper helper) {
        BlueprintArchiveBlockEntity archive = archiveOnBelt(helper, true, true);
        UUID uuid = UUID.randomUUID();
        archive.getBlueprintSlot().insertItem(0, guildBladeCopy(uuid, 5), false);
        boolean[] broke = {false};
        helper.runAfterDelay(20, () -> placeOnBelt(helper, new ItemStack(Items.IRON_SWORD)));
        helper.succeedWhen(() -> {
            if (!broke[0]) {
                BeltBlockEntity controller = BeltHelper.getControllerBE(helper.getLevel(), helper.absolutePos(BELT_START));
                helper.assertTrue(controller != null && controller.getInventory() != null, "belt not ready");
                boolean heldPlain = controller.getInventory().getTransportedItems().stream()
                        .anyMatch(stack -> stack.locked && stack.stack.is(Items.IRON_SWORD) && stack.stack.get(BFComponents.FORGED.get()) == null);
                helper.assertTrue(heldPlain, "the sword should be held in the tunnel before the break");
                helper.destroyBlock(ARCHIVE);
                broke[0] = true;
            }
            List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, ARCHIVE, 3.0).stream()
                    .filter(entity -> BlueprintItem.data(entity.getItem()).map(d -> d.instanceId().equals(uuid)).orElse(false))
                    .toList();
            helper.assertTrue(drops.size() == 1, "the copy must drop, got " + drops.size());
            BlueprintData dropped = BlueprintItem.data(drops.getFirst().getItem()).orElseThrow();
            helper.assertTrue(dropped.clazz() == BlueprintClass.COPY && dropped.runsRemaining() == 5,
                    "an interrupted remake must not spend a run, runs=" + dropped.runsRemaining());
            helper.assertTrue(stacksAround(helper, BlueprintForgeGameTests::isGuildForged).isEmpty(),
                    "an interrupted remake must not leave a forged sword");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 900)
    public static void archiveResearchesMaterialEfficiencyAndKeepsTheOriginal(GameTestHelper helper) {
        BlueprintArchiveBlockEntity archive = archiveOnBelt(helper, true, true);
        UUID uuid = UUID.randomUUID();
        helper.assertTrue(archive.getBlueprintSlot().insertItem(0, guildBladeOriginal(uuid), false).isEmpty(), "original must fit");
        helper.assertTrue(archive.getMaterials().insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false).isEmpty(), "iron must fit");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(archive.getSpeed() != 0, "archive must be turning, speed=" + archive.getSpeed());
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            helper.assertTrue(archive.tryStart(ResearchAxis.MATERIAL, player) == ResearchRefusal.OK, "the ME step must start");
            helper.assertTrue(archive.calculateStressApplied() == 256.0F, "the step applies the profile stress");
            helper.assertTrue(archive.getOrCreateNetwork().getActualStressOf(archive) > 0.0F, "the network is charged while researching");
            int expected = EfficiencyMath.researchTicks(Math.abs(archive.getSpeed()), 400);
            helper.assertTrue(archive.researchData().get(BlueprintArchiveBlockEntity.DATA_TOTAL) == expected,
                    "duration is the mixer formula, expected " + expected);
        });
        helper.succeedWhen(() -> {
            BlueprintData data = BlueprintItem.data(archive.getDocument()).orElse(null);
            helper.assertTrue(data != null && data.materialEfficiency() == 3 && data.timeEfficiency() == 0,
                    "ME advances by one step of 3, got " + (data == null ? "none" : data.materialEfficiency()));
            helper.assertTrue(data.isOriginal() && data.runsRemaining() == -1 && data.instanceId().equals(uuid), "the original stays");
            helper.assertTrue(data.researcherUuid().isPresent(), "the player who started the step is recorded");
            helper.assertTrue(archive.getMaterials().getStackInSlot(0).isEmpty(), "the four iron are spent when the step finishes");
            helper.assertTrue(archive.calculateStressApplied() == 0.0F, "an idle Archive adds no stress");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 900)
    public static void archiveResearchesTimeEfficiency(GameTestHelper helper) {
        BlueprintArchiveBlockEntity archive = archiveOnBelt(helper, true, true);
        UUID uuid = UUID.randomUUID();
        archive.getBlueprintSlot().insertItem(0, guildBladeOriginal(uuid), false);
        archive.getMaterials().insertItem(0, new ItemStack(Items.REDSTONE, 8), false);
        helper.runAfterDelay(20, () -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            helper.assertTrue(archive.tryStart(ResearchAxis.TIME, player) == ResearchRefusal.OK, "the TE step must start");
        });
        helper.succeedWhen(() -> {
            BlueprintData data = BlueprintItem.data(archive.getDocument()).orElseThrow();
            helper.assertTrue(data.timeEfficiency() == 5 && data.materialEfficiency() == 0, "TE advances by 5 and ME stays");
            helper.assertTrue(archive.getMaterials().getStackInSlot(0).isEmpty(), "the redstone is spent");
            helper.assertTrue(data.instanceId().equals(uuid) && data.runsRemaining() == -1, "the original is not consumed");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void archiveRefusesToResearchACopyOrAShortPayment(GameTestHelper helper) {
        BlueprintArchiveBlockEntity archive = archiveOnBelt(helper, true, true);
        UUID uuid = UUID.randomUUID();
        archive.getBlueprintSlot().insertItem(0, guildBladeCopy(uuid, 5), false);
        archive.getMaterials().insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(archive.getSpeed() != 0, "archive must be turning");
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            helper.assertTrue(archive.tryStart(ResearchAxis.MATERIAL, player) == ResearchRefusal.COPY_FORBIDDEN, "a copy is not researched");
            helper.assertTrue(archive.getMaterials().getStackInSlot(0).getCount() == 4, "a refused step does not take the iron");
            archive.getBlueprintSlot().setStackInSlot(0, ItemStack.EMPTY);
            helper.assertTrue(archive.getBlueprintSlot().insertItem(0, guildBladeOriginal(uuid), false).isEmpty(), "original must fit");
            archive.getMaterials().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
            helper.assertTrue(archive.tryStart(ResearchAxis.MATERIAL, player) == ResearchRefusal.MISSING_COST, "three iron do not pay for four");
            helper.assertTrue(BlueprintItem.data(archive.getDocument()).orElseThrow().materialEfficiency() == 0, "ME stays at the start");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void archiveDoesNotResearchPastTheCeilingOrWithoutRotation(GameTestHelper helper) {
        BlueprintArchiveBlockEntity archive = archiveOnBelt(helper, true, false);
        UUID uuid = UUID.randomUUID();
        ItemStack capped = guildBladeOriginal(uuid);
        BlueprintData data = BlueprintItem.data(capped).orElseThrow();
        capped.set(BFComponents.BLUEPRINT.get(), data.withResearch(30, 40, data.researcherUuid(), data.researcherName()));
        archive.getBlueprintSlot().insertItem(0, capped, false);
        archive.getMaterials().insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(archive.getSpeed() == 0, "this archive has no shaft");
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            helper.assertTrue(archive.tryStart(ResearchAxis.MATERIAL, player) == ResearchRefusal.AT_CAP, "ME at 30 does not start");
            helper.assertTrue(archive.tryStart(ResearchAxis.TIME, player) == ResearchRefusal.AT_CAP, "TE at 40 does not start");
            helper.assertTrue(archive.getMaterials().getStackInSlot(0).getCount() == 4, "a ceiling does not take materials");
            ItemStack fresh = guildBladeOriginal(UUID.randomUUID());
            archive.getBlueprintSlot().setStackInSlot(0, fresh);
            helper.assertTrue(archive.tryStart(ResearchAxis.MATERIAL, player) == ResearchRefusal.NO_ROTATION, "a stopped shaft does not start");
            helper.assertTrue(BlueprintItem.data(archive.getDocument()).orElseThrow().materialEfficiency() == 0, "ME stays 0");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void viewerPagesMatchTheReferencePack(GameTestHelper helper) {
        List<ViewerCatalog.ResearchStep> research = ViewerCatalog.researchSteps();
        ViewerCatalog.ResearchStep me = research.stream()
                .filter(step -> step.id().getPath().equals("research/blueprintforge_guild_blade_me"))
                .findFirst().orElse(null);
        helper.assertTrue(me != null, "guild blade ME step must be listed");
        helper.assertTrue(me.cost().size() == 1 && me.cost().getFirst().is(Items.IRON_INGOT) && me.cost().getFirst().getCount() == 4,
                "ME step costs 4 iron");
        helper.assertTrue(BlueprintItem.data(me.result()).orElseThrow().materialEfficiency() == 3, "the shown step lands on 3");
        ViewerCatalog.ResearchStep te = research.stream()
                .filter(step -> step.id().getPath().equals("research/blueprintforge_guild_blade_te"))
                .findFirst().orElse(null);
        helper.assertTrue(te != null && te.cost().size() == 1 && te.cost().getFirst().is(Items.REDSTONE) && te.cost().getFirst().getCount() == 8,
                "TE step costs 8 redstone");

        ViewerCatalog.CopyPrint print = ViewerCatalog.copyPrints().stream()
                .filter(page -> page.id().getPath().equals("copy/blueprintforge_guild_blade"))
                .findFirst().orElse(null);
        helper.assertTrue(print != null, "guild blade copy must be listed");
        helper.assertTrue(print.cost().stream().anyMatch(stack -> stack.is(Items.PAPER) && stack.getCount() == 8), "10 runs cost 8 paper");
        helper.assertTrue(print.cost().stream().anyMatch(stack -> stack.is(Items.INK_SAC) && stack.getCount() == 2), "10 runs cost 2 ink");
        BlueprintData copy = BlueprintItem.data(print.copy()).orElseThrow();
        helper.assertTrue(copy.clazz() == BlueprintClass.COPY && copy.runsRemaining() == 10, "the print is a 10-run copy");
        helper.assertTrue(copy.materialEfficiency() == 0 && copy.timeEfficiency() == 0, "a fresh original prints at the floor");

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
