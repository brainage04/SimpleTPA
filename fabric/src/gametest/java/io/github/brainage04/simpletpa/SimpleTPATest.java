package io.github.brainage04.simpletpa;

import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SimpleTPATest implements CustomTestMethodInvoker {
	private static int playerPairCounter;
	private static final BlockPos START = new BlockPos(1, 1, 1);
	private static final BlockPos END = new BlockPos(8, 1, 8);

	public void executeCommand(ServerPlayer player, String command) {
		SimpleTPAFakePlayerScenario.executeCommand(player, command);
	}

	private static void setPositions(GameTestHelper helper, ServerPlayer sender, ServerPlayer receiver) {
		SimpleTPAFakePlayerScenario.setPositions(
				sender,
				helper.absoluteVec(Vec3.atBottomCenterOf(START)),
				receiver,
				helper.absoluteVec(Vec3.atBottomCenterOf(END))
		);
	}

	public void executeRunnables(GameTestHelper helper, Runnable... runnables) {
		long currentTick = helper.getTick() + 1;
		for (Runnable runnable : runnables) {
			helper.runAtTickTime(currentTick, runnable);
			currentTick++;
		}
	}

	public void requestAcceptCombination(
			GameTestHelper helper,
			ServerPlayer sender,
			ServerPlayer receiver,
			String acceptCommand
	) {
		executeRunnables(
				helper,
				() -> setPositions(helper, sender, receiver),
				() -> executeCommand(sender, "tprequest %s".formatted(receiver.getScoreboardName())),
				() -> executeCommand(receiver, acceptCommand),
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
			String denyCommand
	) {
		executeRunnables(
				helper,
				() -> setPositions(helper, sender, receiver),
				() -> executeCommand(sender, "tprequest %s".formatted(receiver.getScoreboardName())),
				() -> executeCommand(receiver, denyCommand),
				() -> {
					if (!sender.blockPosition().equals(receiver.blockPosition())) {
						helper.succeed();
					} else {
						helper.fail("Sender teleported to receiver despite being denied");
					}
				}
		);
	}

	@GameTest
	public void testAcceptFlow(GameTestHelper helper, ServerPlayer sender, ServerPlayer receiver) {
		requestAcceptCombination(helper, sender, receiver, "tpaccept");
	}

	@GameTest
	public void testNamedAcceptFlow(GameTestHelper helper, ServerPlayer sender, ServerPlayer receiver) {
		requestAcceptCombination(helper, sender, receiver, "tpaccept %s".formatted(sender.getScoreboardName()));
	}

	@GameTest
	public void testDenyFlow(GameTestHelper helper, ServerPlayer sender, ServerPlayer receiver) {
		requestDenyCombination(helper, sender, receiver, "tpdeny");
	}

	@GameTest
	public void testNamedDenyFlow(GameTestHelper helper, ServerPlayer sender, ServerPlayer receiver) {
		requestDenyCombination(helper, sender, receiver, "tpdeny %s".formatted(sender.getScoreboardName()));
	}

	@GameTest
	public void testInstantAutoAcceptFlow(GameTestHelper helper, ServerPlayer sender, ServerPlayer receiver) {
		executeRunnables(
				helper,
				() -> executeCommand(receiver, "tpautoaccept add %s".formatted(sender.getScoreboardName())),
				() -> setPositions(helper, sender, receiver),
				() -> executeCommand(sender, "tprequest %s".formatted(receiver.getScoreboardName())),
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
	public void invokeTestMethod(GameTestHelper helper, Method method) {
		int playerPairId = ++playerPairCounter;
		String senderName = "tpaSender" + playerPairId;
		String receiverName = "tpaRecv" + playerPairId;
		Vec3 senderSpawn = helper.absoluteVec(Vec3.atBottomCenterOf(START));
		Vec3 receiverSpawn = helper.absoluteVec(Vec3.atBottomCenterOf(END));

		spawnFakePlayer(helper, senderName, senderSpawn);
		spawnFakePlayer(helper, receiverName, receiverSpawn);
		helper.runBeforeTestEnd(() -> {
			killFakePlayer(helper, senderName);
			killFakePlayer(helper, receiverName);
		});

		helper.startSequence()
				.thenWaitUntil(() -> {
					assertCarpetFakePlayer(helper, senderName);
					assertCarpetFakePlayer(helper, receiverName);
				})
				.thenExecute(() -> invokeTest(method, helper, senderName, receiverName));
	}

	private void invokeTest(Method method, GameTestHelper helper, String senderName, String receiverName) {
		try {
			method.invoke(this, helper, fakePlayer(helper, senderName), fakePlayer(helper, receiverName));
		} catch (ReflectiveOperationException exception) {
			Throwable cause = exception instanceof InvocationTargetException && exception.getCause() != null
					? exception.getCause()
					: exception;
			throw new IllegalStateException("Failed to invoke SimpleTPA fake-player GameTest", cause);
		}
	}

	private static void spawnFakePlayer(GameTestHelper helper, String name, Vec3 position) {
		SimpleTPAFakePlayerScenario.spawn(helper.getLevel(), name, position);
	}

	private static void killFakePlayer(GameTestHelper helper, String name) {
		SimpleTPAFakePlayerScenario.kill(helper.getLevel(), name);
	}

	private static void assertCarpetFakePlayer(GameTestHelper helper, String name) {
		helper.assertTrue(
				SimpleTPAFakePlayerScenario.isReady(helper.getLevel(), name),
				"Waiting for Carpet fake player " + name
		);
	}

	private static ServerPlayer fakePlayer(GameTestHelper helper, String name) {
		return SimpleTPAFakePlayerScenario.requirePlayer(helper.getLevel(), name);
	}
}
