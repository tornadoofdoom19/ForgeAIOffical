package com.tyler.forgeai.core;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class TrustCommandRegistrar {

    private final CommunicationManager comms;

    public TrustCommandRegistrar(CommunicationManager comms) {
        this.comms = comms;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> root = literal("forgeai")
                .requires(src -> src.hasPermissionLevel(2)) // require operator-level by default

                // /forgeai trust add <player>
                .then(literal("trust").then(literal("add")
                    .then(argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            // Suggest current online players
                            var players = ctx.getSource().getServer().getPlayerManager().getPlayerList();
                            for (ServerPlayerEntity p : players) {
                                builder.suggest(p.getName().getString());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            comms.addTrusted(playerName);
                            ctx.getSource().sendFeedback(() ->
                                Text.literal("ForgeAI: added trusted player '" + playerName + "'"), true);
                            return 1;
                        })
                    )
                ))

                // /forgeai trust remove <player>
                .then(literal("trust").then(literal("remove")
                    .then(argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            // Suggest currently trusted players
                            for (String name : comms.getAllowedPlayers()) {
                                builder.suggest(name);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            comms.removeTrusted(playerName);
                            ctx.getSource().sendFeedback(() ->
                                Text.literal("ForgeAI: removed trusted player '" + playerName + "'"), true);
                            return 1;
                        })
                    )
                ))

                // /forgeai trust list
                .then(literal("trust").then(literal("list")
                    .executes(ctx -> {
                        var names = comms.getAllowedPlayers();
                        String out = names.isEmpty()
                            ? "No trusted players set."
                            : names.stream().sorted().collect(Collectors.joining(", "));
                        ctx.getSource().sendFeedback(() ->
                            Text.literal("ForgeAI trusted: " + out), false);
                        return 1;
                    })
                ));

            dispatcher.register(root);
        });
    }
}
