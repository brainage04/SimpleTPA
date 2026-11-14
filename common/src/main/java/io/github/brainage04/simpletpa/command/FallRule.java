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
        double safeDistance, double multiplier, OptionalInt slowFalling, double gravity, float drag) {
    /** Far past any safe fall distance, so the search for a floor stops well inside it. */
    static final double MAX_SCAN = 128.0;

    /**
     * How the block a fall ends on turns the fall into damage: the fall distance it passes to
     * LivingEntity.causeFallDamage, {@code fall * (1 - reduction) + addedDistance}, and the damage
     * modifier it passes with it. Block.fallOn passes the fall less the block's fall distance
     * reduction and a modifier of 1 (Block.java:486-489).
     */
    record Floor(float reduction, double addedDistance, float damageModifier) {
        static Floor of(float reduction) {
            return new Floor(reduction, 0.0, 1.0F);
        }
    }

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
     * Whether a fall of {@code drop} blocks from rest hurts, judged by the game's fall damage rule
     * (LivingEntity.java:1851-1862). A block that resets the fall on the way down leaves {@code
     * dropBelowReset} blocks of it, less than {@code drop}, and {@code floor} is how the landing
     * block deals the damage. A {@code dropBelowReset} of {@link #MAX_SCAN} means no floor was
     * found within the deepest scan, and the fall may go on to any depth. A shorter reach needs no
     * such rule: a plain landing at the reach already hurts.
     */
    boolean hurts(double drop, double dropBelowReset, Floor floor) {
        if (dropBelowReset >= MAX_SCAN) {
            return true;
        }
        // A block that resets the fall can also hold the player until Slow Falling has run out: a
        // cobweb keeps 5 % of their vertical movement and then stops them (WebBlock.java:22-27,
        // Entity.java:755-761), so below such a block the effect takes nothing off the fall.
        double counted = dropBelowReset < drop ? dropBelowReset : countedFall(drop);
        // The game deals no fall damage for a landing without a fall (Entity.java:1590), whatever
        // the safe distance.
        if (counted <= 0.0) {
            return false;
        }
        double distance = counted * (1.0F - floor.reduction()) + floor.addedDistance();
        // The game floors the damage after applying the modifier and multiplier, not the distance
        // before them (LivingEntity.java:1857).
        return Math.floor(
                        (distance + 1.0E-6 - safeDistance) * floor.damageModifier() * multiplier)
                > 0.0;
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
