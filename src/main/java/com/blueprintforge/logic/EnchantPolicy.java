package com.blueprintforge.logic;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.resources.ResourceLocation;

/**
 * One decision for both the enchanting table and the anvil, so neither can be used to bypass the other.
 * A pure function of the mode, the forged marker, the modpack exception tags and the book flag.
 */
public final class EnchantPolicy {
    private static final Pattern TIER_NUMBER = Pattern.compile("tier(\\d+)");

    private EnchantPolicy() {
    }

    public enum Mode {
        OFF, RESTRICTED, FULL, SCALED;

        public static Optional<Mode> parse(String value) {
            try {
                return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    /** What the item carries, as far as the policy is concerned. */
    public enum Forged {
        /** No {@code forged} component: vanilla or handmade item. */
        NONE,
        /** Forged, and its tier says {@code requires_blueprint: false} (T1). */
        FREE_TIER,
        /** Forged with a tier that requires a blueprint (T2+). */
        BLUEPRINT_TIER,
        /** Forged, but the tier is not in the current datapack. Treated as T2+. */
        UNKNOWN_TIER
    }

    public record Subject(Forged forged, boolean inAllowedTag, boolean inDeniedTag, boolean isBook, int tierNumber) {
        public Subject(Forged forged, boolean inAllowedTag, boolean inDeniedTag, boolean isBook) {
            this(forged, inAllowedTag, inDeniedTag, isBook, -1);
        }
    }

    public enum Verdict {
        ALLOW(null),
        DENY_RESTRICTED("message.blueprintforge.enchanting.restricted"),
        DENY_FULL("message.blueprintforge.enchanting.full"),
        DENY_BOOKS("message.blueprintforge.enchanting.books_disabled"),
        DENY_SCALED("message.blueprintforge.enchanting.scaled_none");

        private final String messageKey;

        Verdict(String messageKey) {
            this.messageKey = messageKey;
        }

        public boolean allowed() {
            return this == ALLOW;
        }

        /** Translation key of the action bar message, {@code null} for {@link #ALLOW}. */
        public String messageKey() {
            return messageKey;
        }
    }

    /** Books are off when the mode is {@code full} or the flag is set. */
    public static boolean booksDisabled(Mode mode, boolean disableBooksFlag) {
        return mode == Mode.FULL || disableBooksFlag;
    }

    /**
     * Number in a tier id path {@code tierN}. Namespace is ignored. Unparsed paths, including a missing tier, are {@code -1}.
     */
    public static int tierNumber(ResourceLocation tierId) {
        if (tierId == null) {
            return -1;
        }
        Matcher matcher = TIER_NUMBER.matcher(tierId.getPath());
        if (!matcher.matches()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Highest enchantment level a forged tier may receive in {@code scaled}.
     * No tier and T0 get nothing. T1 and T2 get 1, T3 gets 2, T4 gets 3, T5 gets 4. Anything else gets nothing.
     */
    public static int levelCap(int tierNumber) {
        return switch (tierNumber) {
            case 1, 2 -> 1;
            case 3 -> 2;
            case 4 -> 3;
            case 5 -> 4;
            default -> 0;
        };
    }

    /** Offer level after the scaled cap. {@code 0} means the offer is dropped. */
    public static int clampedOfferLevel(int rolled, int minLevel, int cap) {
        if (cap <= 0) {
            return 0;
        }
        int next = Math.min(rolled, cap);
        return next >= minLevel ? next : 0;
    }

    public static Verdict evaluate(Mode mode, boolean disableBooksFlag, Subject subject) {
        return switch (mode) {
            case OFF -> Verdict.ALLOW;
            case FULL -> Verdict.DENY_FULL;
            case RESTRICTED -> restricted(disableBooksFlag, subject);
            case SCALED -> restricted(disableBooksFlag, subject);
        };
    }

    private static Verdict restricted(boolean disableBooksFlag, Subject subject) {
        if (subject.inDeniedTag()) {
            return Verdict.DENY_RESTRICTED;
        }
        if (subject.isBook() && disableBooksFlag) {
            return Verdict.DENY_BOOKS;
        }
        if (subject.inAllowedTag()) {
            return Verdict.ALLOW;
        }
        return switch (subject.forged()) {
            case NONE, FREE_TIER -> Verdict.ALLOW;
            case BLUEPRINT_TIER, UNKNOWN_TIER -> Verdict.DENY_RESTRICTED;
        };
    }
}
