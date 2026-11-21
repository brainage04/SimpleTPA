package io.github.brainage04.simpletpa;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import io.github.brainage04.brainagelib.gametest.GameTestPlayer;
import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import io.github.brainage04.minecraftmodconventions.ClientGameTestStages;
import io.github.brainage04.minecraftmodconventions.InputOverlay;
import io.github.brainage04.simpletpa.command.TPRequestCommand;
import io.github.brainage04.simpletpa.data.InstantTpaWhitelist;
import io.github.brainage04.simpletpa.data.SafetyPreference;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
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
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

/**
 * The showcase film: one real client and a second, joined player ({@value #FRIEND_NAME}) on a
 * dedicated server. The server GameTests in {@code SimpleTPAGameTests} check what it shows; the
 * client GameTest checks the [ACCEPT] click, the one step only a real client takes.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SimpleTPAFilm {
    private static final String FRIEND_NAME = "tpaFriend";

    private static final Caption DENY =
            new Caption(
                    "simpletpa.deny",
                    "Request denied",
                    FRIEND_NAME + " denies, so you stay where you are");
    private static final Caption ACCEPT =
            new Caption(
                    "simpletpa.accept",
                    "Request accepted",
                    FRIEND_NAME + " accepts and you arrive at their side");
    private static final Caption CLICK_DENY =
            new Caption(
                    "simpletpa.clickdeny",
                    "Deny in chat",
                    FRIEND_NAME + " asks to teleport to you and you click [DENY]");
    private static final Caption CLICK_ACCEPT =
            new Caption(
                    "simpletpa.clickaccept",
                    "Accept in chat",
                    FRIEND_NAME + " asks to teleport to you and you click [ACCEPT]");
    private static final Caption WAITING =
            new Caption(
                    "simpletpa.waiting",
                    "Request waits",
                    FRIEND_NAME + " is not on your list, so their request waits for you");
    private static final Caption AUTO_ACCEPT =
            new Caption(
                    "simpletpa.autoaccept",
                    "Auto-accept",
                    "You add " + FRIEND_NAME + " to your list, so their request goes through");

    private static final Caption NOTHING_TO_LAND_ON =
            new Caption(
                    "simpletpa.nolanding",
                    "No landing spot",
                    "Nothing under " + FRIEND_NAME + ", so your request waits");
    private static final Caption LANDING_CHECK_OFF =
            new Caption(
                    "simpletpa.landingoff",
                    "Landing check off",
                    "With /tpasafety off you arrive above the drop and fall");
    private static final Caption FLYING_TARGET =
            new Caption(
                    "simpletpa.flyingtarget",
                    "Flying target",
                    FRIEND_NAME + " is flying, so your request waits");
    private static final Caption HAPPY_GHAST =
            new Caption(
                    "simpletpa.happyghast",
                    "Flying happy ghast",
                    "You arrive on top of the happy ghast " + FRIEND_NAME + " rides");
    private static final Caption LONG_DROP =
            new Caption(
                    "simpletpa.longdrop",
                    "Long drop",
                    FRIEND_NAME + " is above a six-block drop, so run it again to go");
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
    private static final Caption LANDING_CHECKS_OFF =
            new Caption(
                    "simpletpa.landingchecksoff",
                    "Landing checks off",
                    "With landing_checks off, the same request goes through");
    private static final Caption CREATIVE =
            new Caption(
                    "simpletpa.creative",
                    "Creative mode",
                    "In creative mode the same request goes through");
    private static final Caption ON_POWDER_SNOW =
            new Caption(
                    "simpletpa.powdersnow",
                    "Powder snow",
                    FRIEND_NAME + " stands in powder snow, so a survival request waits");
    private static final Caption RESET_FALL_OFF =
            new Caption(
                    "simpletpa.resetfalloff",
                    "Fall kept",
                    "With reset_fall off, the fall you carry makes the drop hurt");
    private static final Caption RESET_FALL_ON =
            new Caption(
                    "simpletpa.resetfallon",
                    "Fall reset",
                    "With reset_fall on you arrive with no fall and take no damage");

    private static final int STAGE_Y = 64;
    private static final Vec3 PLAYER_POSITION = new Vec3(-2.5D, STAGE_Y, 0.5D);
    private static final Vec3 FRIEND_POSITION = new Vec3(2.5D, STAGE_Y, 0.5D);
    private static final float EAST = -90.0F;
    private static final float WEST = 90.0F;
    private static final float PLAYER_PITCH = 6.0F;
    private static final int COMMAND_VISIBLE_TICKS = 20;

    /** Frames a stage keeps after its end marker, so a cut never lands on the next scene. */
    private static final int STAGE_HOLD_TICKS = 6;

    /** Ticks a stage's scene is given to settle before its caption, so no cut opens mid-move. */
    private static final int SCENE_SETTLE_TICKS = 8;

    /**
     * Ticks the caption is up before its stage marker. The cut keeps 0.10 s before the marker, so
     * the caption has to be on screen by then, and the scene behind it is already the stage's.
     */
    private static final int CAPTION_LEAD_TICKS = 4;

    /** The stage on screen, for {@link #endStage}. */
    private static String stage;

    private static final Vec3 LOOKOUT_POSITION = new Vec3(-11.5D, STAGE_Y, 0.5D);
    private static final Vec3 HIGH_POSITION = new Vec3(2.5D, 78.0D, -5.5D);
    private static final Vec3 GHAST_POSITION = new Vec3(2.5D, 70.0D, 0.5D);

    /**
     * Where the camera stands on the ghast's top after the arrival. The arrival lands at the
     * middle of the ghast's top, 1.7 blocks from the friend's seat (Landing.java:47-57), so from
     * there the friend's back fills the middle of the frame. This spot is the far corner of the
     * ghast's 4x4 top (EntityTypes.java:499-506), the furthest the camera can stand and still be
     * on the ghast, and the ghast is turned to face it.
     */
    private static final Vec3 GHAST_VIEW =
            new Vec3(GHAST_POSITION.x - 2.0D, GHAST_POSITION.y + 4.0D, GHAST_POSITION.z - 2.0D);

    /** The yaw that turns the ghast, and so its rider, to face {@link #GHAST_VIEW}. */
    private static final float GHAST_VIEW_YAW = yawTowards(GHAST_POSITION, GHAST_VIEW);

    /**
     * Where the ghast waits between its stage and the rest: under the stage floor, which no camera
     * can see through. West of the cameras was not enough: the stages that look up at a target in
     * the air put it in the top of the frame. It stays within the 64 blocks {@link #filmGhast}
     * searches, which is why it is not parked at the bottom of the world.
     */
    private static final Vec3 GHAST_PARKED = new Vec3(-20.0D, 20.0D, 0.5D);

    /**
     * Where the short drop's target hovers: two blocks above the gold mark, over open air, so the
     * arrival spot has a real two-block drop under it and the mark shows the floor it drops to. The
     * target is held without gravity, so the drop stays under the arrival for the whole stage.
     */
    private static final Vec3 SHORT_DROP_POSITION = new Vec3(2.5D, STAGE_Y + 2, 0.5D);

    private static final Vec3 MAGMA_POSITION = new Vec3(2.5D, STAGE_Y, 4.5D);

    /** A target in the air six blocks up: a drop that hurts, but a short one. */
    private static final Vec3 LOW_AIR_POSITION = new Vec3(2.5D, STAGE_Y + 6, 0.5D);

    /**
     * Where the long drop's camera stands: far enough back that the floor under the target is in
     * shot, so the six-block drop reads. From the usual camera the target is close and high, and
     * the shot is all sky. It stands south of the water pool, which fills the floor at x -10 to -7.
     */
    private static final Vec3 LONG_DROP_VIEW = new Vec3(-2.5D, STAGE_Y, 8.5D);

    /**
     * A target in the air over a shaft cut through the stage floor to the bottom of the world, so
     * the arrival spot has nothing under it at all: the one refusal that is not a drop.
     */
    private static final Vec3 VOID_POSITION = new Vec3(2.5D, 78.0D, -16.5D);

    /** A powder snow block the target stands in: a dangerous block that hurts nobody at once. */
    private static final Vec3 SNOW_POSITION = new Vec3(2.5D, STAGE_Y, -12.5D);

    /**
     * Where the requester is when the request is sent: sixteen blocks up over a pool, already
     * falling, so the fall they carry is past the safe distance and the pool catches them.
     */
    private static final Vec3 FALLING_POSITION = new Vec3(-8.5D, STAGE_Y + 16, 0.5D);

    private SimpleTPAFilm() {}

    /**
     * {@code afterClickedAccept} runs on the server once the client has clicked [ACCEPT] in chat,
     * with the friend and the client's player.
     */
    public static void run(
            ClientGameTestContext context,
            BiConsumer<ServerPlayer, ServerPlayer> afterClickedAccept) {
        String playerName = context.computeOnClient(client -> client.getUser().getName());
        // Command suggestions would cover the chat while a command sits in the chat box. Clouds are
        // off so none crosses the sky in the stages that look up at a target in the air. The sun
        // still shows as a white square clipped by the top edge in the long-drop and short-drop
        // stages, which look up at noon; it is the sun, not a cloud.
        context.runOnClient(
                client -> {
                    client.options.autoSuggestions().set(false);
                    client.options.cloudStatus().set(CloudStatus.OFF);
                });
        ServerTickEvents.END_SERVER_TICK.register(
                minecraftServer -> {
                    Consumer<MinecraftServer> stepBack = pendingStepBack;
                    // Within a block, not exactly on the spot: the game pushes a player who lands
                    // inside the friend's box apart in the same tick, so an exact match can miss
                    // the arrival tick and leave the camera in the friend's head for a frame.
                    if (stepBack != null
                            && friend(minecraftServer)
                                            .position()
                                            .distanceToSqr(
                                                    player(minecraftServer, playerName).position())
                                    < 1.0D) {
                        pendingStepBack = null;
                        stepBack.accept(minecraftServer);
                    }
                });
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
                        // The client's player runs each stage's own setup commands, so they need
                        // the permissions a gamerule command asks for.
                        server.runOnServer(
                                minecraftServer -> {
                                    ServerPlayer player = player(minecraftServer, playerName);
                                    minecraftServer
                                            .getPlayerList()
                                            .op(
                                                    new NameAndId(
                                                            player.getUUID(),
                                                            player.getScoreboardName()));
                                });
                        showDeniedRequest(context, server, playerName);
                        showAcceptedRequest(context, server, playerName);
                        showClickedDeny(context, server, playerName);
                        showClickedAccept(context, server, playerName, afterClickedAccept);
                        showWaitingRequest(context, server, playerName);
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

    private static void showDeniedRequest(
            ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
        endStage(context);
        reset(context, server, playerName);
        startStage(context, DENY);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(25);
        server.runOnServer(
                minecraftServer ->
                        SimpleTPAFakePlayerScenario.executeCommand(
                                friend(minecraftServer), "tpdeny"));
        context.waitTicks(16);
    }

    private static void showAcceptedRequest(
            ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
        endStage(context);
        reset(context, server, playerName);
        startStage(context, ACCEPT);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(25);
        server.runOnServer(
                minecraftServer -> {
                    // The arrival takes the target's facing, so the friend turns to the open stage
                    // and the camera does not land nose to nose with the stage's gate.
                    placeTarget(friend(minecraftServer), FRIEND_POSITION, EAST, 0.0F);
                    // The camera lands inside the friend's head for the tick the arrival is
                    // rendered, before the step back reaches the client, so the friend is out of
                    // sight for that tick.
                    friend(minecraftServer).setInvisible(true);
                    SimpleTPAFakePlayerScenario.executeCommand(friend(minecraftServer), "tpaccept");
                    // The same tick as the arrival: a later one leaves the camera inside the
                    // friend's head for the frames in between.
                    stepAside(player(minecraftServer, playerName), friend(minecraftServer));
                });
        showFriendAfterSlide(context, server);
        context.waitTicks(13);
    }

    private static void showClickedDeny(
            ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
        endStage(context);
        reset(context, server, playerName);
        startStage(context, CLICK_DENY);
        server.runOnServer(
                minecraftServer ->
                        SimpleTPAFakePlayerScenario.executeCommand(
                                friend(minecraftServer), "tpa " + playerName));
        context.waitTicks(25);
        clickChatButton(context, server, "tpdeny");
        closeChat(context);
        context.waitTicks(16);
    }

    private static void showClickedAccept(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            String playerName,
            BiConsumer<ServerPlayer, ServerPlayer> afterClickedAccept) {
        endStage(context);
        reset(context, server, playerName);
        startStage(context, CLICK_ACCEPT);
        server.runOnServer(
                minecraftServer ->
                        SimpleTPAFakePlayerScenario.executeCommand(
                                friend(minecraftServer), "tpa " + playerName));
        context.waitTicks(25);
        // The click's command is handled during the server tick, before its end-of-tick event, so
        // the step back runs in the same tick as the arrival. The friend is hidden for that tick
        // as the accept stage hides them: the client interpolates the slide over the next few
        // ticks, so a visible friend fills the frame with their head on the arrival frame.
        pendingStepBack =
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer target = friend(minecraftServer);
                    afterClickedAccept.accept(target, player);
                    target.setInvisible(true);
                    stepAside(player, target);
                };
        clickChatButton(context, server, "tpaccept");
        context.waitTicks(2);
        showFriendAfterSlide(context, server);
        context.waitTicks(13);
        closeChat(context);
        context.waitTicks(16);
    }

    /** The step back to run at the end of the server tick that handles the click, if any. */
    private static Consumer<MinecraftServer> pendingStepBack;

    /**
     * Waits until the server has the client's player falling far enough to hurt, so the request is
     * sent with a real fall behind it. The server learns a client player's fall from their movement
     * packets, so the fall is read there rather than assumed from the tick count.
     */
    private static void waitForFall(
            ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
        for (int tick = 0; tick < 40; tick++) {
            double fallen =
                    server.computeOnServer(
                            minecraftServer ->
                                    (double) player(minecraftServer, playerName).fallDistance);
            if (fallen > 6.0D) {
                return;
            }
            context.waitTicks(1);
        }
        throw new AssertionError("The player never fell far enough to carry a hurtful fall.");
    }

    /** Closes the chat screen, so the next stage starts on the world. */
    private static void closeChat(ClientGameTestContext context) {
        if (context.computeOnClient(client -> client.gui.screen() instanceof ChatScreen)) {
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        }
    }

    /**
     * Opens the chat and clicks the button that runs {@code command}, the way a player does. The
     * button's place is found once, before the cursor moves, so no frame is spent searching.
     */
    private static void clickChatButton(
            ClientGameTestContext context, TestDedicatedServerContext server, String command) {
        String full =
                "/"
                        + command
                        + " "
                        + server.computeOnServer(
                                minecraftServer -> friend(minecraftServer).getUUID());
        context.setScreen(() -> new ChatScreen("", false));
        context.waitTicks(10);
        double[] button = context.computeOnClient(client -> findRunCommand(client, full));
        context.getInput().setCursorPos(button[0], button[1]);
        context.waitTicks(20);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
    }

    private static void showWaitingRequest(
            ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
        endStage(context);
        reset(context, server, playerName);
        startStage(context, WAITING);
        // The request lands a moment into the stage, so the caption is up before the chat line and
        // the stage is not one long still.
        context.waitTicks(12);
        server.runOnServer(
                minecraftServer ->
                        SimpleTPAFakePlayerScenario.executeCommand(
                                friend(minecraftServer), "tpa " + playerName));
        context.waitTicks(16);
    }

    /**
     * The middle of the chat text that runs {@code command}, in screen pixels. One pass over the
     * chat's glyphs, not a hit test per pixel: the per-pixel scan this replaces called {@code
     * captureClickableText} 115,200 times and froze the client for 1.43 s on camera.
     */
    private static double[] findRunCommand(Minecraft client, String command) {
        Window window = client.getWindow();
        int guiHeight = window.getGuiScaledHeight();
        CommandGlyphFinder finder = new CommandGlyphFinder(client, command);
        client.gui
                .hud
                .getChat()
                .captureClickableText(
                        finder,
                        guiHeight,
                        client.gui.hud.getGuiTicks(),
                        ChatComponent.DisplayMode.FOREGROUND);
        if (finder.hits == 0) {
            throw new AssertionError("No chat text runs " + command);
        }
        double scaleX = (double) window.getScreenWidth() / window.getGuiScaledWidth();
        double scaleY = (double) window.getScreenHeight() / guiHeight;
        return new double[] {
            (finder.sumX / finder.hits + 0.5D) * scaleX, (finder.sumY / finder.hits + 0.5D) * scaleY
        };
    }

    /** Collects the glyphs of the chat text whose click event runs one command. */
    private static final class CommandGlyphFinder implements ActiveTextCollector {
        private final Minecraft client;
        private final String command;
        private Parameters parameters = new Parameters(new Matrix3x2f());
        private double sumX;
        private double sumY;
        private int hits;

        private CommandGlyphFinder(Minecraft client, String command) {
            this.client = client;
            this.command = command;
        }

        @Override
        public Parameters defaultParameters() {
            return parameters;
        }

        @Override
        public void defaultParameters(Parameters newParameters) {
            parameters = newParameters;
        }

        @Override
        public void accept(
                TextAlignment alignment,
                int anchorX,
                int y,
                Parameters textParameters,
                FormattedCharSequence text) {
            int leftX = alignment.calculateLeft(anchorX, client.font, text);
            GuiTextRenderState state =
                    new GuiTextRenderState(
                            client.font,
                            text,
                            textParameters.pose(),
                            leftX,
                            y,
                            ARGB.white(textParameters.opacity()),
                            0,
                            true,
                            true,
                            textParameters.scissor());
            state.ensurePrepared()
                    .visit(
                            new Font.GlyphVisitor() {
                                @Override
                                public void acceptGlyph(TextRenderable.Styled glyph) {
                                    Style style = glyph.style();
                                    if (style.getClickEvent()
                                                    instanceof ClickEvent.RunCommand(String run)
                                            && run.equals(command)) {
                                        Vector2f middle =
                                                textParameters
                                                        .pose()
                                                        .transformPosition(
                                                                new Vector2f(
                                                                        (glyph.activeLeft()
                                                                                        + glyph
                                                                                                .activeRight())
                                                                                / 2.0F,
                                                                        (glyph.activeTop()
                                                                                        + glyph
                                                                                                .activeBottom())
                                                                                / 2.0F));
                                        sumX += middle.x;
                                        sumY += middle.y;
                                        hits++;
                                    }
                                }
                            });
        }

        @Override
        public void acceptScrolling(
                Component message,
                int centerX,
                int left,
                int right,
                int top,
                int bottom,
                Parameters textParameters) {
            defaultScrollingHelper(
                    message,
                    centerX,
                    left,
                    right,
                    top,
                    bottom,
                    client.font.width(message),
                    9,
                    textParameters);
        }
    }

    private static void showAutoAcceptedRequest(
            ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
        endStage(context);
        reset(context, server, playerName);
        startStage(context, AUTO_ACCEPT);
        typeCommand(context, "tpautoaccept add " + FRIEND_NAME);
        context.waitTicks(25);
        server.runOnServer(
                minecraftServer -> {
                    friend(minecraftServer).setInvisible(true);
                    SimpleTPAFakePlayerScenario.executeCommand(
                            friend(minecraftServer), "tpa " + playerName);
                    // The whitelist answers the request inside the command, so the arrival has
                    // already landed and the friend steps clear in the same tick.
                    stepBackFrom(player(minecraftServer, playerName), friend(minecraftServer));
                });
        context.waitTicks(16);
    }

    /**
     * The landing checks, one scenario at a time, each a refusal and then the same request going
     * through. The friend's whitelist entry from the auto-accept scenario answers each request at
     * once, so the reason a request waits is the message the player sees.
     */
    private static void showLandingChecks(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            String playerName,
            GameTestPlayer friend) {
        endStage(context);
        reset(context, server, playerName);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    // The friend approves the client's requests, so each one is answered at once.
                    InstantTpaWhitelist.add(friend(minecraftServer), player);
                    voidShaft(player.level(), true);
                    placeTarget(friend(minecraftServer), VOID_POSITION, WEST, 0.0F);
                    look(player, LOOKOUT_POSITION, VOID_POSITION);
                });
        startStage(context, NOTHING_TO_LAND_ON);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(16);

        endStage(context);
        reset(context, server, playerName);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer target = friend(minecraftServer);
                    placeTarget(target, HIGH_POSITION, WEST, 45.0F);
                    // Hidden before the caption, so the cut never shows the friend at the spot the
                    // last stage left them in.
                    target.setInvisible(true);
                    look(player, LOOKOUT_POSITION, HIGH_POSITION);
                    setupCommand(player, "tpasafety off");
                });
        startStage(context, LANDING_CHECK_OFF);
        // The arrival is on the friend's head, so the player steps back in the tick it lands and
        // falls from there, with the friend in shot.
        pendingStepBack =
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    stepBackFrom(player, friend(minecraftServer));
                };
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(45);

        endStage(context);
        reset(context, server, playerName);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer target = friend(minecraftServer);
                    // A target in the air with nothing to stand on: the same refusal as above, for
                    // a target who is flying rather than one above a drop.
                    target.getAbilities().flying = true;
                    target.onUpdateAbilities();
                    placeTarget(target, HIGH_POSITION, WEST, 0.0F);
                    look(player, LOOKOUT_POSITION, HIGH_POSITION);
                });
        startStage(context, FLYING_TARGET);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(16);

        endStage(context);
        reset(context, server, playerName);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer rider = friend(minecraftServer);
                    rider.getAbilities().flying = false;
                    rider.onUpdateAbilities();
                    HappyGhast ghast = filmGhast(minecraftServer, rider);
                    ghast.setPos(GHAST_POSITION.x, GHAST_POSITION.y, GHAST_POSITION.z);
                    // The rider's body follows the ghast's body rotation
                    // (LivingEntityRenderer.java:305-315), so the ghast is turned to face the
                    // camera the stage steps back to; turning the rider alone left their back in
                    // shot.
                    ghast.setYRot(GHAST_VIEW_YAW);
                    ghast.setYHeadRot(GHAST_VIEW_YAW);
                    rider.startRiding(ghast, true, false);
                    rider.setYRot(WEST);
                    rider.setYHeadRot(WEST);
                    rider.setXRot(30.0F);
                    look(player, LOOKOUT_POSITION, rider.position());
                });
        startStage(context, HAPPY_GHAST);
        typeCommandWithoutEnter(context, "tpa " + FRIEND_NAME);
        // The arrival lands on the friend's head, and the friend rides the ghast, so the game does
        // not push the player off them: from the arrival spot the friend's back fills the middle
        // of the frame. The step back goes out in the same server block as the request, so both
        // teleports reach the client in one flush and no frame is drawn from inside the head.
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    player.level()
                            .getServer()
                            .getCommands()
                            .performPrefixedCommand(
                                    player.createCommandSourceStack(), "tpa " + FRIEND_NAME);
                    // Aimed at the friend's feet, not their head: from 2.8 blocks away their head
                    // fills the middle of the frame, and the pitch down puts the ghast's deck in
                    // the shot the caption is about.
                    look(player, GHAST_VIEW, friend(minecraftServer).position());
                });
        closeChat(context);
        context.waitTicks(16);
        assertOnGhast(context, server, friend);
        context.waitTicks(12);

        endStage(context);
        reset(context, server, playerName);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer target = friend(minecraftServer);
                    // Without gravity the target stays six blocks up while the warning is read, so
                    // the repeat is still answered over the drop rather than over the floor they
                    // would have fallen to.
                    target.setNoGravity(true);
                    placeTarget(target, LOW_AIR_POSITION, WEST, 0.0F);
                    look(player, LONG_DROP_VIEW, LOW_AIR_POSITION);
                });
        startStage(context, LONG_DROP);
        typeCommand(context, "tpa " + FRIEND_NAME);
        // The warning and the run-again line stay up long enough to read before the repeat. The
        // repeat is typed whole, as everywhere else, before the first command's typed line
        // expires: the overlay keeps a typed line for two seconds after its last character
        // (InputOverlayState.java:24), so the panel never blanks while the warning is read.
        context.waitTicks(20);
        context.runOnClient(client -> InputOverlay.clear());
        typeCommandWithoutEnter(context, "tpa " + FRIEND_NAME);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer target = friend(minecraftServer);
                    target.setInvisible(true);
                    player.level()
                            .getServer()
                            .getCommands()
                            .performPrefixedCommand(
                                    player.createCommandSourceStack(), "tpa " + FRIEND_NAME);
                    stepBackFrom(player, target);
                });
        closeChat(context);
        // The friend is shown again once the step back has played out, so the stage ends on them
        // rather than on empty sky.
        showFriendAfterSlide(context, server);
        // The arrival is six blocks up, so the player falls to the floor, and the stage ends there.
        // The camera is not turned back up at the friend: that turn is a cut, and it landed 0.57 s
        // before the caption changed, so the last half second of the stage showed a new shot under
        // the old caption. The fall itself is the shot the caption describes.
        context.waitTicks(30);

        endStage(context);
        reset(context, server, playerName);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer target = friend(minecraftServer);
                    // The target hovers two blocks up over open air, so the arrival spot has a
                    // real two-block drop under it. Without gravity the target stays there for
                    // the whole stage. It stays visible, and the gold mark stays under it: an
                    // invisible target, or one with no mark below, left the stage showing a
                    // player against empty sky with nothing to read the drop against.
                    target.setNoGravity(true);
                    placeTarget(target, SHORT_DROP_POSITION, WEST, 20.0F);
                    friendMark(player.level(), true);
                    look(player, PLAYER_POSITION, SHORT_DROP_POSITION);
                });
        startStage(context, SHORT_DROP);
        typeCommandWithoutEnter(context, "tpa " + FRIEND_NAME);
        // The arrival lands inside the friend's head, and the friend has to stay visible here, so
        // the step back goes out in the same server block as the request: both teleports reach the
        // client in one flush and no frame is drawn from inside the friend's head.
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer target = friend(minecraftServer);
                    player.level()
                            .getServer()
                            .getCommands()
                            .performPrefixedCommand(
                                    player.createCommandSourceStack(), "tpa " + FRIEND_NAME);
                    stepBackFrom(player, target);
                });
        closeChat(context);
        context.waitTicks(16);

        endStage(context);
        reset(context, server, playerName);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    magmaBlock(player.level(), true);
                    placeTarget(friend(minecraftServer), MAGMA_POSITION, WEST, 0.0F);
                    look(player, PLAYER_POSITION, MAGMA_POSITION);
                });
        startStage(context, ON_MAGMA);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(16);

        endStage(context);
        reset(context, server, playerName);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    // The same target on the same block as the stage before, in shot: only the
                    // requester's game mode differs, so the pair shows what the mode changes.
                    magmaBlock(player.level(), true);
                    placeTarget(friend(minecraftServer), MAGMA_POSITION, WEST, 0.0F);
                    player.setGameMode(GameType.CREATIVE);
                    look(player, PLAYER_POSITION, MAGMA_POSITION);
                });
        startStage(context, CREATIVE);
        typeCommandWithoutEnter(context, "tpa " + FRIEND_NAME);
        // The arrival lands inside the friend's head. A step back from the end-of-tick event goes
        // out in a later flush than the arrival, so the client draws the arrival first. Hiding the
        // friend and stepping back in the same server block as the request puts both teleports in
        // one flush, as the accept stage does, so no frame is drawn from inside the friend's head.
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer target = friend(minecraftServer);
                    target.setInvisible(true);
                    player.level()
                            .getServer()
                            .getCommands()
                            .performPrefixedCommand(
                                    player.createCommandSourceStack(), "tpa " + FRIEND_NAME);
                    stepBackFrom(player, target);
                });
        closeChat(context);
        context.waitTicks(16);

        endStage(context);
        reset(context, server, playerName);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    placeTarget(friend(minecraftServer), SNOW_POSITION, WEST, 0.0F);
                    look(player, PLAYER_POSITION, SNOW_POSITION);
                });
        startStage(context, ON_POWDER_SNOW);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(16);

        endStage(context);
        reset(context, server, playerName);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer target = friend(minecraftServer);
                    placeTarget(target, SNOW_POSITION, WEST, 0.0F);
                    target.setInvisible(true);
                    setupCommand(player, "gamerule simpletpa:landing_checks false");
                    look(player, PLAYER_POSITION, SNOW_POSITION);
                });
        startStage(context, LANDING_CHECKS_OFF);
        typeCommand(context, "tpa " + FRIEND_NAME);
        context.waitTicks(1);
        server.runOnServer(
                minecraftServer ->
                        stepBackFrom(player(minecraftServer, playerName), friend(minecraftServer)));
        context.waitTicks(16);

        endStage(context);
        reset(context, server, playerName);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    friend(minecraftServer).setInvisible(true);
                    setupCommand(player, "gamerule simpletpa:reset_fall false");
                    // The carried fall counts as a long drop, so with safety on the request would
                    // warn instead of arriving. Safety is turned off so the stage shows what
                    // reset_fall changes: the fall the requester carries into the arrival.
                    setupCommand(player, "tpasafety off");
                    look(player, PLAYER_POSITION, FRIEND_POSITION);
                });
        startStage(context, RESET_FALL_OFF);
        typeCommandWithoutEnter(context, "tpa " + FRIEND_NAME);
        server.runOnServer(
                minecraftServer ->
                        look(
                                player(minecraftServer, playerName),
                                FALLING_POSITION,
                                FRIEND_POSITION));
        waitForFall(context, server, playerName);
        // The request is answered inside the command, so the arrival lands in this tick and the
        // step back runs in it too.
        pendingStepBack =
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    stepBackFrom(player, friend(minecraftServer));
                };
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        // The arrival keeps the fall, so the landing hurts: the stage ends on the hearts it took.
        context.waitTicks(20);

        endStage(context);
        reset(context, server, playerName);
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    friend(minecraftServer).setInvisible(true);
                    setupCommand(player, "gamerule simpletpa:reset_fall true");
                    look(player, PLAYER_POSITION, FRIEND_POSITION);
                });
        startStage(context, RESET_FALL_ON);
        typeCommandWithoutEnter(context, "tpa " + FRIEND_NAME);
        server.runOnServer(
                minecraftServer ->
                        look(
                                player(minecraftServer, playerName),
                                FALLING_POSITION,
                                FRIEND_POSITION));
        waitForFall(context, server, playerName);
        pendingStepBack =
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    stepBackFrom(player, friend(minecraftServer));
                };
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitTicks(16);
    }

    /**
     * Puts the scene back the way every stage starts: both players on the floor, full health, the
     * landing checks and the fall reset on, the friend's request list clear and the ghast parked
     * out of shot. Runs between stages, so the reset is never on camera.
     */
    private static void reset(
            ClientGameTestContext context, TestDedicatedServerContext server, String playerName) {
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer, playerName);
                    ServerPlayer target = friend(minecraftServer);
                    clearRequests(target, player);
                    target.stopRiding();
                    target.setShiftKeyDown(false);
                    target.setInvisible(false);
                    target.setNoGravity(false);
                    magmaBlock(target.level(), false);
                    voidShaft(target.level(), false);
                    target.getAbilities().flying = false;
                    target.onUpdateAbilities();
                    filmGhast(minecraftServer, target)
                            .setPos(GHAST_PARKED.x, GHAST_PARKED.y, GHAST_PARKED.z);
                    player.setGameMode(GameType.SURVIVAL);
                    player.setHealth(20.0F);
                    player.getFoodData().setFoodLevel(20);
                    player.resetFallDistance();
                    // A reset to the default, not a stage's own setup, so it stays out of the
                    // chat: the command answers the player directly, and its line would open
                    // every stage. reset_fall is left to the two fall stages, which set it
                    // visibly: a silent reset to true would make the second one print "already
                    // set to true" instead of showing the change it demonstrates.
                    SafetyPreference.setRefusesDangerousDrop(player, true);
                    gamerule(player, "gamerule simpletpa:landing_checks true");
                    placeActors(player, target);
                });
        // The chat is cleared between stages: a button from an earlier stage would otherwise sit
        // beside this stage's, and a click would land between the two. The input overlay's typed
        // line is cleared with it, so a stage does not open with the last stage's command in it.
        context.runOnClient(
                client -> {
                    client.gui.hud.getChat().clearMessages(false);
                    InputOverlay.clear();
                });
        context.waitTicks(6);
    }

    /**
     * Runs {@code command} as the server, without printing its answer in the chat. A gamerule
     * command needs permissions a survival player does not have, so the source is the server's.
     */
    private static void gamerule(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands()
                .performPrefixedCommand(
                        server.createCommandSourceStack()
                                .withLevel(player.level())
                                .withPosition(player.position())
                                .withPermission(PermissionSet.ALL_PERMISSIONS)
                                .withSuppressedOutput(),
                        command);
    }

    /**
     * Runs {@code command} as {@code player} and shows its answer in the chat, the way a player
     * typing it would see it. A stage's own setup command goes through here, so the film shows what
     * the stage relies on.
     */
    private static void setupCommand(ServerPlayer player, String command) {
        player.level()
                .getServer()
                .getCommands()
                .performPrefixedCommand(player.createCommandSourceStack(), command);
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
     * Moves the player who has just arrived on the friend three blocks back, looking at them, so
     * the shot shows both of them instead of the inside of the friend's head. The player moves
     * rather than the friend: the client applies a player's own teleport at once, while another
     * player's move is interpolated over three ticks, which is the face filling the screen.
     */
    private static void stepBackFrom(ServerPlayer player, ServerPlayer friend) {
        friend.setInvisible(false);
        Vec3 facing =
                new Vec3(
                        -Math.sin(Math.toRadians(friend.getYRot())),
                        0.0D,
                        Math.cos(Math.toRadians(friend.getYRot())));
        look(
                player,
                friend.position().add(facing.scale(4.0D)),
                friend.position().add(0.0D, 1.4D, 0.0D));
    }

    /**
     * Moves the friend four blocks along their own facing, away from the player who has just
     * arrived on them, and turns the player to follow. The player stays where the arrival put them:
     * a player's own teleport is applied by the client at once and reads as a jump, while another
     * player's move is interpolated over three ticks, so the friend slides out of the shot instead.
     * The friend stays hidden until the slide has played out, so no frame shows them at the camera.
     */
    private static void stepAside(ServerPlayer player, ServerPlayer friend) {
        Vec3 facing =
                new Vec3(
                        -Math.sin(Math.toRadians(friend.getYRot())),
                        0.0D,
                        Math.cos(Math.toRadians(friend.getYRot())));
        Vec3 away = friend.position().add(facing.scale(4.0D));
        SimpleTPAFakePlayerScenario.place(friend, away, friend.getYRot(), friend.getXRot());
        look(player, player.position(), away.add(0.0D, 1.4D, 0.0D));
    }

    /** Shows the friend again once their slide has played out, three ticks after the arrival. */
    private static void showFriendAfterSlide(
            ClientGameTestContext context, TestDedicatedServerContext server) {
        context.waitTicks(3);
        server.runOnServer(minecraftServer -> friend(minecraftServer).setInvisible(false));
    }

    /** Places {@code player} at {@code position} looking at {@code target}. */
    private static void look(ServerPlayer player, Vec3 position, Vec3 target) {
        Vec3 towards = target.subtract(position.add(0.0D, 1.62D, 0.0D));
        float yaw = (float) Math.toDegrees(Math.atan2(towards.z, towards.x)) - 90.0F;
        float reach = (float) Math.sqrt(towards.x * towards.x + towards.z * towards.z);
        float pitch = (float) -Math.toDegrees(Math.atan2(towards.y, reach));
        SimpleTPAFakePlayerScenario.place(player, position, yaw, pitch);
    }

    /** The yaw that faces {@code from} towards {@code to}, as {@link #look} computes it. */
    private static float yawTowards(Vec3 from, Vec3 to) {
        Vec3 towards = to.subtract(from);
        return (float) Math.toDegrees(Math.atan2(towards.z, towards.x)) - 90.0F;
    }

    /** Forgets the client's pending request, so the next scenario starts from a clean slate. */
    private static void clearRequests(ServerPlayer friend, ServerPlayer player) {
        TPRequestCommand.TP_REQUESTS.removeIf(
                request ->
                        request.matches(friend.getUUID(), player.getUUID())
                                || request.matches(player.getUUID(), friend.getUUID()));
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
     * Types the command into the chat box and sends it. Typed one character at a time, the partial
     * command shows a parse error above it, so the command is entered whole.
     */
    private static void typeCommand(ClientGameTestContext context, String command) {
        typeCommandWithoutEnter(context, command);
        context.getInput().pressKey(InputConstants.KEY_RETURN);
    }

    /** Types the command into the chat box and leaves it there, unsent. */
    private static void typeCommandWithoutEnter(ClientGameTestContext context, String command) {
        context.setScreen(() -> new ChatScreen("", false));
        context.getInput().typeChars("/" + command);
        context.waitTicks(COMMAND_VISIBLE_TICKS);
    }

    /**
     * Starts a filmed stage. The scene is already in place: every stage resets the actors and the
     * camera before this call, so the caption never appears over the stage before it. The caption
     * goes up a few ticks before the marker, because the cut keeps a little picture before it.
     *
     * <p>The scene is given {@value #SCENE_SETTLE_TICKS} ticks first: the client slides a moved
     * entity into place over three ticks, and the cut keeps the frames just before the marker, so
     * without the wait the first frames of a stage show the friend still on the way to their mark.
     */
    private static void startStage(ClientGameTestContext context, Caption caption) {
        context.waitTicks(SCENE_SETTLE_TICKS);
        ClientGameTestRecorder.showStep(
                context, caption.id(), caption.title(), caption.description());
        context.waitTicks(CAPTION_LEAD_TICKS);
        ClientGameTestStages.markStage(caption.id());
        stage = caption.id();
    }

    /**
     * Ends the stage on screen and holds its last frames, so a cut never lands on the next scene.
     */
    private static void endStage(ClientGameTestContext context) {
        if (stage != null) {
            ClientGameTestStages.endStage(context, stage, STAGE_HOLD_TICKS);
            stage = null;
        }
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
        // The powder snow a target stands in and the pool that catches a falling requester stay
        // for the whole film: neither is in shot in a stage it does not belong to. The stone step
        // and the magma block are placed by the stages that use them, so no other stage shows
        // them.
        level.setBlock(
                BlockPos.containing(SNOW_POSITION).below(),
                Blocks.POWDER_SNOW.defaultBlockState(),
                3);
        for (int x = -10; x <= -7; x++) {
            for (int z = -1; z <= 2; z++) {
                level.setBlock(
                        new BlockPos(x, STAGE_Y - 1, z), Blocks.WATER.defaultBlockState(), 3);
            }
        }
    }

    /**
     * Puts the magma block in place for the two stages that stand a target on it, and puts the
     * stage floor back after, so no later stage's shot shows a hole.
     */
    private static void magmaBlock(ServerLevel level, boolean present) {
        level.setBlock(
                BlockPos.containing(MAGMA_POSITION).below(),
                present
                        ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                3);
    }

    /**
     * Cuts a one-block shaft from the stage floor to the bottom of the world under the void stage's
     * target, and fills it back in after, so no other stage's shot shows a hole.
     */
    private static void voidShaft(ServerLevel level, boolean open) {
        BlockPos column = BlockPos.containing(VOID_POSITION);
        for (int y = level.getMinY(); y < STAGE_Y; y++) {
            level.setBlock(
                    new BlockPos(column.getX(), y, column.getZ()),
                    open
                            ? Blocks.AIR.defaultBlockState()
                            : Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                    3);
        }
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
        friendMark(friend.level(), true);
        SimpleTPAFakePlayerScenario.place(friend, FRIEND_POSITION, WEST, 0.0F);
    }

    /**
     * Places the friend, and keeps the gold block only when they stand on their own mark: the block
     * marks where the friend is, so it does not belong under a stage that moves them.
     */
    private static void placeTarget(ServerPlayer target, Vec3 position, float yaw, float pitch) {
        friendMark(target.level(), position.equals(FRIEND_POSITION));
        SimpleTPAFakePlayerScenario.place(target, position, yaw, pitch);
    }

    /**
     * The gold block under the friend's mark, which shows where the friend stands. Without it the
     * floor block goes back, not air: the mark sits in the stage floor, so air leaves a hole that
     * every stage looking that way shows.
     */
    private static void friendMark(ServerLevel level, boolean present) {
        level.setBlock(
                BlockPos.containing(FRIEND_POSITION).below(),
                present
                        ? Blocks.GOLD_BLOCK.defaultBlockState()
                        : Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                3);
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
