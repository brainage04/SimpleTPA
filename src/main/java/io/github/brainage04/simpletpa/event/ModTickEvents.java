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
    
            TPRequestCommand.TP_REQUESTS.removeIf(tpRequest -> {
                if (time - tpRequest.created < 1200) return false;
    
                ServerPlayer to = server.getPlayerList().getPlayerByName(tpRequest.to);
                if (to != null) to.sendSystemMessage(
                    Component.literal("The TP request that %s sent you has expired.".formatted(tpRequest.from))
                );
    
                ServerPlayer from = server.getPlayerList().getPlayerByName(tpRequest.from);
                if (from != null) from.sendSystemMessage(
                    Component.literal("The TP request that you sent to %s has expired.".formatted(tpRequest.to))
                );
    
                return true;
            });
        });
    }
}
