package io.github.brainage04.simpletpa.command.core;

import io.github.brainage04.simpletpa.SimpleTPA;
import io.github.brainage04.simpletpa.command.TPAcceptCommand;
import io.github.brainage04.simpletpa.command.TPAutoAcceptCommand;
import io.github.brainage04.simpletpa.command.TPDenyCommand;
import io.github.brainage04.simpletpa.command.TPRequestCommand;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ModCommands {
	private ModCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
			dispatcher.register(literal("tpaccept")
					.executes(context -> TPAcceptCommand.execute(context.getSource()))
					.then(argument("name", EntityArgument.player())
							.executes(context -> TPAcceptCommand.execute(
									context.getSource(),
									EntityArgument.getPlayer(context, "name")
							))
					)
			);

			dispatcher.register(literal("tpdeny")
					.executes(context -> TPDenyCommand.execute(context.getSource()))
					.then(argument("name", EntityArgument.player())
							.executes(context -> TPDenyCommand.execute(
									context.getSource(),
									EntityArgument.getPlayer(context, "name")
							))
					)
			);

			dispatcher.register(literal("tprequest")
					.then(argument("name", EntityArgument.player())
							.executes(context -> TPRequestCommand.execute(
									context.getSource(),
									EntityArgument.getPlayer(context, "name")
							))
					)
			);

			dispatcher.register(literal("tpautoaccept")
					.executes(context -> TPAutoAcceptCommand.list(context.getSource()))
					.then(literal("list")
							.executes(context -> TPAutoAcceptCommand.list(context.getSource()))
					)
					.then(literal("add")
							.then(argument("name", EntityArgument.player())
									.executes(context -> TPAutoAcceptCommand.add(
											context.getSource(),
											EntityArgument.getPlayer(context, "name")
									))
							)
					)
					.then(literal("remove")
							.then(argument("name", EntityArgument.player())
									.executes(context -> TPAutoAcceptCommand.remove(
											context.getSource(),
											EntityArgument.getPlayer(context, "name")
									))
							)
					)
					.then(literal("clear")
							.executes(context -> TPAutoAcceptCommand.clear(context.getSource()))
					)
			);

			dispatcher.register(literal("simpletpa")
					.executes(context -> showHelp(context.getSource()))
					.then(literal("help")
							.executes(context -> showHelp(context.getSource()))
					)
			);
	}

	private static int showHelp(CommandSourceStack source) {
		SimpleTPA.FEEDBACK.neutral(source, "Player commands:");
		SimpleTPA.FEEDBACK.neutral(source, "/tprequest <player> — request to teleport to another player");
		SimpleTPA.FEEDBACK.neutral(source, "/tpaccept [player] — accept one incoming request");
		SimpleTPA.FEEDBACK.neutral(source, "/tpdeny [player] — deny one incoming request");
		SimpleTPA.FEEDBACK.neutral(source, "/tpautoaccept <list|add|remove|clear> — manage automatic approvals");
		return 1;
	}
}
