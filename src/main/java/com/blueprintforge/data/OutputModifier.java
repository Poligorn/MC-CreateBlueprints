package com.blueprintforge.data;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * One vanilla attribute modifier from {@code output.attributes}. The same shape is snapshotted into
 * {@link ForgedItemData} so the tooltip shows exactly what was applied.
 */
public record OutputModifier(ResourceLocation attribute, Mode mode, double value, Optional<EquipmentSlotGroup> slot) {
    public static final Codec<OutputModifier> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("attribute").forGetter(OutputModifier::attribute),
            Mode.CODEC.fieldOf("mode").forGetter(OutputModifier::mode),
            Codec.DOUBLE.fieldOf("value").forGetter(OutputModifier::value),
            EquipmentSlotGroup.CODEC.optionalFieldOf("slot").forGetter(OutputModifier::slot)
    ).apply(i, OutputModifier::new));

    public enum Mode implements StringRepresentable {
        ADD("add", AttributeModifier.Operation.ADD_VALUE),
        MULTIPLY_BASE("multiply_base", AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
        MULTIPLY_TOTAL("multiply_total", AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        private final String serializedName;
        private final AttributeModifier.Operation operation;

        Mode(String serializedName, AttributeModifier.Operation operation) {
            this.serializedName = serializedName;
            this.operation = operation;
        }

        public AttributeModifier.Operation operation() {
            return operation;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
