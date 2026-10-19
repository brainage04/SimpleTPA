package io.github.brainage04.simpletpa.command;

import io.github.brainage04.simpletpa.SimpleTPA;
import io.github.brainage04.simpletpa.data.InstantTpaWhitelist;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class TPRequestCommand {
	public static final List<TPRequest> TP_REQUESTS = new ArrayList<>();

	public static int execute(CommandSourceStack source, ServerPlayer to) {
		ServerPlayer from = source.getPlayer();

		if (from == null) {
			SimpleTPA.FEEDBACK.failure(source, "This command can only be used by players!");
			return -1;
		}

		SimpleTPA.FEEDBACK.click(from);

		if (from.equals(to)) {
			SimpleTPA.FEEDBACK.failure(source, "You cannot send a TP request to yourself!");
			return -1;
		}

		TPRequest request = TPRequest.create(to, from, source.getLevel().getGameTime());

		for (TPRequest prevRequest : TP_REQUESTS) {
			if (prevRequest.matches(request.toId(), request.fromId())) {
				SimpleTPA.FEEDBACK.failure(source, "You have already sent a TP request to %s!", to.getScoreboardName());
				return -1;
			}
		}

		if (source.getServer().getGameRules().get(SimpleTPA.AUTO_ACCEPT)
				&& InstantTpaWhitelist.allows(to, from)) {
			teleport(from, to);
			SimpleTPA.FEEDBACK.success(from, "%s has automatically accepted your TP request.", to.getScoreboardName());
			SimpleTPA.FEEDBACK.success(to, "%s has teleported to you.", from.getScoreboardName());
			SimpleTPA.FEEDBACK.click(to);
			return 1;
		}

		TP_REQUESTS.add(request);

		SimpleTPA.FEEDBACK.neutral(to, incomingRequestMessage(from.getScoreboardName()));
		SimpleTPA.FEEDBACK.neutral(from, "You have sent %s a TP request.", to.getScoreboardName());
		SimpleTPA.FEEDBACK.click(to);
		return 1;
	}

	/** The request notice for the target, with [ACCEPT] and [DENY] buttons that run the commands for this requester. */
	public static MutableComponent incomingRequestMessage(String fromName) {
		return Component.literal("%s has sent you a TP request. Use /tpaccept to accept, or /tpdeny to deny. ".formatted(fromName))
				.append(button("[ACCEPT]", ChatFormatting.GREEN, "/tpaccept " + fromName, "Click to accept %s's TP request".formatted(fromName)))
				.append(" ")
				.append(button("[DENY]", ChatFormatting.RED, "/tpdeny " + fromName, "Click to deny %s's TP request".formatted(fromName)));
	}

	private static MutableComponent button(String label, ChatFormatting color, String command, String hover) {
		return Component.literal(label).withStyle(style -> style
				.withColor(color)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	public static void teleport(ServerPlayer from, ServerPlayer to) {
		from.teleportTo(
				to.level(),
				to.getX(),
				to.getY(),
				to.getZ(),
				Set.of(),
				to.getYRot(),
				to.getXRot(),
				true
		);
	}

	public record TPRequest(UUID toId, String toName, UUID fromId, String fromName, long created) {
		public static TPRequest create(ServerPlayer to, ServerPlayer from, long created) {
			return new TPRequest(
					to.getUUID(),
					to.getScoreboardName(),
					from.getUUID(),
					from.getScoreboardName(),
					created
			);
		}

		public boolean matches(UUID toId, UUID fromId) {
			return this.toId.equals(toId) && this.fromId.equals(fromId);
		}
	}
}
