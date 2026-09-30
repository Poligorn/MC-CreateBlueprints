package com.blueprintforge.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Action bar for the player at the machine when a copy has 5, 2, or 1 runs left. */
public final class CopyRunWarning {
    /** Far enough that the operator at the station sees it, short of announcing the base. */
    private static final double REACH_SQR = 16.0 * 16.0;

    private CopyRunWarning() {
    }

    public static Component text(int runsRemaining) {
        return Component.translatable("message.blueprintforge.copy_runs", runsRemaining);
    }

    public static void near(ServerLevel level, BlockPos pos, int runsRemaining) {
        if (!ProductionMath.warnRuns(runsRemaining)) {
            return;
        }
        Component message = text(runsRemaining);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= REACH_SQR) {
                player.displayClientMessage(message, true);
            }
        }
    }
}
