package com.blueprintforge.logic;

/** Why a research step will not start. {@link #OK} means it will. */
public enum ResearchRefusal {
    OK,
    NO_DOCUMENT,
    COPY_FORBIDDEN,
    NO_PROFILE,
    AT_CAP,
    NO_ROTATION,
    FLUID_COST,
    MISSING_COST,
    BUSY;

    public String translationKey() {
        return "gui.blueprintforge.archive.research." + name().toLowerCase();
    }
}
