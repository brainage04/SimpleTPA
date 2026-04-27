package io.github.brainage04.simpletpa.command;

import io.github.brainage04.simpletpa.data.InstantTpaWhitelist;
import io.github.brainage04.simpletpa.util.TPAFeedback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class TPAutoAcceptCommand {
	public static int list(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();

		if (player == null) {
			TPAFeedback.fail(source, "This command can only be used by players!");
			return -1;
		}

		TPAFeedback.click(player);

		List<String> allowedPlayers = InstantTpaWhitelist.list(player);

		if (allowedPlayers.isEmpty()) {
			TPAFeedback.neutral(player, "Your auto-accept whitelist is empty.");
			return 1;
		}

		TPAFeedback.neutral(player, "Your auto-accept whitelist:");

		for (String allowedPlayer : allowedPlayers) {
			TPAFeedback.neutral(player, "- %s", allowedPlayer);
		}

		return 1;
	}

	public static int add(CommandSourceStack source, ServerPlayer requester) {
		ServerPlayer owner = source.getPlayer();

		if (owner == null) {
			TPAFeedback.fail(source, "This command can only be used by players!");
			return -1;
		}

		TPAFeedback.click(owner);

		if (owner.equals(requester)) {
			TPAFeedback.fail(source, "You cannot add yourself to your auto-accept whitelist.");
			return -1;
		}

		boolean added = InstantTpaWhitelist.add(owner, requester);
		InstantTpaWhitelist.save(source.getServer());

		if (added) {
			TPAFeedback.success(owner, "%s can now instantly teleport to you when the gamerule allows it.", requester.getScoreboardName());
		} else {
			TPAFeedback.neutral(owner, "%s is already on your auto-accept whitelist.", requester.getScoreboardName());
		}

		return 1;
	}

	public static int remove(CommandSourceStack source, ServerPlayer requester) {
		ServerPlayer owner = source.getPlayer();

		if (owner == null) {
			TPAFeedback.fail(source, "This command can only be used by players!");
			return -1;
		}

		TPAFeedback.click(owner);

		if (InstantTpaWhitelist.remove(owner, requester)) {
			InstantTpaWhitelist.save(source.getServer());
			TPAFeedback.success(owner, "%s was removed from your auto-accept whitelist.", requester.getScoreboardName());
		} else {
			TPAFeedback.fail(source, "%s is not on your auto-accept whitelist.", requester.getScoreboardName());
			return -1;
		}

		return 1;
	}

	public static int clear(CommandSourceStack source) {
		ServerPlayer owner = source.getPlayer();

		if (owner == null) {
			TPAFeedback.fail(source, "This command can only be used by players!");
			return -1;
		}

		TPAFeedback.click(owner);

		if (InstantTpaWhitelist.clear(owner)) {
			InstantTpaWhitelist.save(source.getServer());
			TPAFeedback.success(owner, "Your auto-accept whitelist has been cleared.");
		} else {
			TPAFeedback.neutral(owner, "Your auto-accept whitelist is already empty.");
		}

		return 1;
	}
}
