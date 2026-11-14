package io.github.brainage04.simpletpa.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.FloatRange;
import net.jqwik.api.constraints.IntRange;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

/**
 * Checks {@link FallRule} against the game's own fall arithmetic in 26.3, written out here as the
 * game runs it: {@link #vanillaDamage} and {@link #vanillaCountedFall}.
 */
class FallRuleTest {
    /** A player's defaults (Attributes.java:10-11, 44-46, 55-56, 86-87). */
    private static final double SAFE_DISTANCE = 3.0;

    private static final double GRAVITY = 0.08;
    private static final float DRAG = FallRule.verticalDrag(1.0);

    private static FallRule player(OptionalInt slowFalling) {
        return new FallRule(SAFE_DISTANCE, 1.0, slowFalling, GRAVITY, DRAG);
    }

    /**
     * The damage the game deals for landing after {@code fallDistance} on a block with {@code
     * reduction}: Entity.checkFallDamage calls fallOn only for a fall above zero
     * (Entity.java:1590), Block.fallOn reduces the distance and passes a damage modifier of 1
     * (Block.java:486-489), and LivingEntity.calculateFallDamage floors the damage
     * (LivingEntity.java:1851-1862).
     */
    private static int vanillaDamage(
            double fallDistance, float reduction, double safeDistance, double multiplier) {
        if (!(fallDistance > 0.0)) {
            return 0;
        }
        double reduced = fallDistance * (1.0F - reduction);
        double fallPower = reduced + 1.0E-6 - safeDistance;
        return Mth.floor(fallPower * 1.0F * multiplier);
    }

    /**
     * The fall distance the game has counted when a player falling from rest lands {@code drop}
     * blocks lower, with Slow Falling on for the first {@code slowTicks} ticks. Each tick
     * LivingEntity.aiStep resets the fall distance while the effect lasts
     * (LivingEntity.java:3119-3120), then travelInAir moves the player by their speed, which
     * Entity.move stops at the floor and Entity.checkFallDamage adds to the fall distance as a
     * float (Entity.java:1584-1587), and then subtracts the effective gravity
     * (LivingEntity.java:2407-2410, 2461) and applies the vertical drag
     * (LivingEntity.java:2471-2476). Gives NaN when the player has not landed after {@code
     * maxTicks}.
     */
    private static double vanillaCountedFall(
            double drop, int slowTicks, double gravity, float drag, int maxTicks) {
        double fallDistance = 0.0;
        double fallen = 0.0;
        double movementY = 0.0;
        for (int tick = 0; tick < maxTicks; tick++) {
            boolean slow = tick < slowTicks;
            if (slow) {
                fallDistance = 0.0;
            }
            double ya = -Math.min(-movementY, drop - fallen);
            fallen -= ya;
            fallDistance -= (float) ya;
            if (fallen >= drop) {
                return fallDistance;
            }
            double effectiveGravity = slow && movementY <= 0.0 ? Math.min(gravity, 0.01) : gravity;
            movementY = (movementY - effectiveGravity) * drag;
        }
        return Double.NaN;
    }

    /** A drop of the full scan depth or more always hurts: see {@link #aFallPastTheScanHurts}. */
    @Property
    void withoutSlowFallingADropHurtsExactlyWhenTheGameDealsDamage(
            @ForAll @DoubleRange(min = 0.0, max = 128.0, minIncluded = false, maxIncluded = false)
                    double drop,
            @ForAll @FloatRange(min = 0.0F, max = 1.0F) float reduction,
            @ForAll @DoubleRange(min = -1024.0, max = 1024.0) double safeDistance,
            @ForAll @DoubleRange(min = 0.01, max = 100.0) double multiplier) {
        FallRule rule = new FallRule(safeDistance, multiplier, OptionalInt.empty(), GRAVITY, DRAG);
        assertEquals(
                vanillaDamage(drop, reduction, safeDistance, multiplier) > 0,
                rule.hurts(drop, drop, reduction));
    }

    /**
     * With or without Slow Falling: a block that resets a fall can also hold the player until the
     * effect has run out. A cobweb keeps 5 % of a player's vertical movement and then stops them
     * (WebBlock.java:22-27, Entity.java:755-761), so at the 0.01 a tick Slow Falling allows they
     * take thousands of ticks to sink through one.
     */
    @Property
    void aFallThatABlockResetsCountsFromThatBlocksBottom(
            @ForAll @DoubleRange(min = 0.0, max = 128.0, minIncluded = false) double drop,
            @ForAll @DoubleRange(min = 0.0, max = 1.0, minIncluded = false, maxIncluded = false)
                    double share,
            @ForAll @FloatRange(min = 0.0F, max = 1.0F) float reduction,
            @ForAll @DoubleRange(min = -1024.0, max = 1024.0) double safeDistance,
            @ForAll @DoubleRange(min = 0.01, max = 100.0) double multiplier,
            @ForAll @IntRange(min = 0, max = 2400) int slowFallingTicks) {
        OptionalInt slowFalling =
                slowFallingTicks > 0 ? OptionalInt.of(slowFallingTicks) : OptionalInt.empty();
        FallRule rule = new FallRule(safeDistance, multiplier, slowFalling, GRAVITY, DRAG);
        double belowReset = drop * share;
        assertEquals(
                vanillaDamage(belowReset, reduction, safeDistance, multiplier) > 0,
                rule.hurts(drop, belowReset, reduction));
    }

    @Property
    void slowFallingCountsTheFallFromItsLastTickOn(
            @ForAll @DoubleRange(min = 0.0, max = 128.0) double drop,
            @ForAll @IntRange(min = 1, max = 2400) int duration,
            @ForAll @DoubleRange(min = 0.01, max = 1.0) double gravity,
            @ForAll @DoubleRange(min = 0.0, max = 10.0) double airDragModifier) {
        float drag = FallRule.verticalDrag(airDragModifier);
        FallRule rule = new FallRule(SAFE_DISTANCE, 1.0, OptionalInt.of(duration), gravity, drag);
        double counted = rule.countedFall(drop);
        // The effect is taken to end a tick early, after duration - 1 ticks.
        double vanilla = vanillaCountedFall(drop, duration - 1, gravity, drag, 1_000_000);
        // The game adds each tick's movement to the fall distance as a float.
        assertEquals(vanilla, counted, 1.0E-6 * Math.max(1.0, drop));
        double longer = vanillaCountedFall(drop, duration, gravity, drag, 1_000_000);
        assertTrue(counted >= longer - 1.0E-6 * Math.max(1.0, drop), counted + " < " + longer);
    }

    @Property
    void slowFallingNeverCountsMoreThanTheDropOrLessThanNothing(
            @ForAll @DoubleRange(min = 0.0, max = 128.0) double drop,
            @ForAll @IntRange(min = 1, max = 2400) int duration,
            @ForAll @DoubleRange(min = -1.0, max = 1.0) double gravity,
            @ForAll @DoubleRange(min = 0.0, max = 2048.0) double airDragModifier) {
        FallRule rule =
                new FallRule(
                        SAFE_DISTANCE,
                        1.0,
                        OptionalInt.of(duration),
                        gravity,
                        FallRule.verticalDrag(airDragModifier));
        double counted = rule.countedFall(drop);
        assertTrue(counted >= 0.0 && counted <= drop, "counted " + counted + " of " + drop);
    }

    @Property
    void moreSlowFallingNeverCountsMore(
            @ForAll @DoubleRange(min = 0.0, max = 128.0) double drop,
            @ForAll @IntRange(min = 1, max = 2400) int duration,
            @ForAll @DoubleRange(min = 0.01, max = 1.0) double gravity,
            @ForAll @DoubleRange(min = 0.0, max = 10.0) double airDragModifier) {
        float drag = FallRule.verticalDrag(airDragModifier);
        double shorter =
                new FallRule(SAFE_DISTANCE, 1.0, OptionalInt.of(duration), gravity, drag)
                        .countedFall(drop);
        double longer =
                new FallRule(SAFE_DISTANCE, 1.0, OptionalInt.of(duration + 1), gravity, drag)
                        .countedFall(drop);
        assertTrue(longer <= shorter, longer + " > " + shorter);
    }

    @Property
    void aFallAsDeepAsTheScanHurts(
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double safeDistance,
            @ForAll @DoubleRange(min = 0.04, max = 100.0) double multiplier) {
        FallRule rule = new FallRule(safeDistance, multiplier, OptionalInt.empty(), GRAVITY, DRAG);
        double reach = rule.reach();
        assertTrue(vanillaDamage(reach, 0.0F, safeDistance, multiplier) > 0, "reach " + reach);
    }

    /**
     * Over the attributes' whole ranges (Attributes.java:44-46, 86-87). Landing answers a zero
     * multiplier before it asks the rule.
     */
    @Property
    void aFallPastTheScanHurts(
            @ForAll @DoubleRange(min = -1024.0, max = 1024.0) double safeDistance,
            @ForAll @DoubleRange(min = 0.01, max = 100.0) double multiplier,
            @ForAll @DoubleRange(min = 0.0, max = 1024.0) double extra) {
        FallRule rule = new FallRule(safeDistance, multiplier, OptionalInt.empty(), GRAVITY, DRAG);
        double reach = rule.reach();
        // Landing looks for a floor down to the reach and no further.
        assertTrue(reach > 0.0 && reach <= FallRule.MAX_SCAN, "reach " + reach);
        assertTrue(rule.hurts(reach + extra, reach + extra, 0.0F), "reach " + reach);
    }

    @Property
    void aDropOfNothingNeverHurts(
            @ForAll @DoubleRange(min = -1024.0, max = 1024.0) double safeDistance,
            @ForAll @DoubleRange(min = 0.01, max = 100.0) double multiplier,
            @ForAll @FloatRange(min = 0.0F, max = 1.0F) float reduction,
            @ForAll @IntRange(min = 0, max = 2400) int duration) {
        OptionalInt slowFalling = duration > 0 ? OptionalInt.of(duration) : OptionalInt.empty();
        FallRule rule = new FallRule(safeDistance, multiplier, slowFalling, GRAVITY, DRAG);
        assertFalse(rule.hurts(0.0, 0.0, reduction));
    }

    @Property
    void withSlowFallingNoFloorWithinTheScanHurts(
            @ForAll @DoubleRange(min = 0.0, max = 1024.0) double extra,
            @ForAll @IntRange(min = 1, max = 2400) int duration) {
        FallRule rule = player(OptionalInt.of(duration));
        assertEquals(FallRule.MAX_SCAN, rule.reach());
        double drop = rule.reach() + extra;
        assertTrue(rule.hurts(drop, drop, 0.0F));
    }

    @Test
    void aPlayerTakesDamageFromFourBlocksDown() {
        FallRule rule = player(OptionalInt.empty());
        assertEquals(5.0, rule.reach());
        assertFalse(rule.hurts(3.0, 3.0, 0.0F));
        assertFalse(rule.hurts(3.99, 3.99, 0.0F));
        assertTrue(rule.hurts(4.0, 4.0, 0.0F));
        // A bed halves the distance (Blocks.java:715-721, Block.java:487).
        assertFalse(rule.hurts(7.99, 7.99, 0.5F));
        assertTrue(rule.hurts(8.0, 8.0, 0.5F));
        // Below a resetting block only the three blocks under its bottom count.
        assertFalse(rule.hurts(20.0, 3.0, 0.0F));
    }

    @Test
    void theFallDamageMultiplierAppliesBeforeTheDamageIsFloored() {
        FallRule half = new FallRule(SAFE_DISTANCE, 0.5, OptionalInt.empty(), GRAVITY, DRAG);
        assertEquals(6.0, half.reach());
        assertFalse(half.hurts(4.99, 4.99, 0.0F));
        assertTrue(half.hurts(5.0, 5.0, 0.0F));
    }

    @Test
    void slowFallingLeavesOnlyItsLastTickAndTheRestOfTheFall() {
        // The effect is taken to end after 20 ticks; the first 19 fall about 1.5 blocks.
        FallRule rule = player(OptionalInt.of(21));
        double counted = rule.countedFall(10.0);
        assertTrue(counted > 8.0 && counted < 9.0, "counted " + counted);
        assertTrue(rule.hurts(10.0, 10.0, 0.0F));
        // A long effect lands the player before it ends: only the last tick's step counts.
        FallRule longEffect = player(OptionalInt.of(1200));
        assertTrue(longEffect.countedFall(10.0) < 0.5);
        assertFalse(longEffect.hurts(10.0, 10.0, 0.0F));
        assertEquals(FallRule.MAX_SCAN, longEffect.reach());
    }

    @Test
    void slowFallingTakesNothingOffTheFallBelowACobweb() {
        // A cobweb one block down, then twenty blocks of air: the effect can end in the cobweb.
        FallRule rule = player(OptionalInt.of(600));
        assertFalse(rule.hurts(21.0, 21.0, 0.0F));
        assertTrue(rule.hurts(21.0, 20.0, 0.0F));
        assertFalse(rule.hurts(21.0, 3.0, 0.0F));
    }

    @Test
    void withoutGravityOrDragTheWholeDropCounts() {
        FallRule weightless = new FallRule(SAFE_DISTANCE, 1.0, OptionalInt.of(1200), 0.0, DRAG);
        assertEquals(10.0, weightless.countedFall(10.0));
        FallRule dragged =
                new FallRule(
                        SAFE_DISTANCE,
                        1.0,
                        OptionalInt.of(1200),
                        GRAVITY,
                        FallRule.verticalDrag(2048.0));
        assertEquals(10.0, dragged.countedFall(10.0));
        assertEquals(0.98F, DRAG);
    }
}
