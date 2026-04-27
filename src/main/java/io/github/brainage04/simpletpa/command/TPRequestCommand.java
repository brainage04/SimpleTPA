package io.github.brainage04.simpletpa.command;

import io.github.brainage04.simpletpa.SimpleTPA;
import io.github.brainage04.simpletpa.data.InstantTpaWhitelist;
import io.github.brainage04.simpletpa.util.TPAFeedback;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class TPRequestCommand {
	public static final List<TPRequest> TP_REQUESTS = new ArrayList<>();

	public static int execute(CommandSourceStack source, ServerPlayer to) {
		ServerPlayer from = source.getPlayer();

		if (from == null) {
			TPAFeedback.fail(source, "This command can only be used by players!");
			return -1;
		}

		TPAFeedback.click(from);

		if (from.equals(to)) {
			TPAFeedback.fail(source, "You cannot send a TP request to yourself!");
			return -1;
		}

		TPRequest request = TPRequest.create(to, from, source.getLevel().getGameTime());

		for (TPRequest prevRequest : TP_REQUESTS) {
			if (prevRequest.matches(request.toId(), request.fromId())) {
				TPAFeedback.fail(source, "You have already sent a TP request to %s!", to.getScoreboardName());
				return -1;
			}
		}

		if (source.getServer().getGameRules().get(SimpleTPA.ALLOW_INSTANT_TPA_ACCEPTING)
				&& InstantTpaWhitelist.allows(to, from)) {
			teleport(from, to);
			TPAFeedback.success(from, "%s has auto-accepted your TP request.", to.getScoreboardName());
			TPAFeedback.success(to, "%s instantly teleported to you from your auto-accept whitelist.", from.getScoreboardName());
			TPAFeedback.click(to);
			return 1;
		}

		TP_REQUESTS.add(request);

		TPAFeedback.neutral(to, "%s has sent you a TP request. Use /tpaccept to accept, or /tpdeny to deny.", from.getScoreboardName());
		TPAFeedback.neutral(from, "You have sent %s a TP request.", to.getScoreboardName());
		TPAFeedback.click(to);
		return 1;
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
