package io.github.brainage04.simpletpa.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.TeleportCommand;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ModCommands {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("tpaccept")
                    .executes(context ->
                            TPAcceptCommand.execute(
                                    context.getSource()
                            )
                    )
                    .then(argument("name", EntityArgumentType.player())
                            .executes(context ->
                                    TPAcceptCommand.execute(
                                            context.getSource(),
                                            EntityArgumentType.getPlayer(context, "name")
                                    )
                            )
                    )
            );

            dispatcher.register(literal("tpdeny")
                    .executes(context ->
                            TPDenyCommand.execute(
                                    context.getSource()
                            )
                    )
                    .then(argument("name", EntityArgumentType.player())
                            .executes(context ->
                                    TPDenyCommand.execute(
                                            context.getSource(),
                                            EntityArgumentType.getPlayer(context, "name")
                                    )
                            )
                    )
            );

            dispatcher.register(literal("tprequest")
                    .then(argument("name", EntityArgumentType.player())
                            .executes(context ->
                                    TPRequestCommand.execute(
                                            context.getSource(),
                                            EntityArgumentType.getPlayer(context, "name")
                                    )
                            )
                    )
            );
        }));
    }
}
