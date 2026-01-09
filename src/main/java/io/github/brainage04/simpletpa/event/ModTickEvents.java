package io.github.brainage04.simpletpa.event;

import io.github.brainage04.simpletpa.command.TPRequestCommand;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashSet;
import java.util.Set;

public class ModTickEvents {
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long time = server.overworld().getGameTime();

            Set<Integer> requestsToRemove = new HashSet<>();

            for (int i = 0; i < TPRequestCommand.TP_REQUESTS.size(); i++) {
                TPRequestCommand.TPRequest tpRequest = TPRequestCommand.TP_REQUESTS.get(i);

                long elapsed = time - tpRequest.created;

                if (elapsed >= 1200) {
                    ServerPlayer to = server.getPlayerList().getPlayerByName(tpRequest.to);
                    if (to != null) {
                        to.sendSystemMessage(Component.literal("The TP request that %s sent you has expired.".formatted(tpRequest.from)));
                    }

                    ServerPlayer from = server.getPlayerList().getPlayerByName(tpRequest.from);
                    if (from != null) {
                        from.sendSystemMessage(Component.literal("The TP request that you sent to %s has expired.".formatted(tpRequest.to)));
                    }

                    requestsToRemove.add(i);
                }
            }

            if (!requestsToRemove.isEmpty()) {
                for (int i : requestsToRemove) {
                    TPRequestCommand.TP_REQUESTS.remove(i);
                }
            }
        });
    }
}
