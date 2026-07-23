package io.github.brainage04.simpletpa;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class SimpleTPATest implements CustomTestMethodInvoker {
	private static int playerPairCounter;

	private static final Function<ServerPlayer, String> TP_REQUEST_FUNCTION_NAME = receiver -> "tprequest %s".formatted(receiver.getScoreboardName());

	private static final Function<ServerPlayer, String> TP_ACCEPT_FUNCTION = sender -> "tpaccept";
	private static final Function<ServerPlayer, String> TP_ACCEPT_FUNCTION_NAME = sender -> "tpaccept %s".formatted(sender.getScoreboardName());

	private static final Function<ServerPlayer, String> TP_DENY_FUNCTION = sender -> "tpdeny";
	private static final Function<ServerPlayer, String> TP_DENY_FUNCTION_NAME = sender -> "tpdeny %s".formatted(sender.getScoreboardName());

	private static final List<Function<ServerPlayer, String>> TP_REQUEST_FUNCTIONS = new ArrayList<>(List.of(
			TP_REQUEST_FUNCTION_NAME
	));

	private static final List<Function<ServerPlayer, String>> TP_ACCEPT_FUNCTIONS = new ArrayList<>(List.of(
			TP_ACCEPT_FUNCTION,
			TP_ACCEPT_FUNCTION_NAME
	));

	private static final List<Function<ServerPlayer, String>> TP_DENY_FUNCTIONS = new ArrayList<>(List.of(
			TP_DENY_FUNCTION,
			TP_DENY_FUNCTION_NAME
	));

	public static final BlockPos START = new BlockPos(0, 0, 0);
	public static final BlockPos END = new BlockPos(10, 10, 10);

	public ServerPlayer makeMockServerPlayerInLevel(GameTestHelper helper, GameType gameType, String name) {
		CommonListenerCookie commonListenerCookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);

		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		assert server != null;

		ServerPlayer serverPlayer = new ServerPlayer(
				server, level, commonListenerCookie.gameProfile(), commonListenerCookie.clientInformation()
		) {
			@Override
			public GameType gameMode() {
				return gameType;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, serverPlayer, commonListenerCookie);

		return serverPlayer;
	}

	public void executeCommand(ServerPlayer player, String command) {
		player.connection.handleChatCommand(new ServerboundChatCommandPacket(command));
	}

	private static void setPositions(ServerPlayer sender, ServerPlayer receiver) {
		sender.setPos(new Vec3(START));
		receiver.setPos(new Vec3(END));
	}

	public void executeRunnables(GameTestHelper helper, Runnable... runnables) {
		int currentTick = 1;

		for (Runnable runnable : runnables) {
			helper.runAtTickTime(currentTick, runnable);
			currentTick++;
		}
	}

	public void requestAcceptCombination(
			GameTestHelper helper,
			ServerPlayer sender,
			ServerPlayer receiver,
			Function<ServerPlayer, String> request,
			Function<ServerPlayer, String> accept
	) {
		executeRunnables(
				helper,
				() -> setPositions(sender, receiver),
				() -> executeCommand(sender, request.apply(receiver)),
				() -> executeCommand(receiver, accept.apply(sender)),
				() -> {
					if (sender.blockPosition().equals(receiver.blockPosition())) {
						helper.succeed();
					} else {
						helper.fail("Sender did not teleport to receiver despite being accepted");
					}
				}
		);
	}

	public void requestDenyCombination(
			GameTestHelper helper,
			ServerPlayer sender,
			ServerPlayer receiver,
			Function<ServerPlayer, String> request,
			Function<ServerPlayer, String> deny
	) {
		executeRunnables(
				helper,
				() -> setPositions(sender, receiver),
				() -> executeCommand(sender, request.apply(receiver)),
				() -> executeCommand(receiver, deny.apply(sender)),
				() -> {
					if (sender.blockPosition().equals(START)) {
						helper.succeed();
					} else {
						helper.fail("Sender teleported to receiver despite being denied");
					}
				}
		);
	}

	@GameTest
	public void testAcceptFlows(GameTestHelper helper, ServerPlayer sender, ServerPlayer receiver) {
		for (Function<ServerPlayer, String> request : TP_REQUEST_FUNCTIONS) {
			for (Function<ServerPlayer, String> accept : TP_ACCEPT_FUNCTIONS) {
				requestAcceptCombination(
						helper,
						sender,
						receiver,
						request,
						accept
				);
			}
		}
	}

	@GameTest
	public void testDenyFlows(GameTestHelper helper, ServerPlayer sender, ServerPlayer receiver) {
		for (Function<ServerPlayer, String> request : TP_REQUEST_FUNCTIONS) {
			for (Function<ServerPlayer, String> deny : TP_DENY_FUNCTIONS) {
				requestDenyCombination(
						helper,
						sender,
						receiver,
						request,
						deny
				);
			}
		}
	}

	@GameTest
	public void testInstantAutoAcceptFlow(GameTestHelper helper, ServerPlayer sender, ServerPlayer receiver) {
		executeRunnables(
				helper,
				() -> executeCommand(receiver, "tpautoaccept add %s".formatted(sender.getScoreboardName())),
				() -> setPositions(sender, receiver),
				() -> executeCommand(sender, TP_REQUEST_FUNCTION_NAME.apply(receiver)),
				() -> {
					if (sender.blockPosition().equals(receiver.blockPosition())) {
						helper.succeed();
					} else {
						helper.fail("Sender did not instantly teleport to receiver despite being whitelisted");
					}
				}
		);
	}

	@GameTest
	public void testCombinedServerHelpIncludesSimpleTPA(
			GameTestHelper helper,
			ServerPlayer sender,
			ServerPlayer receiver
	) {
		boolean registered = ServerModHelpRegistry.entries().stream()
				.anyMatch(entry -> entry.modId().equals(SimpleTPA.MOD_ID)
						&& entry.helpCommand().equals("/simpletpa help"));
		if (!registered) {
			throw new AssertionError("Expected SimpleTPA in the shared server help registry.");
		}
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
		int playerPairId = ++playerPairCounter;
		ServerPlayer sender = makeMockServerPlayerInLevel(helper, GameType.SPECTATOR, "sender%s".formatted(playerPairId));
		ServerPlayer receiver = makeMockServerPlayerInLevel(helper, GameType.SPECTATOR, "receiver%s".formatted(playerPairId));

		method.invoke(this, helper, sender, receiver);
	}
}
