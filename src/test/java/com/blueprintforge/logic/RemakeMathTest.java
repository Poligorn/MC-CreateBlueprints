package com.blueprintforge.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RemakeMathTest {
    @Test
    void processingTicksMatchTheCreateMixerFormula() {
        assertEquals(76, RemakeMath.processingTicks(16, 100));
        assertEquals(33, RemakeMath.processingTicks(32, 50));
        assertEquals(16, RemakeMath.processingTicks(256, 100));
        assertEquals(1, RemakeMath.processingTicks(512, 100));
        assertEquals(0, RemakeMath.processingTicks(0, 100));
        assertEquals(0, RemakeMath.processingTicks(16, 0));
        assertEquals(901, RemakeMath.processingTicks(16, 1200));
        assertEquals(61, RemakeMath.processingTicks(16, 80));
    }
}
