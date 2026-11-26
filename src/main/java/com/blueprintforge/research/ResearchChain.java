package com.blueprintforge.research;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * Seed and deterministic chain generation: one seed, tier and version always give one chain.
 * Version 0 (task 4) builds chains of the tier's press and blast operations only. Version 1 (task 8) builds the very same
 * press/blast part from the same random stream and then adds the tier's deployer steps with resources on top of it, so
 * seeds of version 0 keep their chains. The chain is never longer than {@link ResearchTier#MAX_STEPS} (decision D5).
 *
 * The random source is SplitMix64. The press/blast part never consists of a single repeated operation when the tier
 * allows at least two.
 *
 * TODO(egor): the deployer counts and the pool of the tiers are a balance draft, not canon (task 8, open question 6).
 */
public final class ResearchChain {
    public static final int GENERATOR_VERSION = 1;
    /** The first generator, press and blast only. Kept so old blueprints keep their chains. */
    public static final int VERSION_PRESS_BLAST = 0;

    private ResearchChain() {
    }

    /** Operation ids of the chain, one per step; a deployer step shows as {@link ChainStep#DEPLOY}. */
    public static List<ResourceLocation> generate(long seed, ResearchTier tier, int generatorVersion) {
        return steps(seed, tier, generatorVersion).stream().map(ChainStep::operation).toList();
    }

    /** The full chain of a tier, with the resources of the deployer steps. */
    public static List<ChainStep> steps(long seed, ResearchTier tier, int generatorVersion) {
        return steps(seed, tier.steps().min(), tier.steps().max(), tier.operations(),
                tier.attemptCost().deployers(), tier.attemptCost().pool(), generatorVersion);
    }

    /** Press and blast part only, the version 0 core. Kept free of game classes except the id type for fixed vectors. */
    public static List<ResourceLocation> generate(long seed, int minSteps, int maxSteps, List<ResourceLocation> operations, int generatorVersion) {
        if (generatorVersion != VERSION_PRESS_BLAST) {
            throw new IllegalArgumentException("not a version 0 chain: generator version " + generatorVersion);
        }
        return base(new SplitMix64(seed), minSteps, maxSteps, operations);
    }

    /** Pure core of both versions. Version 0 ignores the deployer parameters. */
    public static List<ChainStep> steps(long seed, int minSteps, int maxSteps, List<ResourceLocation> operations,
                                        IntRange deployers, List<ResourceEntry> pool, int generatorVersion) {
        if (generatorVersion != VERSION_PRESS_BLAST && generatorVersion != GENERATOR_VERSION) {
            throw new IllegalArgumentException("unknown generator version " + generatorVersion);
        }
        SplitMix64 random = new SplitMix64(seed);
        List<ChainStep> chain = new ArrayList<>();
        for (ResourceLocation operation : base(random, minSteps, maxSteps, operations)) {
            chain.add(ChainStep.of(operation));
        }
        if (generatorVersion == VERSION_PRESS_BLAST || chain.isEmpty() || pool.isEmpty() || deployers.max() < 1) {
            return List.copyOf(chain);
        }
        // Draw the whole deployer set first, then cut it to what still fits under the limit of 16, last drawn goes first.
        int wanted = deployers.min() + random.nextInt(deployers.max() - deployers.min() + 1);
        List<ChainStep> feeds = new ArrayList<>(wanted);
        for (int i = 0; i < wanted; i++) {
            ResourceEntry entry = pool.get(random.nextInt(pool.size()));
            int count = entry.count().min() + random.nextInt(entry.count().max() - entry.count().min() + 1);
            feeds.add(ChainStep.deploy(entry.item(), count));
        }
        int room = Math.max(0, ResearchTier.MAX_STEPS - chain.size());
        for (ChainStep feed : feeds.subList(0, Math.min(room, feeds.size()))) {
            chain.add(random.nextInt(chain.size() + 1), feed);
        }
        return List.copyOf(chain);
    }

    private static List<ResourceLocation> base(SplitMix64 random, int minSteps, int maxSteps, List<ResourceLocation> operations) {
        if (operations.isEmpty() || minSteps < 1 || maxSteps < minSteps) {
            return List.of();
        }
        int length = minSteps + random.nextInt(maxSteps - minSteps + 1);
        int[] picks = new int[length];
        boolean allSame = true;
        for (int i = 0; i < length; i++) {
            picks[i] = random.nextInt(operations.size());
            allSame &= picks[i] == picks[0];
        }
        if (allSame && length >= 2 && operations.size() >= 2) {
            int position = random.nextInt(length);
            picks[position] = (picks[position] + 1) % operations.size();
        }
        List<ResourceLocation> chain = new ArrayList<>(length);
        for (int pick : picks) {
            chain.add(operations.get(pick));
        }
        return List.copyOf(chain);
    }

    /** SplitMix64 as published by Vigna; the same constants as the reference implementation. */
    static final class SplitMix64 {
        private long state;

        SplitMix64(long seed) {
            this.state = seed;
        }

        long next() {
            state += 0x9E3779B97F4A7C15L;
            long z = state;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        /** A value in {@code [0, bound)}, by unsigned remainder, which is what the reference vectors use. */
        int nextInt(int bound) {
            return (int) Long.remainderUnsigned(next(), bound);
        }
    }
}
