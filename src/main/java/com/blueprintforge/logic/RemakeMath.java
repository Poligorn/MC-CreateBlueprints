package com.blueprintforge.logic;

import net.minecraft.util.Mth;

/**
 * How many game ticks a belt remake takes. Same formula as {@code MechanicalMixerBlockEntity} in Create
 * 6.0.10: {@code processingTime} is the recipe's {@code processingTime} (100 is one reference cycle),
 * and the network RPM turns that into a countdown. The {@code 512 / speed} ratio is clamped to at least 1
 * so {@code log2} stays defined above 512 RPM, where the mixer formula would see zero.
 */
public final class RemakeMath {
    private RemakeMath() {
    }

    /**
     * @param absoluteSpeed shaft speed in RPM, already absolute
     * @param processingTime datapack {@code remake.processing_time}
     * @return game ticks until the remake completes, or {@code 0} when the shaft is stopped or the time is not positive
     */
    public static int processingTicks(float absoluteSpeed, int processingTime) {
        if (!(absoluteSpeed > 0.0F) || processingTime <= 0) {
            return 0;
        }
        int ratio = Math.max(1, (int) (512.0F / absoluteSpeed));
        float recipeSpeed = processingTime / 100.0F;
        return Math.max(Mth.log2(ratio) * Mth.ceil(recipeSpeed * 15.0F) + 1, 1);
    }
}
