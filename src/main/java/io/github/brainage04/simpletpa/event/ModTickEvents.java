package io.github.brainage04.simpletpa.event;

import io.github.brainage04.simpletpa.command.TPRequestCommand;
import io.github.brainage04.simpletpa.SimpleTPA;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

public class ModTickEvents {
	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long time = server.overworld().getGameTime();

			TPRequestCommand.TP_REQUESTS.removeIf(tpRequest -> {
				if (time - tpRequest.created() < 1200) {
					return false;
				}

				ServerPlayer to = server.getPlayerList().getPlayer(tpRequest.toId());

				if (to != null) {
					SimpleTPA.FEEDBACK.error(to, "The TP request that %s sent you has expired.", tpRequest.fromName());
					SimpleTPA.FEEDBACK.click(to);
				}

				ServerPlayer from = server.getPlayerList().getPlayer(tpRequest.fromId());

				if (from != null) {
					SimpleTPA.FEEDBACK.error(from, "The TP request that you sent to %s has expired.", tpRequest.toName());
					SimpleTPA.FEEDBACK.click(from);
				}

				return true;
			});
		});
	}
}
