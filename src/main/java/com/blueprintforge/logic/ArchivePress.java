package com.blueprintforge.logic;

/**
 * Unused strike curve kept so the unit vectors stay. The Project Bureau is a drafting table and does
 * not render a press, so nothing in the game calls {@link #headDown}.
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
