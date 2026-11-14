package io.github.brainage04.simpletpa.command;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HayBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Where a requester arrives, and whether the drop below that spot would hurt them. */
public final class Landing {
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
     * on below such a block counts again from about that block's bottom ({@link
     * FallRule#restartAbove}), with the same rule. The landing block is the one the game calls
     * {@code fallOn} on ({@link #landingPos}). Its own reduction applies (Block.java:486-489), and
     * powder snow underfoot deals no damage: it holds a falling player on top and its {@code
     * fallOn} hurts nobody (PowderSnowBlock.java:101-107, 116-131). A drop that leaves the
     * requester held in powder snow they cannot climb out of counts as hurting ({@link
     * #heldInPowderSnow}). Slow Falling counts as {@link FallRule#countedFall} says, but only for a
     * fall that crosses no resetting block.
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
        // Levitation resets the fall every tick it lasts, as Slow Falling does
        // (LivingEntity.java:3119-3120), so an infinite one never lets the requester fall.
        MobEffectInstance levitation = from.getEffect(MobEffects.LEVITATION);
        if (levitation != null && levitation.isInfiniteDuration()) {
            return false;
        }

        FallRule rule =
                new FallRule(
                        from.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
                        multiplier,
                        slowFalling != null
                                ? OptionalInt.of(slowFalling.getDuration())
                                : OptionalInt.empty(),
                        from.getAttributeValue(Attributes.GRAVITY),
                        FallRule.verticalDrag(
                                from.getAttributeValue(Attributes.AIR_DRAG_MODIFIER)));
        double reach = rule.reach();
        CollisionContext collisions = new ArrivalCollisions(from, arrival);
        AABB fallStart = arrival;
        double countFrom = arrival.minY;
        double support;
        boolean landsInReset = false;
        while (true) {
            support = support(level, from, collisions, fallStart, reach);
            BlockPos reset = lowestReset(level, fallStart, support);
            if (reset == null) {
                break;
            }
            if (reset.getY() <= support) {
                landsInReset = true;
                break;
            }
            BlockState state = level.getBlockState(reset);
            boolean climbable = state.is(BlockTags.CLIMBABLE);
            boolean water = state.getFluidState().is(FluidTags.WATER);
            double depth = arrival.minY - reset.getY();
            double restart =
                    reset.getY() + rule.restartAbove(climbable, water, depth, arrival.getYsize());
            countFrom = Math.min(countFrom, restart);
            fallStart = fallStart.move(0.0, reset.getY() - fallStart.minY, 0.0);
        }

        AABB landed = arrival.move(0.0, support - arrival.minY, 0.0);
        if (heldInPowderSnow(level, from, landed)) {
            return true;
        }
        if (landsInReset) {
            return false;
        }
        BlockState landing = level.getBlockState(landingPos(level, collisions, landed));
        if (landing.getBlock() instanceof PowderSnowBlock) {
            return false;
        }
        return rule.hurts(
                arrival.minY - support,
                fallStart.minY - support,
                fallStart == arrival ? Double.NaN : countFrom - support,
                floor(landing));
    }

    /**
     * Whether {@code from}, at rest in {@code landed}, is held in powder snow they cannot climb out
     * of. Powder snow holds a living entity whose feet are in it (PowderSnowBlock.java:59-60, the
     * block at its position, Entity.java:3837-3839) and freezes one that wears no leather
     * (LivingEntity.java:3977-3988, 3140-3148). Only an entity that can walk on powder snow climbs
     * out of it (LivingEntity.java:2665), which for a player means leather boots
     * (PowderSnowBlock.java:139-145).
     */
    private static boolean heldInPowderSnow(ServerLevel level, ServerPlayer from, AABB landed) {
        return !PowderSnowBlock.canEntityWalkOnPowderSnow(from)
                && level.getBlockState(BlockPos.containing(landed.getBottomCenter())).getBlock()
                        instanceof PowderSnowBlock;
    }

    /**
     * The block whose {@code fallOn} the game calls when a player lands in {@code landed}: the main
     * supporting block, the block closest to the player's position among those whose collision
     * reaches a 1e-6 slab under their feet (Entity.java:704-716, CollisionGetter.java:138-152),
     * taken 0.2 below the feet unless it is a fence, wall or fence gate, and without a supporting
     * block the block 0.2 below the position (Entity.java:1093-1103, getOnPosLegacy at 1081-1083).
     * CollisionGetter.findSupportingBlock measures from the player's present position, not from
     * where they will land, so its choice is repeated here.
     */
    private static BlockPos landingPos(
            ServerLevel level, CollisionContext collisionContext, AABB landed) {
        Vec3 position = landed.getBottomCenter();
        AABB underFeet =
                new AABB(
                        landed.minX,
                        landed.minY - 1.0E-6,
                        landed.minZ,
                        landed.maxX,
                        landed.minY,
                        landed.maxZ);
        BlockCollisions<BlockPos> collisions =
                new BlockCollisions<>(
                        level, collisionContext, underFeet, false, (pos, shape) -> pos);
        BlockPos main = null;
        double mainDistance = Double.MAX_VALUE;
        while (collisions.hasNext()) {
            BlockPos pos = collisions.next();
            double distance = pos.distToCenterSqr(position);
            if (distance < mainDistance
                    || distance == mainDistance && (main == null || main.compareTo(pos) < 0)) {
                main = pos.immutable();
                mainDistance = distance;
            }
        }
        if (main == null) {
            return BlockPos.containing(position.x, position.y - 0.2F, position.z);
        }
        BlockState state = level.getBlockState(main);
        if (state.is(BlockTags.FENCES)
                || state.is(BlockTags.WALLS)
                || state.getBlock() instanceof FenceGateBlock) {
            return main;
        }
        return main.atY(Mth.floor(position.y - 0.2F));
    }

    /**
     * How {@code landing} turns a fall into damage ({@link FallRule.Floor}). A stalagmite tip is a
     * pointed dripstone pointing up (PointedDripstoneBlock.java:54-58), and slime deals none
     * whether or not the player sneaks: its {@code fallOn} calls causeFallDamage, with a modifier
     * of 0, only for a player who does not (SlimeBlock.java:15-19).
     */
    private static FallRule.Floor floor(BlockState landing) {
        Block block = landing.getBlock();
        if (block instanceof PointedDripstoneBlock
                && landing.getValue(SpeleothemBlock.TIP_DIRECTION) == Direction.UP
                && landing.getValue(SpeleothemBlock.THICKNESS) == SpeleothemThickness.TIP) {
            return FallRule.Floor.STALAGMITE_TIP;
        }
        if (block instanceof HayBlock || block instanceof HoneyBlock) {
            return FallRule.Floor.FIFTH;
        }
        if (block instanceof SlimeBlock) {
            return FallRule.Floor.NO_DAMAGE;
        }
        return FallRule.Floor.of(block.getFallDistanceReduction());
    }

    /** The top of the first block or standable entity within {@code reach} below {@code box}. */
    private static double support(
            ServerLevel level,
            ServerPlayer from,
            CollisionContext collisionContext,
            AABB box,
            double reach) {
        AABB column = box.setMinY(box.minY - reach).setMaxY(box.minY);
        List<VoxelShape> colliders = new ArrayList<>(level.getEntityCollisions(from, column));
        level.getBlockCollisionsFromContext(collisionContext, column).forEach(colliders::add);
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
     * Block collisions as the requester would meet them falling from the arrival, not from where
     * they are now. Scaffolding holds only a player above it (ScaffoldingBlock.java:130-140), and
     * the game's own context asks the requester's present height (EntityCollisionContext.java:
     * 78-80), so a requester below the target would fall through a scaffolding floor the target
     * stands on. CollisionContext.withPosition would set the height but marks the context as a
     * placement, which empties scaffolding (CollisionContext.java:52-60).
     *
     * <p>Powder snow never holds the falling requester here. Its shape depends on the requester's
     * present fall distance (PowderSnowBlock.java:120-121), not the one they arrive with. A player
     * who has fallen more than 2.5 blocks lands on a 0.9 high top, and once landed they are no
     * longer above the block, so it stops holding them and they sink through (125-131). Only
     * leather boots hold a player who reaches it sooner, and letting that player sink too is the
     * cautious answer: the snow resets the fall ({@link #lowestReset}), the fall below it counts,
     * and a landing in it is judged by {@link #heldInPowderSnow}.
     */
    private static final class ArrivalCollisions extends EntityCollisionContext {
        ArrivalCollisions(ServerPlayer from, AABB arrival) {
            super(from.isDescending(), false, arrival.minY, from.getMainHandItem(), false, from);
        }

        @Override
        public VoxelShape getCollisionShape(
                BlockState state, CollisionGetter collisionGetter, BlockPos pos) {
            return state.getBlock() instanceof PowderSnowBlock
                    ? Shapes.empty()
                    : super.getCollisionShape(state, collisionGetter, pos);
        }
    }
}
