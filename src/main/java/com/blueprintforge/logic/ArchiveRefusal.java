package com.blueprintforge.logic;

/** Why copying or fragment assembly will not start. {@link #OK} means it will. */
public enum ArchiveRefusal {
    OK,
    NO_DOCUMENT,
    COPY_FORBIDDEN,
    DISABLED,
    NO_PROFILE,
    FLUID_COST,
    MISSING_COST,
    NO_ROTATION,
    BUSY,
    OUTPUT_FULL,
    BAD_QUOTE,
    ASSEMBLY_SHORT,
    ALREADY_DONE;

    public String translationKey() {
        return "gui.blueprintforge.archive.job." + name().toLowerCase();
    }
}
