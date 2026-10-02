package com.blueprintforge.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FluxMathTest {
    @Test
    void scrapFallsWithFluxAndFloorsAtZero() {
        assertEquals(0.24, FluxMath.scrapChance(0.24, 0), 1.0E-9);
        assertEquals(0.048, FluxMath.scrapChance(0.24, 80), 1.0E-9);
        assertEquals(0.0, FluxMath.scrapChance(0.24, 100), 1.0E-9);
        assertEquals(0.0, FluxMath.scrapChance(0.0, 0), 1.0E-9);
    }

    @Test
    void aRollBelowTheChanceIsScrap() {
        assertTrue(FluxMath.scraps(0.24, 0.0));
        assertTrue(FluxMath.scraps(0.24, 0.239));
        assertFalse(FluxMath.scraps(0.24, 0.24));
        assertFalse(FluxMath.scraps(0.048, 0.05));
    }

    @Test
    void lineMaterialsKeepAtLeastOneUntilFullEfficiency() {
        assertEquals(4, EfficiencyMath.consumed(4, 0));
        assertEquals(2, EfficiencyMath.consumed(4, 30));
        assertEquals(1, EfficiencyMath.consumed(1, 30));
    }
}
