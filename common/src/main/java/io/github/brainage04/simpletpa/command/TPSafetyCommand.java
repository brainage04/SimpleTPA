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

        SimpleTPA.FEEDBACK.neutral(
                player,
                "Your TP requests %s while the player you teleport to is in the air above a long drop.",
                SafetyPreference.refusesDangerousDrop(player) ? "wait" : "do not wait");
        return 1;
    }

    public static int set(CommandSourceStack source, boolean refuses) {
        ServerPlayer player = SimpleTPA.FEEDBACK.requirePlayer(source);
        if (player == null) {
            return -1;
        }

        if (!SafetyPreference.setRefusesDangerousDrop(player, refuses)) {
            return show(source);
        }

        if (!SafetyPreference.save()) {
            SimpleTPA.FEEDBACK.failure(
                    source,
                    refuses
                            ? "Your TP requests wait for this session, but your choice could not be saved."
                            : "Your TP requests go through for this session, but your choice could not be saved.");
            return -1;
        }
        SimpleTPA.FEEDBACK.success(
                player,
                refuses
                        ? "Your TP requests wait while the player you teleport to is in the air above a long drop."
                        : "Your TP requests go through while the player you teleport to is in the air above a long drop, so you will fall when you arrive.");
        return 1;
    }
}
