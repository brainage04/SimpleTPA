package io.github.brainage04.simpletpa;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainagelib.gametest.GameTestPlayer;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import io.github.brainage04.simpletpa.command.TPAutoAcceptCommand;
import io.github.brainage04.simpletpa.command.TPRequestCommand;
import io.github.brainage04.simpletpa.data.InstantTpaWhitelist;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral server GameTest bodies. Both loaders compile this source set into their GameTest
 * mods: Fabric runs them through {@code @GameTest} methods, NeoForge through registered test
 * functions and {@code test_instance} data.
 */
public final class SimpleTPAGameTests {
	/** Ticks the accept-all expiry test needs: the confirmation window plus a few ticks of setup and checks. */
	public static final int ACCEPT_ALL_EXPIRY_MAX_TICKS = TPAutoAcceptCommand.CONFIRM_WINDOW_TICKS + 20;
	/** Test environment (data/simpletpa/test_environment) that sets simpletpa:auto_accept to false for its batch. */
	public static final String AUTO_ACCEPT_OFF_ENVIRONMENT = "simpletpa:auto_accept_off";
	private static int playerPairCounter;
	/** The joined players of each running test, closed when it passes or fails. */
	private static final Map<GameTestHelper, List<GameTestPlayer>> OPEN_PLAYERS = new IdentityHashMap<>();

	private SimpleTPAGameTests() {
	}

	public static void acceptFlows(GameTestHelper helper) {
		pair(helper, players -> {
			players[0].setPos(new Vec3(0, 0, 0));
			players[1].setPos(new Vec3(10, 10, 10));
			command(players[0], "tprequest " + players[1].getScoreboardName());
			command(players[1], "tpaccept");
			helper.runAtTickTime(2, () -> {
				if (players[0].blockPosition().equals(players[1].blockPosition())) succeed(helper);
				else fail(helper, "accepted request did not teleport");
			});
		});
	}

	public static void tpaAliasAcceptFlow(GameTestHelper helper) {
		pair(helper, players -> {
			players[0].setPos(new Vec3(0, 0, 0));
			players[1].setPos(new Vec3(10, 10, 10));
			command(players[0], "tpa " + players[1].getScoreboardName());
			command(players[1], "tpaccept");
			helper.runAtTickTime(2, () -> {
				if (players[0].blockPosition().equals(players[1].blockPosition())) succeed(helper);
				else fail(helper, "/tpa request did not teleport after /tpaccept");
			});
		});
	}

	public static void tpaTabCompletion(GameTestHelper helper) {
		pair(helper, players -> {
			var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
			String target = players[1].getScoreboardName();
			for (String literal : new String[]{"tpa ", "tprequest "}) {
				var parse = dispatcher.parse(literal, players[0].createCommandSourceStack());
				boolean suggested = dispatcher.getCompletionSuggestions(parse).join().getList().stream()
						.anyMatch(suggestion -> suggestion.getText().equals(target));
				if (!suggested) {
					fail(helper, "/" + literal.trim() + " did not suggest online player " + target);
					return;
				}
			}
			succeed(helper);
		});
	}

	public static void denyFlows(GameTestHelper helper) {
		pair(helper, players -> {
			players[0].setPos(new Vec3(0, 0, 0));
			players[1].setPos(new Vec3(10, 10, 10));
			command(players[0], "tprequest " + players[1].getScoreboardName());
			command(players[1], "tpdeny");
			helper.runAtTickTime(2, () -> {
				if (players[0].blockPosition().equals(new BlockPos(0, 0, 0))) succeed(helper);
				else fail(helper, "denied request teleported");
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
				if (players[0].blockPosition().equals(players[1].blockPosition())) succeed(helper);
				else fail(helper, "whitelist did not auto accept");
			});
		});
	}

	public static void requestButtons(GameTestHelper helper) {
		pair(helper, players -> {
			String sender = players[0].getScoreboardName();
			players[0].setPos(new Vec3(0, 0, 0));
			players[1].setPos(new Vec3(10, 10, 10));
			command(players[0], "tpa " + players[1].getScoreboardName());
			helper.runAtTickTime(2, () -> {
				Component notice = messages(players[1]).stream()
						.filter(message -> message.getString().contains("has sent you a TP request"))
						.findFirst()
						.orElse(null);
				if (notice == null) {
					fail(helper, "receiver got no request notice");
					return;
				}
				String expectedText = "[SimpleTPA] " + sender + " has sent you a TP request. Use /tpaccept to accept, or /tpdeny to deny. [ACCEPT] [DENY]";
				if (!notice.getString().equals(expectedText)) {
					fail(helper, "notice text was \"" + notice.getString() + "\"");
					return;
				}
				String accept = checkButton(notice, "[ACCEPT]", ChatFormatting.GREEN, "/tpaccept " + sender, "Click to accept " + sender + "'s TP request");
				String deny = checkButton(notice, "[DENY]", ChatFormatting.RED, "/tpdeny " + sender, "Click to deny " + sender + "'s TP request");
				if (accept != null || deny != null) {
					fail(helper, accept != null ? accept : deny);
					return;
				}
				// Run what the [ACCEPT] button would send, without the leading slash like the client does.
				command(players[1], "tpaccept " + sender);
			});
			helper.runAtTickTime(4, () -> {
				if (players[0].blockPosition().equals(players[1].blockPosition())) succeed(helper);
				else fail(helper, "[ACCEPT] button command did not teleport");
			});
		});
	}

	public static void tpautoAlias(GameTestHelper helper) {
		pair(helper, players -> {
			var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
			var subcommands = dispatcher.getCompletionSuggestions(dispatcher.parse("tpauto ", players[1].createCommandSourceStack())).join().getList().stream()
					.map(suggestion -> suggestion.getText())
					.collect(Collectors.toSet());
			if (!subcommands.containsAll(Set.of("*", "list", "add", "remove", "clear"))) {
				fail(helper, "/tpauto suggested " + subcommands);
				return;
			}
			boolean playerSuggested = dispatcher.getCompletionSuggestions(dispatcher.parse("tpauto add ", players[1].createCommandSourceStack())).join().getList().stream()
					.anyMatch(suggestion -> suggestion.getText().equals(players[0].getScoreboardName()));
			if (!playerSuggested) {
				fail(helper, "/tpauto add did not suggest " + players[0].getScoreboardName());
				return;
			}
			helper.runAtTickTime(1, () -> command(players[1], "tpauto add " + players[0].getScoreboardName()));
			helper.runAtTickTime(2, () -> {
				players[0].setPos(new Vec3(0, 0, 0));
				players[1].setPos(new Vec3(10, 10, 10));
				command(players[0], "tpa " + players[1].getScoreboardName());
			});
			helper.runAtTickTime(4, () -> {
				if (!InstantTpaWhitelist.list(players[1]).equals(List.of(players[0].getScoreboardName()))) {
					fail(helper, "/tpauto add did not whitelist the sender");
				} else if (!players[0].blockPosition().equals(players[1].blockPosition())) {
					fail(helper, "/tpauto whitelist did not auto accept");
				} else {
					succeed(helper);
				}
			});
		});
	}

	public static void acceptAllNeedsConfirmation(GameTestHelper helper) {
		pair(helper, players -> {
			helper.runAtTickTime(1, () -> command(players[1], "tpauto"));
			helper.runAtTickTime(2, () -> {
				players[0].setPos(new Vec3(0, 0, 0));
				players[1].setPos(new Vec3(10, 10, 10));
				command(players[0], "tpa " + players[1].getScoreboardName());
			});
			helper.runAtTickTime(4, () -> {
				if (InstantTpaWhitelist.acceptsAll(players[1])) {
					fail(helper, "a single /tpauto turned accept-all on");
				} else if (!received(players[1], "[SimpleTPA] Warning: accept-all lets anyone on the server teleport to you at any time without asking.")
						|| !received(players[1], "[SimpleTPA] Run /tpauto again within 30 seconds to turn it on.")) {
					fail(helper, "no accept-all warning, got " + texts(players[1]));
				} else if (!players[0].blockPosition().equals(new BlockPos(0, 0, 0))) {
					fail(helper, "unconfirmed accept-all teleported the requester");
				} else {
					succeed(helper);
				}
			});
		});
	}

	public static void acceptAllConfirmationExpires(GameTestHelper helper) {
		pair(helper, players -> {
			int secondRun = 2 + TPAutoAcceptCommand.CONFIRM_WINDOW_TICKS + 2;
			helper.runAtTickTime(2, () -> command(players[1], "tpautoaccept *"));
			helper.runAtTickTime(secondRun, () -> {
				fixture(players[1]).clearMessages();
				command(players[1], "tpautoaccept *");
			});
			helper.runAtTickTime(secondRun + 2, () -> {
				if (InstantTpaWhitelist.acceptsAll(players[1])) fail(helper, "a run after the window confirmed accept-all");
				else if (!received(players[1], "[SimpleTPA] Run /tpautoaccept * again within 30 seconds to turn it on.")) fail(helper, "late run did not warn again, got " + texts(players[1]));
				else succeed(helper);
			});
		});
	}

	public static void acceptAllTeleports(GameTestHelper helper) {
		pair(helper, players -> {
			helper.runAtTickTime(1, () -> command(players[1], "tpautoaccept"));
			helper.runAtTickTime(2, () -> command(players[1], "tpautoaccept"));
			helper.runAtTickTime(3, () -> {
				players[0].setPos(new Vec3(0, 0, 0));
				players[1].setPos(new Vec3(10, 10, 10));
				command(players[0], "tpa " + players[1].getScoreboardName());
			});
			helper.runAtTickTime(5, () -> {
				if (!received(players[1], "[SimpleTPA] Accept-all is now on. Anyone on the server can teleport to you without asking. Run /tpautoaccept again to turn it off.")) {
					fail(helper, "no accept-all confirmation, got " + texts(players[1]));
				} else if (!InstantTpaWhitelist.list(players[1]).isEmpty()) {
					fail(helper, "accept-all changed the whitelist");
				} else if (!players[0].blockPosition().equals(players[1].blockPosition())) {
					fail(helper, "accept-all did not teleport a requester who is not whitelisted");
				} else {
					succeed(helper);
				}
			});
		});
	}

	public static void acceptAllToggleOff(GameTestHelper helper) {
		pair(helper, players -> {
			helper.runAtTickTime(1, () -> command(players[1], "tpauto *"));
			helper.runAtTickTime(2, () -> command(players[1], "tpauto *"));
			helper.runAtTickTime(3, () -> command(players[1], "tpauto *"));
			helper.runAtTickTime(4, () -> {
				players[0].setPos(new Vec3(0, 0, 0));
				players[1].setPos(new Vec3(10, 10, 10));
				command(players[0], "tpa " + players[1].getScoreboardName());
			});
			helper.runAtTickTime(6, () -> {
				if (InstantTpaWhitelist.acceptsAll(players[1])) {
					fail(helper, "third run did not turn accept-all off");
				} else if (!received(players[1], "[SimpleTPA] Accept-all is now off. Only players on your auto-accept whitelist can teleport to you without asking.")) {
					fail(helper, "no accept-all off message, got " + texts(players[1]));
				} else if (!players[0].blockPosition().equals(new BlockPos(0, 0, 0))) {
					fail(helper, "requester teleported after accept-all was turned off");
				} else {
					succeed(helper);
				}
			});
		});
	}

	/** Runs in {@link #AUTO_ACCEPT_OFF_ENVIRONMENT}, so the gamerule is false only for this batch. */
	public static void acceptAllGameruleOff(GameTestHelper helper) {
		pair(helper, players -> {
			helper.runAtTickTime(1, () -> command(players[1], "tpauto"));
			helper.runAtTickTime(2, () -> command(players[1], "tpauto"));
			helper.runAtTickTime(3, () -> {
				players[0].setPos(new Vec3(0, 0, 0));
				players[1].setPos(new Vec3(10, 10, 10));
				command(players[0], "tpa " + players[1].getScoreboardName());
			});
			helper.runAtTickTime(5, () -> {
				boolean pending = TPRequestCommand.TP_REQUESTS.stream()
						.anyMatch(request -> request.matches(players[1].getUUID(), players[0].getUUID()));
				if (helper.getLevel().getServer().getGameRules().get(SimpleTPA.AUTO_ACCEPT)) {
					fail(helper, "test environment did not set simpletpa:auto_accept to false");
				} else if (!InstantTpaWhitelist.acceptsAll(players[1])) {
					fail(helper, "accept-all was not turned on");
				} else if (!received(players[1], "[SimpleTPA] It has no effect while the simpletpa:auto_accept gamerule is false.")) {
					fail(helper, "no gamerule note, got " + texts(players[1]));
				} else if (!players[0].blockPosition().equals(new BlockPos(0, 0, 0)) || !pending) {
					fail(helper, "accept-all bypassed simpletpa:auto_accept false");
				} else {
					succeed(helper);
				}
			});
		});
	}

	/**
	 * Saves and reloads the auto-accept file, with a hand-written entry in the pre-accept-all format
	 * added to it. Everything runs in one server tick, so other tests' entries round-trip unchanged.
	 */
	public static void acceptAllPersistence(GameTestHelper helper) {
		pair(helper, players -> {
			helper.runAtTickTime(1, () -> {
				command(players[1], "tpautoaccept");
				command(players[1], "tpautoaccept");
				command(players[0], "tpautoaccept add " + players[1].getScoreboardName());
			});
			helper.runAtTickTime(3, () -> {
				var server = helper.getLevel().getServer();
				Path file = server.getWorldPath(LevelResource.ROOT).resolve("simpletpa-instant-accept-whitelist.json");
				UUID legacyOwner = UUID.randomUUID();
				UUID legacyAllowed = UUID.randomUUID();
				JsonObject saved;
				try {
					InstantTpaWhitelist.save(server);
					saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
					JsonArray legacy = new JsonArray();
					JsonObject entry = new JsonObject();
					entry.addProperty("uuid", legacyAllowed.toString());
					entry.addProperty("name", "legacyFriend");
					legacy.add(entry);
					saved.add(legacyOwner.toString(), legacy);
					Files.writeString(file, saved.toString());
				} catch (IOException exception) {
					throw new IllegalStateException(exception);
				}

				JsonArray acceptAllEntries = saved.getAsJsonArray(players[1].getUUID().toString());
				if (acceptAllEntries == null || acceptAllEntries.size() != 1
						|| !acceptAllEntries.get(0).getAsJsonObject().get("uuid").getAsString().equals(InstantTpaWhitelist.ACCEPT_ALL_MARKER)) {
					fail(helper, "accept-all was saved as " + acceptAllEntries);
					return;
				}

				InstantTpaWhitelist.setAcceptAll(players[1], false);
				InstantTpaWhitelist.clear(players[0]);
				InstantTpaWhitelist.load(server);
				InstantTpaWhitelist.save(server);

				ServerPlayer legacyPlayer = player(helper, "legacyOwner" + playerPairCounter, legacyOwner);
				if (!InstantTpaWhitelist.acceptsAll(players[1])) fail(helper, "accept-all did not survive a reload");
				else if (InstantTpaWhitelist.acceptsAll(players[0])) fail(helper, "reload turned accept-all on for a whitelist owner");
				else if (!InstantTpaWhitelist.list(players[0]).equals(List.of(players[1].getScoreboardName()))) fail(helper, "whitelist did not survive a reload");
				else if (!InstantTpaWhitelist.list(legacyPlayer).equals(List.of("legacyFriend"))) fail(helper, "legacy whitelist entry did not load");
				else succeed(helper);
			});
		});
	}

	/**
	 * A whitelist file exactly as the previous version wrote it (pretty-printed, owners sorted, names sorted)
	 * loads and is written back byte for byte. Runs in one tick and restores the file afterwards.
	 */
	public static void whitelistFromPreviousVersionLoads(GameTestHelper helper) {
		pair(helper, players -> {
			var server = helper.getLevel().getServer();
			Path file = server.getWorldPath(LevelResource.ROOT).resolve("simpletpa-instant-accept-whitelist.json");
			UUID friend = UUID.fromString("00000000-0000-4000-8000-000000000001");
			String owner = players[0].getUUID().toString();
			String acceptAllOwner = players[1].getUUID().toString();
			String whitelistEntry = "\"" + owner + "\": [\n    {\n      \"uuid\": \"" + friend + "\",\n      \"name\": \"legacyFriend\"\n    }\n  ]";
			String acceptAllEntry = "\"" + acceptAllOwner + "\": [\n    {\n      \"uuid\": \"*\",\n      \"name\": \"everyone\"\n    }\n  ]";
			String previous = "{\n  " + (owner.compareTo(acceptAllOwner) < 0
					? whitelistEntry + ",\n  " + acceptAllEntry
					: acceptAllEntry + ",\n  " + whitelistEntry) + "\n}";
			try {
				InstantTpaWhitelist.save(server);
				byte[] original = Files.readAllBytes(file);
				try {
					Files.writeString(file, previous);
					InstantTpaWhitelist.load(server);
					if (!InstantTpaWhitelist.list(players[0]).equals(List.of("legacyFriend"))) {
						fail(helper, "the previous version's whitelist did not load: " + InstantTpaWhitelist.list(players[0]));
						return;
					}
					if (!InstantTpaWhitelist.acceptsAll(players[1])) {
						fail(helper, "the previous version's accept-all did not load");
						return;
					}
					InstantTpaWhitelist.save(server);
					if (!Files.readString(file).equals(previous)) {
						fail(helper, "saving unchanged settings changed the file to " + Files.readString(file));
						return;
					}
				} finally {
					Files.write(file, original);
					InstantTpaWhitelist.load(server);
				}
			} catch (IOException exception) {
				throw new IllegalStateException(exception);
			}
			succeed(helper);
		});
	}

	/**
	 * A whitelist file that failed to load (malformed or a newer schema) must not stop the server from starting,
	 * and neither a later change nor shutdown may overwrite it. Runs in one tick and restores the file afterwards,
	 * so other tests' entries round-trip unchanged.
	 */
	public static void unreadableWhitelistIsNotOverwritten(GameTestHelper helper) {
		pair(helper, players -> {
			var server = helper.getLevel().getServer();
			Path file = server.getWorldPath(LevelResource.ROOT).resolve("simpletpa-instant-accept-whitelist.json");
			try {
				InstantTpaWhitelist.save(server);
				byte[] original = Files.readAllBytes(file);
				try {
					for (String document : List.of("{\"" + players[0].getUUID() + "\": [", "{\"schemaVersion\": 2, \"owners\": {}}")) {
						byte[] bytes = document.getBytes(java.nio.charset.StandardCharsets.UTF_8);
						Files.write(file, bytes);
						InstantTpaWhitelist.load(server);
						if (TPAutoAcceptCommand.add(players[0].createCommandSourceStack(), players[1]) != -1) {
							fail(helper, "adding to an unreadable whitelist reported success");
							return;
						}
						if (!InstantTpaWhitelist.list(players[0]).equals(List.of(players[1].getScoreboardName()))) {
							fail(helper, "the unsaved whitelist change did not apply for the session");
							return;
						}
						if (!received(players[0], "[SimpleTPA] " + players[1].getScoreboardName()
								+ " can instantly teleport to you for this session, but your auto-accept whitelist could not be saved.")) {
							fail(helper, "the player was not told the change was not saved: " + texts(players[0]));
							return;
						}
						InstantTpaWhitelist.clear(players[0]);
						// What the server-stopping hook does.
						InstantTpaWhitelist.save(server);
						if (!java.util.Arrays.equals(Files.readAllBytes(file), bytes)) {
							fail(helper, "an unreadable whitelist was overwritten: " + document);
							return;
						}
					}
				} finally {
					Files.write(file, original);
					InstantTpaWhitelist.load(server);
				}
			} catch (IOException exception) {
				throw new IllegalStateException(exception);
			}
			succeed(helper);
		});
	}

	public static void addWildcardRejected(GameTestHelper helper) {
		pair(helper, players -> {
			var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
			var parse = dispatcher.parse("tpautoaccept add *", players[1].createCommandSourceStack());
			if (!parse.getReader().canRead() && parse.getExceptions().isEmpty()) {
				fail(helper, "/tpautoaccept add * parsed as a command");
				return;
			}
			command(players[1], "tpautoaccept add *");
			command(players[1], "tpauto add *");
			helper.runAtTickTime(2, () -> {
				if (InstantTpaWhitelist.acceptsAll(players[1]) || !InstantTpaWhitelist.list(players[1]).isEmpty()) fail(helper, "add * changed auto-accept settings");
				else succeed(helper);
			});
		});
	}

	public static void combinedServerHelp(GameTestHelper helper) {
		if (ServerModHelpRegistry.entries().stream().anyMatch(entry -> entry.modId().equals(SimpleTPA.MOD_ID) && entry.helpCommand().equals("/simpletpa help"))) succeed(helper);
		else fail(helper, "missing help");
	}

	public static void gameruleRegistration(GameTestHelper helper) {
		GameRule<?> registered = BuiltInRegistries.GAME_RULE.getValue(Identifier.fromNamespaceAndPath(SimpleTPA.MOD_ID, "auto_accept"));
		if (SimpleTPA.AUTO_ACCEPT != null && registered == SimpleTPA.AUTO_ACCEPT) succeed(helper);
		else fail(helper, "simpletpa:auto_accept gamerule is not registered");
	}

	/** Returns null when {@code button} is present with the given colour, run-command and hover text, else why not. */
	private static String checkButton(Component message, String label, ChatFormatting color, String command, String hover) {
		Component button = find(message, label);
		if (button == null) return "no " + label + " button";
		var style = button.getStyle();
		if (!TextColor.fromLegacyFormat(color).equals(style.getColor())) return label + " colour was " + style.getColor();
		if (!(style.getClickEvent() instanceof ClickEvent.RunCommand(String run)) || !run.equals(command)) return label + " click event was " + style.getClickEvent();
		if (!(style.getHoverEvent() instanceof HoverEvent.ShowText(Component text)) || !text.getString().equals(hover)) return label + " hover was " + style.getHoverEvent();
		return null;
	}

	private static Component find(Component component, String text) {
		if (component.getSiblings().isEmpty() && component.getString().equals(text)) return component;
		for (Component sibling : component.getSiblings()) {
			Component found = find(sibling, text);
			if (found != null) return found;
		}
		return null;
	}

	private static List<Component> messages(ServerPlayer player) {
		return fixture(player).messages();
	}

	private static boolean received(ServerPlayer player, String text) {
		return fixture(player).received(text);
	}

	private static List<String> texts(ServerPlayer player) {
		return fixture(player).messageTexts();
	}

	private static ServerPlayer player(GameTestHelper helper, String name) {
		return player(helper, name, UUID.randomUUID());
	}

	/** Joins a spectator player that stays connected until the test calls {@link #succeed} or {@link #fail}. */
	private static ServerPlayer player(GameTestHelper helper, String name, UUID id) {
		GameTestPlayer fixture = GameTestPlayer.join(helper, new GameProfile(id, name)).gameMode(GameType.SPECTATOR);
		synchronized (OPEN_PLAYERS) {
			OPEN_PLAYERS.computeIfAbsent(helper, ignored -> new ArrayList<>()).add(fixture);
		}
		return fixture.player();
	}

	private static GameTestPlayer fixture(ServerPlayer player) {
		synchronized (OPEN_PLAYERS) {
			return OPEN_PLAYERS.values().stream().flatMap(List::stream)
					.filter(fixture -> fixture.player() == player)
					.findFirst()
					.orElseThrow(() -> new IllegalStateException(player.getScoreboardName() + " is not a joined test player"));
		}
	}

	private static void command(ServerPlayer player, String command) {
		fixture(player).command(command);
	}

	private static void pair(GameTestHelper helper, Consumer<ServerPlayer[]> body) {
		int id = ++playerPairCounter;
		body.accept(new ServerPlayer[]{player(helper, "sender" + id), player(helper, "receiver" + id)});
	}

	/** Disconnects the test's players, then passes the test. */
	private static void succeed(GameTestHelper helper) {
		closePlayers(helper);
		helper.succeed();
	}

	/** Disconnects the test's players, then fails the test. */
	private static void fail(GameTestHelper helper, String message) {
		closePlayers(helper);
		helper.fail(message);
	}

	private static void closePlayers(GameTestHelper helper) {
		List<GameTestPlayer> players;
		synchronized (OPEN_PLAYERS) {
			players = OPEN_PLAYERS.remove(helper);
		}
		if (players != null) {
			players.forEach(GameTestPlayer::close);
		}
	}
}
