package com.blueprintforge.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import com.blueprintforge.logic.EnchantPolicy.Forged;
import com.blueprintforge.logic.EnchantPolicy.Mode;
import com.blueprintforge.logic.EnchantPolicy.Subject;
import com.blueprintforge.logic.EnchantPolicy.Verdict;

class EnchantPolicyTest {
    private static final Subject VANILLA_IRON_SWORD = new Subject(Forged.NONE, false, false, false);
    private static final Subject FORGED_T1 = new Subject(Forged.FREE_TIER, false, false, false);
    private static final Subject FORGED_T2 = new Subject(Forged.BLUEPRINT_TIER, false, false, false);
    private static final Subject FORGED_UNKNOWN_TIER = new Subject(Forged.UNKNOWN_TIER, false, false, false);
    private static final Subject BOOK = new Subject(Forged.NONE, false, false, true);

    @Test
    void restrictedIsTheDefaultModeValue() {
        assertEquals(Mode.RESTRICTED, Mode.parse("restricted").orElseThrow());
        assertEquals(Mode.OFF, Mode.parse("off").orElseThrow());
        assertEquals(Mode.FULL, Mode.parse("FULL").orElseThrow());
        assertEquals(Mode.SCALED, Mode.parse("scaled").orElseThrow());
        assertTrue(Mode.parse("strict").isEmpty());
    }

    @Test
    void restrictedAllowsVanillaAndT1() {
        assertEquals(Verdict.ALLOW, EnchantPolicy.evaluate(Mode.RESTRICTED, false, VANILLA_IRON_SWORD));
        assertEquals(Verdict.ALLOW, EnchantPolicy.evaluate(Mode.RESTRICTED, false, FORGED_T1));
    }

    @Test
    void restrictedRefusesForgedT2AndUnknownTier() {
        assertEquals(Verdict.DENY_RESTRICTED, EnchantPolicy.evaluate(Mode.RESTRICTED, false, FORGED_T2));
        assertEquals(Verdict.DENY_RESTRICTED, EnchantPolicy.evaluate(Mode.RESTRICTED, false, FORGED_UNKNOWN_TIER));
    }

    @Test
    void restrictedKeepsBooksUnlessFlagged() {
        assertEquals(Verdict.ALLOW, EnchantPolicy.evaluate(Mode.RESTRICTED, false, BOOK));
        assertEquals(Verdict.DENY_BOOKS, EnchantPolicy.evaluate(Mode.RESTRICTED, true, BOOK));
    }

    @Test
    void exceptionTagsOverrideForgedMarkerAndDeniedWins() {
        Subject allowedT2 = new Subject(Forged.BLUEPRINT_TIER, true, false, false);
        Subject deniedVanilla = new Subject(Forged.NONE, false, true, false);
        Subject both = new Subject(Forged.NONE, true, true, false);
        assertEquals(Verdict.ALLOW, EnchantPolicy.evaluate(Mode.RESTRICTED, false, allowedT2));
        assertEquals(Verdict.DENY_RESTRICTED, EnchantPolicy.evaluate(Mode.RESTRICTED, false, deniedVanilla));
        assertEquals(Verdict.DENY_RESTRICTED, EnchantPolicy.evaluate(Mode.RESTRICTED, false, both));
    }

    @Test
    void exceptionTagsAreIgnoredOutsideRestricted() {
        Subject allowedT2 = new Subject(Forged.BLUEPRINT_TIER, true, false, false);
        Subject deniedVanilla = new Subject(Forged.NONE, false, true, false);
        assertEquals(Verdict.DENY_FULL, EnchantPolicy.evaluate(Mode.FULL, false, allowedT2));
        assertEquals(Verdict.ALLOW, EnchantPolicy.evaluate(Mode.OFF, false, deniedVanilla));
    }

    @Test
    void offAllowsEverything() {
        for (Subject subject : new Subject[]{VANILLA_IRON_SWORD, FORGED_T2, FORGED_UNKNOWN_TIER, BOOK}) {
            assertEquals(Verdict.ALLOW, EnchantPolicy.evaluate(Mode.OFF, true, subject));
        }
    }

    @Test
    void fullRefusesEverythingAndDisablesBooks() {
        for (Subject subject : new Subject[]{VANILLA_IRON_SWORD, FORGED_T1, FORGED_T2, BOOK}) {
            assertEquals(Verdict.DENY_FULL, EnchantPolicy.evaluate(Mode.FULL, false, subject));
        }
        assertTrue(EnchantPolicy.booksDisabled(Mode.FULL, false));
        assertFalse(EnchantPolicy.booksDisabled(Mode.RESTRICTED, false));
        assertTrue(EnchantPolicy.booksDisabled(Mode.RESTRICTED, true));
        assertFalse(EnchantPolicy.booksDisabled(Mode.OFF, false));
    }

    @Test
    void scaledCapsEnchantmentLevelByTierAndIgnoresTags() {
        assertEquals(0, EnchantPolicy.levelCap(EnchantPolicy.tierNumber(null)));
        assertEquals(-1, EnchantPolicy.tierNumber(ResourceLocation.fromNamespaceAndPath("blueprintforge", "handmade")));
        assertEquals(0, EnchantPolicy.levelCap(0));
        assertEquals(1, EnchantPolicy.levelCap(1));
        assertEquals(1, EnchantPolicy.levelCap(2));
        assertEquals(2, EnchantPolicy.levelCap(3));
        assertEquals(3, EnchantPolicy.levelCap(4));
        assertEquals(4, EnchantPolicy.levelCap(5));
        assertEquals(0, EnchantPolicy.levelCap(6));
        assertEquals(2, EnchantPolicy.tierNumber(ResourceLocation.fromNamespaceAndPath("othermod", "tier2")));

        Subject vanilla = new Subject(Forged.NONE, true, false, false, -1);
        Subject tier2 = new Subject(Forged.BLUEPRINT_TIER, false, true, false, 2);
        Subject book = new Subject(Forged.NONE, false, false, true, -1);
        assertEquals(Verdict.DENY_SCALED, EnchantPolicy.evaluate(Mode.SCALED, false, vanilla));
        assertEquals(Verdict.ALLOW, EnchantPolicy.evaluate(Mode.SCALED, false, tier2));
        assertEquals(Verdict.DENY_SCALED, EnchantPolicy.evaluate(Mode.SCALED, false, book));
        assertFalse(EnchantPolicy.booksDisabled(Mode.SCALED, false));
        assertEquals(1, EnchantPolicy.clampedOfferLevel(4, 1, 1));
        assertEquals(0, EnchantPolicy.clampedOfferLevel(4, 1, 0));
        assertEquals(0, EnchantPolicy.clampedOfferLevel(1, 2, 1));
    }

    @Test
    void everyDenialHasAMessage() {
        for (Verdict verdict : Verdict.values()) {
            assertEquals(verdict.allowed(), verdict.messageKey() == null, verdict.name());
        }
    }
}
