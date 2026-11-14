package io.github.brainage04.simpletpa;

import io.github.brainage04.brainagelib.gametest.GameTestPlayer;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

final class SimpleTPAFakePlayerScenario {
    private SimpleTPAFakePlayerScenario() {}

    /** Joins a survival player with the offline identity of {@code name}. Close it when done. */
    static GameTestPlayer join(ServerLevel level, String name) {
        return GameTestPlayer.join(level, UUIDUtil.createOfflineProfile(name))
                .gameMode(GameType.SURVIVAL);
    }

    static void colorName(ServerLevel level, String name, String color) {
        String team = teamName(name);
        executeServerCommand(level, Vec3.ZERO, "team add " + team);
        executeServerCommand(level, Vec3.ZERO, "team modify %s color %s".formatted(team, color));
        executeServerCommand(
                level, Vec3.ZERO, "team modify %s collisionRule always".formatted(team));
        executeServerCommand(level, Vec3.ZERO, "team join %s %s".formatted(team, name));
    }

    static void place(ServerPlayer player, Vec3 position, float yaw, float pitch) {
        player.teleportTo(
                player.level(), position.x, position.y, position.z, Set.of(), yaw, pitch, true);
        player.setYHeadRot(yaw);
        player.setDeltaMovement(Vec3.ZERO);
    }

    /** Runs {@code command} as {@code player} and shows it in the other players' chat. */
    static void executeCommand(ServerPlayer player, String command) {
        Component line =
                Component.literal("<")
                        .append(player.getDisplayName())
                        .append("> ")
                        .append(Component.literal("/" + command).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer viewer : player.level().getServer().getPlayerList().getPlayers()) {
            if (viewer != player) {
                viewer.sendSystemMessage(line);
            }
        }
        player.connection.handleChatCommand(new ServerboundChatCommandPacket(command));
    }

    private static String teamName(String name) {
        return "simpletpa_" + name;
    }

    private static void executeServerCommand(ServerLevel level, Vec3 position, String command) {
        CommandSourceStack source =
                level.getServer()
                        .createCommandSourceStack()
                        .withLevel(level)
                        .withPosition(position)
                        .withPermission(PermissionSet.ALL_PERMISSIONS)
                        .withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, command);
    }
}
