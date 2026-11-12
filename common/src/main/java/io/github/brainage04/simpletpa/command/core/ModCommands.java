package io.github.brainage04.simpletpa.command.core;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.brainage04.brainagelib.help.HelpPage;
import io.github.brainage04.simpletpa.SimpleTPA;
import io.github.brainage04.simpletpa.command.TPAutoAcceptCommand;
import io.github.brainage04.simpletpa.command.TPRequestCommand;
import io.github.brainage04.simpletpa.command.TPResponseCommand;
import io.github.brainage04.simpletpa.data.InstantTpaWhitelist;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;

public final class ModCommands {
    private ModCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (TPResponseCommand.Response response : TPResponseCommand.Response.values()) {
            dispatcher.register(
                    literal(response.command())
                            .executes(
                                    context ->
                                            TPResponseCommand.respond(
                                                    context.getSource(), response))
                            .then(
                                    name(
                                            TPResponseCommand::senderNames,
                                            (source, player) ->
                                                    TPResponseCommand.respond(
                                                            source, player, response))));
        }

        LiteralCommandNode<CommandSourceStack> tpa =
                dispatcher.register(literal("tpa").then(player(TPRequestCommand::execute)));

        // Legacy name kept for existing users; redirects into /tpa's argument tree so it parses,
        // suggests and executes identically.
        dispatcher.register(literal("tprequest").redirect(tpa));

        Command<CommandSourceStack> acceptAll =
                context -> TPAutoAcceptCommand.acceptAll(context.getSource(), context.getInput());
        LiteralCommandNode<CommandSourceStack> tpautoaccept =
                dispatcher.register(
                        literal("tpautoaccept")
                                .executes(acceptAll)
                                .then(
                                        literal(InstantTpaWhitelist.ACCEPT_ALL_MARKER)
                                                .executes(acceptAll))
                                .then(
                                        literal("list")
                                                .executes(
                                                        context ->
                                                                TPAutoAcceptCommand.list(
                                                                        context.getSource())))
                                .then(literal("add").then(player(TPAutoAcceptCommand::add)))
                                .then(
                                        literal("remove")
                                                .then(
                                                        name(
                                                                ModCommands::whitelistedNames,
                                                                TPAutoAcceptCommand::remove)))
                                .then(
                                        literal("clear")
                                                .executes(
                                                        context ->
                                                                TPAutoAcceptCommand.clear(
                                                                        context.getSource()))));

        // Short alias. A redirect alone does not run when nothing follows the literal, so the
        // bare /tpauto form gets its own executes; everything after it goes through /tpautoaccept.
        dispatcher.register(literal("tpauto").executes(acceptAll).redirect(tpautoaccept));

        HelpPage.create(SimpleTPA.FEEDBACK, "simpletpa")
                .line(
                        "/tpa <player> - request to teleport to another player (/tprequest also works)")
                .line("/tpaccept [player] - accept one incoming request")
                .line("/tpdeny [player] - deny one incoming request")
                .line(
                        "/tpautoaccept <list|add|remove|clear> - manage automatic approvals (/tpauto also works)")
                .line(
                        "/tpautoaccept [*] - accept requests from everyone (run twice to confirm, again to turn off)")
                .register(dispatcher);
    }

    private static List<String> whitelistedNames(CommandSourceStack source) {
        ServerPlayer owner = source.getPlayer();
        return owner == null ? List.of() : InstantTpaWhitelist.list(owner);
    }

    /**
     * A player name or UUID read as plain text: unlike an entity selector it can name an offline
     * player, and it reads a name such as {@code @r} as that name.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> name(
            Function<CommandSourceStack, List<String>> suggestions,
            ToIntBiFunction<CommandSourceStack, String> command) {
        return argument("name", StringArgumentType.greedyString())
                .suggests(
                        (context, builder) ->
                                SharedSuggestionProvider.suggest(
                                        suggestions.apply(context.getSource()), builder))
                .executes(
                        context ->
                                command.applyAsInt(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> player(
            ToIntBiFunction<CommandSourceStack, ServerPlayer> command) {
        return argument("name", EntityArgument.player())
                .executes(
                        context ->
                                command.applyAsInt(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "name")));
    }
}
