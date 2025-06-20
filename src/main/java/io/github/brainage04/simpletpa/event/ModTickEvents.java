package io.github.brainage04.simpletpa.event;

import io.github.brainage04.simpletpa.command.TPRequestCommand;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;

public class ModTickEvents {
    public static void initalize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long time = server.getOverworld().getTime();

            Set<TPRequestCommand.TPRequest> requestsToRemove = new HashSet<>();

            for (TPRequestCommand.TPRequest tpRequest : TPRequestCommand.TP_REQUESTS) {
                long elapsed = time - tpRequest.created;

                if (elapsed >= 1200) {
                    ServerPlayerEntity to = server.getPlayerManager().getPlayer(tpRequest.to);
                    if (to != null) {
                        to.sendMessage(Text.literal("The TP request that %s sent you has expired.".formatted(tpRequest.from)));
                    }

                    ServerPlayerEntity from = server.getPlayerManager().getPlayer(tpRequest.from);
                    if (from != null) {
                        from.sendMessage(Text.literal("The TP request that you sent to %s has expired.".formatted(tpRequest.to)));
                    }

                    requestsToRemove.add(tpRequest);
                }
            }

            if (!requestsToRemove.isEmpty()) {
                TPRequestCommand.TP_REQUESTS.removeAll(requestsToRemove);
            }
        });
    }
}
