package io.github.brainage04.simpletpa.command;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TPAcceptCommand {
    public static int execute(ServerCommandSource source) {
        ServerPlayerEntity to = source.getPlayer();
        if (to == null) {
            source.sendError(Text.literal("This command can only be used by players!"));
            return -1;
        }

        List<TPRequestCommand.TPRequest> tpRequests = new ArrayList<>();

        for (TPRequestCommand.TPRequest tpRequest : TPRequestCommand.TP_REQUESTS) {
            if (tpRequest.to.equals(to.getNameForScoreboard())) {
                tpRequests.add(tpRequest);
            }
        }

        if (tpRequests.isEmpty()) {
            source.sendError(Text.literal("You have no incoming TP requests!"));
            return -1;
        }

        if (tpRequests.size() > 1) {
            source.sendError(Text.literal("You have more than 1 incoming TP request, specifically from the following players:"));
            for (TPRequestCommand.TPRequest tpRequest : tpRequests) {
                source.sendError(Text.literal("- %s".formatted(tpRequest.from)));
            }
            source.sendError(Text.literal("Please specify which one you wish to accept using /tpaccept <name>!"));
            return -1;
        }

        ServerPlayerEntity from = source.getServer().getPlayerManager().getPlayer(tpRequests.getFirst().from);
        if (from == null) {
            source.sendError(Text.literal("%s is not online!".formatted(tpRequests.getFirst().from)));
            return -1;
        }

        return execute(source, from);
    }

    public static int execute(ServerCommandSource source, ServerPlayerEntity from) {
        ServerPlayerEntity to = source.getPlayer();
        if (to == null) {
            source.sendError(Text.literal("This command can only be used by players!"));
            return -1;
        }

        for (TPRequestCommand.TPRequest tpRequest : TPRequestCommand.TP_REQUESTS) {
            if (tpRequest.to.equals(to.getNameForScoreboard()) && tpRequest.from.equals(from.getNameForScoreboard())) {
                to.sendMessage(Text.literal("You accepted %s's TP request.".formatted(from.getNameForScoreboard())));
                from.sendMessage(Text.literal("%s accepted your TP request.".formatted(to.getNameForScoreboard())));

                from.teleport(
                        to.getWorld(),
                        to.getX(),
                        to.getY(),
                        to.getZ(),
                        Set.of(),
                        to.getYaw(),
                        to.getPitch(),
                        true
                );

                TPRequestCommand.TP_REQUESTS.remove(tpRequest);

                return 0;
            }
        }

        source.sendError(Text.literal("You do not have a TP request from %s!".formatted(from.getNameForScoreboard())));
        return -1;
    }
}
