package io.github.brainage04.simpletpa;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import io.github.brainage04.brainagelib.gametest.GameTestPlayer;
import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import io.github.brainage04.minecraftmodconventions.ClientGameTestStages;
import io.github.brainage04.simpletpa.command.TPRequestCommand;
import io.github.brainage04.simpletpa.data.InstantTpaWhitelist;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
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

    private static final Caption NOTHING_TO_LAND_ON =
            new Caption(
                    "simpletpa.nolanding",
                    "No landing spot",
                    "Nothing under " + FRIEND_NAME + ", so your request waits");
    private static final Caption HAPPY_GHAST =
            new Caption(
                    "simpletpa.happyghast",
                    "Flying happy ghast",
                    "You arrive on top of the happy ghast " + FRIEND_NAME + " rides");
    private static final Caption LANDING_CHECK_OFF =
            new Caption(
                    "simpletpa.landingoff",
                    "Landing check off",
                    "With /tpasafety off, you arrive above the drop and fall");
    private static final Caption SHORT_DROP =
            new Caption(
                    "simpletpa.shortdrop",
                    "Short drop",
                    "A two-block drop below " + FRIEND_NAME + " does not stop you");
    private static final Caption ON_MAGMA =
            new Caption(
                    "simpletpa.magma",
                    "Dangerous block",
                    FRIEND_NAME + " stands on magma, so a survival request waits");
    private static final Caption CREATIVE =
            new Caption(
                    "simpletpa.creative",
                    "Creative mode",
                    "In creative mode the same request goes through");

    private static final int STAGE_Y = 64;
    private static final Vec3 PLAYER_POSITION = new Vec3(-2.5D, STAGE_Y, 0.5D);
    private static final Vec3 FRIEND_POSITION = new Vec3(2.5D, STAGE_Y, 0.5D);
    private static final float EAST = -90.0F;
    private static final float WEST = 90.0F;
    private static final float PLAYER_PITCH = 6.0F;
    private static final int COMMAND_VISIBLE_TICKS = 20;

    /** Frames a stage keeps after its end marker, so a cut never lands on the next scene. */
    private static final int STAGE_HOLD_TICKS = 6;

    /** The stage on screen, for {@link #show}. */
    private static String stage;

    private static final Vec3 LOOKOUT_POSITION = new Vec3(-11.5D, STAGE_Y, 0.5D);
    private static final Vec3 HIGH_POSITION = new Vec3(2.5D, 78.0D, -5.5D);
    private static final Vec3 GHAST_POSITION = new Vec3(2.5D, 70.0D, 0.5D);
    private static final Vec3 STEP_POSITION = new Vec3(2.2D, STAGE_Y + 2, 0.5D);
    private static final Vec3 STEP_BLOCK = new Vec3(1.0D, STAGE_Y + 1, 0.0D);
    private static final Vec3 MAGMA_POSITION = new Vec3(2.5D, STAGE_Y, 4.5D);

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
                        // The first frame before the world draws has no terrain yet.
                        context.waitTicks(10);
                        showAcceptedRequest(context, server, playerName);
                        showDeniedRequest(context, server, playerName);
                        showClickedAccept(context, server, playerName, afterClickedAccept);
                        showAutoAcceptedRequest(context, server, playerName);
                        showLandingChecks(context, server, playerName, friend[0]);
                    } finally {
                        if (stage != null) {
                            ClientGameTestStages.endStage(context, stage, STAGE_HOLD_TICKS);
                            stage = null;
                        }
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
        context.waitTicks(8);
        server.runOnServer(minecraftServer -> stepAside(friend(minecraftServer)));
        context.waitTicks(30);
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
                minecraftServer -> {
                    afterClickedAccept.accept(
                            friend(minecraftServer), player(minecraftServer, playerName));
                    stepAside(friend(minecraftServer));
                });
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
        context.waitTicks(8);
        server.runOnServer(minecraftServer -> stepAside(friend(minecraftServer)));
        context.waitTicks(30);
    }

    /**
     * The landing checks, one scenario at a time: a request that waits, then a request that goes
     * through. The friend's whitelist entry from the auto-accept scenario answers each request at
     * once, so the reason a request waits is the message the player sees.
     */
    private static void showLandingChecks(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            String playerName,
            GameTestPlayer friend) {
        show(context, NOTHING_TO_LAND_ON);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    clearRequests(friend(minecraftServer), player);
                    // The friend approves the client's requests, so each one is answered at once.
                    InstantTpaWhitelist.add(friend(minecraftServer), player);
                    friend(minecraftServer).stopRiding();
                    SimpleTPAFakePlayerScenario.place(
                            friend(minecraftServer), HIGH_POSITION, WEST, 0.0F);
                    look(player, LOOKOUT_POSITION, HIGH_POSITION);
                });
        context.waitTicks(10);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(30);

        show(context, LANDING_CHECK_OFF);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer rider = friend(minecraftServer);
                    clearRequests(rider, player);
                    rider.setXRot(45.0F);
                    SimpleTPAFakePlayerScenario.place(rider, HIGH_POSITION, WEST, 45.0F);
                    look(player, LOOKOUT_POSITION, HIGH_POSITION);
                    minecraftServer
                            .getCommands()
                            .performPrefixedCommand(
                                    player.createCommandSourceStack(), "tpasafety off");
                });
        context.waitTicks(10);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(45);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    player.setHealth(20.0F);
                    player.getFoodData().setFoodLevel(20);
                });

        show(context, HAPPY_GHAST);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer rider = friend(minecraftServer);
                    clearRequests(rider, player);
                    rider.startRiding(filmGhast(minecraftServer, rider), true, false);
                    rider.setYRot(WEST);
                    rider.setYHeadRot(WEST);
                    rider.setXRot(30.0F);
                    look(player, LOOKOUT_POSITION, rider.position());
                });
        context.waitTicks(10);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(25);
        assertOnGhast(context, server, friend);
        context.waitTicks(20);

        show(context, SHORT_DROP);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer target = friend(minecraftServer);
                    clearRequests(target, player);
                    target.stopRiding();
                    target.setShiftKeyDown(true);
                    SimpleTPAFakePlayerScenario.place(target, STEP_POSITION, WEST, 20.0F);
                    look(player, PLAYER_POSITION, STEP_POSITION);
                    // The check is on again here, so the short drop passes on its length.
                    minecraftServer
                            .getCommands()
                            .performPrefixedCommand(
                                    player.createCommandSourceStack(), "tpasafety on");
                });
        context.waitTicks(10);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(8);
        server.runOnServer(minecraftServer -> stepAside(friend(minecraftServer)));
        context.waitTicks(30);

        show(context, ON_MAGMA);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer target = friend(minecraftServer);
                    clearRequests(target, player);
                    target.setShiftKeyDown(false);
                    SimpleTPAFakePlayerScenario.place(target, MAGMA_POSITION, WEST, 0.0F);
                    look(player, PLAYER_POSITION, MAGMA_POSITION);
                });
        context.waitTicks(10);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(35);

        show(context, CREATIVE);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    clearRequests(friend(minecraftServer), player);
                    player.setGameMode(GameType.CREATIVE);
                    look(player, PLAYER_POSITION, MAGMA_POSITION);
                });
        context.waitTicks(10);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(8);
        server.runOnServer(minecraftServer -> stepAside(friend(minecraftServer)));
        context.waitTicks(30);
        server.runOnServer(
                minecraftServer ->
                        player(minecraftServer, playerName).setGameMode(GameType.SURVIVAL));
        context.waitTicks(10);
    }

    /**
     * Fails unless the client's player stands on the happy ghast: the game only holds a player
     * whose feet are at or above its top (HappyGhast.java:576-585), so this is the client-side
     * check of the arrival spot.
     */
    private static void assertOnGhast(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            GameTestPlayer friend) {
        double top =
                server.computeOnServer(
                        minecraftServer ->
                                filmGhast(minecraftServer, friend(minecraftServer))
                                        .getBoundingBox()
                                        .maxY);
        double[] player =
                context.computeOnClient(
                        (Minecraft game) ->
                                game.player == null
                                        ? new double[] {Double.NaN, Double.NaN}
                                        : new double[] {
                                            game.player.getY(), game.player.fallDistance
                                        });
        if (Math.abs(player[0] - top) > 0.25 || player[1] > 0.25) {
            throw new AssertionError(
                    "The player did not stay on the happy ghast: feet at "
                            + player[0]
                            + ", its top "
                            + top
                            + ", fall distance "
                            + player[1]);
        }
    }

    /**
     * Moves the friend three blocks the way they face, out of the camera of a player who has just
     * arrived on them, so the shot shows both of them instead of the inside of the friend's head.
     */
    private static void stepAside(ServerPlayer friend) {
        Vec3 facing =
                new Vec3(
                        -Math.sin(Math.toRadians(friend.getYRot())),
                        0.0D,
                        Math.cos(Math.toRadians(friend.getYRot())));
        SimpleTPAFakePlayerScenario.place(
                friend,
                friend.position().add(facing.scale(3.0D)),
                friend.getYRot(),
                friend.getXRot());
    }

    /** Places {@code player} at {@code position} looking at {@code target}. */
    private static void look(ServerPlayer player, Vec3 position, Vec3 target) {
        Vec3 towards = target.subtract(position.add(0.0D, 1.62D, 0.0D));
        float yaw = (float) Math.toDegrees(Math.atan2(towards.z, towards.x)) - 90.0F;
        float reach = (float) Math.sqrt(towards.x * towards.x + towards.z * towards.z);
        float pitch = (float) -Math.toDegrees(Math.atan2(towards.y, reach));
        SimpleTPAFakePlayerScenario.place(player, position, yaw, pitch);
    }

    /** Forgets the client's pending request, so the next scenario starts from a clean slate. */
    private static void clearRequests(ServerPlayer friend, ServerPlayer player) {
        TPRequestCommand.TP_REQUESTS.removeIf(
                request -> request.matches(friend.getUUID(), player.getUUID()));
    }

    /** The happy ghast the film spawns before the client joins. */
    private static HappyGhast filmGhast(MinecraftServer server, ServerPlayer friend) {
        return friend
                .level()
                .getEntities(
                        EntityTypes.HAPPY_GHAST,
                        friend.getBoundingBox().inflate(64.0),
                        entity -> true)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected the film's happy ghast."));
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

    /** Shows a caption and films it as one stage, ending the one before it. */
    private static void show(ClientGameTestContext context, Caption caption) {
        if (stage != null) {
            ClientGameTestStages.endStage(context, stage, STAGE_HOLD_TICKS);
        }
        ClientGameTestStages.markStage(caption.id());
        stage = caption.id();
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
                    spawnGhast(level);
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
        // The step the sneaking target stands on, two blocks above the stage floor, and the magma
        // block a survival requester is refused on.
        level.setBlock(BlockPos.containing(STEP_BLOCK), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(
                BlockPos.containing(MAGMA_POSITION).below(),
                Blocks.MAGMA_BLOCK.defaultBlockState(),
                3);
    }

    private static void spawnGhast(ServerLevel level) {
        HappyGhast ghast = EntityTypes.HAPPY_GHAST.create(level, EntitySpawnReason.COMMAND);
        if (ghast == null) {
            throw new AssertionError("Could not create the film's happy ghast.");
        }
        ghast.setPos(GHAST_POSITION.x, GHAST_POSITION.y, GHAST_POSITION.z);
        ghast.setNoAi(true);
        ghast.setPersistenceRequired();
        ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.pick(DyeColor.WHITE)));
        level.addFreshEntity(ghast);
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
