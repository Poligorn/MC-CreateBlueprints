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

    @GameTest(template = EMPTY)
    public static void archiveTurnsOnCreateNetwork(GameTestHelper helper) {
        BlockPos motor = new BlockPos(2, 1, 2);
        BlockPos archive = motor.above();
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
        helper.setBlock(archive, BFBlocks.BLUEPRINT_ARCHIVE.get());
        helper.succeedWhen(() -> {
            BlueprintArchiveBlockEntity be = helper.getBlockEntity(archive);
            helper.assertTrue(be.getSpeed() != 0, "archive must receive rotation from the shaft below, speed=" + be.getSpeed());
        });
    }

    @GameTest(template = EMPTY)
    public static void archiveIgnoresRotationFromTheSide(GameTestHelper helper) {
        BlockPos archive = new BlockPos(2, 1, 2);
        helper.setBlock(archive.east(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST));
        helper.setBlock(archive, BFBlocks.BLUEPRINT_ARCHIVE.get());
        helper.runAfterDelay(10, () -> {
            BlueprintArchiveBlockEntity be = helper.getBlockEntity(archive);
            helper.assertTrue(be.getSpeed() == 0, "archive only takes a shaft from below");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void archiveKeepsOneDocumentAcrossSaveAndDropsItWhenBroken(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BFBlocks.BLUEPRINT_ARCHIVE.get());
        BlueprintArchiveBlockEntity be = helper.getBlockEntity(pos);
        UUID uuid = UUID.randomUUID();

        helper.assertTrue(be.getBlueprintSlot().insertItem(0, guildBladeOriginal(uuid), false).isEmpty(), "original must fit");
        helper.assertFalse(be.getBlueprintSlot().insertItem(0, guildBladeOriginal(UUID.randomUUID()), false).isEmpty(),
                "a second document must not fit");

        ServerLevel level = helper.getLevel();
        CompoundTag saved = be.saveWithFullMetadata(level.registryAccess());
        BlockState state = level.getBlockState(helper.absolutePos(pos));
        BlueprintArchiveBlockEntity reloaded = new BlueprintArchiveBlockEntity(helper.absolutePos(pos), state);
        reloaded.loadWithComponents(saved, level.registryAccess());
        helper.assertTrue(BlueprintItem.data(reloaded.getDocument()).map(BlueprintData::instanceId).filter(uuid::equals).isPresent(),
                "document must survive save/load with its UUID");

        helper.destroyBlock(pos);
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, pos, 2.0).stream()
                .filter(entity -> BlueprintItem.data(entity.getItem()).map(d -> d.instanceId().equals(uuid)).orElse(false))
                .toList();
        helper.assertTrue(drops.size() == 1, "exactly one dropped original expected, got " + drops.size());
        helper.assertTrue(BlueprintItem.data(drops.getFirst().getItem()).map(BlueprintData::isOriginal).orElse(false), "drop is the original");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void archiveRejectsBlanksFragmentsAndOtherItems(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BFBlocks.BLUEPRINT_ARCHIVE.get());
        BlueprintArchiveBlockEntity be = helper.getBlockEntity(pos);

        ItemStack fragment = guildBladeOriginal(UUID.randomUUID());
        BlueprintData data = BlueprintItem.data(fragment).orElseThrow();
        fragment.set(BFComponents.BLUEPRINT.get(), new BlueprintData(data.instanceId(), data.definitionId(), BlueprintClass.FRAGMENT,
                data.tierId(), data.target(), 1, 0, 0, data.researcherUuid(), data.researcherName(), data.copierUuid(),
                data.copierName(), data.roll()));

        for (ItemStack stack : List.of(new ItemStack(com.blueprintforge.registry.BFItems.BLUEPRINT.get()), fragment, new ItemStack(Items.PAPER))) {
            helper.assertFalse(be.getBlueprintSlot().insertItem(0, stack, false).isEmpty(), "must reject " + stack);
        }
        helper.assertTrue(be.getDocument().isEmpty(), "slot stays empty");
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
