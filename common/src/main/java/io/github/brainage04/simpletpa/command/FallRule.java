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

    /** How far below the start of a fall to look for the floor that ends it. */
    double reach() {
        // Slow Falling leaves the first part of a fall uncounted, so the floor that decides may be
        // deeper than the safe distance.
        return slowFalling.isPresent()
                ? MAX_SCAN
                : Math.min(MAX_SCAN, Math.ceil(safeDistance + 1.0 / multiplier) + 1.0);
    }

    /**
     * Whether a fall of {@code drop} blocks from rest hurts, judged by the game's fall damage rule
     * (LivingEntity.java:1851-1862). A block that resets the fall on the way down leaves {@code
     * dropBelowReset} blocks of it, and {@code reduction} is the landing block's fall distance
     * reduction (Block.java:486-489). A {@code dropBelowReset} of {@link #reach} or more means no
     * floor was found within the scan.
     */
    boolean hurts(double drop, double dropBelowReset, float reduction) {
        if (slowFalling.isPresent() && dropBelowReset >= reach()) {
            // No floor within the scan: how much of the fall counts depends on where it ends.
            return true;
        }
        // Slow Falling and a resetting block each take away the fall above them, so the lower of
        // the two starts what counts.
        double counted = Math.min(countedFall(drop), dropBelowReset);
        double distance = counted * (1.0F - reduction);
        // The game floors the damage after applying the multiplier, not the distance before it
        // (LivingEntity.java:1857).
        return Math.floor((distance + 1.0E-6 - safeDistance) * multiplier) > 0.0;
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
