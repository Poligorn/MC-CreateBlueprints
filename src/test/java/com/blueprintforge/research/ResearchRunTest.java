package com.blueprintforge.research;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

class ResearchRunTest {
    private static final ResourceLocation TIER = ResourceLocation.parse("blueprintforge:t3");
    private static final ResourceLocation TARGET = ResourceLocation.parse("blueprintforge:pickaxe_t3");
    private static final ResourceLocation ITEM = ResourceLocation.parse("minecraft:iron_pickaxe");

    @Test
    void stepsSurviveAJsonRoundTrip() {
        ResearchRun run = new ResearchRun(TIER, TARGET, ITEM, -42L, 0, 7);
        JsonElement json = ResearchRun.CODEC.encodeStart(JsonOps.INSTANCE, run).getOrThrow();
        assertEquals(run, ResearchRun.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(7, json.getAsJsonObject().get("steps").getAsInt());
    }

    @Test
    void aWorkpieceIssuedBeforeStepsExistedReadsAsZero() {
        JsonElement old = JsonParser.parseString(
                "{\"tier\": \"blueprintforge:t3\", \"target\": \"blueprintforge:pickaxe_t3\", \"item\": \"minecraft:iron_pickaxe\", \"seed\": 5, \"generator_version\": 0}");
        ResearchRun run = ResearchRun.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        assertEquals(0, run.steps(), "0 means unknown: the hint is shown without M");
        assertEquals(5L, run.seed());
    }
}
