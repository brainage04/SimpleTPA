package io.github.brainage04.simpletpa.command;

import io.github.brainage04.brainagelib.confirmation.ConfirmationWindow;
import io.github.brainage04.simpletpa.SimpleTPA;
import io.github.brainage04.simpletpa.data.InstantTpaWhitelist;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class TPAutoAcceptCommand {
    public static final int CONFIRM_WINDOW_TICKS = 30 * 20;
    public static final ConfirmationWindow<UUID> ACCEPT_ALL_CONFIRMATION =
            new ConfirmationWindow<>(CONFIRM_WINDOW_TICKS, ConfirmationWindow.Boundary.INCLUSIVE);

    public static int acceptAll(CommandSourceStack source, String input) {
        ServerPlayer owner = SimpleTPA.FEEDBACK.requirePlayer(source);
        if (owner == null) {
            return -1;
        }

        String command = input.startsWith("/") ? input.strip() : "/" + input.strip();

        if (InstantTpaWhitelist.acceptsAll(owner)) {
            InstantTpaWhitelist.setAcceptAll(owner, false);
            ACCEPT_ALL_CONFIRMATION.cancel(owner.getUUID());
            if (!InstantTpaWhitelist.save()) {
                SimpleTPA.FEEDBACK.failure(
                        source,
                        "Accept-all is off for this session, but the change could not be saved.");
                return -1;
            }
            SimpleTPA.FEEDBACK.success(
                    owner,
                    "Accept-all is now off. Only players on your auto-accept whitelist can teleport to you without asking.");
            return 1;
        }

        if (ACCEPT_ALL_CONFIRMATION.requestOrConfirm(
                        owner.getUUID(), source.getServer().getTickCount())
                == ConfirmationWindow.Outcome.REQUESTED) {
            SimpleTPA.FEEDBACK.error(
                    owner,
                    "Warning: accept-all lets anyone on the server teleport to you at any time without asking.");
            SimpleTPA.FEEDBACK.neutral(
                    owner,
                    "Run %s again within %d seconds to turn it on.",
                    command,
                    CONFIRM_WINDOW_TICKS / 20);
            return 1;
        }

        InstantTpaWhitelist.setAcceptAll(owner, true);
        boolean saved = InstantTpaWhitelist.save();
        if (saved) {
            SimpleTPA.FEEDBACK.success(
                    owner,
                    "Accept-all is now on. Anyone on the server can teleport to you without asking. Run %s again to turn it off.",
                    command);
        } else {
            SimpleTPA.FEEDBACK.failure(
                    source,
                    "Accept-all is on for this session, but the change could not be saved. Run %s again to turn it off.",
                    command);
        }

        if (!source.getServer().getGameRules().get(SimpleTPA.AUTO_ACCEPT)) {
            SimpleTPA.FEEDBACK.neutral(
                    owner, "It has no effect while the simpletpa:auto_accept gamerule is false.");
        }

        return saved ? 1 : -1;
    }

    public static int list(CommandSourceStack source) {
        ServerPlayer player = SimpleTPA.FEEDBACK.requirePlayer(source);
        if (player == null) {
            return -1;
        }

        if (InstantTpaWhitelist.acceptsAll(player)) {
            SimpleTPA.FEEDBACK.neutral(
                    player,
                    "Accept-all is on: anyone on the server can teleport to you without asking.");
        } else {
            SimpleTPA.FEEDBACK.neutral(player, "Accept-all is off.");
        }

        List<String> allowedPlayers = InstantTpaWhitelist.list(player);

        if (allowedPlayers.isEmpty()) {
            SimpleTPA.FEEDBACK.neutral(player, "Your auto-accept whitelist is empty.");
            return 1;
        }

        SimpleTPA.FEEDBACK.neutral(player, "Your auto-accept whitelist:");

        for (String allowedPlayer : allowedPlayers) {
            SimpleTPA.FEEDBACK.neutral(player, "- %s", allowedPlayer);
        }

        return 1;
    }

    public static int add(CommandSourceStack source, ServerPlayer requester) {
        ServerPlayer owner = SimpleTPA.FEEDBACK.requirePlayer(source);
        if (owner == null) {
            return -1;
        }

        if (owner.equals(requester)) {
            SimpleTPA.FEEDBACK.failure(
                    source, "You cannot add yourself to your auto-accept whitelist.");
            return -1;
        }

        boolean added = InstantTpaWhitelist.add(owner, requester);
        boolean saved = InstantTpaWhitelist.save();

        if (added && !saved) {
            SimpleTPA.FEEDBACK.failure(
                    source,
                    "%s can instantly teleport to you for this session, but your auto-accept whitelist could not be saved.",
                    requester.getScoreboardName());
            return -1;
        } else if (added) {
            SimpleTPA.FEEDBACK.success(
                    owner,
                    "%s can now instantly teleport to you when the gamerule allows it.",
                    requester.getScoreboardName());
        } else {
            SimpleTPA.FEEDBACK.neutral(
                    owner,
                    "%s is already on your auto-accept whitelist.",
                    requester.getScoreboardName());
        }

        return 1;
    }

    /**
     * {@code player} is the name of an online player, or a name or UUID on the owner's whitelist,
     * so offline players can be removed. An online player is matched first: after renames, an
     * offline player's entry can still be stored under the name an online player has now.
     */
    public static int remove(CommandSourceStack source, String player) {
        ServerPlayer owner = SimpleTPA.FEEDBACK.requirePlayer(source);
        if (owner == null) {
            return -1;
        }

        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(player);
        String removed =
                online == null
                        ? null
                        : InstantTpaWhitelist.remove(owner, online.getUUID().toString());
        if (removed == null) {
            removed = InstantTpaWhitelist.remove(owner, player);
        }

        if (removed == null) {
            SimpleTPA.FEEDBACK.failure(source, "%s is not on your auto-accept whitelist.", player);
            return -1;
        }

        if (!InstantTpaWhitelist.save()) {
            SimpleTPA.FEEDBACK.failure(
                    source,
                    "%s was removed from your auto-accept whitelist for this session, but the change could not be saved.",
                    removed);
            return -1;
        }
        SimpleTPA.FEEDBACK.success(
                owner, "%s was removed from your auto-accept whitelist.", removed);
        return 1;
    }

    public static int clear(CommandSourceStack source) {
        ServerPlayer owner = SimpleTPA.FEEDBACK.requirePlayer(source);
        if (owner == null) {
            return -1;
        }

        if (InstantTpaWhitelist.clear(owner)) {
            if (!InstantTpaWhitelist.save()) {
                SimpleTPA.FEEDBACK.failure(
                        source,
                        "Your auto-accept whitelist has been cleared for this session, but the change could not be saved.");
                return -1;
            }
            SimpleTPA.FEEDBACK.success(owner, "Your auto-accept whitelist has been cleared.");
        } else {
            SimpleTPA.FEEDBACK.neutral(owner, "Your auto-accept whitelist is already empty.");
        }

        return 1;
    }
}
