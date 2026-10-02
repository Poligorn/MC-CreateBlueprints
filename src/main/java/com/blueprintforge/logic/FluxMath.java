package com.blueprintforge.logic;

/**
 * Scrap chance of a sequenced line. Flux is a percent reduction of the datapack chance, floored at 0.
 * A roll in {@code [0, 1)} scraps when it is strictly below the chance.
 */
public final class FluxMath {
    private FluxMath() {
    }

    public static double scrapChance(double baseChance, int flux) {
        if (baseChance <= 0) {
            return 0;
        }
        int clamped = Math.max(0, Math.min(100, flux));
        return baseChance * (100.0 - clamped) / 100.0;
    }

    public static boolean scraps(double chance, double roll01) {
        return roll01 < chance;
    }
}
