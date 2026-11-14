package io.github.brainage04.simpletpa.command;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Where a requester arrives, and whether the drop below that spot would hurt them. */
public final class Landing {
    /** Far past any safe fall distance, so the search for a floor stops well inside it. */
    private static final double MAX_SCAN = 128.0;

    private Landing() {}

    /**
     * Where {@code from} arrives when teleported to {@code to}: {@code to}'s position, or the
     * middle of the top of the entity they ride when they sit inside it. A player only counts as
     * standing on a happy ghast while their feet are at or above its top (HappyGhast.java:576-585),
     * and vanilla puts a player who leaves one there (HappyGhast.java:594-596), so arriving at the
     * rider's seat would drop them out of it. The middle rather than the seat keeps the two players
     * from pushing each other off.
     */
    public static Vec3 arrival(ServerPlayer from, ServerPlayer to) {
        Entity vehicle = to.getVehicle();
        if (vehicle == null) {
            return to.position();
        }
        AABB box = vehicle.getBoundingBox();
        if (!standable(from, vehicle) || to.getY() >= box.maxY) {
            return to.position();
        }
        return new Vec3((box.minX + box.maxX) / 2.0, box.maxY, (box.minZ + box.maxZ) / 2.0);
    }

    /**
     * Whether {@code entity}'s top holds a player standing on it. The game's own answer is {@code
     * canBeCollidedWith} (Entity.java:2425-2431), which decides entity collision for a falling
     * player (EntityGetter.java:54-71). A ridden happy ghast answers it false on the server and
     * true on the client, and the client is the side that decides a player's fall (Player.java:
     * 1265-1267), so it counts here too.
     */
    private static boolean standable(ServerPlayer from, Entity entity) {
        return entity.canBeCollidedWith(from)
                || entity instanceof HappyGhast ghast && !ghast.isBaby() && ghast.isAlive();
    }

    /**
     * Whether the drop below {@code arrival} would hurt {@code from}, judged by the game's fall
     * damage rule (LivingEntity.java:1851-1858) for their own attributes. The fall stops at the
     * first block or standable entity below. Water or a block the game tags as resetting fall
     * damage on the way down resets the fall (Entity.java:768-775, 1684-1689), and so does powder
     * snow, which holds a player who sinks into it stuck (Entity.java:3030-3032). A fall that goes
     * on below such a block counts again from that block's bottom, with the same rule. The landing
     * block's own reduction applies (Block.java:486-489), and powder snow underfoot deals no damage:
     * it holds a falling player on top and its {@code fallOn} hurts nobody
     * (PowderSnowBlock.java:101-107, 116-131). Slow Falling counts as {@link #countedFall} says.
     *
     * <p>A tagged block the player lands on top of does not reset the fall: the game's check casts
     * a ray along the tick's movement (Entity.java:768-775), which ends on that block's top face,
     * and a ray only hits a face strictly before its end (AABB.java:393).
     *
     * <p>{@code level} holds {@code arrival}: a cross-dimension request arrives in the target's
     * level, not the requester's, and reading the requester's level would judge the drop against
     * another world's blocks.
     */
    public static boolean dropHurts(ServerLevel level, ServerPlayer from, AABB arrival) {
        if (from.is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            return false;
        }
        double multiplier = from.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        if (multiplier <= 0.0) {
            return false;
        }
        MobEffectInstance slowFalling = from.getEffect(MobEffects.SLOW_FALLING);
        if (slowFalling != null && slowFalling.isInfiniteDuration()) {
            return false;
        }

        double safeDistance = from.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        // Slow Falling leaves the first part of a fall uncounted, so the floor that decides may be
        // deeper than the safe distance.
        double reach =
                slowFalling != null
                        ? MAX_SCAN
                        : Math.min(MAX_SCAN, Math.ceil(safeDistance + 1.0 / multiplier) + 1.0);
        AABB fallStart = arrival;
        double support;
        while (true) {
            support = support(level, from, fallStart, reach);
            double resetBottom = lowestReset(level, fallStart, support);
            if (Double.isNaN(resetBottom)) {
                break;
            }
            if (resetBottom <= support) {
                return false;
            }
            fallStart = fallStart.move(0.0, resetBottom - fallStart.minY, 0.0);
        }

        Vec3 centre = arrival.getCenter();
        BlockPos landingPos = BlockPos.containing(centre.x, support - 1.0E-4, centre.z);
        BlockState landing = level.getBlockState(landingPos);
        if (landing.getBlock() instanceof PowderSnowBlock) {
            return false;
        }
        if (slowFalling != null && fallStart.minY - support >= reach) {
            // No floor within the scan: how much of the fall counts depends on where it ends.
            return true;
        }
        // Slow Falling and a resetting block each take away the fall above them, so the lower of
        // the two starts what counts.
        double counted =
                Math.min(
                        countedFall(from, slowFalling, arrival.minY - support),
                        fallStart.minY - support);
        double distance = counted * (1.0F - landing.getBlock().getFallDistanceReduction());
        // The game floors the damage after applying the multiplier, not the distance before it
        // (LivingEntity.java:1857).
        return Math.floor((distance + 1.0E-6 - safeDistance) * multiplier) > 0.0;
    }

    /** The top of the first block or standable entity within {@code reach} below {@code box}. */
    private static double support(ServerLevel level, ServerPlayer from, AABB box, double reach) {
        AABB column = box.setMinY(box.minY - reach).setMaxY(box.minY);
        List<VoxelShape> colliders = Entity.collectAllColliders(from, level, column);
        double support = box.minY + Shapes.collide(Direction.Axis.Y, box, colliders, -reach);
        for (Entity entity : level.getEntities(from, column, entity -> standable(from, entity))) {
            double top = entity.getBoundingBox().maxY;
            if (top <= box.minY) {
                support = Math.max(support, top);
            }
        }
        return support;
    }

    /**
     * The bottom of the lowest block between {@code box} and {@code support} that resets a fall
     * through it, or NaN when there is none.
     */
    private static double lowestReset(ServerLevel level, AABB box, double support) {
        Vec3 centre = box.getCenter();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double bottom = Double.NaN;
        for (double y = box.minY - 1.0E-4; y > support; y -= 1.0) {
            pos.set(centre.x, y, centre.z);
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().is(FluidTags.WATER)
                    || state.is(BlockTags.FALL_DAMAGE_RESETTING)
                    || state.getBlock() instanceof PowderSnowBlock) {
                bottom = pos.getY();
            }
        }
        return bottom;
    }

    /**
     * How much of a fall of {@code drop} blocks from rest still counts as fall distance when it
     * lands. Slow Falling resets the fall distance every tick while it lasts
     * (LivingEntity.java:3119-3120) and caps the pull at 0.01 a tick while falling
     * (LivingEntity.java:2407-2410), so only the part of the fall from the effect's last tick on
     * counts. A tick moves the player by their speed, then gravity is added and the vertical air
     * drag applied (LivingEntity.java:2450-2476); a teleport starts the fall at rest. The effect is
     * taken to end a tick early and its last tick to count in full, so the uncertain tick counts
     * against the requester.
     *
     * <p>Network delay is not covered. The server counts the fall from the client's movement
     * packets (ServerGamePacketListenerImpl.java:1216), and the client keeps the effect until the
     * server's removal reaches it (LivingEntity.java:845-865), so on a slow connection the fall
     * the server counts after the effect ends can be up to about 0.49 blocks longer per tick of
     * round trip. The README states this limit.
     */
    private static double countedFall(
            ServerPlayer from, MobEffectInstance slowFalling, double drop) {
        double gravity = from.getAttributeValue(Attributes.GRAVITY);
        if (slowFalling == null || gravity <= 0.0) {
            return drop;
        }
        // LivingEntity.computeModifiedFriction (LivingEntity.java:518-520), private there.
        float drag =
                Mth.clamp(
                        1.0F
                                - (1.0F - LivingEntity.BASE_VERTICAL_AIR_DRAG)
                                        * (float)
                                                from.getAttributeValue(
                                                        Attributes.AIR_DRAG_MODIFIER),
                        0.0F,
                        1.0F);
        int slowTicks = slowFalling.getDuration() - 1;
        double fallen = 0.0;
        double speed = 0.0;
        for (int tick = 0; tick < slowTicks; tick++) {
            double step = Math.min(speed, drop - fallen);
            if (tick == slowTicks - 1 || fallen + step >= drop) {
                return drop - fallen;
            }
            fallen += step;
            // The cap from LivingEntity.getEffectiveGravity (LivingEntity.java:2409), which is
            // protected and answers for the motion the player has now, not for this fall.
            speed = (speed + Math.min(gravity, 0.01)) * drag;
        }
        return drop;
    }
}
