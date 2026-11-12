package io.github.brainage04.simpletpa.command;

import io.github.brainage04.simpletpa.SimpleTPA;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class TPResponseCommand {
    public enum Response {
        ACCEPT("accept", "accepted"),
        DENY("deny", "denied");

        private final String verb;
        private final String pastTense;

        Response(String verb, String pastTense) {
            this.verb = verb;
            this.pastTense = pastTense;
        }

        public String command() {
            return "tp" + verb;
        }
    }

    private TPResponseCommand() {}

    public static int respond(CommandSourceStack source, Response response) {
        ServerPlayer to = SimpleTPA.FEEDBACK.requirePlayer(source);
        if (to == null) {
            return -1;
        }

        List<TPRequestCommand.TPRequest> incoming =
                TPRequestCommand.TP_REQUESTS.stream()
                        .filter(request -> request.toId().equals(to.getUUID()))
                        .toList();

        if (incoming.isEmpty()) {
            SimpleTPA.FEEDBACK.failure(source, "You have no incoming TP requests!");
            return -1;
        }

        if (incoming.size() > 1) {
            SimpleTPA.FEEDBACK.failure(
                    source, "You have more than 1 incoming TP request, specifically from:");
            for (TPRequestCommand.TPRequest request : incoming) {
                SimpleTPA.FEEDBACK.error(to, "- %s", request.fromName());
            }
            SimpleTPA.FEEDBACK.failure(
                    source,
                    "Please specify which one you wish to %s using /%s <name>.",
                    response.verb,
                    response.command());
            return -1;
        }

        ServerPlayer from =
                source.getServer().getPlayerList().getPlayer(incoming.getFirst().fromId());
        if (from == null) {
            SimpleTPA.FEEDBACK.failure(source, "%s is not online!", incoming.getFirst().fromName());
            return -1;
        }

        return resolve(source, to, from, response);
    }

    public static int respond(CommandSourceStack source, ServerPlayer from, Response response) {
        ServerPlayer to = SimpleTPA.FEEDBACK.requirePlayer(source);
        if (to == null) {
            return -1;
        }
        return resolve(source, to, from, response);
    }

    private static int resolve(
            CommandSourceStack source, ServerPlayer to, ServerPlayer from, Response response) {
        for (int i = 0; i < TPRequestCommand.TP_REQUESTS.size(); i++) {
            if (TPRequestCommand.TP_REQUESTS.get(i).matches(to.getUUID(), from.getUUID())) {
                if (response == Response.ACCEPT) {
                    String blocked = TPRequestCommand.blockedReason(from, to);
                    if (blocked != null) {
                        SimpleTPA.FEEDBACK.failure(
                                source,
                                "%s can't teleport to you while you are %s. Their TP request is still open.",
                                from.getScoreboardName(),
                                blocked);
                        return -1;
                    }
                    if (!TPRequestCommand.teleport(from, to)) {
                        SimpleTPA.FEEDBACK.failure(
                                source,
                                "%s could not be teleported. Their TP request is still open.",
                                from.getScoreboardName());
                        return -1;
                    }
                }
                tell(
                        to,
                        response,
                        "You %s %s's TP request.",
                        response.pastTense,
                        from.getScoreboardName());
                tell(
                        from,
                        response,
                        "%s %s your TP request.",
                        to.getScoreboardName(),
                        response.pastTense);
                SimpleTPA.FEEDBACK.click(from);
                TPRequestCommand.TP_REQUESTS.remove(i);
                return 1;
            }
        }

        SimpleTPA.FEEDBACK.failure(
                source, "You do not have a TP request from %s!", from.getScoreboardName());
        return -1;
    }

    private static void tell(
            ServerPlayer player, Response response, String message, Object... arguments) {
        if (response == Response.ACCEPT) {
            SimpleTPA.FEEDBACK.success(player, message, arguments);
        } else {
            SimpleTPA.FEEDBACK.error(player, message, arguments);
        }
    }
}
