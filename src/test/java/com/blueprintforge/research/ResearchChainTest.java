package com.blueprintforge.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

/** Reference vectors of the stub generator. The expected values were computed independently with a Python SplitMix64. */
class ResearchChainTest {
    private static final ResourceLocation PRESS = ResourceLocation.fromNamespaceAndPath("blueprintforge", "press");
    private static final ResourceLocation BLAST = ResourceLocation.fromNamespaceAndPath("blueprintforge", "blast");
    private static final List<ResourceLocation> OPS = List.of(PRESS, BLAST);

    private static String letters(List<ResourceLocation> chain) {
        return chain.stream().map(id -> id.equals(PRESS) ? "P" : "B").collect(Collectors.joining());
    }

    @Test
    void splitMixMatchesTheReferenceImplementation() {
        ResearchChain.SplitMix64 zero = new ResearchChain.SplitMix64(0);
        assertEquals(0xE220A8397B1DCDAFL, zero.next());
        assertEquals(0x6E789E6AA1B965F4L, zero.next());
        assertEquals(0x06C45D188009454FL, zero.next());
        ResearchChain.SplitMix64 one = new ResearchChain.SplitMix64(1);
        assertEquals(0x910A2DEC89025CC1L, one.next());
        assertEquals(0xBEEB8DA1658EEC67L, one.next());
    }

    @Test
    void shortChainVectors() {
        Map<Long, String> expected = Map.of(0L, "PBPB", 1L, "BPBB", 42L, "BPPP", -1L, "BBP", 1234567890123L, "PBP");
        expected.forEach((seed, chain) -> assertEquals(chain, letters(ResearchChain.generate(seed, 3, 4, OPS, 0)), "seed " + seed));
    }

    @Test
    void longChainVectors() {
        Map<Long, String> expected = Map.of(0L, "PBPBPBP", 1L, "BPBBPBBP", 42L, "BPPPPBP", -1L, "BBPPBBPP", 1234567890123L, "PBPBPPP");
        expected.forEach((seed, chain) -> assertEquals(chain, letters(ResearchChain.generate(seed, 6, 8, OPS, 0)), "seed " + seed));
    }

    @Test
    void aChainOfOneRepeatedOperationIsBroken() {
        // Seed 9 first draws PPP; the generator must change one position, here to PPB.
        assertEquals("PPB", letters(ResearchChain.generate(9, 3, 4, OPS, 0)));
    }

    @Test
    void theSameSeedAlwaysGivesTheSameChain() {
        for (long seed = -50; seed < 50; seed++) {
            assertEquals(ResearchChain.generate(seed, 3, 8, OPS, 0), ResearchChain.generate(seed, 3, 8, OPS, 0));
        }
    }

    @Test
    void lengthStaysInTheTierRangeAndChainsAreNotOneOperation() {
        for (long seed = 0; seed < 2000; seed++) {
            List<ResourceLocation> chain = ResearchChain.generate(seed * 7919, 3, 6, OPS, 0);
            assertTrue(chain.size() >= 3 && chain.size() <= 6, "length " + chain.size());
            assertTrue(chain.stream().distinct().count() >= 2, "seed " + seed + " gave " + letters(chain));
        }
    }

    @Test
    void aSingleOperationTierMayRepeatItAndAnEmptyOneGivesNothing() {
        assertEquals("PPP", letters(ResearchChain.generate(3, 3, 3, List.of(PRESS), 0)));
        assertTrue(ResearchChain.generate(3, 3, 4, List.of(), 0).isEmpty());
        assertTrue(ResearchChain.generate(3, 0, 4, OPS, 0).isEmpty());
    }

    @Test
    void anUnknownGeneratorVersionIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ResearchChain.generate(1, 3, 4, OPS, 99));
        assertFalse(ResearchChain.GENERATOR_VERSION < 0);
    }
}
