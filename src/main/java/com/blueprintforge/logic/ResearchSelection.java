package com.blueprintforge.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.blueprintforge.data.ResearchProfile;

import net.minecraft.resources.ResourceLocation;

/**
 * Picks the research profile for one blueprint. An exact id beats any tag. A longer tag beats a shorter one.
 * Two matches of the same specificity do not average: the lexicographically smallest profile id wins, and the
 * caller is told the match was ambiguous.
 */
public final class ResearchSelection {
    /** Specificity of an exact blueprint id. Tag lengths stay below this. */
    private static final int ID_MATCH = 1_000_000;

    private ResearchSelection() {
    }

    public record Choice(Optional<ResourceLocation> profileId, boolean ambiguous) {
        public static Choice none() {
            return new Choice(Optional.empty(), false);
        }
    }

    public static Choice choose(ResourceLocation blueprintId, List<ResourceLocation> tags,
                                Map<ResourceLocation, ResearchProfile> profiles) {
        int best = -1;
        List<ResourceLocation> winners = new ArrayList<>();
        for (Map.Entry<ResourceLocation, ResearchProfile> entry : profiles.entrySet()) {
            int specificity = specificity(entry.getValue(), blueprintId, tags);
            if (specificity < 0) {
                continue;
            }
            if (specificity > best) {
                best = specificity;
                winners.clear();
                winners.add(entry.getKey());
            } else if (specificity == best) {
                winners.add(entry.getKey());
            }
        }
        if (winners.isEmpty()) {
            return Choice.none();
        }
        winners.sort(Comparator.naturalOrder());
        return new Choice(Optional.of(winners.getFirst()), winners.size() > 1);
    }

    private static int specificity(ResearchProfile profile, ResourceLocation blueprintId, List<ResourceLocation> tags) {
        int best = -1;
        for (ResearchProfile.AppliesTo target : profile.appliesTo()) {
            if (!target.tag() && target.id().equals(blueprintId)) {
                best = Math.max(best, ID_MATCH);
            } else if (target.tag() && tags.contains(target.id())) {
                best = Math.max(best, target.id().toString().length());
            }
        }
        return best;
    }
}
