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
 * <p>The limit is a quarter of the frame. The largest legitimate frame measured across takes 6 and
 * 7 is 0.131, and the defect is 1.0, so a quarter leaves the real shots room and still fails a
 * friend filling a third of the frame.
 */
final class FriendFrameCheck implements HudElement {
    /** The most of a frame the friend's box may cover. */
    static final double MAX_FRACTION = 0.25D;

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath("simpletpa", "friend_frame_check");
    private static final float NEAR = 0.05F;

    private static FriendFrameCheck active;

    private final String friendName;
    private final Supplier<String> stage;
    private double worst;
    private String worstStage = "none";
    private int measured;

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
    }

    /** Throws when any measured frame showed the friend over {@link #MAX_FRACTION}. */
    void assertWithinLimit() {
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
        if (friend == null || friend.isInvisible()) {
            return;
        }
        measured++;
        double fraction =
                fraction(client, friend, deltaTracker.getGameTimeDeltaPartialTick(true));
        if (fraction > worst) {
            worst = fraction;
            worstStage = stage.get();
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
