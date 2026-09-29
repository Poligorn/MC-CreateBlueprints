package com.blueprintforge.gametest;

import java.util.List;
import java.util.UUID;

import com.blueprintforge.BFConfig;
import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDataLoader;
import com.blueprintforge.data.BlueprintDefinition;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.ForgedItemData;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.item.BlueprintItem;
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
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

        for (ItemStack stack : List.of(new ItemStack(BFItems.BLUEPRINT.get()), fragment, template, new ItemStack(Items.PAPER))) {
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
}
