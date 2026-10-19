package io.github.brainage04.simpletpa;

import com.mojang.blaze3d.platform.Window;
import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Properties;

/**
 * Feature showcase seen through the client player's own eyes: the client types its commands into chat (or clicks them there)
 * and sees the replies and teleports as a real user would, while one Carpet fake player is the other side.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SimpleTPAClientGameTest implements FabricClientGameTest {
	private static final String FRIEND_NAME = "tpaFriend";

	// Showcase captions: edit the on-screen titles and descriptions here.
	private static final Caption REQUEST = new Caption("simpletpa.request",
			"Teleport request",
			"You ask " + FRIEND_NAME + " if you can teleport to them");
	private static final Caption ACCEPT = new Caption("simpletpa.accept",
			"Request accepted",
			FRIEND_NAME + " accepts and you arrive at their side");
	private static final Caption DENY = new Caption("simpletpa.deny",
			"Request denied",
			FRIEND_NAME + " denies, so you stay where you are");
	private static final Caption CLICK_ACCEPT = new Caption("simpletpa.clickaccept",
			"Accept in chat",
			FRIEND_NAME + " asks to teleport to you and you click [ACCEPT]");
	private static final Caption AUTO_ACCEPT = new Caption("simpletpa.autoaccept",
			"Auto-accept",
			"You whitelist " + FRIEND_NAME + ", who can now teleport to you instantly");

	private static final int STAGE_Y = 64;
	// The client stands on the diamond block facing east, the friend on the gold block facing west.
	private static final Vec3 PLAYER_POSITION = new Vec3(-2.5D, STAGE_Y, 0.5D);
	private static final Vec3 FRIEND_POSITION = new Vec3(2.5D, STAGE_Y, 0.5D);
	private static final float EAST = -90.0F;
	private static final float WEST = 90.0F;
	private static final float PLAYER_PITCH = 6.0F;
	private static final int COMMAND_VISIBLE_TICKS = 20;

	@Override
	public void runTest(ClientGameTestContext context) {
		String playerName = context.computeOnClient(client -> client.getUser().getName());
		// Command suggestions would cover the chat while a command sits in the chat box.
		context.runOnClient(client -> client.options.autoSuggestions().set(false));
		Properties serverProperties = ClientGameTestServers.flatServerProperties();
		// The Carpet player joins first and takes a slot, so the client needs a second one.
		serverProperties.setProperty("max-players", "2");
		ClientGameTestServers.withDedicatedServer(context, serverProperties, "SimpleTPA recording GameTest",
				server -> prepareWorld(context, server, playerName),
				server -> {
					context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
					showAcceptedRequest(context, server, playerName);
					showDeniedRequest(context, server, playerName);
					showClickedAccept(context, server, playerName);
					showAutoAcceptedRequest(context, server, playerName);
				});
	}

	private static void showAcceptedRequest(ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
		show(context, REQUEST);
		context.waitTicks(10);
		typeCommand(context, "tpa " + FRIEND_NAME);
		context.waitTicks(30);

		show(context, ACCEPT);
		context.waitTicks(10);
		server.runOnServer(minecraftServer -> {
			ServerPlayer friend = friend(minecraftServer);
			SimpleTPAFakePlayerScenario.executeCommand(friend, "tpaccept");
			SimpleTPAFakePlayerScenario.assertLandedOn(player(minecraftServer, playerName), friend,
					"The client player did not land on " + FRIEND_NAME + " after the request was accepted");
		});
		// Both players now share one spot; they stay there until the next step resets them.
		context.waitTicks(38);
	}

	private static void showDeniedRequest(ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
		show(context, DENY);
		server.runOnServer(minecraftServer -> placeActors(player(minecraftServer, playerName), friend(minecraftServer)));
		context.waitTicks(10);
		typeCommand(context, "tpa " + FRIEND_NAME);
		context.waitTicks(25);
		server.runOnServer(minecraftServer -> SimpleTPAFakePlayerScenario.executeCommand(friend(minecraftServer), "tpdeny"));
		context.waitTicks(35);
		server.runOnServer(minecraftServer -> SimpleTPAFakePlayerScenario.assertPlayersApart(
				player(minecraftServer, playerName),
				friend(minecraftServer),
				"The client player teleported despite the request being denied."));
	}

	/** tpaFriend asks to come over and the player clicks the [ACCEPT] button in the chat window with the mouse. */
	private static void showClickedAccept(ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
		show(context, CLICK_ACCEPT);
		server.runOnServer(minecraftServer -> placeActors(player(minecraftServer, playerName), friend(minecraftServer)));
		context.waitTicks(10);
		server.runOnServer(minecraftServer -> SimpleTPAFakePlayerScenario.executeCommand(friend(minecraftServer), "tpa " + playerName));
		context.waitTicks(25);
		context.setScreen(() -> new ChatScreen("", false));
		context.waitTicks(10);
		String acceptCommand = "/tpaccept " + FRIEND_NAME;
		double[] button = context.computeOnClient(client -> findRunCommand(client, acceptCommand));
		// Hover first, so the button's tooltip shows before the click.
		context.getInput().setCursorPos(button[0], button[1]);
		context.waitTicks(25);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		server.runOnServer(minecraftServer -> SimpleTPAFakePlayerScenario.assertLandedOn(friend(minecraftServer), player(minecraftServer, playerName),
				FRIEND_NAME + " did not land on the client player after [ACCEPT] was clicked"));
		context.waitTicks(15);
		if (context.computeOnClient(client -> client.gui.screen() instanceof ChatScreen)) {
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		}
		context.waitTicks(30);
	}

	/**
	 * Finds the chat text whose click event runs {@code command}, the way {@link ChatScreen} looks up what was
	 * clicked, and returns the middle of it in window pixels for {@code TestInput.setCursorPos}.
	 */
	private static double[] findRunCommand(Minecraft client, String command) {
		Window window = client.getWindow();
		ChatComponent chat = client.gui.hud.getChat();
		int guiWidth = window.getGuiScaledWidth();
		int guiHeight = window.getGuiScaledHeight();
		long sumX = 0;
		long sumY = 0;
		int hits = 0;
		for (int y = guiHeight / 2; y < guiHeight; y++) {
			for (int x = 0; x < guiWidth; x++) {
				ActiveTextCollector.ClickableStyleFinder finder = new ActiveTextCollector.ClickableStyleFinder(client.font, x, y);
				chat.captureClickableText(finder, guiHeight, client.gui.hud.getGuiTicks(), ChatComponent.DisplayMode.FOREGROUND);
				Style style = finder.result();
				if (style != null && style.getClickEvent() instanceof ClickEvent.RunCommand(String run) && run.equals(command)) {
					sumX += x;
					sumY += y;
					hits++;
				}
			}
		}
		if (hits == 0) {
			throw new AssertionError("No chat text runs " + command);
		}
		double scaleX = (double) window.getScreenWidth() / guiWidth;
		double scaleY = (double) window.getScreenHeight() / guiHeight;
		return new double[]{(sumX / (double) hits + 0.5D) * scaleX, (sumY / (double) hits + 0.5D) * scaleY};
	}

	/** The last step: the recording ends right after it, without a closing caption card. */
	private static void showAutoAcceptedRequest(ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
		show(context, AUTO_ACCEPT);
		server.runOnServer(minecraftServer -> placeActors(player(minecraftServer, playerName), friend(minecraftServer)));
		context.waitTicks(10);
		typeCommand(context, "tpautoaccept add " + FRIEND_NAME);
		context.waitTicks(25);
		server.runOnServer(minecraftServer -> {
			ServerPlayer friend = friend(minecraftServer);
			SimpleTPAFakePlayerScenario.executeCommand(friend, "tpa " + playerName);
			SimpleTPAFakePlayerScenario.assertLandedOn(friend, player(minecraftServer, playerName),
					"The whitelisted " + FRIEND_NAME + " did not land on the client player instantly");
		});
		context.waitTicks(38);
	}

	/**
	 * Opens chat, enters {@code command} and sends it after a pause, as a player would. The command is
	 * entered whole: typed one character at a time, the partial command shows a parse error above it.
	 */
	private static void typeCommand(ClientGameTestContext context, String command) {
		context.setScreen(() -> new ChatScreen("", false));
		context.getInput().typeChars("/" + command);
		context.waitTicks(COMMAND_VISIBLE_TICKS);
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
	}

	private static void show(ClientGameTestContext context, Caption caption) {
		ClientGameTestRecorder.showStep(context, caption.id(), caption.title(), caption.description());
	}

	/** Runs before the client joins, so none of the setup is in the recording. */
	private static void prepareWorld(ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			buildStage(level);
			// At noon the sun is overhead and out of view, instead of behind the friend's name tag.
			minecraftServer.getCommands().performPrefixedCommand(
					minecraftServer.createCommandSourceStack().withSuppressedOutput(), "time set noon");
			minecraftServer.getGameRules().set(GameRules.ADVANCE_TIME, false, minecraftServer);
			// A new player joins exactly on the world spawn, facing its yaw and pitch.
			minecraftServer.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, minecraftServer);
			minecraftServer.setRespawnData(LevelData.RespawnData.of(
					Level.OVERWORLD, BlockPos.containing(PLAYER_POSITION), EAST, PLAYER_PITCH));
			SimpleTPAFakePlayerScenario.spawn(level, FRIEND_NAME, FRIEND_POSITION);
		});
		waitForFriend(context, server);
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			placeFriend(friend(minecraftServer));
			SimpleTPAFakePlayerScenario.colorName(level, playerName, "aqua");
			SimpleTPAFakePlayerScenario.colorName(level, FRIEND_NAME, "gold");
		});
	}

	private static void buildStage(ServerLevel level) {
		// A plain dark floor reaching far back, with the client's diamond block and the friend's gold block.
		for (BlockPos position : BlockPos.betweenClosed(-30, STAGE_Y - 1, -24, 30, STAGE_Y + 4, 24)) {
			level.setBlock(position, position.getY() == STAGE_Y - 1
					? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
					: Blocks.AIR.defaultBlockState(), 3);
		}
		level.setBlock(BlockPos.containing(PLAYER_POSITION).below(), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
		level.setBlock(BlockPos.containing(FRIEND_POSITION).below(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
	}

	private static void waitForFriend(ClientGameTestContext context, TestDedicatedServerContext server) {
		for (int tick = 0; tick < 100; tick++) {
			if (server.computeOnServer(minecraftServer -> SimpleTPAFakePlayerScenario.isReady(minecraftServer.overworld(), FRIEND_NAME))) {
				return;
			}
			context.waitTick();
		}
		throw new AssertionError("Timed out waiting for the SimpleTPA Carpet player.");
	}

	private static void placeActors(ServerPlayer player, ServerPlayer friend) {
		SimpleTPAFakePlayerScenario.place(player, PLAYER_POSITION, EAST, PLAYER_PITCH);
		placeFriend(friend);
	}

	private static void placeFriend(ServerPlayer friend) {
		SimpleTPAFakePlayerScenario.place(friend, FRIEND_POSITION, WEST, 0.0F);
	}

	private static ServerPlayer friend(MinecraftServer server) {
		return SimpleTPAFakePlayerScenario.requirePlayer(server.overworld(), FRIEND_NAME);
	}

	private static ServerPlayer player(MinecraftServer server, String playerName) {
		ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
		if (player == null) {
			throw new AssertionError("Expected the client player " + playerName + " to remain connected.");
		}
		return player;
	}

	private record Caption(String id, String title, String description) {
	}
}
