package io.github.brainage04.simpletpa.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.arguments.EntityArgument;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ModCommands {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("tpaccept")
                    .executes(context ->
                            TPAcceptCommand.execute(
                                    context.getSource()
                            )
                    )
                    .then(argument("name", EntityArgument.player())
                            .executes(context ->
                                    TPAcceptCommand.execute(
                                            context.getSource(),
                                            EntityArgument.getPlayer(context, "name")
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
                    .then(argument("name", EntityArgument.player())
                            .executes(context ->
                                    TPDenyCommand.execute(
                                            context.getSource(),
                                            EntityArgument.getPlayer(context, "name")
                                    )
                            )
                    )
            );

            dispatcher.register(literal("tprequest")
                    .then(argument("name", EntityArgument.player())
                            .executes(context ->
                                    TPRequestCommand.execute(
                                            context.getSource(),
                                            EntityArgument.getPlayer(context, "name")
                                    )
                            )
                    )
            );
        }));
    }
}
