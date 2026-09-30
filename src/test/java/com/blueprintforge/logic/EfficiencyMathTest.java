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
}
