package com.blueprintforge.command;

import java.util.UUID;

import com.blueprintforge.BFConfig;
import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintDataLoader;
import com.blueprintforge.item.BlueprintItem;
import com.blueprintforge.registry.BFItems;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Hand-to-hand transfer with a second confirmation, plus the transfer journal and datapack check. */
public final class BFCommands {
    private BFCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bf")
                .then(Commands.literal("transfer")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> offer(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
                        .then(Commands.literal("accept")
                                .executes(ctx -> accept(ctx.getSource()))))
                .then(Commands.literal("log").executes(ctx -> log(ctx.getSource())))
                .then(Commands.literal("validate").requires(source -> source.hasPermission(2))
                        .executes(ctx -> validate(ctx.getSource()))));
    }

    private static int offer(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer from;
        try {
            from = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("command.blueprintforge.players_only"));
            return 0;
        }
        if (from.getUUID().equals(target.getUUID())) {
            source.sendFailure(Component.translatable("command.blueprintforge.transfer.self"));
            return 0;
        }
        ItemStack hand = from.getMainHandItem();
        BlueprintData data = BlueprintItem.data(hand).orElse(null);
        if (!hand.is(BFItems.BLUEPRINT.get()) || data == null || data.isUnissued()) {
            source.sendFailure(Component.translatable("command.blueprintforge.transfer.no_document"));
            return 0;
        }
        long expires = System.currentTimeMillis() + BFConfig.transferTimeoutSeconds() * 1000L;
        TransferLog.pending(from.serverLevel()).offer(from.getUUID(), target.getUUID(), hand.copy(), data.instanceId(), expires);
        Component name = hand.getHoverName();
        from.sendSystemMessage(Component.translatable("command.blueprintforge.transfer.offered", name, target.getName()));
        target.sendSystemMessage(Component.translatable("command.blueprintforge.transfer.incoming", name, from.getName()));
        return 1;
    }

    private static int accept(CommandSourceStack source) {
        ServerPlayer to;
        try {
            to = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("command.blueprintforge.players_only"));
            return 0;
        }
        TransferLog.Pending pending = TransferLog.pending(to.serverLevel()).take(to.getUUID());
        if (pending == null) {
            source.sendFailure(Component.translatable("command.blueprintforge.transfer.none"));
            return 0;
        }
        ServerPlayer from = to.server.getPlayerList().getPlayer(pending.from());
        if (from == null) {
            source.sendFailure(Component.translatable("command.blueprintforge.transfer.gone"));
            return 0;
        }
        int slot = find(from, pending.instanceId());
        if (slot < 0) {
            source.sendFailure(Component.translatable("command.blueprintforge.transfer.missing"));
            return 0;
        }
        ItemStack stack = from.getInventory().removeItem(slot, 1);
        if (!to.getInventory().add(stack)) {
            from.getInventory().add(stack);
            source.sendFailure(Component.translatable("command.blueprintforge.transfer.full"));
            return 0;
        }
        TransferLog.get(to.serverLevel()).append(from.getUUID(), from.getGameProfile().getName(), to.getUUID(),
                to.getGameProfile().getName(), pending.blueprintId(), pending.instanceId(), pending.materialEfficiency(), pending.timeEfficiency());
        from.sendSystemMessage(Component.translatable("command.blueprintforge.transfer.done", stack.getHoverName(), to.getName()));
        to.sendSystemMessage(Component.translatable("command.blueprintforge.transfer.received", stack.getHoverName(), from.getName()));
        return 1;
    }

    private static int find(ServerPlayer player, UUID instanceId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (BlueprintItem.data(stack).map(data -> instanceId.equals(data.instanceId())).orElse(false)) {
                return slot;
            }
        }
        return -1;
    }

    private static int log(CommandSourceStack source) {
        var entries = TransferLog.get(source.getLevel()).entries();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.blueprintforge.log.empty"), false);
            return 1;
        }
        int shown = Math.min(20, entries.size());
        for (int i = entries.size() - shown; i < entries.size(); i++) {
            TransferLog.Entry entry = entries.get(i);
            source.sendSuccess(() -> Component.translatable("command.blueprintforge.log.line",
                    entry.time(), entry.fromName(), entry.toName(), entry.blueprintId().toString(),
                    entry.instanceId().toString(), entry.materialEfficiency(), entry.timeEfficiency()), false);
        }
        return 1;
    }

    private static int validate(CommandSourceStack source) {
        var errors = BlueprintDataLoader.lastErrors();
        if (errors.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.blueprintforge.validate.ok"), false);
            return 1;
        }
        BlueprintForge.LOGGER.info("bf validate: {} datapack errors", errors.size());
        for (String error : errors) {
            source.sendSuccess(() -> Component.literal(error), false);
            BlueprintForge.LOGGER.info("bf validate: {}", error);
        }
        return errors.size();
    }
}
