package io.github.brainage04.simpletpa;

import io.github.brainage04.fabricmoddingconventions.ClientGameTestRecorder;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public final class SimpleTPAClientGameTest implements FabricClientGameTest {
	private static final int STAGE_Y = 64;
	private static final String SENDER_NAME = "tpaViewSender";
	private static final String RECEIVER_NAME = "tpaViewRecv";
	private static final Vec3 SENDER_POSITION = new Vec3(-4.5D, STAGE_Y, 0.5D);
	private static final Vec3 RECEIVER_POSITION = new Vec3(4.5D, STAGE_Y, 0.5D);

	@Override
	public void runTest(ClientGameTestContext context) {
		ClientGameTestServers.withDedicatedServer(context, "SimpleTPA recording GameTest", server -> {
			UUID observerId = server.computeOnServer(minecraftServer ->
					minecraftServer.getPlayerList().getPlayers().getFirst().getUUID());
			server.runOnServer(minecraftServer -> prepareStage(requireObserver(minecraftServer.getPlayerList().getPlayer(observerId))));
			waitForActors(context, server, observerId);
			pointSpectatorCamera(context);

			try {
				context.waitTicks(20);
				ClientGameTestRecorder.startRecording(context);
				ClientGameTestRecorder.showStep(
						context,
						"simpletpa.stage",
						"SimpleTPA",
						"A spectator observes two independently controlled Carpet players"
				);
				context.waitTicks(35);

				showAcceptedRequest(context, server, observerId);
				showDeniedRequest(context, server, observerId);
				showAutoAcceptedRequest(context, server, observerId);

				ClientGameTestRecorder.showStep(
						context,
						"simpletpa.complete",
						"SimpleTPA verified",
						"Accept, deny, and auto-accept flows behaved correctly"
				);
				context.waitTicks(35);
			} finally {
				server.runOnServer(minecraftServer -> {
					ServerPlayer observer = minecraftServer.getPlayerList().getPlayer(observerId);
					if (observer != null) {
						SimpleTPAFakePlayerScenario.kill(observer.level(), SENDER_NAME);
						SimpleTPAFakePlayerScenario.kill(observer.level(), RECEIVER_NAME);
					}
				});
			}
		});
	}

	private static void showAcceptedRequest(
			ClientGameTestContext context,
			TestDedicatedServerContext server,
			UUID observerId
	) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = requireObserver(minecraftServer.getPlayerList().getPlayer(observerId)).level();
			ServerPlayer sender = SimpleTPAFakePlayerScenario.requirePlayer(level, SENDER_NAME);
			ServerPlayer receiver = SimpleTPAFakePlayerScenario.requirePlayer(level, RECEIVER_NAME);
			SimpleTPAFakePlayerScenario.setPositions(sender, SENDER_POSITION, receiver, RECEIVER_POSITION);
			SimpleTPAFakePlayerScenario.executeCommand(sender, "tprequest " + RECEIVER_NAME);
		});
		ClientGameTestRecorder.showStep(
				context,
				"simpletpa.request",
				"Teleport requested",
				SENDER_NAME + " asks to teleport to " + RECEIVER_NAME
		);
		context.waitTicks(30);

		ClientGameTestRecorder.showStep(
				context,
				"simpletpa.accept",
				"Request accepted",
				RECEIVER_NAME + " runs /tpaccept"
		);
		server.runOnServer(minecraftServer -> actors(minecraftServer.getPlayerList().getPlayer(observerId),
				(sender, receiver) -> SimpleTPAFakePlayerScenario.executeCommand(receiver, "tpaccept")));
		context.waitTicks(30);
		server.runOnServer(minecraftServer -> actors(minecraftServer.getPlayerList().getPlayer(observerId),
				(sender, receiver) -> SimpleTPAFakePlayerScenario.assertPlayersTogether(
						sender,
						receiver,
						"Sender did not teleport after the request was accepted."
				)));
	}

	private static void showDeniedRequest(
			ClientGameTestContext context,
			TestDedicatedServerContext server,
			UUID observerId
	) {
		server.runOnServer(minecraftServer -> actors(minecraftServer.getPlayerList().getPlayer(observerId), (sender, receiver) -> {
			SimpleTPAFakePlayerScenario.setPositions(sender, SENDER_POSITION, receiver, RECEIVER_POSITION);
			SimpleTPAFakePlayerScenario.executeCommand(sender, "tprequest " + RECEIVER_NAME);
		}));
		ClientGameTestRecorder.showStep(
				context,
				"simpletpa.deny",
				"Request denied",
				RECEIVER_NAME + " runs /tpdeny; both players should stay apart"
		);
		context.waitTicks(25);
		server.runOnServer(minecraftServer -> actors(minecraftServer.getPlayerList().getPlayer(observerId),
				(sender, receiver) -> SimpleTPAFakePlayerScenario.executeCommand(receiver, "tpdeny")));
		context.waitTicks(30);
		server.runOnServer(minecraftServer -> actors(minecraftServer.getPlayerList().getPlayer(observerId),
				(sender, receiver) -> SimpleTPAFakePlayerScenario.assertPlayersApart(
						sender,
						receiver,
						"Sender teleported despite the request being denied."
				)));
	}

	private static void showAutoAcceptedRequest(
			ClientGameTestContext context,
			TestDedicatedServerContext server,
			UUID observerId
	) {
		server.runOnServer(minecraftServer -> actors(minecraftServer.getPlayerList().getPlayer(observerId), (sender, receiver) -> {
			SimpleTPAFakePlayerScenario.executeCommand(receiver, "tpautoaccept add " + SENDER_NAME);
			SimpleTPAFakePlayerScenario.setPositions(sender, SENDER_POSITION, receiver, RECEIVER_POSITION);
		}));
		ClientGameTestRecorder.showStep(
				context,
				"simpletpa.autoaccept",
				"Auto-accept whitelist",
				"The next request should teleport immediately without /tpaccept"
		);
		context.waitTicks(25);
		server.runOnServer(minecraftServer -> actors(minecraftServer.getPlayerList().getPlayer(observerId),
				(sender, receiver) -> SimpleTPAFakePlayerScenario.executeCommand(sender, "tprequest " + RECEIVER_NAME)));
		context.waitTicks(30);
		server.runOnServer(minecraftServer -> actors(minecraftServer.getPlayerList().getPlayer(observerId),
				(sender, receiver) -> SimpleTPAFakePlayerScenario.assertPlayersTogether(
						sender,
						receiver,
						"Auto-accepted request did not teleport the sender."
				)));
	}

	private static void prepareStage(ServerPlayer observer) {
		ServerLevel level = observer.level();
		for (BlockPos position : BlockPos.betweenClosed(-10, STAGE_Y - 1, -5, 10, STAGE_Y + 4, 5)) {
			level.setBlock(position, position.getY() == STAGE_Y - 1
					? Blocks.SMOOTH_STONE.defaultBlockState()
					: Blocks.AIR.defaultBlockState(), 3);
		}
		level.setBlock(new BlockPos(-5, STAGE_Y - 1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
		level.setBlock(new BlockPos(4, STAGE_Y - 1, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 3);

		observer.setGameMode(GameType.SPECTATOR);
		observer.teleportTo(0.5D, STAGE_Y + 5.0D, 11.5D);
		observer.setDeltaMovement(Vec3.ZERO);
		SimpleTPAFakePlayerScenario.spawn(level, SENDER_NAME, SENDER_POSITION);
		SimpleTPAFakePlayerScenario.spawn(level, RECEIVER_NAME, RECEIVER_POSITION);
	}

	private static void waitForActors(
			ClientGameTestContext context,
			TestDedicatedServerContext server,
			UUID observerId
	) {
		for (int tick = 0; tick < 100; tick++) {
			boolean ready = server.computeOnServer(minecraftServer -> {
				ServerLevel level = requireObserver(minecraftServer.getPlayerList().getPlayer(observerId)).level();
				return SimpleTPAFakePlayerScenario.isReady(level, SENDER_NAME)
						&& SimpleTPAFakePlayerScenario.isReady(level, RECEIVER_NAME);
			});
			if (ready) {
				return;
			}
			context.waitTick();
		}
		throw new AssertionError("Timed out waiting for the SimpleTPA Carpet actors.");
	}

	private static void actors(ServerPlayer observer, ActorAction action) {
		ServerLevel level = requireObserver(observer).level();
		action.run(
				SimpleTPAFakePlayerScenario.requirePlayer(level, SENDER_NAME),
				SimpleTPAFakePlayerScenario.requirePlayer(level, RECEIVER_NAME)
		);
	}

	private static void pointSpectatorCamera(ClientGameTestContext context) {
		context.runOnClient(client -> {
			if (client.player == null) {
				throw new AssertionError("Expected a connected spectator for the recording.");
			}
			client.player.setYRot(180.0F);
			client.player.setXRot(20.0F);
		});
	}

	private static ServerPlayer requireObserver(ServerPlayer observer) {
		if (observer == null) {
			throw new AssertionError("Expected the recording spectator to remain connected.");
		}
		return observer;
	}

	@FunctionalInterface
	private interface ActorAction {
		void run(ServerPlayer sender, ServerPlayer receiver);
	}
}
