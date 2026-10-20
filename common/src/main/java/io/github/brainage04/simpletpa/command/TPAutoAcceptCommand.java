package io.github.brainage04.simpletpa.command;

import io.github.brainage04.simpletpa.data.InstantTpaWhitelist;
import io.github.brainage04.simpletpa.SimpleTPA;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TPAutoAcceptCommand {
	/** How long after the accept-all warning a second run confirms it: 30 seconds at 20 ticks per second. */
	public static final int CONFIRM_WINDOW_TICKS = 30 * 20;
	/** Server tick at which each player was last warned about accept-all; cleared when the server stops. */
	public static final Map<UUID, Integer> PENDING_ACCEPT_ALL = new HashMap<>();

	/**
	 * Toggles accepting requests from everyone. Turning it on needs a second run within
	 * {@link #CONFIRM_WINDOW_TICKS}; turning it off is immediate.
	 *
	 * @param input the command as typed, echoed back so the player can repeat it
	 */
	public static int acceptAll(CommandSourceStack source, String input) {
		ServerPlayer owner = source.getPlayer();

		if (owner == null) {
			SimpleTPA.FEEDBACK.failure(source, "This command can only be used by players!");
			return -1;
		}

		SimpleTPA.FEEDBACK.click(owner);

		String command = input.startsWith("/") ? input.strip() : "/" + input.strip();

		if (InstantTpaWhitelist.acceptsAll(owner)) {
			InstantTpaWhitelist.setAcceptAll(owner, false);
			PENDING_ACCEPT_ALL.remove(owner.getUUID());
			if (!InstantTpaWhitelist.save(source.getServer())) {
				SimpleTPA.FEEDBACK.failure(source, "Accept-all is off for this session, but the change could not be saved.");
				return -1;
			}
			SimpleTPA.FEEDBACK.success(owner, "Accept-all is now off. Only players on your auto-accept whitelist can teleport to you without asking.");
			return 1;
		}

		int now = source.getServer().getTickCount();
		Integer warnedAt = PENDING_ACCEPT_ALL.remove(owner.getUUID());

		if (warnedAt == null || now - warnedAt > CONFIRM_WINDOW_TICKS) {
			PENDING_ACCEPT_ALL.put(owner.getUUID(), now);
			SimpleTPA.FEEDBACK.error(owner, "Warning: accept-all lets anyone on the server teleport to you at any time without asking.");
			SimpleTPA.FEEDBACK.neutral(owner, "Run %s again within %d seconds to turn it on.", command, CONFIRM_WINDOW_TICKS / 20);
			return 1;
		}

		InstantTpaWhitelist.setAcceptAll(owner, true);
		boolean saved = InstantTpaWhitelist.save(source.getServer());
		if (saved) {
			SimpleTPA.FEEDBACK.success(owner, "Accept-all is now on. Anyone on the server can teleport to you without asking. Run %s again to turn it off.", command);
		} else {
			SimpleTPA.FEEDBACK.failure(source, "Accept-all is on for this session, but the change could not be saved. Run %s again to turn it off.", command);
		}

		if (!source.getServer().getGameRules().get(SimpleTPA.AUTO_ACCEPT)) {
			SimpleTPA.FEEDBACK.neutral(owner, "It has no effect while the simpletpa:auto_accept gamerule is false.");
		}

		return saved ? 1 : -1;
	}

	public static int list(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();

		if (player == null) {
			SimpleTPA.FEEDBACK.failure(source, "This command can only be used by players!");
			return -1;
		}

		SimpleTPA.FEEDBACK.click(player);

		if (InstantTpaWhitelist.acceptsAll(player)) {
			SimpleTPA.FEEDBACK.neutral(player, "Accept-all is on: anyone on the server can teleport to you without asking.");
		} else {
			SimpleTPA.FEEDBACK.neutral(player, "Accept-all is off.");
		}

		List<String> allowedPlayers = InstantTpaWhitelist.list(player);

		if (allowedPlayers.isEmpty()) {
			SimpleTPA.FEEDBACK.neutral(player, "Your auto-accept whitelist is empty.");
			return 1;
		}

		SimpleTPA.FEEDBACK.neutral(player, "Your auto-accept whitelist:");

		for (String allowedPlayer : allowedPlayers) {
			SimpleTPA.FEEDBACK.neutral(player, "- %s", allowedPlayer);
		}

		return 1;
	}

	public static int add(CommandSourceStack source, ServerPlayer requester) {
		ServerPlayer owner = source.getPlayer();

		if (owner == null) {
			SimpleTPA.FEEDBACK.failure(source, "This command can only be used by players!");
			return -1;
		}

		SimpleTPA.FEEDBACK.click(owner);

		if (owner.equals(requester)) {
			SimpleTPA.FEEDBACK.failure(source, "You cannot add yourself to your auto-accept whitelist.");
			return -1;
		}

		boolean added = InstantTpaWhitelist.add(owner, requester);
		boolean saved = InstantTpaWhitelist.save(source.getServer());

		if (added && !saved) {
			SimpleTPA.FEEDBACK.failure(source, "%s can instantly teleport to you for this session, but your auto-accept whitelist could not be saved.", requester.getScoreboardName());
			return -1;
		} else if (added) {
			SimpleTPA.FEEDBACK.success(owner, "%s can now instantly teleport to you when the gamerule allows it.", requester.getScoreboardName());
		} else {
			SimpleTPA.FEEDBACK.neutral(owner, "%s is already on your auto-accept whitelist.", requester.getScoreboardName());
		}

		return 1;
	}

	public static int remove(CommandSourceStack source, ServerPlayer requester) {
		ServerPlayer owner = source.getPlayer();

		if (owner == null) {
			SimpleTPA.FEEDBACK.failure(source, "This command can only be used by players!");
			return -1;
		}

		SimpleTPA.FEEDBACK.click(owner);

		if (InstantTpaWhitelist.remove(owner, requester)) {
			if (!InstantTpaWhitelist.save(source.getServer())) {
				SimpleTPA.FEEDBACK.failure(source, "%s was removed from your auto-accept whitelist for this session, but the change could not be saved.", requester.getScoreboardName());
				return -1;
			}
			SimpleTPA.FEEDBACK.success(owner, "%s was removed from your auto-accept whitelist.", requester.getScoreboardName());
		} else {
			SimpleTPA.FEEDBACK.failure(source, "%s is not on your auto-accept whitelist.", requester.getScoreboardName());
			return -1;
		}

		return 1;
	}

	public static int clear(CommandSourceStack source) {
		ServerPlayer owner = source.getPlayer();

		if (owner == null) {
			SimpleTPA.FEEDBACK.failure(source, "This command can only be used by players!");
			return -1;
		}

		SimpleTPA.FEEDBACK.click(owner);

		if (InstantTpaWhitelist.clear(owner)) {
			if (!InstantTpaWhitelist.save(source.getServer())) {
				SimpleTPA.FEEDBACK.failure(source, "Your auto-accept whitelist has been cleared for this session, but the change could not be saved.");
				return -1;
			}
			SimpleTPA.FEEDBACK.success(owner, "Your auto-accept whitelist has been cleared.");
		} else {
			SimpleTPA.FEEDBACK.neutral(owner, "Your auto-accept whitelist is already empty.");
		}

		return 1;
	}
}
