package io.github.brainage04.simpletpa.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class TPAcceptCommand {
    public static int execute(CommandSourceStack source) {
        ServerPlayer to = source.getPlayer();
        if (to == null) {
            source.sendFailure(Component.literal("This command can only be used by players!"));
            return -1;
        }

        List<TPRequestCommand.TPRequest> tpRequests = new ArrayList<>();

        for (TPRequestCommand.TPRequest tpRequest : TPRequestCommand.TP_REQUESTS) {
            if (tpRequest.to.equals(to.getScoreboardName())) {
                tpRequests.add(tpRequest);
            }
        }

        if (tpRequests.isEmpty()) {
            source.sendFailure(Component.literal("You have no incoming TP requests!"));
            return -1;
        }

        if (tpRequests.size() > 1) {
            source.sendFailure(Component.literal("You have more than 1 incoming TP request, specifically from the following players:"));
            for (TPRequestCommand.TPRequest tpRequest : tpRequests) {
                source.sendFailure(Component.literal("- %s".formatted(tpRequest.from)));
            }
            source.sendFailure(Component.literal("Please specify which one you wish to accept using /tpaccept <name>!"));
            return -1;
        }

        ServerPlayer from = source.getServer().getPlayerList().getPlayerByName(tpRequests.getFirst().from);
        if (from == null) {
            source.sendFailure(Component.literal("%s is not online!".formatted(tpRequests.getFirst().from)));
            return -1;
        }

        return execute(source, from);
    }

    public static int execute(CommandSourceStack source, ServerPlayer from) {
        ServerPlayer to = source.getPlayer();
        if (to == null) {
            source.sendFailure(Component.literal("This command can only be used by players!"));
            return -1;
        }

        for (int i = 0; i < TPRequestCommand.TP_REQUESTS.size(); i++) {
            TPRequestCommand.TPRequest tpRequest = TPRequestCommand.TP_REQUESTS.get(i);

            if (tpRequest.to.equals(to.getScoreboardName()) && tpRequest.from.equals(from.getScoreboardName())) {
                to.sendSystemMessage(Component.literal("You accepted %s's TP request.".formatted(from.getScoreboardName())));
                from.sendSystemMessage(Component.literal("%s accepted your TP request.".formatted(to.getScoreboardName())));

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

                TPRequestCommand.TP_REQUESTS.remove(i);

                return 0;
            }
        }

        source.sendFailure(Component.literal("You do not have a TP request from %s!".formatted(from.getScoreboardName())));
        return -1;
    }
}
