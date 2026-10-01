package com.blueprintforge.logic;

import net.minecraft.util.Mth;

/**
 * How many game ticks one Create {@code processingTime} budget takes on a shaft. Same formula as
 * {@code MechanicalMixerBlockEntity} in Create 6.0.10. Research uses {@code time_per_step_ticks}.
 * One printed copy run uses {@code copy_time_per_run_ticks}; the bureau multiplies that by the run count.
 * The {@code 512 / speed} ratio is clamped to at least 1 so {@code log2} stays defined above 512 RPM.
 */
public final class RemakeMath {
    private RemakeMath() {
    }

    /**
     * @param absoluteSpeed shaft speed in RPM, already absolute
     * @param processingTime datapack time in Create {@code processingTime} units
     * @return game ticks for that budget, or {@code 0} when the shaft is stopped or the time is not positive
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
