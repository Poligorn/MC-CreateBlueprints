package com.blueprintforge.research;

import java.util.List;

/**
 * Scores a run by position: the share of chain steps that sit where the chain has them. A missing step matches nothing
 * at its position, an extra step keeps the run below 100%. The denominator is the chain length.
 * TODO(egor): proposal for open question 40, not a decision.
 */
public record RunScore(int matched, int total, int actualLength) {
    public static RunScore of(List<String> expected, List<String> actual) {
        int matched = 0;
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
            if (expected.get(i).equals(actual.get(i))) {
                matched++;
            }
        }
        return new RunScore(matched, expected.size(), actual.size());
    }

    /** Only a run that matched every step and added nothing is complete. */
    public boolean complete() {
        return total > 0 && matched == total && actualLength == total;
    }

    /** Whole percent, rounded down; 99 at most while the run has extra steps or is otherwise not complete. */
    public int percent() {
        if (total == 0) {
            return 0;
        }
        int percent = matched * 100 / total;
        return complete() ? 100 : Math.min(percent, 99);
    }
}
