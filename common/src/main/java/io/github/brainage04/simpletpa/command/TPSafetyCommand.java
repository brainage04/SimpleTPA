package io.github.brainage04.simpletpa.command;

import io.github.brainage04.simpletpa.SimpleTPA;
import io.github.brainage04.simpletpa.data.SafetyPreference;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** The requester's own choice of whether a long drop below the target stops their TP request. */
public class TPSafetyCommand {
    public static int show(CommandSourceStack source) {
        ServerPlayer player = SimpleTPA.FEEDBACK.requirePlayer(source);
        if (player == null) {
            return -1;
        }
        return show(player);
    }

    private static int show(ServerPlayer player) {
        if (landingChecksOff(player)) {
            SimpleTPA.FEEDBACK.neutral(
                    player,
                    "Your TP requests do not wait: this server has turned landing checks off.");
            return 1;
        }
        SimpleTPA.FEEDBACK.neutral(
                player, waitingMessage(SafetyPreference.refusesDangerousDrop(player)));
        return 1;
    }

    public static int set(CommandSourceStack source, boolean refuses) {
        ServerPlayer player = SimpleTPA.FEEDBACK.requirePlayer(source);
        if (player == null) {
            return -1;
        }

        if (!SafetyPreference.setRefusesDangerousDrop(player, refuses)) {
            return show(player);
        }

        boolean off = landingChecksOff(player);
        if (!SafetyPreference.save()) {
            SimpleTPA.FEEDBACK.failure(source, savedMessage(off, refuses, false));
            return -1;
        }
        SimpleTPA.FEEDBACK.success(player, savedMessage(off, refuses, true));
        return 1;
    }

    private static String waitingMessage(boolean refuses) {
        return refuses
                ? "Your TP requests warn you while the player you teleport to is in the air above a long drop, and go through if you run the command again within "
                        + TPRequestCommand.LONG_DROP_CONFIRM_WINDOW_TICKS / 20
                        + " seconds."
                : "Your TP requests go through while the player you teleport to is in the air above a long drop, so you will fall when you arrive.";
    }

    private static String savedMessage(boolean landingChecksOff, boolean refuses, boolean saved) {
        if (landingChecksOff) {
            return saved
                    ? "Your choice is saved, but this server has turned landing checks off, so your TP requests do not wait."
                    : "Your choice could not be saved, and this server has turned landing checks off, so your TP requests do not wait.";
        }
        if (saved) {
            return waitingMessage(refuses);
        }
        return refuses
                ? "Your TP requests warn you for this session, but your choice could not be saved."
                : "Your TP requests go through for this session, but your choice could not be saved.";
    }

    private static boolean landingChecksOff(ServerPlayer player) {
        return !player.level().getGameRules().get(SimpleTPA.LANDING_CHECKS);
    }
}
