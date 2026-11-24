package com.blueprintforge.research;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * Seed and deterministic chain generation (task 4): one seed, tier and version always give one chain. Version 0 builds
 * chains of the tier's operations only, up to 16 steps. Deployer steps with resources are reserved for task 8 and arrive
 * as a new generator version, so existing seeds keep their chains.
 *
 * The random source is SplitMix64. The chain is a list of operation ids; a chain never consists of a single repeated
 * operation when the tier allows at least two.
 */
public final class ResearchChain {
    public static final int GENERATOR_VERSION = 0;

    private ResearchChain() {
    }

    public static List<ResourceLocation> generate(long seed, ResearchTier tier, int generatorVersion) {
        return generate(seed, tier.steps().min(), tier.steps().max(), tier.operations(), generatorVersion);
    }

    /** Pure core, kept free of game classes except the id type so it can be tested with fixed vectors. */
    public static List<ResourceLocation> generate(long seed, int minSteps, int maxSteps, List<ResourceLocation> operations, int generatorVersion) {
        if (generatorVersion != GENERATOR_VERSION) {
            throw new IllegalArgumentException("unknown generator version " + generatorVersion);
        }
        if (operations.isEmpty() || minSteps < 1 || maxSteps < minSteps) {
            return List.of();
        }
        SplitMix64 random = new SplitMix64(seed);
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
