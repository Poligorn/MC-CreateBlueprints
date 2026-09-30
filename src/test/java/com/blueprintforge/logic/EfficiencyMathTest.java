package com.blueprintforge.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

class EfficiencyMathTest {
    @Test
    void researchStepAddsTheDatapackStepAndStopsOnTheCeiling() {
        assertEquals(OptionalInt.of(3), EfficiencyMath.nextStep(0, 30, 3));
        assertEquals(OptionalInt.of(30), EfficiencyMath.nextStep(27, 30, 3));
        assertEquals(OptionalInt.of(30), EfficiencyMath.nextStep(28, 30, 3));
        assertEquals(OptionalInt.empty(), EfficiencyMath.nextStep(30, 30, 3));
        assertEquals(OptionalInt.of(40), EfficiencyMath.nextStep(35, 40, 5));
        assertEquals(OptionalInt.empty(), EfficiencyMath.nextStep(40, 40, 5));
        assertEquals(OptionalInt.empty(), EfficiencyMath.nextStep(0, 0, 0));
    }

    @Test
    void researchDurationUsesTheMixerFormulaAndIgnoresAStoppedShaft() {
        assertEquals(301, EfficiencyMath.researchTicks(16, 400));
        assertEquals(0, EfficiencyMath.researchTicks(0, 400));
    }

    @Test
    void copyPriceIsSuperlinearAndMatchesTheReferenceVector() {
        assertEquals(8, EfficiencyMath.copyCost(8, 10, 10, 1.35));
        assertEquals(71, EfficiencyMath.copyCost(8, 50, 10, 1.35));
        assertTrue(EfficiencyMath.copyCost(8, 50, 10, 1.35) > 8 * 5);
        assertEquals(1, EfficiencyMath.clampRuns(0, 50));
        assertEquals(50, EfficiencyMath.clampRuns(80, 50));
    }

    @Test
    void productionMaterialsAndTimeMatchTheBalanceVectors() {
        assertEquals(7, EfficiencyMath.consumed(10, 30));
        assertEquals(2, EfficiencyMath.consumed(3, 30));
        assertEquals(1, EfficiencyMath.consumed(1, 30));
        assertEquals(0, EfficiencyMath.consumed(0, 30));
        assertEquals(240, EfficiencyMath.productionTicks(400, 40));
        assertEquals(1, EfficiencyMath.productionTicks(1, 99));
    }

    @Test
    void copyPenaltyFloorsAtZero() {
        assertEquals(20, EfficiencyMath.penalized(30, 10));
        assertEquals(0, EfficiencyMath.penalized(6, 10));
    }
}
