package io.github.brainage04.simpletpa.command;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
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
     * damage rule (LivingEntity.java:1851-1862) for their own attributes. The fall stops at the
     * first block or standable entity below, and water or a block the game tags as resetting fall
     * damage on the way down (Entity.java:770-780,1684-1689) takes the damage away, as does the
     * landing block's own reduction (Block.java:486-489).
     */
    public static boolean dropHurts(ServerPlayer from, AABB arrival) {
        if (from.is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            return false;
        }
        double multiplier = from.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        if (multiplier <= 0.0) {
            return false;
        }

        double safeDistance = from.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        double reach = Math.min(MAX_SCAN, Math.ceil(safeDistance + 1.0 / multiplier) + 1.0);
        ServerLevel level = from.level();
        AABB column = arrival.setMinY(arrival.minY - reach).setMaxY(arrival.minY);
        List<VoxelShape> colliders = Entity.collectAllColliders(from, level, column);
        double drop = -Shapes.collide(Direction.Axis.Y, arrival, colliders, -reach);
        double support = arrival.minY - drop;
        for (Entity entity : level.getEntities(from, column, entity -> standable(from, entity))) {
            double top = entity.getBoundingBox().maxY;
            if (top <= arrival.minY) {
                support = Math.max(support, top);
            }
        }

        Vec3 centre = arrival.getCenter();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double y = arrival.minY - 1.0E-4; y > support; y -= 1.0) {
            pos.set(centre.x, y, centre.z);
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().is(FluidTags.WATER)
                    || state.is(BlockTags.FALL_DAMAGE_RESETTING)) {
                return false;
            }
        }

        BlockPos landingPos = BlockPos.containing(centre.x, support - 1.0E-4, centre.z);
        BlockState landing = level.getBlockState(landingPos);
        double distance =
                (arrival.minY - support) * (1.0F - landing.getBlock().getFallDistanceReduction());
        return Math.floor(distance + 1.0E-6 - safeDistance) * multiplier > 0.0;
    }
}
