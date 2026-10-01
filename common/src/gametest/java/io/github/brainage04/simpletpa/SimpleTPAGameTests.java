package io.github.brainage04.simpletpa;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral server GameTest bodies. Both loaders compile this source set into their GameTest
 * mods: Fabric runs them through {@code @GameTest} methods, NeoForge through registered test
 * functions and {@code test_instance} data.
 */
public final class SimpleTPAGameTests {
	private static int playerPairCounter;

	private SimpleTPAGameTests() {
	}

	public static void acceptFlows(GameTestHelper helper) {
		pair(helper, players -> {
			players[0].setPos(new Vec3(0, 0, 0));
			players[1].setPos(new Vec3(10, 10, 10));
			command(players[0], "tprequest " + players[1].getScoreboardName());
			command(players[1], "tpaccept");
			helper.runAtTickTime(2, () -> {
				if (players[0].blockPosition().equals(players[1].blockPosition())) helper.succeed();
				else helper.fail("accepted request did not teleport");
			});
		});
	}

	public static void denyFlows(GameTestHelper helper) {
		pair(helper, players -> {
			players[0].setPos(new Vec3(0, 0, 0));
			players[1].setPos(new Vec3(10, 10, 10));
			command(players[0], "tprequest " + players[1].getScoreboardName());
			command(players[1], "tpdeny");
			helper.runAtTickTime(2, () -> {
				if (players[0].blockPosition().equals(new BlockPos(0, 0, 0))) helper.succeed();
				else helper.fail("denied request teleported");
			});
		});
	}

	public static void instantAutoAcceptFlow(GameTestHelper helper) {
		pair(helper, players -> {
			helper.runAtTickTime(1, () -> command(players[1], "tpautoaccept add " + players[0].getScoreboardName()));
			helper.runAtTickTime(2, () -> {
				players[0].setPos(new Vec3(0, 0, 0));
				players[1].setPos(new Vec3(10, 10, 10));
			});
			helper.runAtTickTime(3, () -> command(players[0], "tprequest " + players[1].getScoreboardName()));
			helper.runAtTickTime(4, () -> {
				if (players[0].blockPosition().equals(players[1].blockPosition())) helper.succeed();
				else helper.fail("whitelist did not auto accept");
			});
		});
	}

	public static void combinedServerHelp(GameTestHelper helper) {
		if (ServerModHelpRegistry.entries().stream().anyMatch(entry -> entry.modId().equals(SimpleTPA.MOD_ID) && entry.helpCommand().equals("/simpletpa help"))) helper.succeed();
		else helper.fail("missing help");
	}

	public static void gameruleRegistration(GameTestHelper helper) {
		if (SimpleTPA.ALLOW_INSTANT_TPA_ACCEPTING != null) helper.succeed();
		else helper.fail("missing gamerule");
	}

	private static ServerPlayer player(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SPECTATOR;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}

	private static void command(ServerPlayer player, String command) {
		player.connection.handleChatCommand(new ServerboundChatCommandPacket(command));
	}

	private static void pair(GameTestHelper helper, Consumer<ServerPlayer[]> body) {
		int id = ++playerPairCounter;
		body.accept(new ServerPlayer[]{player(helper, "sender" + id), player(helper, "receiver" + id)});
	}
}
