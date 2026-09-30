package com.blueprintforge.logic;

import java.util.OptionalInt;

/**
 * Research step, copy price, and production ME/TE. Ancient rolls stay out of this class.
 * Research and belt-remake duration use the Create mixer countdown: the datapack field is in Create
 * {@code processingTime} units, and TE does not enter that formula. Production time is different:
 * TE scales the recipe's own {@code processingTime} before Create applies shaft speed.
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

    /**
     * Superlinear copy price for one ingredient line. {@code runs} must already be clamped to {@code 1…maxRuns}.
     * At {@code runs == defaultRuns} the multiplier is 1.
     */
    public static int copyCost(int baseCount, int runs, int defaultRuns, double costScaling) {
        if (baseCount <= 0 || runs <= 0 || defaultRuns <= 0) {
            return 0;
        }
        double scaled = baseCount * Math.pow(runs / (double) defaultRuns, costScaling);
        return (int) Math.ceil(scaled - 1.0E-9);
    }

    public static int clampRuns(int runs, int maxRuns) {
        return Math.max(1, Math.min(runs, Math.max(1, maxRuns)));
    }

    /** Materials consumed from a recipe line. Zero base stays zero. Otherwise at least one while ME is below 100. */
    public static int consumed(int base, int materialEfficiency) {
        if (base <= 0) {
            return 0;
        }
        int me = Math.max(0, Math.min(100, materialEfficiency));
        return Math.max(1, (int) Math.floor(base * (100.0 - me) / 100.0));
    }

    /** Recipe processing time after TE, in the same units as the recipe's {@code processingTime}. */
    public static int productionTicks(int baseTicks, int timeEfficiency) {
        if (baseTicks <= 0) {
            return 0;
        }
        int te = Math.max(0, Math.min(100, timeEfficiency));
        return Math.max(1, (int) Math.ceil(baseTicks * (100.0 - te) / 100.0));
    }

    /** Copy efficiency in percentage points, floored at 0. */
    public static int penalized(int current, int penalty) {
        return Math.max(0, current - Math.max(0, penalty));
    }
}
