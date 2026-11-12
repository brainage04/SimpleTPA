package io.github.brainage04.simpletpa;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import io.github.brainage04.brainagelib.gametest.GameTestPlayer;
import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import java.util.Properties;
import java.util.function.BiConsumer;
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

/**
 * The showcase film: one real client and a second, joined player ({@value #FRIEND_NAME}) on a
 * dedicated server. The server GameTests in {@code SimpleTPAGameTests} check what it shows; the
 * client GameTest checks the [ACCEPT] click, the one step only a real client takes.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SimpleTPAFilm {
    private static final String FRIEND_NAME = "tpaFriend";

    private static final Caption REQUEST =
            new Caption(
                    "simpletpa.request",
                    "Teleport request",
                    "You ask " + FRIEND_NAME + " if you can teleport to them");
    private static final Caption ACCEPT =
            new Caption(
                    "simpletpa.accept",
                    "Request accepted",
                    FRIEND_NAME + " accepts and you arrive at their side");
    private static final Caption DENY =
            new Caption(
                    "simpletpa.deny",
                    "Request denied",
                    FRIEND_NAME + " denies, so you stay where you are");
    private static final Caption CLICK_ACCEPT =
            new Caption(
                    "simpletpa.clickaccept",
                    "Accept in chat",
                    FRIEND_NAME + " asks to teleport to you and you click [ACCEPT]");
    private static final Caption AUTO_ACCEPT =
            new Caption(
                    "simpletpa.autoaccept",
                    "Auto-accept",
                    "You whitelist " + FRIEND_NAME + ", who can now teleport to you instantly");

    private static final int STAGE_Y = 64;
    private static final Vec3 PLAYER_POSITION = new Vec3(-2.5D, STAGE_Y, 0.5D);
    private static final Vec3 FRIEND_POSITION = new Vec3(2.5D, STAGE_Y, 0.5D);
    private static final float EAST = -90.0F;
    private static final float WEST = 90.0F;
    private static final float PLAYER_PITCH = 6.0F;
    private static final int COMMAND_VISIBLE_TICKS = 20;

    private SimpleTPAFilm() {}

    /**
     * {@code afterClickedAccept} runs on the server once the client has clicked [ACCEPT] in chat,
     * with the friend and the client's player.
     */
    public static void run(
            ClientGameTestContext context,
            BiConsumer<ServerPlayer, ServerPlayer> afterClickedAccept) {
        String playerName = context.computeOnClient(client -> client.getUser().getName());
        // Command suggestions would cover the chat while a command sits in the chat box.
        context.runOnClient(client -> client.options.autoSuggestions().set(false));
        Properties serverProperties = ClientGameTestServers.flatServerProperties();
        // The friend joins first and takes a slot, so the client needs a second one.
        serverProperties.setProperty("max-players", "2");
        GameTestPlayer[] friend = new GameTestPlayer[1];
        ClientGameTestServers.withDedicatedServer(
                context,
                serverProperties,
                "SimpleTPA recording GameTest",
                server -> friend[0] = prepareWorld(server, playerName),
                server -> {
                    try {
                        context.runOnClient(
                                client -> client.gui.hud.getChat().clearMessages(false));
                        showAcceptedRequest(context, server, playerName);
                        showDeniedRequest(context, server, playerName);
                        showClickedAccept(context, server, playerName, afterClickedAccept);
                        showAutoAcceptedRequest(context, server, playerName);
                    } finally {
                        server.runOnServer(minecraftServer -> friend[0].close());
                    }
                });
    }

    private static void showAcceptedRequest(
            ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
        show(context, REQUEST);
        context.waitTicks(10);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(30);

        show(context, ACCEPT);
        context.waitTicks(10);
        server.runOnServer(
                minecraftServer ->
                        SimpleTPAFakePlayerScenario.executeCommand(
                                friend(minecraftServer), "tpaccept"));
        context.waitTicks(38);
    }

    private static void showDeniedRequest(
            ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
        show(context, DENY);
        server.runOnServer(
                minecraftServer ->
                        placeActors(player(minecraftServer, playerName), friend(minecraftServer)));
        context.waitTicks(10);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(25);
        server.runOnServer(
                minecraftServer ->
                        SimpleTPAFakePlayerScenario.executeCommand(
                                friend(minecraftServer), "tpdeny"));
        context.waitTicks(35);
    }

    private static void showClickedAccept(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            String playerName,
            BiConsumer<ServerPlayer, ServerPlayer> afterClickedAccept) {
        show(context, CLICK_ACCEPT);
        server.runOnServer(
                minecraftServer ->
                        placeActors(player(minecraftServer, playerName), friend(minecraftServer)));
        context.waitTicks(10);
        server.runOnServer(
                minecraftServer ->
                        SimpleTPAFakePlayerScenario.executeCommand(
                                friend(minecraftServer), "tpa " + playerName));
        context.waitTicks(25);
        context.setScreen(() -> new ChatScreen("", false));
        context.waitTicks(10);
        String acceptCommand =
                "/tpaccept "
                        + server.computeOnServer(
                                minecraftServer -> friend(minecraftServer).getUUID());
        double[] button = context.computeOnClient(client -> findRunCommand(client, acceptCommand));
        context.getInput().setCursorPos(button[0], button[1]);
        context.waitTicks(25);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
        server.runOnServer(
                minecraftServer ->
                        afterClickedAccept.accept(
                                friend(minecraftServer), player(minecraftServer, playerName)));
        context.waitTicks(15);
        if (context.computeOnClient(client -> client.gui.screen() instanceof ChatScreen)) {
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        }
        context.waitTicks(30);
    }

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
                ActiveTextCollector.ClickableStyleFinder finder =
                        new ActiveTextCollector.ClickableStyleFinder(client.font, x, y);
                chat.captureClickableText(
                        finder,
                        guiHeight,
                        client.gui.hud.getGuiTicks(),
                        ChatComponent.DisplayMode.FOREGROUND);
                Style style = finder.result();
                if (style != null
                        && style.getClickEvent() instanceof ClickEvent.RunCommand(String run)
                        && run.equals(command)) {
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
        return new double[] {
            (sumX / (double) hits + 0.5D) * scaleX, (sumY / (double) hits + 0.5D) * scaleY
        };
    }

    private static void showAutoAcceptedRequest(
            ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
        show(context, AUTO_ACCEPT);
        server.runOnServer(
                minecraftServer ->
                        placeActors(player(minecraftServer, playerName), friend(minecraftServer)));
        context.waitTicks(10);
        typeCommand(context, "tpautoaccept add " + FRIEND_NAME);
        context.waitTicks(25);
        server.runOnServer(
                minecraftServer ->
                        SimpleTPAFakePlayerScenario.executeCommand(
                                friend(minecraftServer), "tpa " + playerName));
        context.waitTicks(38);
    }

    /**
     * Typed one character at a time, the partial command shows a parse error above it, so the
     * command is entered whole.
     */
    private static void typeCommand(ClientGameTestContext context, String command) {
        context.setScreen(() -> new ChatScreen("", false));
        context.getInput().typeChars("/" + command);
        context.waitTicks(COMMAND_VISIBLE_TICKS);
        context.getInput().pressKey(InputConstants.KEY_RETURN);
    }

    private static void show(ClientGameTestContext context, Caption caption) {
        ClientGameTestRecorder.showStep(
                context, caption.id(), caption.title(), caption.description());
    }

    private static GameTestPlayer prepareWorld(
            TestDedicatedServerContext server, String playerName) {
        return server.computeOnServer(
                minecraftServer -> {
                    ServerLevel level = minecraftServer.overworld();
                    buildStage(level);
                    minecraftServer
                            .getCommands()
                            .performPrefixedCommand(
                                    minecraftServer
                                            .createCommandSourceStack()
                                            .withSuppressedOutput(),
                                    "time set noon");
                    minecraftServer
                            .getGameRules()
                            .set(GameRules.ADVANCE_TIME, false, minecraftServer);
                    minecraftServer
                            .getGameRules()
                            .set(GameRules.RESPAWN_RADIUS, 0, minecraftServer);
                    minecraftServer.setRespawnData(
                            LevelData.RespawnData.of(
                                    Level.OVERWORLD,
                                    BlockPos.containing(PLAYER_POSITION),
                                    EAST,
                                    PLAYER_PITCH));
                    GameTestPlayer friend = SimpleTPAFakePlayerScenario.join(level, FRIEND_NAME);
                    placeFriend(friend.player());
                    SimpleTPAFakePlayerScenario.colorName(level, playerName, "aqua");
                    SimpleTPAFakePlayerScenario.colorName(level, FRIEND_NAME, "gold");
                    return friend;
                });
    }

    private static void buildStage(ServerLevel level) {
        for (BlockPos position :
                BlockPos.betweenClosed(-30, STAGE_Y - 1, -24, 30, STAGE_Y + 4, 24)) {
            level.setBlock(
                    position,
                    position.getY() == STAGE_Y - 1
                            ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                            : Blocks.AIR.defaultBlockState(),
                    3);
        }
        level.setBlock(
                BlockPos.containing(PLAYER_POSITION).below(),
                Blocks.DIAMOND_BLOCK.defaultBlockState(),
                3);
        level.setBlock(
                BlockPos.containing(FRIEND_POSITION).below(),
                Blocks.GOLD_BLOCK.defaultBlockState(),
                3);
    }

    private static void placeActors(ServerPlayer player, ServerPlayer friend) {
        SimpleTPAFakePlayerScenario.place(player, PLAYER_POSITION, EAST, PLAYER_PITCH);
        placeFriend(friend);
    }

    private static void placeFriend(ServerPlayer friend) {
        SimpleTPAFakePlayerScenario.place(friend, FRIEND_POSITION, WEST, 0.0F);
    }

    private static ServerPlayer friend(MinecraftServer server) {
        return player(server, FRIEND_NAME);
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
        if (player == null) {
            throw new AssertionError("Expected " + name + " to remain connected.");
        }
        return player;
    }

    private record Caption(String id, String title, String description) {}
}
