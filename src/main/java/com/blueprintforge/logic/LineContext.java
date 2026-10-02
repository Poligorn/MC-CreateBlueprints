package com.blueprintforge.logic;

import com.blueprintforge.data.LineMark;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Open only while a deployer is applying a recipe, so a recipe search that peeks at {@code advance}
 * cannot pay for a stamp.
 */
public final class LineContext {
    private static final ThreadLocal<Frame> FRAME = new ThreadLocal<>();

    private LineContext() {
    }

    public record Frame(ServerLevel level, BlockPos deployerPos, ItemStack stamp, LineMark mark, boolean stamped) {
        public Frame withMark(LineMark next) {
            return new Frame(level, deployerPos, stamp, next, true);
        }
    }

    public static void open(ServerLevel level, BlockPos deployerPos, ItemStack stamp) {
        FRAME.set(new Frame(level, deployerPos, stamp, null, false));
    }

    public static void close() {
        FRAME.remove();
    }

    public static boolean active() {
        return FRAME.get() != null;
    }

    public static Frame frame() {
        return FRAME.get();
    }

    public static void remember(LineMark mark) {
        Frame frame = FRAME.get();
        if (frame != null) {
            FRAME.set(frame.withMark(mark));
        }
    }
}
