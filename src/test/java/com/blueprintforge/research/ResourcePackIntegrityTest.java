package com.blueprintforge.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The client is not started by any test, so a broken model reference would only show up in the game. This test walks the
 * blueprint and terminal resources and checks that every model and texture they name exists and is valid JSON or PNG.
 */
class ResourcePackIntegrityTest {
    private static final String ASSETS = "assets/blueprintforge/";

    private static InputStream open(String path) {
        return ResourcePackIntegrityTest.class.getClassLoader().getResourceAsStream(path);
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = open(path)) {
            assertNotNull(in, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** "blueprintforge:item/foo" to the resource path of a model or texture under assets. */
    private static String resource(String id, String kind, String extension) {
        String[] parts = id.split(":", 2);
        String namespace = parts.length == 2 ? parts[0] : "minecraft";
        return "assets/" + namespace + "/" + kind + "/" + parts[parts.length - 1] + extension;
    }

    private static void assertPng(String id) throws IOException {
        if (id.startsWith("minecraft:")) {
            return; // vanilla textures are not in this jar
        }
        try (InputStream in = open(resource(id, "textures", ".png"))) {
            assertNotNull(in, "missing texture " + id);
            byte[] header = in.readNBytes(8);
            assertEquals((byte) 0x89, header[0], "not a PNG: " + id);
            assertEquals((byte) 'P', header[1], "not a PNG: " + id);
        }
    }

    private static void assertModel(String id, List<String> seen) throws IOException {
        if (id.startsWith("minecraft:")) {
            return;
        }
        seen.add(id);
        JsonObject model = json(resource(id, "models", ".json"));
        if (model.has("textures")) {
            for (var entry : model.getAsJsonObject("textures").entrySet()) {
                String value = entry.getValue().getAsString();
                if (!value.startsWith("#")) {
                    assertPng(value);
                }
            }
        }
    }

    @Test
    void researchBlueprintModelsAndOfficialIconsAreAllThere() throws IOException {
        JsonObject root = json(ASSETS + "models/item/research_blueprint.json");
        List<String> seen = new ArrayList<>();
        assertModel("blueprintforge:item/research_blueprint", seen);
        JsonArray overrides = root.getAsJsonArray("overrides");
        // 14 types (9 approved, 5 proposed) x 5 tiers x (original, copy)
        assertEquals(140, overrides.size());
        int previous = 0;
        for (JsonElement element : overrides) {
            JsonObject override = element.getAsJsonObject();
            int variant = override.getAsJsonObject("predicate").get("blueprintforge:variant").getAsInt();
            assertTrue(variant > previous, "overrides must ascend, the last matching one wins");
            previous = variant;
            assertModel(override.get("model").getAsString(), seen);
        }
        assertEquals(1 + 140, seen.size());
        assertEquals(140, previous);
    }

    @Test
    void variantIndexMatchesTheTypeOrder() {
        for (ResearchType type : ResearchType.values()) {
            for (boolean copy : new boolean[] {false, true}) {
                for (int tier = 1; tier <= 5; tier++) {
                    int expected = 1 + type.ordinal() * 10 + (copy ? 5 : 0) + (tier - 1);
                    ResearchBlueprint blueprint = new ResearchBlueprint(
                            net.minecraft.resources.ResourceLocation.parse("blueprintforge:t1"),
                            net.minecraft.resources.ResourceLocation.parse("blueprintforge:iron_sword"),
                            net.minecraft.resources.ResourceLocation.parse("minecraft:iron_sword"),
                            type, tier, copy, java.util.Optional.empty(), 0);
                    assertEquals(expected, blueprint.variant());
                }
            }
        }
        assertEquals(14, ResearchType.values().length);
    }

    @Test
    void terminalBlockstateModelsAndTexturesAreAllThere() throws IOException {
        JsonObject variants = json(ASSETS + "blockstates/terminal.json").getAsJsonObject("variants");
        assertEquals(16, variants.size(), "4 facings x 2 halves x 2 screens");
        List<String> seen = new ArrayList<>();
        for (var entry : variants.entrySet()) {
            assertModel(entry.getValue().getAsJsonObject().get("model").getAsString(), seen);
        }
        for (String model : List.of("terminal_lower", "terminal_upper_idle", "terminal_upper_active")) {
            assertTrue(seen.contains("blueprintforge:block/" + model), model + " must be used");
        }
        assertModel("blueprintforge:item/terminal", new ArrayList<>());
        assertModel("blueprintforge:item/scrap", new ArrayList<>());
    }

    @Test
    void cutOutTerminalTexturesKeepTheirTransparency() throws IOException {
        for (String name : List.of("east_upper", "west_upper", "upper_top", "desk_front")) {
            try (InputStream in = open(ASSETS + "textures/block/terminal/" + name + ".png")) {
                java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(in);
                int clear = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if ((image.getRGB(x, y) >>> 24) == 0) {
                            clear++;
                        }
                    }
                }
                assertTrue(clear > 0, name + " must have transparent texels");
            }
        }
    }

    @Test
    void terminalLootTableAndMiningTagExist() throws IOException {
        assertNotNull(open("data/blueprintforge/loot_table/blocks/terminal.json"));
        // The vanilla jar has a pickaxe.json of its own, so look at every copy on the classpath.
        boolean listed = false;
        var urls = ResourcePackIntegrityTest.class.getClassLoader().getResources("data/minecraft/tags/block/mineable/pickaxe.json");
        while (urls.hasMoreElements()) {
            try (InputStream in = urls.nextElement().openStream()) {
                listed |= new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("blueprintforge:terminal");
            }
        }
        assertTrue(listed, "the terminal must be mineable with a pickaxe");
    }

    @Test
    void everyNewTranslationKeyExistsInBothLanguages() throws IOException {
        JsonObject en = json(ASSETS + "lang/en_us.json");
        JsonObject ru = json(ASSETS + "lang/ru_ru.json");
        for (String key : List.of("block.blueprintforge.terminal", "item.blueprintforge.research_blueprint", "item.blueprintforge.scrap",
                "research.tier.blueprintforge.t1", "research.tier.blueprintforge.t2", "research.tier.blueprintforge.t3",
                "gui.blueprintforge.terminal.action.make", "gui.blueprintforge.terminal.action.check", "gui.blueprintforge.terminal.warning",
                "gui.blueprintforge.terminal.last", "gui.blueprintforge.terminal.line.missing", "tooltip.blueprintforge.workpiece",
                "tooltip.blueprintforge.result.from", "gui.blueprintforge.terminal.recorded", "tooltip.blueprintforge.workpiece.of")) {
            assertTrue(en.has(key), "en_us lacks " + key);
            assertTrue(ru.has(key), "ru_ru lacks " + key);
        }
        for (String lang : new String[] {"en", "ru"}) {
            JsonObject own = lang.equals("en") ? en : ru;
            for (var entry : (lang.equals("en") ? ru : en).entrySet()) {
                if (entry.getKey().startsWith("gui.blueprintforge.terminal.")) {
                    assertTrue(own.has(entry.getKey()), lang + " lacks " + entry.getKey());
                }
            }
        }
    }
}
