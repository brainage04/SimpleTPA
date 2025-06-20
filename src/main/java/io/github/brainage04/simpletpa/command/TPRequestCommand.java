package io.github.brainage04.simpletpa.command;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashSet;

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

    public static final HashSet<TPRequest> TP_REQUESTS = new HashSet<>();

    public static int execute(ServerCommandSource source, ServerPlayerEntity to) {
        ServerPlayerEntity from = source.getPlayer();
        if (from == null) {
            source.sendError(Text.literal("This command can only be used by players!"));
            return -1;
        }

        if (from.equals(to)) {
            source.sendError(Text.literal("You cannot sent a TP request to yourself!"));
            return -1;
        }

        String toName = to.getNameForScoreboard();
        String fromName = from.getNameForScoreboard();

        if (!TP_REQUESTS.add(new TPRequest(toName, fromName, source.getWorld().getTime()))) {
            source.sendError(Text.literal("You have already sent a TP request to %s!".formatted(toName)));
            return -1;
        }

        to.sendMessage(Text.literal("%s has sent you a TP request. Use /tpaccept to accept, or /tpdeny to deny.".formatted(fromName)));
        from.sendMessage(Text.literal("You have sent %s a TP request.".formatted(toName)));
        return 0;
    }
}
