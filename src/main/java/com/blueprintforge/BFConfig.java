package com.blueprintforge;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code config/blueprintforge-server.toml}. Only keys that the current phase executes live here: today that is
 * {@code rarity.multiplier} for blueprint loot.
 */
public final class BFConfig {
    public static final String FILE_NAME = "blueprintforge-server.toml";
    public static final double DEFAULT_RARITY_MULTIPLIER = 1.0;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue RARITY_MULTIPLIER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("rarity");
        RARITY_MULTIPLIER = builder
                .comment("Scales the chance of every blueprint loot source; the result is clamped to 0..1. 0 turns blueprint loot off")
                .defineInRange("multiplier", DEFAULT_RARITY_MULTIPLIER, 0.0, 1000.0);
        builder.pop();
        SPEC = builder.build();
    }

    private BFConfig() {
    }

    /** The configured multiplier, or the default if the config has not been loaded yet. */
    public static double rarityMultiplier() {
        try {
            return RARITY_MULTIPLIER.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_RARITY_MULTIPLIER;
        }
    }
}
