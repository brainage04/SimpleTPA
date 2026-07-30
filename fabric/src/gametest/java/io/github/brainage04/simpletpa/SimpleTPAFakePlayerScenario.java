package io.github.brainage04.simpletpa;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec3;

final class SimpleTPAFakePlayerScenario {
	private static final String CARPET_FAKE_PLAYER_CLASS = "carpet.patches.EntityPlayerMPFake";

	private SimpleTPAFakePlayerScenario() {
	}

	static void spawn(ServerLevel level, String name, Vec3 position) {
		executeCarpetCommand(level, position, "player %s spawn in survival".formatted(name));
	}

	static void kill(ServerLevel level, String name) {
		if (player(level, name) != null) {
			executeCarpetCommand(level, Vec3.ZERO, "player %s kill".formatted(name));
		}
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

	static void executeCommand(ServerPlayer player, String command) {
		player.connection.handleChatCommand(new ServerboundChatCommandPacket(command));
	}

	static void assertPlayersTogether(ServerPlayer sender, ServerPlayer receiver, String message) {
		if (!sender.blockPosition().equals(receiver.blockPosition())) {
			throw new AssertionError(message);
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

	private static void executeCarpetCommand(ServerLevel level, Vec3 position, String command) {
		CommandSourceStack source = level.getServer().createCommandSourceStack()
				.withLevel(level)
				.withPosition(position)
				.withPermission(PermissionSet.ALL_PERMISSIONS);
		level.getServer().getCommands().performPrefixedCommand(source, command);
	}
}
