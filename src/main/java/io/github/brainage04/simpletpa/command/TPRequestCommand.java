package io.github.brainage04.simpletpa.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class TPRequestCommand {
    public static class TPRequest {
        public String to;
        public String from;
        public long created;

        public TPRequest(String to, String from, long created) {
            this.to = to;
            this.from = from;
            this.created = created;
        }
    }

    // todo: fix being able to send multiple requests to the same player and having them all stored in the hashset
    public static final List<TPRequest> TP_REQUESTS = new ArrayList<>();

    public static int execute(CommandSourceStack source, ServerPlayer to) {
        ServerPlayer from = source.getPlayer();
        if (from == null) {
            source.sendFailure(Component.literal("This command can only be used by players!"));
            return -1;
        }

        if (from.equals(to)) {
            source.sendFailure(Component.literal("You cannot sent a TP request to yourself!"));
            return -1;
        }

        String toName = to.getScoreboardName();
        String fromName = from.getScoreboardName();

        TPRequest request = new TPRequest(toName, fromName, source.getLevel().getGameTime());

        for (TPRequest prevRequest : TP_REQUESTS) {
            if (prevRequest.to.equals(request.to) && prevRequest.from.equals(request.from)) {
                source.sendFailure(Component.literal("You have already sent a TP request to %s!".formatted(toName)));
                return -1;
            }
        }

        TP_REQUESTS.add(request);

        to.sendSystemMessage(Component.literal("%s has sent you a TP request. Use /tpaccept to accept, or /tpdeny to deny.".formatted(fromName)));
        from.sendSystemMessage(Component.literal("You have sent %s a TP request.".formatted(toName)));
        return 0;
    }
}
