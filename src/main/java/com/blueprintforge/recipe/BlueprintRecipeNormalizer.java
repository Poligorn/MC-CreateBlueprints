package com.blueprintforge.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Folds the datapack sugar form into a custom ingredient Create's codec accepts.
 * Sugar is a top-level {@code blueprint} object plus a key whose item is {@code blueprintforge:blueprint_slot}.
 */
public final class BlueprintRecipeNormalizer {
    private BlueprintRecipeNormalizer() {
    }

    public static void normalize(JsonObject json) {
        if (json == null || !json.has("blueprint") || !json.get("blueprint").isJsonObject()) {
            return;
        }
        JsonObject blueprint = json.getAsJsonObject("blueprint");
        if (!blueprint.has("accept_tag")) {
            return;
        }
        JsonObject slot = new JsonObject();
        slot.addProperty("type", "blueprintforge:blueprint_slot");
        slot.add("accept_tag", blueprint.get("accept_tag"));
        if (blueprint.has("mode")) {
            slot.add("mode", blueprint.get("mode"));
        }
        boolean folded = false;
        if (json.has("key") && json.get("key").isJsonObject()) {
            folded = foldKeys(json.getAsJsonObject("key"), slot);
        }
        if (json.has("ingredients") && json.get("ingredients").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("ingredients")) {
                if (element.isJsonObject() && isMarker(element.getAsJsonObject())) {
                    JsonObject object = element.getAsJsonObject();
                    object.entrySet().clear();
                    slot.entrySet().forEach(entry -> object.add(entry.getKey(), entry.getValue()));
                    folded = true;
                }
            }
        }
        if (folded) {
            json.remove("blueprint");
        }
    }

    private static boolean foldKeys(JsonObject key, JsonObject slot) {
        boolean folded = false;
        for (var entry : key.entrySet()) {
            if (entry.getValue().isJsonObject() && isMarker(entry.getValue().getAsJsonObject())) {
                entry.setValue(slot.deepCopy());
                folded = true;
            }
        }
        return folded;
    }

    private static boolean isMarker(JsonObject object) {
        return object.has("item") && "blueprintforge:blueprint_slot".equals(object.get("item").getAsString());
    }
}
