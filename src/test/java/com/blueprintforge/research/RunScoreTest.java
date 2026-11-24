package com.blueprintforge.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RunScoreTest {
    private static List<String> chain(String letters) {
        return letters.chars().mapToObj(c -> c == 'P' ? "press" : "blast").toList();
    }

    @Test
    void fiveOfTenIsFiftyAndTwoOfTenIsTwenty() {
        // Expected P B P B P B P B P B. The first run is right in positions 0-4 and flipped after that.
        RunScore half = RunScore.of(chain("PBPBPBPBPB"), chain("PBPBPPBPBP"));
        assertEquals(5, half.matched());
        assertEquals(50, half.percent());
        // The second run is right only in positions 0 and 1.
        RunScore fifth = RunScore.of(chain("PBPBPBPBPB"), chain("PBBPBPBPBP"));
        assertEquals(2, fifth.matched());
        assertEquals(20, fifth.percent());
    }

    @Test
    void eachPositionCountsOnItsOwn() {
        // Positions 2 and 8 differ, the other eight match.
        RunScore score = RunScore.of(chain("PBPBPBPBPB"), chain("PBBBPBPBBB"));
        assertEquals(8, score.matched());
        assertEquals(80, score.percent());
        assertFalse(score.complete());
    }

    @Test
    void anExactRunIsOneHundred() {
        RunScore score = RunScore.of(chain("PBPB"), chain("PBPB"));
        assertEquals(100, score.percent());
        assertTrue(score.complete());
    }

    @Test
    void aMissingStepMatchesNothingAndShiftsTheRest() {
        // Dropping the first step of P B P B shifts everything: no position matches.
        RunScore score = RunScore.of(chain("PBPB"), chain("BPB"));
        assertEquals(0, score.matched());
        assertEquals(0, score.percent());
    }

    @Test
    void aShortRunCannotBeComplete() {
        RunScore score = RunScore.of(chain("PBPB"), chain("PBP"));
        assertEquals(3, score.matched());
        assertEquals(75, score.percent());
        assertFalse(score.complete());
    }

    @Test
    void anExtraStepKeepsTheRunBelowOneHundred() {
        RunScore score = RunScore.of(chain("PBPB"), chain("PBPBP"));
        assertEquals(4, score.matched());
        assertEquals(99, score.percent());
        assertFalse(score.complete());
    }

    @Test
    void anEmptyChainOrRunScoresZero() {
        assertEquals(0, RunScore.of(List.of(), chain("PB")).percent());
        assertEquals(0, RunScore.of(chain("PB"), List.of()).percent());
        assertFalse(RunScore.of(List.of(), List.of()).complete());
    }
}
