package com.blueprintforge.logic;

import java.util.Locale;
import java.util.Optional;

/**
 * One decision for both the enchanting table and the anvil, so neither can be used to bypass the other.
 * A pure function of the mode, the forged marker, the modpack exception tags and the book flag.
 */
public final class EnchantPolicy {
    private EnchantPolicy() {
    }

    public enum Mode {
        OFF, RESTRICTED, FULL;

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

    public record Subject(Forged forged, boolean inAllowedTag, boolean inDeniedTag, boolean isBook) {
    }

    public enum Verdict {
        ALLOW(null),
        DENY_RESTRICTED("message.blueprintforge.enchanting.restricted"),
        DENY_FULL("message.blueprintforge.enchanting.full"),
        DENY_BOOKS("message.blueprintforge.enchanting.books_disabled");

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

    public static Verdict evaluate(Mode mode, boolean disableBooksFlag, Subject subject) {
        return switch (mode) {
            case OFF -> Verdict.ALLOW;
            case FULL -> Verdict.DENY_FULL;
            case RESTRICTED -> restricted(disableBooksFlag, subject);
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
