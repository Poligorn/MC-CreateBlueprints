package com.blueprintforge.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.blueprintforge.data.ResearchProfile;

import net.minecraft.resources.ResourceLocation;

class ResearchSelectionTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("blueprintforge", path);
    }

    private static ResearchProfile profile(String target, boolean tag) {
        return new ResearchProfile(List.of(new ResearchProfile.AppliesTo(id(target), tag)), List.of(), List.of(), 256, 400, false, 80, 256);
    }

    @Test
    void anExactIdBeatsATagAndALongerTagBeatsAShorterOne() {
        ResourceLocation blade = id("guild_blade");
        Map<ResourceLocation, ResearchProfile> profiles = Map.of(
                id("wide"), profile("weapons", true),
                id("narrow"), profile("tier2_weapons", true),
                id("exact"), profile("guild_blade", false));
        ResearchSelection.Choice choice = ResearchSelection.choose(blade, List.of(id("weapons"), id("tier2_weapons")), profiles);
        assertEquals(id("exact"), choice.profileId().orElseThrow());
        assertFalse(choice.ambiguous());

        ResearchSelection.Choice byTag = ResearchSelection.choose(blade, List.of(id("weapons"), id("tier2_weapons")),
                Map.of(id("wide"), profiles.get(id("wide")), id("narrow"), profiles.get(id("narrow"))));
        assertEquals(id("narrow"), byTag.profileId().orElseThrow());
        assertFalse(byTag.ambiguous());
    }

    @Test
    void equalSpecificityKeepsTheLexicographicallyFirstProfile() {
        ResourceLocation blade = id("guild_blade");
        Map<ResourceLocation, ResearchProfile> profiles = Map.of(
                id("beta"), profile("guild_blade", false),
                id("alpha"), profile("guild_blade", false));
        ResearchSelection.Choice choice = ResearchSelection.choose(blade, List.of(), profiles);
        assertEquals(id("alpha"), choice.profileId().orElseThrow());
        assertTrue(choice.ambiguous());
    }
}
