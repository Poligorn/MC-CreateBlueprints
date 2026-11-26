package com.blueprintforge.compat.createapi;

import java.util.List;
import java.util.Optional;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.registry.BFComponents;
import com.blueprintforge.research.ChainStep;
import com.blueprintforge.research.ResearchChain;
import com.blueprintforge.research.ResearchRegistry;
import com.blueprintforge.research.ResearchRun;
import com.blueprintforge.research.ResearchTier;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerRecipeSearchEvent;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipeParams;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.IEventBus;

/**
 * Deployer hook without a mixin and without a registered recipe. Create asks {@link DeployerRecipeSearchEvent} for a
 * recipe before it uses a deployer on a belt item; for a workpiece with something in the deployer's hand this hook
 * answers with a throwaway {@link DeployerApplicationRecipe} that is never registered or sent anywhere.
 *
 * What the deployer feeds (task 8, open question 42, TODO(egor): temporary answer, not canon). The resource comes from
 * the deployer itself, the stack in its hand, which a mechanical arm or a player fills. When the step fires:
 * <ul>
 * <li>if the chain's next step is a deployer step and the hand holds that item in at least the asked count, the step
 * takes exactly that count and the log records the step's own key;</li>
 * <li>otherwise one item of whatever is in the hand is taken (what Create takes anyway) and the log records
 * {@code deploy:<held item>*1}, which never matches the chain unless the chain really asked for exactly one of it.</li>
 * </ul>
 * Either way the resource is gone for good, whatever the final percent is. Nothing is refunded (open question 6).
 */
public final class SpikeDeployerHook {
    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(BlueprintForge.MOD_ID, "deployer_feed");
    private static final int PRIORITY = 1000;

    private SpikeDeployerHook() {
    }

    static void register(IEventBus gameBus) {
        gameBus.addListener(DeployerRecipeSearchEvent.class, SpikeDeployerHook::onSearch);
    }

    private static void onSearch(DeployerRecipeSearchEvent event) {
        ItemStack belt = event.getInventory().getItem(0);
        ItemStack held = event.getInventory().getItem(1);
        if (!SpikeLine.isWorkpiece(belt) || held.isEmpty() || !event.shouldAddRecipeWithPriority(PRIORITY)) {
            return;
        }
        DeployerBlockEntity deployer = event.getBlockEntity();
        FeedDecision decision = decide(belt, held);
        event.addRecipe(() -> Optional.of(new RecipeHolder<>(RECIPE_ID, new FeedRecipe(deployer, belt.copy(), held.copy(), decision))), PRIORITY);
    }

    /** What a deployer does to this workpiece with this hand: the key to record and how many items to take. */
    record FeedDecision(String key, int take) {
    }

    /** Pure rule of the feed, public for tests. {@code expected} is the workpiece's chain, null when it has no known one. */
    public static FeedDecision decide(ItemStack workpiece, ItemStack held) {
        ResourceLocation heldId = BuiltInRegistries.ITEM.getKey(held.getItem());
        ChainStep next = nextStep(workpiece);
        if (next != null && next.isDeploy() && next.item().equals(heldId) && held.getCount() >= next.count()) {
            return new FeedDecision(next.key(), next.count());
        }
        return new FeedDecision(ChainStep.deployKey(heldId, 1), 1);
    }

    private static ChainStep nextStep(ItemStack workpiece) {
        ResearchRun run = workpiece.get(BFComponents.RESEARCH_RUN.get());
        if (run == null) {
            return null;
        }
        ResearchTier tier = ResearchRegistry.tier(run.tier()).orElse(null);
        if (tier == null) {
            return null;
        }
        try {
            List<ChainStep> chain = ResearchChain.steps(run.seed(), tier, run.generatorVersion());
            int position = SpikeLine.operationsOf(workpiece).size();
            return position < chain.size() ? chain.get(position) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A recipe that exists only for one deployer activation. */
    private static final class FeedRecipe extends DeployerApplicationRecipe {
        private final DeployerBlockEntity deployer;
        private final FeedDecision decision;
        private boolean taken;

        FeedRecipe(DeployerBlockEntity deployer, ItemStack workpiece, ItemStack held, FeedDecision decision) {
            super(params(SpikeLine.withOperation(workpiece, decision.key()), held));
            this.deployer = deployer;
            this.decision = decision;
        }

        /** The result is already stamped (see {@link FeedParams}); rolling it is where the rest of the count is taken. */
        @Override
        public List<ItemStack> rollResults(RandomSource random) {
            takeTheRest();
            return super.rollResults(random);
        }

        @Override
        public List<ItemStack> rollResults(List<ProcessingOutput> rollableResults, RandomSource random) {
            takeTheRest();
            return super.rollResults(rollableResults, random);
        }

        /** Create takes one item of the hand itself; the rest of the step's count is taken here, once per activation. */
        private void takeTheRest() {
            if (!taken && decision.take() > 1) {
                deployer.getPlayer().getMainHandItem().shrink(decision.take() - 1);
            }
            taken = true;
        }
    }

    private static final class FeedParams extends ItemApplicationRecipeParams {
        /** {@code stamped} is the workpiece with this step already in its log: the recipe's only output. */
        FeedParams(ItemStack stamped, ItemStack held) {
            ingredients.add(Ingredient.of(stamped.getItem()));
            ingredients.add(Ingredient.of(held.getItem()));
            results.add(new ProcessingOutput(stamped, 1.0F));
        }
    }

    private static ItemApplicationRecipeParams params(ItemStack stamped, ItemStack held) {
        return new FeedParams(stamped, held);
    }
}
