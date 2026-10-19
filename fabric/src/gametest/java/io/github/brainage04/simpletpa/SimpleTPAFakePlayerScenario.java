package io.github.brainage04.simpletpa;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

final class SimpleTPAFakePlayerScenario {
	private static final String CARPET_FAKE_PLAYER_CLASS = "carpet.patches.EntityPlayerMPFake";

	private SimpleTPAFakePlayerScenario() {
	}

	static void spawn(ServerLevel level, String name, Vec3 position) {
		executeServerCommand(level, position, "player %s spawn in survival".formatted(name));
	}

	static void kill(ServerLevel level, String name) {
		if (player(level, name) != null) {
			executeServerCommand(level, Vec3.ZERO, "player %s kill".formatted(name));
		}
	}

	/**
	 * Puts the player on its own scoreboard team so its name tag and chat name use {@code color}. The
	 * team keeps vanilla player collision, so team members still push other players.
	 */
	static void colorName(ServerLevel level, String name, String color) {
		String team = teamName(name);
		executeServerCommand(level, Vec3.ZERO, "team add " + team);
		executeServerCommand(level, Vec3.ZERO, "team modify %s color %s".formatted(team, color));
		executeServerCommand(level, Vec3.ZERO, "team modify %s collisionRule always".formatted(team));
		executeServerCommand(level, Vec3.ZERO, "team join %s %s".formatted(team, name));
	}

	static boolean isReady(ServerLevel level, String name) {
		ServerPlayer player = player(level, name);
		return player != null && CARPET_FAKE_PLAYER_CLASS.equals(player.getClass().getName());
	}

	static ServerPlayer requirePlayer(ServerLevel level, String name) {
		ServerPlayer player = player(level, name);
		if (player == null) {
			throw new AssertionError("Expected Carpet fake player " + name + ".");
		}
		if (!CARPET_FAKE_PLAYER_CLASS.equals(player.getClass().getName())) {
			throw new AssertionError("Expected " + name + " to use Carpet's fake-player implementation.");
		}
		return player;
	}

	static void setPositions(ServerPlayer sender, Vec3 senderPosition, ServerPlayer receiver, Vec3 receiverPosition) {
		sender.teleportTo(senderPosition.x, senderPosition.y, senderPosition.z);
		sender.setDeltaMovement(Vec3.ZERO);
		receiver.teleportTo(receiverPosition.x, receiverPosition.y, receiverPosition.z);
		receiver.setDeltaMovement(Vec3.ZERO);
	}

	/** Moves {@code player} to {@code position}, facing {@code yaw} and {@code pitch}. */
	static void place(ServerPlayer player, Vec3 position, float yaw, float pitch) {
		player.teleportTo(player.level(), position.x, position.y, position.z, Set.of(), yaw, pitch, true);
		player.setYHeadRot(yaw);
		player.setDeltaMovement(Vec3.ZERO);
	}

	/** Runs {@code command} as {@code player}, first showing {@code <player> /command} to every real player. */
	static void executeCommand(ServerPlayer player, String command) {
		Component line = Component.literal("<")
				.append(player.getDisplayName())
				.append("> ")
				.append(Component.literal("/" + command).withStyle(ChatFormatting.WHITE));
		for (ServerPlayer viewer : player.level().getServer().getPlayerList().getPlayers()) {
			if (!CARPET_FAKE_PLAYER_CLASS.equals(viewer.getClass().getName())) {
				viewer.sendSystemMessage(line);
			}
		}
		player.connection.handleChatCommand(new ServerboundChatCommandPacket(command));
	}

	/** Asserts that a teleport put {@code mover} exactly on {@code target}'s position, facing its way. */
	static void assertLandedOn(ServerPlayer mover, ServerPlayer target, String message) {
		if (!mover.position().equals(target.position())
				|| mover.getYRot() != target.getYRot()
				|| mover.getXRot() != target.getXRot()) {
			throw new AssertionError("%s: %s at %s facing %.1f/%.1f, %s at %s facing %.1f/%.1f.".formatted(
					message,
					mover.getScoreboardName(), mover.position(), mover.getYRot(), mover.getXRot(),
					target.getScoreboardName(), target.position(), target.getYRot(), target.getXRot()));
		}
	}

	static void assertPlayersApart(ServerPlayer sender, ServerPlayer receiver, String message) {
		if (sender.blockPosition().equals(receiver.blockPosition())) {
			throw new AssertionError(message);
		}
	}

	private static ServerPlayer player(ServerLevel level, String name) {
		return level.getServer().getPlayerList().getPlayerByName(name);
	}

	private static String teamName(String name) {
		return "simpletpa_" + name;
	}

	private static void executeServerCommand(ServerLevel level, Vec3 position, String command) {
		CommandSourceStack source = level.getServer().createCommandSourceStack()
				.withLevel(level)
				.withPosition(position)
				.withPermission(PermissionSet.ALL_PERMISSIONS)
				.withSuppressedOutput();
		level.getServer().getCommands().performPrefixedCommand(source, command);
	}
}
