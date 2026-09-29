package com.blueprintforge.data;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum BlueprintClass implements StringRepresentable {
    ORIGINAL("original"),
    COPY("copy"),
    ANCIENT("ancient"),
    FRAGMENT("fragment");

    public static final Codec<BlueprintClass> CODEC = StringRepresentable.fromEnum(BlueprintClass::values);

    private final String serializedName;

    BlueprintClass(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** Runs a freshly created instance of this class starts with; {@code -1} means unlimited. */
    public int initialRuns(int copyDefaultRuns) {
        return switch (this) {
            case ORIGINAL -> -1;
            case COPY -> copyDefaultRuns;
            case ANCIENT, FRAGMENT -> 1;
        };
    }
}
