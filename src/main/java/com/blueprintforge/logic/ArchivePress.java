package com.blueprintforge.logic;

/**
 * Where the Archive's press head sits. {@code 0} is fully raised under the roof, {@code 1} is down on the item.
 * The head only moves while an operation is actually working an item (research step or belt remake).
 * A stopped shaft or an idle archive holds the head up: it does not keep striking.
 * <p>
 * Strike length shortens as the shaft spins faster, in the same spirit as Create's {@code 512 / speed}
 * term, and the phase advances with operation progress so the blow is part of that operation.
 */
public final class ArchivePress {
    private ArchivePress() {
    }

    public static float headDown(boolean workingItem, float absoluteSpeed, int progress, int total, float partialTick) {
        if (!workingItem || total <= 0 || !(absoluteSpeed > 0.0F)) {
            return 0.0F;
        }
        float ticks = Math.max(0.0F, progress) + Math.max(0.0F, partialTick);
        float strikeTicks = strikeLength(absoluteSpeed);
        float phase = (ticks % strikeTicks) / strikeTicks;
        return curve(phase);
    }

    /** Ticks for one raise-strike-raise. Faster shafts hit quicker, but a blow never collapses to a flicker. */
    public static float strikeLength(float absoluteSpeed) {
        float rpm = Math.max(1.0F, absoluteSpeed);
        return Math.max(6.0F, 48.0F * 16.0F / rpm);
    }

    /**
     * Most of the strike the head stays up. Then it accelerates down, dwells, and rises.
     * Idle phase 0 is raised, so a paused operation does not rest on the item.
     */
    static float curve(float phase) {
        if (phase < 0.55F) {
            return 0.0F;
        }
        if (phase < 0.75F) {
            float t = (phase - 0.55F) / 0.20F;
            return t * t;
        }
        if (phase < 0.82F) {
            return 1.0F;
        }
        float t = Math.min(1.0F, (phase - 0.82F) / 0.18F);
        return 1.0F - t;
    }
}
