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

    /**
     * The most a climbable lets a player fall in a tick, from LivingEntity.handleOnClimbable
     * (LivingEntity.java:2690-2693), where it is a local value no API exposes.
     */
    private static final double CLIMB_SPEED = 0.15;

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
     * on below such a block counts again from about that block's bottom ({@link #restartAbove}),
     * with the same rule. The landing block's own reduction applies (Block.java:486-489), and
     * powder snow underfoot deals no damage: it holds a falling player on top and its {@code
     * fallOn} hurts nobody (PowderSnowBlock.java:101-107, 116-131). Slow Falling counts as {@link
     * #countedFall} says, but only for a fall that crosses no resetting block.
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
        double countFrom = arrival.minY;
        double support;
        while (true) {
            support = support(level, from, fallStart, reach);
            BlockPos reset = lowestReset(level, fallStart, support);
            if (reset == null) {
                break;
            }
            if (reset.getY() <= support) {
                return false;
            }
            double restart =
                    reset.getY() + restartAbove(level.getBlockState(reset), from, arrival, reset);
            countFrom = Math.min(countFrom, restart);
            fallStart = fallStart.move(0.0, reset.getY() - fallStart.minY, 0.0);
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
        // A resetting block also slows the fall through it: a climbable to 0.15 a tick
        // (LivingEntity.java:2690-2693), a cobweb, berry bush or powder snow to a fraction
        // (WebBlock.java:27, SweetBerryBushBlock.java:79, PowderSnowBlock.java:60), water by its
        // drag (LivingEntity.java:2497-2498). How long it holds the requester decides where Slow
        // Falling runs out, which may be inside it, so below one the effect gets no credit.
        double counted =
                fallStart == arrival
                        ? countedFall(from, slowFalling, arrival.minY - support)
                        : countFrom - support;
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
     * The lowest block between {@code box} and {@code support} that resets a fall through it, or
     * null when there is none.
     */
    private static BlockPos lowestReset(ServerLevel level, AABB box, double support) {
        Vec3 centre = box.getCenter();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos lowest = null;
        for (double y = box.minY - 1.0E-4; y > support; y -= 1.0) {
            pos.set(centre.x, y, centre.z);
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().is(FluidTags.WATER)
                    || state.is(BlockTags.FALL_DAMAGE_RESETTING)
                    || state.getBlock() instanceof PowderSnowBlock) {
                lowest = pos.immutable();
            }
        }
        return lowest;
    }

    /**
     * How far above the bottom of the resetting block at {@code pos} the fall that counts below it
     * may start, for a requester who falls from rest at {@code arrival}.
     *
     * <ul>
     *   <li>A tick that moves the player a block or more resets the fall when its movement crosses
     *       water or a tagged block (Entity.java:766-775), and the whole movement of that tick is
     *       then added (Entity.java:1584-1587, through ServerGamePacketListenerImpl.java:1215). A
     *       tick that passes a thin block whole therefore counts from where it began, up to one
     *       tick's movement above the block's bottom.
     *   <li>A climbable also resets the fall at the start of each tick that begins with the feet in
     *       it (LivingEntity.java:2688-2689) and holds that tick's fall to {@link #CLIMB_SPEED}. A
     *       fall slower than a block a tick cannot skip it, so it counts from at most that far
     *       above the bottom.
     *   <li>Water also resets it after each tick that ends with any part of the player's box in it
     *       (Entity.java:1679-1683, EntityFluidInteraction.java:40-73). The tick after the last
     *       such one, or a tick that passes it whole, ends with the box below the water, so the
     *       fall counts from at most one tick's movement less the box's height above its bottom.
     *   <li>A cobweb, berry bush or powder snow resets it after each tick whose movement touched it
     *       (Entity.java:3030-3032), and the last such tick ends below the block.
     * </ul>
     *
     * <p>The blocks on the way can only slow a fall down, so the tick's movement is taken from a
     * fall through open air from {@code arrival} ({@link #freeFallStep}).
     */
    private static double restartAbove(
            BlockState state, ServerPlayer from, AABB arrival, BlockPos pos) {
        double step = freeFallStep(from, arrival.minY - pos.getY());
        double above = 0.0;
        if (state.is(BlockTags.CLIMBABLE)) {
            above = step >= 1.0 ? step : Math.min(step, CLIMB_SPEED);
        }
        if (state.getFluidState().is(FluidTags.WATER)) {
            double water = Math.max(0.0, step - arrival.getYsize());
            above = state.is(BlockTags.CLIMBABLE) ? Math.min(above, water) : water;
        }
        return above;
    }

    /**
     * The movement of the tick that takes a player falling from rest through open air more than
     * {@code depth} blocks down, by the air model {@link #countedFall} describes.
     */
    private static double freeFallStep(ServerPlayer from, double depth) {
        double gravity = from.getAttributeValue(Attributes.GRAVITY);
        if (gravity <= 0.0) {
            return 0.0;
        }
        float drag = verticalDrag(from);
        double fallen = 0.0;
        double speed = 0.0;
        while (fallen + speed <= depth) {
            fallen += speed;
            speed = (speed + gravity) * drag;
        }
        return speed;
    }

    /** LivingEntity.computeModifiedFriction (LivingEntity.java:518-520), private there. */
    private static float verticalDrag(ServerPlayer from) {
        return Mth.clamp(
                1.0F
                        - (1.0F - LivingEntity.BASE_VERTICAL_AIR_DRAG)
                                * (float) from.getAttributeValue(Attributes.AIR_DRAG_MODIFIER),
                0.0F,
                1.0F);
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
     * server's removal reaches it (LivingEntity.java:845-865), so on a slow connection the fall the
     * server counts after the effect ends can be up to about 0.49 blocks longer per tick of round
     * trip. The README states this limit.
     */
    private static double countedFall(
            ServerPlayer from, MobEffectInstance slowFalling, double drop) {
        double gravity = from.getAttributeValue(Attributes.GRAVITY);
        if (slowFalling == null || gravity <= 0.0) {
            return drop;
        }
        float drag = verticalDrag(from);
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
