package io.github.brainage04.simpletpa.command;

import io.github.brainage04.simpletpa.SimpleTPA;
import io.github.brainage04.simpletpa.data.InstantTpaWhitelist;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class TPRequestCommand {
    public static final List<TPRequest> TP_REQUESTS = new ArrayList<>();

    public static int execute(CommandSourceStack source, ServerPlayer to) {
        ServerPlayer from = SimpleTPA.FEEDBACK.requirePlayer(source);
        if (from == null) {
            return -1;
        }

        if (from.equals(to)) {
            SimpleTPA.FEEDBACK.failure(source, "You cannot send a TP request to yourself!");
            return -1;
        }

        TPRequest request = TPRequest.create(to, from, source.getLevel().getGameTime());

        if (source.getServer().getGameRules().get(SimpleTPA.AUTO_ACCEPT)
                && InstantTpaWhitelist.allows(to, from)) {
            String blocked = canBeMoved(from) ? blockedReason(from, to) : null;
            if (blocked == null) {
                if (!teleport(from, to)) {
                    SimpleTPA.FEEDBACK.failure(
                            source, "You could not be teleported to %s.", to.getScoreboardName());
                    return -1;
                }
                SimpleTPA.FEEDBACK.success(
                        from,
                        "%s has automatically accepted your TP request.",
                        to.getScoreboardName());
                SimpleTPA.FEEDBACK.success(
                        to, "%s has teleported to you.", from.getScoreboardName());
                SimpleTPA.FEEDBACK.click(to);
                TP_REQUESTS.removeIf(pending -> pending.matches(to.getUUID(), from.getUUID()));
                return 1;
            }
            SimpleTPA.FEEDBACK.neutral(
                    from,
                    "%s is %s, so your TP request was not accepted automatically.",
                    to.getScoreboardName(),
                    blocked);
        }

        for (TPRequest prevRequest : TP_REQUESTS) {
            if (prevRequest.matches(request.toId(), request.fromId())) {
                SimpleTPA.FEEDBACK.failure(
                        source,
                        "You have already sent a TP request to %s!",
                        to.getScoreboardName());
                return -1;
            }
        }

        TP_REQUESTS.add(request);

        SimpleTPA.FEEDBACK.neutral(to, incomingRequestMessage(from));
        SimpleTPA.FEEDBACK.neutral(from, "You have sent %s a TP request.", to.getScoreboardName());
        SimpleTPA.FEEDBACK.click(to);
        return 1;
    }

    /**
     * The buttons name the sender by UUID: an offline-mode name may be up to 16 printable
     * characters without spaces (StringUtil.java:66-68), such as {@code @r}.
     */
    public static MutableComponent incomingRequestMessage(ServerPlayer from) {
        String fromName = from.getScoreboardName();
        return Component.literal(
                        "%s has sent you a TP request. Use /tpaccept to accept, or /tpdeny to deny. "
                                .formatted(fromName))
                .append(
                        button(
                                "[ACCEPT]",
                                ChatFormatting.GREEN,
                                "/tpaccept " + from.getUUID(),
                                "Click to accept %s's TP request".formatted(fromName)))
                .append(" ")
                .append(
                        button(
                                "[DENY]",
                                ChatFormatting.RED,
                                "/tpdeny " + from.getUUID(),
                                "Click to deny %s's TP request".formatted(fromName)));
    }

    private static MutableComponent button(
            String label, ChatFormatting color, String command, String hover) {
        return Component.literal(label)
                .withStyle(
                        style ->
                                style.withColor(color)
                                        .withClickEvent(new ClickEvent.RunCommand(command))
                                        .withHoverEvent(
                                                new HoverEvent.ShowText(Component.literal(hover))));
    }

    /**
     * Why {@code from}, who {@link #canBeMoved can be moved}, cannot be put on {@code to} now, as
     * words that follow "is", or null when nothing stops it. A removed target is in the End credits
     * (ServerPlayer.java:1128-1131) or between levels. A gliding or flying target is in the air,
     * and a teleport arrives with no velocity (Entity.java:3446), so the requester would fall.
     *
     * <p>The spot checks are a subset of the ones vanilla makes for a respawn spot in {@code
     * DismountHelper.findSafeDismountLocation} (DismountHelper.java:80-111): no block collision, no
     * block the player type finds dangerous in it or underfoot, and inside the world border. They
     * leave out vanilla's floor check (:87-90) and its refusal of a spot in INVALID_SPAWN_INSIDE
     * blocks (:105-107), which are the End portal and End gateway
     * (VanillaBlockTagsProvider.java:1178).
     */
    public static @Nullable String blockedReason(ServerPlayer from, ServerPlayer to) {
        if (to.isRemoved()) {
            return "not in a world";
        }
        if (to.isDeadOrDying()) {
            return "dead";
        }
        if (to.isSpectator()) {
            return "in spectator mode";
        }
        if (to.isFallFlying()) {
            return "gliding";
        }
        if (to.getAbilities().flying) {
            return "flying";
        }
        ServerLevel level = to.level();
        AABB arrival = from.getBoundingBox().move(to.position().subtract(from.position()));
        if (!level.noBlockCollision(from, arrival)
                || level.getBlockStates(arrival).anyMatch(from.getType()::isBlockDangerous)
                || from.getType().isBlockDangerous(level.getBlockState(to.getOnPos()))
                || !level.getWorldBorder().isWithinBounds(arrival)) {
            return "somewhere unsafe";
        }
        return null;
    }

    /** Whether {@code from} can be moved at all: alive and in a level. */
    public static boolean canBeMoved(ServerPlayer from) {
        return !from.isDeadOrDying() && !from.isRemoved();
    }

    /** Puts {@code from} on {@code to}; false when {@code from} cannot be moved. */
    public static boolean teleport(ServerPlayer from, ServerPlayer to) {
        return canBeMoved(from)
                && from.teleportTo(
                        to.level(),
                        to.getX(),
                        to.getY(),
                        to.getZ(),
                        Set.of(),
                        to.getYRot(),
                        to.getXRot(),
                        true);
    }

    public record TPRequest(UUID toId, String toName, UUID fromId, String fromName, long created) {
        public static TPRequest create(ServerPlayer to, ServerPlayer from, long created) {
            return new TPRequest(
                    to.getUUID(),
                    to.getScoreboardName(),
                    from.getUUID(),
                    from.getScoreboardName(),
                    created);
        }

        public boolean matches(UUID toId, UUID fromId) {
            return this.toId.equals(toId) && this.fromId.equals(fromId);
        }
    }
}
