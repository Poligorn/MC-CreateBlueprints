package com.blueprintforge.research;

import com.blueprintforge.registry.BFComponents;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * The seed is lazy: a blueprint gets its own the first time it lands in a player's inventory, whether it was picked up,
 * given by a command or taken from the creative tab. The terminal does the same as a fallback.
 */
public final class SeedAssigner {
    private SeedAssigner() {
    }

    /** Gives a seed to the stack if it is a blueprint without one. Originals and copies alike. */
    public static boolean assignIfAbsent(ItemStack stack, RandomSource random) {
        ResearchBlueprint blueprint = stack.get(BFComponents.RESEARCH_BLUEPRINT.get());
        if (blueprint == null || blueprint.seed().isPresent()) {
            return false;
        }
        stack.set(BFComponents.RESEARCH_BLUEPRINT.get(), blueprint.withSeed(random.nextLong()));
        return true;
    }

    /** @return how many blueprints got a seed */
    public static int assignAll(Iterable<ItemStack> stacks, RandomSource random) {
        int assigned = 0;
        for (ItemStack stack : stacks) {
            if (assignIfAbsent(stack, random)) {
                assigned++;
            }
        }
        return assigned;
    }
}
