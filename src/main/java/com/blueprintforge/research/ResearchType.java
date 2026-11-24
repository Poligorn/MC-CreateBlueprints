package com.blueprintforge.research;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * The corner symbol of a blueprint icon: the nine approved types and the five proposed ones, all with official icons.
 * What an item from another mod gets (open question 27) is not decided, so unknown values are rejected rather than guessed.
 */
public enum ResearchType implements StringRepresentable {
    SWORD("sword"), PICKAXE("pickaxe"), AXE("axe"), SHOVEL("shovel"), HOE("hoe"),
    HELMET("helmet"), CHESTPLATE("chestplate"), LEGGINGS("leggings"), BOOTS("boots"),
    // Proposed types: the artwork exists, the owner asked for them in the creative tab. Append only: the order is the icon index.
    BOW("bow"), CROSSBOW("crossbow"), TRIDENT("trident"), SHIELD("shield"), MACE("mace");

    public static final Codec<ResearchType> CODEC = StringRepresentable.fromEnum(ResearchType::values);

    private final String serializedName;

    ResearchType(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
