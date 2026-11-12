package io.github.brainage04.simpletpa.event;

import io.github.brainage04.simpletpa.SimpleTPA;
import io.github.brainage04.simpletpa.command.TPRequestCommand;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ModTickEvents {
    public static final int REQUEST_LIFETIME_TICKS = 60 * 20;

    public static void onEndServerTick(MinecraftServer server) {
        long time = server.overworld().getGameTime();

        TPRequestCommand.TP_REQUESTS.removeIf(
                tpRequest -> {
                    if (time - tpRequest.created() < REQUEST_LIFETIME_TICKS) {
                        return false;
                    }

                    ServerPlayer to = server.getPlayerList().getPlayer(tpRequest.toId());

                    if (to != null) {
                        SimpleTPA.FEEDBACK.error(
                                to,
                                "The TP request that %s sent you has expired.",
                                tpRequest.fromName());
                        SimpleTPA.FEEDBACK.click(to);
                    }

                    ServerPlayer from = server.getPlayerList().getPlayer(tpRequest.fromId());

                    if (from != null) {
                        SimpleTPA.FEEDBACK.error(
                                from,
                                "The TP request that you sent to %s has expired.",
                                tpRequest.toName());
                        SimpleTPA.FEEDBACK.click(from);
                    }

                    return true;
                });
    }
}
