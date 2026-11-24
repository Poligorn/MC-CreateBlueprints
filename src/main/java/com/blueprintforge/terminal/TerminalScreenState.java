package com.blueprintforge.terminal;

import net.minecraft.util.StringRepresentable;

/** Which front texture the terminal shows: empty, or a blueprint inside (T4 and T5 icons share the T4 screen). */
public enum TerminalScreenState implements StringRepresentable {
    IDLE("idle"), ACTIVE_T2("active_t2"), ACTIVE_T4("active_t4");

    private final String name;

    TerminalScreenState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
