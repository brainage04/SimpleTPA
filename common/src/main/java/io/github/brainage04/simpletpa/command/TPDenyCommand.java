package io.github.brainage04.simpletpa.command;

import io.github.brainage04.simpletpa.SimpleTPA;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class TPDenyCommand {
	public static int execute(CommandSourceStack source) {
		ServerPlayer to = source.getPlayer();

		if (to == null) {
			SimpleTPA.FEEDBACK.failure(source, "This command can only be used by players!");
			return -1;
		}

		SimpleTPA.FEEDBACK.click(to);

		List<TPRequestCommand.TPRequest> tpRequests = new ArrayList<>();

		for (TPRequestCommand.TPRequest tpRequest : TPRequestCommand.TP_REQUESTS) {
			if (tpRequest.toId().equals(to.getUUID())) {
				tpRequests.add(tpRequest);
			}
		}

		if (tpRequests.isEmpty()) {
			SimpleTPA.FEEDBACK.failure(source, "You have no incoming TP requests!");
			return -1;
		}

		if (tpRequests.size() > 1) {
			SimpleTPA.FEEDBACK.failure(source, "You have more than 1 incoming TP request, specifically from:");

			for (TPRequestCommand.TPRequest tpRequest : tpRequests) {
				SimpleTPA.FEEDBACK.error(to, "- %s", tpRequest.fromName());
			}

			SimpleTPA.FEEDBACK.failure(source, "Please specify which one you wish to deny using /tpdeny <name>.");
			return -1;
		}

		ServerPlayer from = source.getServer().getPlayerList().getPlayer(tpRequests.getFirst().fromId());

		if (from == null) {
			SimpleTPA.FEEDBACK.failure(source, "%s is not online!", tpRequests.getFirst().fromName());
			return -1;
		}

		return execute(source, from, false);
	}

	public static int execute(CommandSourceStack source, ServerPlayer from) {
		return execute(source, from, true);
	}

	private static int execute(CommandSourceStack source, ServerPlayer from, boolean playCommandSound) {
		ServerPlayer to = source.getPlayer();

		if (to == null) {
			SimpleTPA.FEEDBACK.failure(source, "This command can only be used by players!");
			return -1;
		}

		if (playCommandSound) {
			SimpleTPA.FEEDBACK.click(to);
		}

		for (int i = 0; i < TPRequestCommand.TP_REQUESTS.size(); i++) {
			TPRequestCommand.TPRequest tpRequest = TPRequestCommand.TP_REQUESTS.get(i);

			if (tpRequest.matches(to.getUUID(), from.getUUID())) {
				SimpleTPA.FEEDBACK.error(to, "You denied %s's TP request.", from.getScoreboardName());
				SimpleTPA.FEEDBACK.error(from, "%s denied your TP request.", to.getScoreboardName());
				SimpleTPA.FEEDBACK.click(from);

				TPRequestCommand.TP_REQUESTS.remove(i);

				return 1;
			}
		}

		SimpleTPA.FEEDBACK.failure(source, "You do not have a TP request from %s!", from.getScoreboardName());
		return -1;
	}
}
