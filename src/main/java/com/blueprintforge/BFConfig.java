package com.blueprintforge;

import com.blueprintforge.logic.EnchantPolicy;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code config/blueprintforge-server.toml}. Only keys that the current phase actually executes live here.
 */
public final class BFConfig {
    public static final String FILE_NAME = "blueprintforge-server.toml";

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> ENCHANTING_MODE;
    public static final ModConfigSpec.BooleanValue DISABLE_BOOKS;
    public static final ModConfigSpec.DoubleValue RARITY_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue FITTINGS_ENABLED;
    public static final ModConfigSpec.BooleanValue ALLOW_FAKE_PLAYERS;
    public static final ModConfigSpec.IntValue TRANSFER_TIMEOUT_SECONDS;

    private static boolean fittingsWarned;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("enchanting");
        ENCHANTING_MODE = builder
                .comment("off | restricted | full. Tags enchanting_allowed and enchanting_denied apply only to restricted")
                .define("mode", "restricted", value -> value instanceof String s && EnchantPolicy.Mode.parse(s).isPresent());
        DISABLE_BOOKS = builder
                .comment("With mode = \"full\" books are disabled in any case")
                .define("disable_books", false);
        builder.pop();

        builder.push("rarity");
        RARITY_MULTIPLIER = builder
                .comment("Scales the chance of every blueprint source; the result is clamped to 0..1")
                .defineInRange("multiplier", 1.0, 0.0, 1000.0);
        builder.pop();

        builder.push("drops");
        ALLOW_FAKE_PLAYERS = builder
                .comment("When false, fake players do not roll mob or fishing blueprint drops")
                .define("allow_fake_players", false);
        builder.pop();

        builder.push("transfer");
        TRANSFER_TIMEOUT_SECONDS = builder
                .comment("Seconds the other player has to accept a handed transfer")
                .defineInRange("confirm_timeout_seconds", 60, 5, 600);
        builder.pop();

        builder.push("fittings");
        FITTINGS_ENABLED = builder
                .comment("Ignored until the fittings feature exists")
                .define("enabled", false);
        builder.pop();

        SPEC = builder.build();
    }

    private BFConfig() {
    }

    public static EnchantPolicy.Mode enchantingMode() {
        return EnchantPolicy.Mode.parse(ENCHANTING_MODE.get()).orElse(EnchantPolicy.Mode.RESTRICTED);
    }

    public static boolean booksDisabled() {
        return EnchantPolicy.booksDisabled(enchantingMode(), DISABLE_BOOKS.get());
    }

    public static double rarityMultiplier() {
        return RARITY_MULTIPLIER.get();
    }

    public static boolean allowFakePlayers() {
        return ALLOW_FAKE_PLAYERS.get();
    }

    public static int transferTimeoutSeconds() {
        return TRANSFER_TIMEOUT_SECONDS.get();
    }

    static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC || event instanceof ModConfigEvent.Unloading) {
            return;
        }
        if (FITTINGS_ENABLED.get() && !fittingsWarned) {
            fittingsWarned = true;
            BlueprintForge.LOGGER.warn("fittings.enabled = true is ignored: fittings are not implemented in this version");
        }
    }
}
