package com.blueprintforge.terminal;

import net.minecraft.util.StringRepresentable;

/** Which screen the terminal shows: waiting, or with a blueprint inside. */
public enum TerminalScreenState implements StringRepresentable {
    IDLE("idle"), ACTIVE("active");

    private final String name;

    TerminalScreenState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
