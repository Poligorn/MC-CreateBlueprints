package com.blueprintforge.logic;

import java.util.OptionalInt;

/**
 * Research step and duration. Production consumption and copy pricing stay out of this class until those
 * roadmap items land. Research duration uses the same Create mixer countdown as a belt remake: the datapack
 * field is named {@code time_per_step_ticks}, but it is in Create {@code processingTime} units, and TE does
 * not enter the formula. A stopped shaft contributes no ticks.
 */
public final class EfficiencyMath {
    private EfficiencyMath() {
    }

    /**
     * Next efficiency value after one step. Empty when the value is already at {@code max} or the step cannot move.
     * A step that would pass {@code max} stops on {@code max}.
     */
    public static OptionalInt nextStep(int current, int max, int step) {
        if (step <= 0 || current >= max) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Math.min(max, current + step));
    }

    /** Game ticks of a research step at {@code absoluteSpeed} RPM. Zero when the shaft is stopped. */
    public static int researchTicks(float absoluteSpeed, int timePerStepTicks) {
        return RemakeMath.processingTicks(absoluteSpeed, timePerStepTicks);
    }
}
