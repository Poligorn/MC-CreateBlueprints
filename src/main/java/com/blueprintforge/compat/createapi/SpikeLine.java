package com.blueprintforge.compat.createapi;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Spike-only marker: an item carrying this component is a "workpiece" and records which Create machine
 * touched it, in the order the machines fired. Not a design decision: the real workpiece component belongs
 * to task 7 of docs/MVP_TASKS.md.
 */
public record SpikeLine(List<String> operations) {
    public static final String PRESS = "press";
    public static final String BLAST = "blast";

    public static final Codec<SpikeLine> CODEC = Codec.STRING.listOf().xmap(SpikeLine::new, SpikeLine::operations);
    public static final StreamCodec<ByteBuf, SpikeLine> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(SpikeLine::new, SpikeLine::operations);

    public SpikeLine {
        operations = List.copyOf(operations);
    }

    public static SpikeLine empty() {
        return new SpikeLine(List.of());
    }

    /** A copy of the stack turned into a workpiece with no operations yet. */
    public static ItemStack workpiece(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.set(SpikeComponents.LINE.get(), empty());
        return copy;
    }

    public static boolean isWorkpiece(ItemStack stack) {
        return stack.has(SpikeComponents.LINE.get());
    }

    public static List<String> operationsOf(ItemStack stack) {
        SpikeLine line = stack.get(SpikeComponents.LINE.get());
        return line == null ? List.of() : line.operations();
    }

    /** A copy of the stack with one more operation appended; the input stack is not touched. */
    public static ItemStack withOperation(ItemStack stack, String operation) {
        List<String> operations = new ArrayList<>(operationsOf(stack));
        operations.add(operation);
        ItemStack copy = stack.copy();
        copy.set(SpikeComponents.LINE.get(), new SpikeLine(operations));
        return copy;
    }
}
