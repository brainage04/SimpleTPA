package io.github.brainage04.simpletpa.command;

import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
     * on below such a block counts again from that block's bottom, with the same rule. The landing
     * block's own reduction applies (Block.java:486-489), a stalagmite's tip adds to the fall and
     * doubles the damage ({@link #floor}), and powder snow underfoot deals no damage: it holds a
     * falling player on top and its {@code fallOn} hurts nobody (PowderSnowBlock.java:101-107,
     * 116-131). Slow Falling counts as {@link FallRule#countedFall} says.
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
        return rule.hurts(arrival.minY - support, fallStart.minY - support, floor(landing));
    }

    /**
     * How {@code landing} turns a fall into damage. A stalagmite's tip, a pointed dripstone
     * pointing up, replaces Block.fallOn with a fall 2.5 blocks longer and double the damage
     * (PointedDripstoneBlock.java:54-58). Both numbers are private constants there
     * (PointedDripstoneBlock.java:39-40), and nothing in the game answers them for a fall that has
     * not happened, so they are repeated here.
     */
    private static FallRule.Floor floor(BlockState landing) {
        if (landing.getBlock() instanceof PointedDripstoneBlock
                && landing.getValue(SpeleothemBlock.TIP_DIRECTION) == Direction.UP
                && landing.getValue(SpeleothemBlock.THICKNESS) == SpeleothemThickness.TIP) {
            return new FallRule.Floor(0.0F, 2.5, 2.0F);
        }
        return FallRule.Floor.of(landing.getBlock().getFallDistanceReduction());
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
}
