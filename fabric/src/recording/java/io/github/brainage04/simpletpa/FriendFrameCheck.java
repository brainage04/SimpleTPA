package io.github.brainage04.simpletpa;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Fails the film when the friend fills more than {@link #MAX_FRACTION} of a frame.
 *
 * <p>The measure is the friend's own box projected into the frame, not a count of skin-coloured
 * pixels: the player's first-person arm is the same brown as the friend's skin and sits in the
 * bottom right of every shot, so a colour count would have to be told which brown is which. The box
 * is the game's own answer for what the friend occupies, interpolated the way the renderer draws
 * them ({@code Entity.getInterpolatedBoundingBox}), and the matrix is the camera's own
 * view-projection matrix, so the fraction is what the frame shows.
 *
 * <p>A frame the friend is hidden in is not measured: an invisible friend fills nothing. A camera
 * inside the friend's box counts as the whole frame, which is the defect this check exists for.
 *
 * <p>The limit is a quarter of the frame. The largest legitimate frame measured is 0.131, in take
 * 6's arrival-frame runs, and the defect is 1.0, so a quarter leaves the real shots room and still
 * fails a friend filling a third of the frame. The delivered film's own worst frame is 0.119, in
 * {@code simpletpa.longdrop}.
 *
 * <p>Take 6's own code does not fail this check: its flash was a race between the arrival and the
 * step back reaching the client in one flush or two, and three runs of that code with the check
 * added passed. The red evidence is take 6's arrival ordering with the step back removed, which
 * draws the arrival frame every run and fails at 1.0.
 *
 * <p>Two more rules, both about a camera move inside a stage. A frame-to-frame camera turn above
 * {@link #MAX_TURN_DEGREES} reads as a cut, so the check fails one: take 7's long-drop crane
 * snapped 55 in one frame. A stage that keeps the friend in shot must not lose them either, so a
 * frame with no part of the friend's box on screen fails when the stage is one of {@link
 * #FRIEND_IN_SHOT_STAGES}.
 */
final class FriendFrameCheck implements HudElement {
    /** The most of a frame the friend's box may cover. */
    static final double MAX_FRACTION = 0.25D;

    /**
     * The most the camera may turn between two frames, in degrees. The rest of the film turns at
     * most 2.9 a frame. The long drop's follow needs more: the camera falls six blocks while the
     * friend stays put, so the angle to them steepens fastest as the player lands. From the eight
     * blocks the step back leaves, the angle changes by at most 4.6 degrees a tick, and the client
     * draws a tick's turn across two frames, so the follow's own worst frame is about 2.3. The
     * limit is 6, which leaves the follow room for the landing's snap and still fails a cut: take
     * 7's crane snapped 55 in one frame.
     */
    static final double MAX_TURN_DEGREES = 6.0D;

    /**
     * How far the camera may move in one frame before the frame counts as a teleport rather than a
     * camera move. The crane's fastest step is 0.45 blocks; an arrival moves six or more.
     */
    private static final double TELEPORT_BLOCKS = 1.0D;

    /** The stages whose shot is the friend, so a frame without them on screen is a defect. */
    private static final java.util.Set<String> FRIEND_IN_SHOT_STAGES =
            java.util.Set.of("simpletpa.longdrop");

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(SimpleTPA.MOD_ID, "friend_frame_check");
    // A corner nearer than the near plane Camera.setupPerspective uses (0.05F, Camera.java:106),
    // which Camera.PROJECTION_Z_NEAR declares (Camera.java:40), has no screen position.
    private static final float NEAR = Camera.PROJECTION_Z_NEAR;

    private static FriendFrameCheck active;

    private final String friendName;
    private final Supplier<String> stage;
    private double worst;
    private String worstStage = "none";
    private int measured;
    private double worstTurn;
    private String worstTurnStage = "none";
    private int worstTurnFrame = -1;
    private int frame;
    private String lastStage;
    private float lastYaw;
    private float lastPitch;
    private Vec3 lastEye;
    private int missing;
    private String missingStage = "none";

    private FriendFrameCheck(String friendName, Supplier<String> stage) {
        this.friendName = friendName;
        this.stage = stage;
    }

    /** Starts measuring every frame drawn from now on. */
    static void start(String friendName, Supplier<String> stage) {
        if (active != null) {
            throw new IllegalStateException("The friend frame check is already running.");
        }
        active = new FriendFrameCheck(friendName, stage);
        HudElementRegistry.addLast(ID, active);
    }

    /** Stops measuring and returns the check, for its verdict. */
    static FriendFrameCheck stop() {
        FriendFrameCheck check = active;
        if (check == null) {
            throw new IllegalStateException("The friend frame check is not running.");
        }
        active = null;
        HudElementRegistry.removeElement(ID);
        return check;
    }

    /** Prints the worst frame and the count, so a passing take still shows what it measured. */
    void report() {
        System.out.println(
                "FRIEND_FRAME_CHECK worst=%.3f stage=%s measured=%d limit=%.2f"
                        .formatted(worst, worstStage, measured, MAX_FRACTION));
        System.out.println(
                "CAMERA_TURN_CHECK worst=%.2f stage=%s frame=%d limit=%.2f"
                        .formatted(worstTurn, worstTurnStage, worstTurnFrame, MAX_TURN_DEGREES));
        System.out.println(
                "FRIEND_IN_SHOT_CHECK missing=%d stage=%s".formatted(missing, missingStage));
    }

    /** Throws when any measured frame showed the friend over {@link #MAX_FRACTION}. */
    void assertWithinLimit() {
        if (measured == 0) {
            throw new AssertionError(
                    "The friend frame check measured no frame, so it checked nothing.");
        }
        if (worst > MAX_FRACTION) {
            throw new AssertionError(
                    "%s filled %.0f%% of a frame in %s, over the %.0f%% limit (%d frames measured)."
                            .formatted(
                                    friendName,
                                    worst * 100.0D,
                                    worstStage,
                                    MAX_FRACTION * 100.0D,
                                    measured));
        }
        if (worstTurn > MAX_TURN_DEGREES) {
            throw new AssertionError(
                    "The camera turned %.1f degrees in one frame in %s, over the %.1f limit."
                            .formatted(worstTurn, worstTurnStage, MAX_TURN_DEGREES));
        }
        if (missing > 0) {
            throw new AssertionError(
                    "%s was off screen for %d frames of %s, which is their shot."
                            .formatted(friendName, missing, missingStage));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        // Only a filmed stage is measured: the frames between stages are not in the film.
        if (client.level == null || client.player == null || stage.get() == null) {
            return;
        }
        AbstractClientPlayer friend = null;
        for (AbstractClientPlayer candidate : client.level.players()) {
            if (candidate.getScoreboardName().equals(friendName)) {
                friend = candidate;
                break;
            }
        }
        String current = stage.get();
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        // The turn is measured on every filmed frame, friend or not: a snap is a snap. Only the
        // frame the camera teleported in is skipped, because the mod's own arrival moves the
        // player and a teleport is not a camera move the film can smooth. The frame after it is
        // measured: a teleport's rotation is applied at once (ClientPacketListener.java:825-837),
        // so a turn that follows one is a turn the film made.
        boolean teleported = lastEye != null && eye.distanceTo(lastEye) > TELEPORT_BLOCKS;
        if (current.equals(lastStage) && !teleported) {
            double dYaw = Math.abs(Mth.wrapDegrees(camera.yRot() - lastYaw));
            double dPitch = Math.abs(camera.xRot() - lastPitch);
            double turn = Math.hypot(dYaw, dPitch);
            if (turn > worstTurn) {
                worstTurn = turn;
                worstTurnStage = current;
                worstTurnFrame = frame;
                System.out.println(
                        "CAMERA_TURN_FRAME frame=%d yaw=%.2f pitch=%.2f at %.2f/%.2f"
                                .formatted(frame, dYaw, dPitch, camera.yRot(), camera.xRot()));
            }
        }
        frame++;
        lastStage = current;
        lastYaw = camera.yRot();
        lastPitch = camera.xRot();
        lastEye = eye;
        // A hidden friend fills nothing, so a frame they are hidden in is not measured. In a stage
        // whose shot is the friend, a hidden friend is a missing one: the frame shows the sky where
        // they should be, which is the defect the in-shot rule exists for.
        if (friend == null || friend.isInvisible()) {
            if (FRIEND_IN_SHOT_STAGES.contains(current)) {
                missing++;
                missingStage = current;
            }
            return;
        }
        measured++;
        double fraction = fraction(client, friend, deltaTracker.getGameTimeDeltaPartialTick(true));
        if (fraction > worst) {
            worst = fraction;
            worstStage = current;
        }
        if (fraction <= 0.0D && FRIEND_IN_SHOT_STAGES.contains(current)) {
            missing++;
            missingStage = current;
        }
    }

    private static double fraction(
            Minecraft client, AbstractClientPlayer friend, float partialTicks) {
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        AABB box = friend.getInterpolatedBoundingBox(partialTicks);
        if (box.contains(eye)) {
            return 1.0D;
        }
        Matrix4f matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
        double minX = 1.0D;
        double maxX = -1.0D;
        double minY = 1.0D;
        double maxY = -1.0D;
        int inFront = 0;
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? box.minX : box.maxX;
            double y = (corner & 2) == 0 ? box.minY : box.maxY;
            double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
            Vector4f point =
                    new Vector4f(
                            (float) (x - eye.x), (float) (y - eye.y), (float) (z - eye.z), 1.0F);
            matrix.transform(point);
            if (point.w <= NEAR) {
                continue;
            }
            inFront++;
            double ndcX = point.x / point.w;
            double ndcY = point.y / point.w;
            minX = Math.min(minX, ndcX);
            maxX = Math.max(maxX, ndcX);
            minY = Math.min(minY, ndcY);
            maxY = Math.max(maxY, ndcY);
        }
        if (inFront == 0) {
            return 0.0D;
        }
        // A corner behind the camera has no screen position, so the box is measured by the corners
        // that do: the camera is inside or beside the friend, and the part in front of it is what
        // the frame shows.
        double width = Math.min(maxX, 1.0D) - Math.max(minX, -1.0D);
        double height = Math.min(maxY, 1.0D) - Math.max(minY, -1.0D);
        if (width <= 0.0D || height <= 0.0D) {
            return 0.0D;
        }
        return width * height / 4.0D;
    }
}
