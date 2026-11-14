package io.github.brainage04.simpletpa.command;

import java.util.OptionalInt;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The arithmetic of {@link Landing#dropHurts} for one player's fall attributes, apart from the
 * blocks and entities below them.
 *
 * @param safeDistance the player's {@code safe_fall_distance}
 * @param multiplier the player's {@code fall_damage_multiplier}, above zero
 * @param slowFalling the ticks left on the player's Slow Falling effect, if they have it
 * @param gravity the player's {@code gravity}
 * @param drag the player's vertical air drag, as {@link #verticalDrag} works it out
 */
record FallRule(
        double safeDistance,
        double multiplier,
        OptionalInt slowFalling,
        double gravity,
        float drag) {
    /** Far past any safe fall distance, so the search for a floor stops well inside it. */
    static final double MAX_SCAN = 128.0;

    /**
     * The most a climbable lets a player fall in a tick, from LivingEntity.handleOnClimbable
     * (LivingEntity.java:2690-2693), where it is a local value no API exposes.
     */
    static final double CLIMB_SPEED = 0.15;

    /**
     * The vertical air drag for {@code airDragModifier}: LivingEntity.computeModifiedFriction
     * (LivingEntity.java:518-520), which is private there, applied to the vertical drag
     * (LivingEntity.java:2474).
     */
    static float verticalDrag(double airDragModifier) {
        return Mth.clamp(
                1.0F - (1.0F - LivingEntity.BASE_VERTICAL_AIR_DRAG) * (float) airDragModifier,
                0.0F,
                1.0F);
    }

    /**
     * How far below the start of a fall to look for the floor that ends it. A safe fall distance
     * far enough below zero would take the reach to zero or above the player, so it is at least one
     * block.
     */
    double reach() {
        // Slow Falling leaves the first part of a fall uncounted, so the floor that decides may be
        // deeper than the safe distance.
        return slowFalling.isPresent()
                ? MAX_SCAN
                : Math.clamp(Math.ceil(safeDistance + 1.0 / multiplier) + 1.0, 1.0, MAX_SCAN);
    }

    /**
     * Whether a fall of {@code drop} blocks from rest onto {@code floor} hurts. {@code scanned} is
     * the part of it below the lowest block that resets the fall on the way down, or all of it when
     * there is none; a {@code scanned} of {@link #MAX_SCAN} or more means no floor was found within
     * the deepest scan, and the fall may go on to any depth. A shorter reach needs no such rule: a
     * plain landing at the reach already hurts. {@code restarted} is the fall that still counts
     * below that block ({@link #restartAbove}), or NaN when the fall crosses none.
     */
    boolean hurts(double drop, double scanned, double restarted, Floor floor) {
        if (scanned >= MAX_SCAN) {
            return true;
        }
        // A resetting block also slows the fall through it: a climbable to 0.15 a tick
        // (LivingEntity.java:2690-2693), a cobweb, berry bush or powder snow to a fraction
        // (WebBlock.java:27, SweetBerryBushBlock.java:79, PowderSnowBlock.java:60), water by its
        // drag (LivingEntity.java:2497-2498). How long it holds the requester decides where Slow
        // Falling runs out, which may be inside it, so below one the effect gets no credit.
        double counted = Double.isNaN(restarted) ? countedFall(drop) : restarted;
        // The game deals no fall damage for a landing without a fall (Entity.java:1590), whatever
        // the safe distance or the block.
        if (counted <= 0.0) {
            return false;
        }
        return floor.hurts(counted, safeDistance, multiplier);
    }

    /**
     * How the block a fall ends on turns the fall into damage. Block.fallOn passes the fall less
     * the block's own reduction with a damage modifier of 1 (Block.java:486-489). A few blocks
     * override {@code fallOn} with numbers of their own that no API exposes, which Landing copies:
     * a stalagmite tip adds 2.5 blocks and doubles the damage (PointedDripstoneBlock.java:54-58),
     * hay and honey deal a fifth of it (HayBlock.java:17-19, HoneyBlock.java:45-50), and slime none
     * (SlimeBlock.java:15-19).
     *
     * @param reduction the share of the fall the block takes away
     * @param addedDistance the blocks the block adds to the fall
     * @param damageModifier the damage modifier the block passes to causeFallDamage
     */
    record Floor(float reduction, double addedDistance, float damageModifier) {
        static final Floor STALAGMITE_TIP = new Floor(0.0F, 2.5, 2.0F);
        static final Floor FIFTH = new Floor(0.0F, 0.0, 0.2F);
        static final Floor NO_DAMAGE = new Floor(0.0F, 0.0, 0.0F);

        static Floor of(float reduction) {
            return new Floor(reduction, 0.0, 1.0F);
        }

        /**
         * Whether a counted fall of {@code counted} blocks onto this floor deals damage. The game
         * floors the damage after applying the modifier and the multiplier, not the distance before
         * them (LivingEntity.java:1851-1862).
         */
        boolean hurts(double counted, double safeDistance, double multiplier) {
            double fall = counted * (1.0F - reduction) + addedDistance;
            return Math.floor((fall + 1.0E-6 - safeDistance) * damageModifier * multiplier) > 0.0;
        }
    }

    /**
     * How far above the bottom of a resetting block the fall that counts below it may start, for a
     * player falling from rest who reaches the block's bottom {@code depth} blocks down, with a box
     * {@code height} blocks tall.
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
     * fall through open air ({@link #freeFallStep}).
     */
    double restartAbove(boolean climbable, boolean water, double depth, double height) {
        double step = freeFallStep(depth);
        double above = 0.0;
        if (climbable) {
            above = step >= 1.0 ? step : Math.min(step, CLIMB_SPEED);
        }
        if (water) {
            double inWater = Math.max(0.0, step - height);
            above = climbable ? Math.min(above, inWater) : inWater;
        }
        return above;
    }

    /**
     * The movement of the tick that takes a player falling from rest through open air more than
     * {@code depth} blocks down, by the air model {@link #countedFall} describes.
     */
    double freeFallStep(double depth) {
        if (gravity <= 0.0) {
            return 0.0;
        }
        double fallen = 0.0;
        double speed = 0.0;
        while (fallen + speed <= depth) {
            fallen += speed;
            double next = (speed + gravity) * drag;
            if (next <= speed) {
                // The speed has settled at the drag's terminal speed, to within rounding, or at
                // nothing for a drag of zero, and every later tick moves as far. Counting those
                // ticks one by one could take billions of them.
                return speed;
            }
            speed = next;
        }
        return speed;
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
    double countedFall(double drop) {
        if (slowFalling.isEmpty() || gravity <= 0.0) {
            return drop;
        }
        int slowTicks = slowFalling.getAsInt() - 1;
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
