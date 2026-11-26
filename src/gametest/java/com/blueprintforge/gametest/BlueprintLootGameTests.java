package com.blueprintforge.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.blueprintforge.BFConfig;
import com.blueprintforge.BlueprintForge;
import com.blueprintforge.loot.BlueprintLoot;
import com.blueprintforge.loot.BlueprintLootModifier;
import com.blueprintforge.loot.BlueprintSource;
import com.blueprintforge.loot.BlueprintSourceLoader;
import com.blueprintforge.loot.BlueprintSourceRegistry;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.registry.BFItems;
import com.blueprintforge.research.ResearchBlueprint;
import com.blueprintforge.research.ResearchChain;
import com.blueprintforge.research.ResearchRegistry;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Blueprint loot: found in chest tables through the global loot modifier, never from mobs, off at multiplier 0, T1-T3 only. */
@GameTestHolder(BlueprintForge.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlueprintLootGameTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation DUNGEON = ResourceLocation.withDefaultNamespace("chests/simple_dungeon");
    private static final ResourceLocation ANCIENT_CITY = ResourceLocation.withDefaultNamespace("chests/ancient_city");

    private BlueprintLootGameTests() {
    }

    private static void assertValidLootBlueprint(GameTestHelper helper, ItemStack stack) {
        helper.assertTrue(stack.is(BFItems.RESEARCH_BLUEPRINT.get()), "only blueprints are added");
        ResearchBlueprint data = stack.get(BFComponents.RESEARCH_BLUEPRINT.get());
        helper.assertTrue(data != null, "the blueprint has its data");
        helper.assertTrue(data.seed().isPresent(), "the seed is assigned when the loot is generated");
        helper.assertTrue(!data.copy(), "loot gives an original, not a copy");
        helper.assertTrue(data.generatorVersion() == ResearchChain.GENERATOR_VERSION && data.generatorVersion() == 1, "the current generator version");
        var tier = ResearchRegistry.tier(data.tier());
        helper.assertTrue(tier.isPresent(), "the tier is loaded: " + data.tier());
        helper.assertTrue(tier.get().spriteTier() >= 1 && tier.get().spriteTier() <= 3, "tier is T1-T3, got " + data.tier());
        var target = ResearchRegistry.target(data.target());
        helper.assertTrue(target.isPresent(), "the target is loaded: " + data.target());
        helper.assertTrue(target.get().tier().equals(data.tier()), "the target belongs to the tier of the blueprint");
        helper.assertTrue(target.get().item().equals(data.item()), "the item matches the target");
    }

    private static BlueprintSource source(ResourceLocation table, float chance, Map<ResourceLocation, Integer> weights) {
        return new BlueprintSource(BlueprintSource.Type.CHEST, List.of(table), chance, weights);
    }

    /** Installs sources for one test and puts the datapack ones back; a test body runs on the server thread in one go. */
    private static void withSources(Map<ResourceLocation, BlueprintSource> sources, Runnable body) {
        var before = BlueprintSourceRegistry.all();
        try {
            BlueprintSourceRegistry.replace(sources);
            body.run();
        } finally {
            // The loaded sources are rebuilt from the datapack files the same way the loader does it.
            BlueprintSourceRegistry.replace(unindex(before));
        }
    }

    private static Map<ResourceLocation, BlueprintSource> unindex(Map<ResourceLocation, List<BlueprintSource>> index) {
        Map<ResourceLocation, BlueprintSource> out = new java.util.LinkedHashMap<>();
        int n = 0;
        Set<BlueprintSource> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (List<BlueprintSource> list : index.values()) {
            for (BlueprintSource source : list) {
                if (seen.add(source)) {
                    out.put(ResourceLocation.fromNamespaceAndPath("restored", String.format("%04d", n++)), source);
                }
            }
        }
        return out;
    }

    private static LootContext context(GameTestHelper helper, ResourceLocation table, boolean fromKill) {
        LootParams.Builder params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        if (!fromKill) {
            return new LootContext.Builder(params.create(LootContextParamSets.CHEST)).withQueriedLootTableId(table).create(Optional.empty());
        }
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 1);
        params.withParameter(LootContextParams.THIS_ENTITY, zombie)
                .withParameter(LootContextParams.DAMAGE_SOURCE, helper.getLevel().damageSources().generic());
        return new LootContext.Builder(params.create(LootContextParamSets.ENTITY)).withQueriedLootTableId(table).create(Optional.empty());
    }

    @GameTest(template = EMPTY)
    public static void aDungeonChestSometimesHoldsAValidSeededBlueprint(GameTestHelper helper) {
        helper.assertTrue(BlueprintSourceLoader.lastErrors().isEmpty(), "shipped sources load cleanly: " + BlueprintSourceLoader.lastErrors());
        helper.assertTrue(!BlueprintSourceRegistry.sourcesFor(DUNGEON).isEmpty(), "the datapack lists the simple dungeon");

        // The real path: a chest with a vanilla table, opened through the loot table, so the global modifier is what adds the blueprint.
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int chests = 400;
        int found = 0;
        for (int i = 0; i < chests; i++) {
            chest.clearContent();
            chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, DUNGEON), helper.getLevel().random.nextLong());
            chest.unpackLootTable(player);
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (stack.is(BFItems.RESEARCH_BLUEPRINT.get())) {
                    assertValidLootBlueprint(helper, stack);
                    found++;
                }
            }
        }
        // 10 percent per chest at multiplier 1: missing it 400 times in a row is about 5e-19.
        helper.assertTrue(found > 0, "a blueprint turned up in " + chests + " chests, found " + found);
        helper.assertTrue(found < chests / 2, "but not in most of them, found " + found + " of " + chests);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void lootBlueprintsStayWithinT1ToT3AndHaveValidTargetsAtEveryPlace(GameTestHelper helper) {
        Set<ResourceLocation> tiers = new HashSet<>();
        for (ResourceLocation table : List.of(DUNGEON, ANCIENT_CITY, ResourceLocation.withDefaultNamespace("chests/village/village_weaponsmith"))) {
            for (int i = 0; i < 600; i++) {
                for (ItemStack stack : BlueprintLoot.roll(table, false, 1000.0, helper.getLevel().random)) {
                    assertValidLootBlueprint(helper, stack);
                    tiers.add(stack.get(BFComponents.RESEARCH_BLUEPRINT.get()).tier());
                    if (table.getPath().startsWith("chests/village/")) {
                        helper.assertTrue(stack.get(BFComponents.RESEARCH_BLUEPRINT.get()).tier().getPath().equals("t1"), "simple chests give T1 only");
                    }
                }
            }
        }
        helper.assertTrue(tiers.equals(Set.of(BlueprintForge.id("t1"), BlueprintForge.id("t2"), BlueprintForge.id("t3"))), "all three tiers can turn up, got " + tiers);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void tiersAboveT3AreNeverDroppedEvenIfASourceListsThem(GameTestHelper helper) {
        ResourceLocation table = ResourceLocation.fromNamespaceAndPath("blueprintforge_test", "chests/high");
        withSources(Map.of(BlueprintForge.id("high"), source(table, 1.0F, Map.of(BlueprintForge.id("t4"), 100, BlueprintForge.id("t5"), 100))), () -> {
            for (int i = 0; i < 200; i++) {
                helper.assertTrue(BlueprintLoot.roll(table, false, 1.0, helper.getLevel().random).isEmpty(), "a T4/T5-only source drops nothing");
            }
        });
        withSources(Map.of(BlueprintForge.id("mixed"), source(table, 1.0F, Map.of(BlueprintForge.id("t2"), 1, BlueprintForge.id("t4"), 1000, BlueprintForge.id("t5"), 1000))), () -> {
            for (int i = 0; i < 200; i++) {
                List<ItemStack> rolled = BlueprintLoot.roll(table, false, 1.0, helper.getLevel().random);
                helper.assertTrue(rolled.size() == 1, "the T2 entry still drops");
                helper.assertTrue(rolled.get(0).get(BFComponents.RESEARCH_BLUEPRINT.get()).tier().equals(BlueprintForge.id("t2")), "only T2 is picked");
            }
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void multiplierZeroTurnsBlueprintLootOff(GameTestHelper helper) {
        // Rolls: at 0 nothing, even for a source with chance 1; the same source drops at 1.
        withSources(Map.of(BlueprintForge.id("sure"), source(DUNGEON, 1.0F, Map.of(BlueprintForge.id("t1"), 1))), () -> {
            for (int i = 0; i < 300; i++) {
                helper.assertTrue(BlueprintLoot.roll(DUNGEON, false, 0.0, helper.getLevel().random).isEmpty(), "multiplier 0 injects nothing");
            }
            helper.assertTrue(BlueprintLoot.roll(DUNGEON, false, 1.0, helper.getLevel().random).size() == 1, "multiplier 1 injects");

            // Through the modifier itself: it reads the configured multiplier, the default being 1.
            helper.assertTrue(BFConfig.rarityMultiplier() == BFConfig.DEFAULT_RARITY_MULTIPLIER, "the default multiplier is 1.0");
            ObjectArrayList<ItemStack> loot = new ObjectArrayList<>();
            new BlueprintLootModifier(new LootItemCondition[0]).apply(loot, context(helper, DUNGEON, false));
            helper.assertTrue(loot.size() == 1, "the modifier injects at the default multiplier");
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void mobsNeverDropBlueprints(GameTestHelper helper) {
        // Even if a table of a mob were wrongly listed as a source (the loader refuses it), a kill's loot gets nothing.
        ResourceLocation zombie = ResourceLocation.withDefaultNamespace("entities/zombie");
        withSources(Map.of(BlueprintForge.id("zombies"), source(zombie, 1.0F, Map.of(BlueprintForge.id("t1"), 1))), () -> {
            for (int i = 0; i < 100; i++) {
                ObjectArrayList<ItemStack> loot = new ObjectArrayList<>();
                new BlueprintLootModifier(new LootItemCondition[0]).apply(loot, context(helper, zombie, true));
                helper.assertTrue(loot.stream().noneMatch(stack -> stack.is(BFItems.RESEARCH_BLUEPRINT.get())), "no blueprint in mob loot");
            }
            helper.assertTrue(BlueprintLoot.roll(zombie, true, 1000.0, helper.getLevel().random).isEmpty(), "mob loot is refused by the rules too");
        });
        // And with the shipped sources a real zombie kills drops none.
        for (int i = 0; i < 100; i++) {
            LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("entities/zombie")));
            Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 1);
            LootParams params = new LootParams.Builder(helper.getLevel())
                    .withParameter(LootContextParams.ORIGIN, victim.position())
                    .withParameter(LootContextParams.THIS_ENTITY, victim)
                    .withParameter(LootContextParams.DAMAGE_SOURCE, helper.getLevel().damageSources().generic())
                    .create(LootContextParamSets.ENTITY);
            helper.assertTrue(table.getRandomItems(params).stream().noneMatch(stack -> stack.is(BFItems.RESEARCH_BLUEPRINT.get())), "zombies drop no blueprints");
        }
        helper.succeed();
    }
}
