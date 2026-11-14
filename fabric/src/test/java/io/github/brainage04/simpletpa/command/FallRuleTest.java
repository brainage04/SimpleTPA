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
 * game runs it: {@link #vanillaDamage}, the blocks' {@code fallOn} below it, {@link
 * #vanillaCountedFall} and {@link #vanillaFreeFallStep}.
 */
class FallRuleTest {
    /** A player's defaults (Attributes.java:10-11, 44-46, 55-56, 86-87). */
    private static final double SAFE_DISTANCE = 3.0;

    private static final double GRAVITY = 0.08;
    private static final float DRAG = FallRule.verticalDrag(1.0);
    private static final FallRule.Floor PLAIN = FallRule.Floor.of(0.0F);

    private static FallRule player(OptionalInt slowFalling) {
        return new FallRule(SAFE_DISTANCE, 1.0, slowFalling, GRAVITY, DRAG);
    }

    private static FallRule.Floor floor(int kind, float reduction) {
        return switch (kind) {
            case 1 -> FallRule.Floor.STALAGMITE_TIP;
            case 2 -> FallRule.Floor.FIFTH;
            case 3 -> FallRule.Floor.NO_DAMAGE;
            default -> FallRule.Floor.of(reduction);
        };
    }

    /**
     * The damage of Entity.causeFallDamage for a fall of {@code fallDistance} with {@code
     * damageModifier}: LivingEntity.calculateFallDamage floors it after the modifier and the
     * multiplier (LivingEntity.java:1851-1862). Entity.checkFallDamage calls the landing block's
     * fallOn, which calls this, only for a fall above zero (Entity.java:1590), so each fallOn below
     * returns 0 for a fall of zero.
     */
    private static int vanillaDamage(
            double fallDistance, float damageModifier, double safeDistance, double multiplier) {
        double fallPower = fallDistance + 1.0E-6 - safeDistance;
        return Mth.floor(fallPower * damageModifier * multiplier);
    }

    /** Block.fallOn (Block.java:486-489). */
    private static int plainFallOn(
            double fallDistance, float reduction, double safeDistance, double multiplier) {
        if (!(fallDistance > 0.0)) {
            return 0;
        }
        double reduced = fallDistance * (1.0F - reduction);
        return vanillaDamage(reduced, 1.0F, safeDistance, multiplier);
    }

    /** PointedDripstoneBlock.fallOn on a stalagmite tip (PointedDripstoneBlock.java:54-56). */
    private static int stalagmiteTipFallOn(
            double fallDistance, double safeDistance, double multiplier) {
        if (!(fallDistance > 0.0)) {
            return 0;
        }
        return vanillaDamage(fallDistance + 2.5, 2.0F, safeDistance, multiplier);
    }

    /** HayBlock.fallOn and HoneyBlock.fallOn (HayBlock.java:17-19, HoneyBlock.java:45-50). */
    private static int hayOrHoneyFallOn(
            double fallDistance, double safeDistance, double multiplier) {
        if (!(fallDistance > 0.0)) {
            return 0;
        }
        return vanillaDamage(fallDistance, 0.2F, safeDistance, multiplier);
    }

    /** SlimeBlock.fallOn for a player who does not sneak (SlimeBlock.java:15-19). */
    private static int slimeFallOn(double fallDistance, double safeDistance, double multiplier) {
        if (!(fallDistance > 0.0)) {
            return 0;
        }
        return vanillaDamage(fallDistance, 0.0F, safeDistance, multiplier);
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

    /**
     * The movement of the tick that takes a player falling from rest through open air more than
     * {@code depth} blocks down: each tick travelInAir moves the player by their speed, then
     * subtracts the gravity and applies the vertical drag (LivingEntity.java:2450-2476).
     */
    private static double vanillaFreeFallStep(double depth, double gravity, float drag) {
        double fallen = 0.0;
        double movementY = 0.0;
        while (true) {
            double step = -movementY;
            if (fallen + step > depth) {
                return step;
            }
            fallen += step;
            movementY = (movementY - gravity) * drag;
        }
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
                plainFallOn(drop, reduction, safeDistance, multiplier) > 0,
                rule.hurts(drop, drop, Double.NaN, FallRule.Floor.of(reduction)));
    }

    @Property
    void blocksWithTheirOwnFallOnHurtExactlyWhenTheGameDealsDamage(
            @ForAll @DoubleRange(min = 0.0, max = 128.0, minIncluded = false) double counted,
            @ForAll @DoubleRange(min = -1024.0, max = 1024.0) double safeDistance,
            @ForAll @DoubleRange(min = 0.01, max = 100.0) double multiplier) {
        assertEquals(
                stalagmiteTipFallOn(counted, safeDistance, multiplier) > 0,
                FallRule.Floor.STALAGMITE_TIP.hurts(counted, safeDistance, multiplier),
                "stalagmite tip");
        assertEquals(
                hayOrHoneyFallOn(counted, safeDistance, multiplier) > 0,
                FallRule.Floor.FIFTH.hurts(counted, safeDistance, multiplier),
                "hay or honey");
        assertEquals(
                slimeFallOn(counted, safeDistance, multiplier) > 0,
                FallRule.Floor.NO_DAMAGE.hurts(counted, safeDistance, multiplier),
                "slime");
    }

    /**
     * With or without Slow Falling, a fall that crosses a block that resets it counts only what
     * restarted below that block.
     */
    @Property
    void aFallThatABlockResetsCountsWhatRestartedBelowIt(
            @ForAll @DoubleRange(min = 0.0, max = 128.0, minIncluded = false) double drop,
            @ForAll @DoubleRange(min = 0.0, max = 1.0, minIncluded = false) double share,
            @ForAll @FloatRange(min = 0.0F, max = 1.0F) float reduction,
            @ForAll @DoubleRange(min = -1024.0, max = 1024.0) double safeDistance,
            @ForAll @DoubleRange(min = 0.01, max = 100.0) double multiplier,
            @ForAll @IntRange(min = 0, max = 2400) int slowFallingTicks) {
        OptionalInt slowFalling =
                slowFallingTicks > 0 ? OptionalInt.of(slowFallingTicks) : OptionalInt.empty();
        FallRule rule = new FallRule(safeDistance, multiplier, slowFalling, GRAVITY, DRAG);
        double restarted = drop * share;
        assertEquals(
                plainFallOn(restarted, reduction, safeDistance, multiplier) > 0,
                rule.hurts(drop, restarted, restarted, FallRule.Floor.of(reduction)));
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
    void aFreeFallStepIsTheMovementOfTheTickThatPassesTheDepth(
            @ForAll @DoubleRange(min = 0.0, max = 128.0) double depth,
            @ForAll @DoubleRange(min = 0.01, max = 1.0) double gravity,
            @ForAll @DoubleRange(min = 0.0, max = 10.0) double airDragModifier) {
        float drag = FallRule.verticalDrag(airDragModifier);
        FallRule rule = new FallRule(SAFE_DISTANCE, 1.0, OptionalInt.empty(), gravity, drag);
        assertEquals(vanillaFreeFallStep(depth, gravity, drag), rule.freeFallStep(depth));
    }

    @Property
    void aRestartStartsNoHigherThanOneTicksMovementAboveTheBlock(
            @ForAll boolean climbable,
            @ForAll boolean water,
            @ForAll @DoubleRange(min = 0.0, max = 128.0) double depth,
            @ForAll @DoubleRange(min = 0.0, max = 4.0) double height,
            @ForAll @DoubleRange(min = 0.01, max = 1.0) double gravity,
            @ForAll @DoubleRange(min = 0.0, max = 10.0) double airDragModifier) {
        float drag = FallRule.verticalDrag(airDragModifier);
        FallRule rule = new FallRule(SAFE_DISTANCE, 1.0, OptionalInt.empty(), gravity, drag);
        double above = rule.restartAbove(climbable, water, depth, height);
        double step = rule.freeFallStep(depth);
        assertTrue(above >= 0.0 && above <= step, above + " of " + step);
        if (!climbable && !water) {
            assertEquals(0.0, above);
        }
        if (climbable && step < 1.0) {
            assertTrue(above <= FallRule.CLIMB_SPEED, "climbable " + above);
        }
        if (water) {
            assertTrue(above <= Math.max(0.0, step - height), "water " + above);
        }
    }

    @Property
    void aFallAsDeepAsTheScanHurts(
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double safeDistance,
            @ForAll @DoubleRange(min = 0.04, max = 100.0) double multiplier) {
        FallRule rule = new FallRule(safeDistance, multiplier, OptionalInt.empty(), GRAVITY, DRAG);
        double reach = rule.reach();
        assertTrue(reach > 0.0 && reach <= FallRule.MAX_SCAN, "reach " + reach);
        assertTrue(plainFallOn(reach, 0.0F, safeDistance, multiplier) > 0, "reach " + reach);
        assertTrue(rule.hurts(reach, reach, Double.NaN, PLAIN), "reach " + reach);
    }

    @Property
    void withSlowFallingNoFloorWithinTheScanHurts(
            @ForAll @DoubleRange(min = 0.0, max = 1024.0) double extra,
            @ForAll @IntRange(min = 1, max = 2400) int duration) {
        FallRule rule = player(OptionalInt.of(duration));
        assertEquals(FallRule.MAX_SCAN, rule.reach());
        double drop = rule.reach() + extra;
        assertTrue(rule.hurts(drop, drop, Double.NaN, PLAIN));
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
        assertTrue(rule.hurts(reach + extra, reach + extra, Double.NaN, PLAIN), "reach " + reach);
    }

    /** The game calls fallOn only for a fall above zero (Entity.java:1590). */
    @Property
    void aDropOfNothingNeverHurts(
            @ForAll @DoubleRange(min = -1024.0, max = 1024.0) double safeDistance,
            @ForAll @DoubleRange(min = 0.01, max = 100.0) double multiplier,
            @ForAll @IntRange(min = 0, max = 3) int kind,
            @ForAll @FloatRange(min = 0.0F, max = 1.0F) float reduction,
            @ForAll @DoubleRange(min = 0.0, max = 128.0, maxIncluded = false) double drop,
            @ForAll @IntRange(min = 0, max = 2400) int duration) {
        OptionalInt slowFalling = duration > 0 ? OptionalInt.of(duration) : OptionalInt.empty();
        FallRule rule = new FallRule(safeDistance, multiplier, slowFalling, GRAVITY, DRAG);
        FallRule.Floor floor = floor(kind, reduction);
        assertFalse(rule.hurts(0.0, 0.0, Double.NaN, floor), "no drop");
        assertFalse(rule.hurts(drop, 0.0, 0.0, floor), "nothing below a resetting block");
    }

    @Test
    void aPlayerTakesDamageFromFourBlocksDown() {
        FallRule rule = player(OptionalInt.empty());
        assertEquals(5.0, rule.reach());
        assertFalse(rule.hurts(3.0, 3.0, Double.NaN, PLAIN));
        assertFalse(rule.hurts(3.99, 3.99, Double.NaN, PLAIN));
        assertTrue(rule.hurts(4.0, 4.0, Double.NaN, PLAIN));
        // A bed halves the distance (Blocks.java:715-721, Block.java:487).
        assertFalse(rule.hurts(7.99, 7.99, Double.NaN, FallRule.Floor.of(0.5F)));
        assertTrue(rule.hurts(8.0, 8.0, Double.NaN, FallRule.Floor.of(0.5F)));
        // Below a resetting block only the three blocks under its bottom count.
        assertFalse(rule.hurts(20.0, 3.0, 3.0, PLAIN));
    }

    @Test
    void blocksWithTheirOwnFallOnChangeTheDamage() {
        // A stalagmite tip one block down: 1 + 2.5 - 3 = 0.5, doubled.
        assertTrue(FallRule.Floor.STALAGMITE_TIP.hurts(1.0, SAFE_DISTANCE, 1.0));
        assertFalse(FallRule.Floor.STALAGMITE_TIP.hurts(0.99, SAFE_DISTANCE, 1.0));
        // Hay: (8 - 3) / 5 = 1.
        assertTrue(FallRule.Floor.FIFTH.hurts(8.0, SAFE_DISTANCE, 1.0));
        assertFalse(FallRule.Floor.FIFTH.hurts(7.99, SAFE_DISTANCE, 1.0));
        assertFalse(FallRule.Floor.NO_DAMAGE.hurts(128.0, SAFE_DISTANCE, 100.0));
    }

    @Test
    void theFallDamageMultiplierAppliesBeforeTheDamageIsFloored() {
        FallRule half = new FallRule(SAFE_DISTANCE, 0.5, OptionalInt.empty(), GRAVITY, DRAG);
        assertEquals(6.0, half.reach());
        assertFalse(half.hurts(4.99, 4.99, Double.NaN, PLAIN));
        assertTrue(half.hurts(5.0, 5.0, Double.NaN, PLAIN));
    }

    @Test
    void slowFallingLeavesOnlyItsLastTickAndTheRestOfTheFall() {
        // The effect is taken to end after 20 ticks; the first 19 fall about 1.5 blocks.
        FallRule rule = player(OptionalInt.of(21));
        double counted = rule.countedFall(10.0);
        assertTrue(counted > 8.0 && counted < 9.0, "counted " + counted);
        assertTrue(rule.hurts(10.0, 10.0, Double.NaN, PLAIN));
        // A long effect lands the player before it ends: only the last tick's step counts.
        FallRule longEffect = player(OptionalInt.of(1200));
        assertTrue(longEffect.countedFall(10.0) < 0.5);
        assertFalse(longEffect.hurts(10.0, 10.0, Double.NaN, PLAIN));
        assertEquals(FallRule.MAX_SCAN, longEffect.reach());
        // Below a resetting block the effect gets no credit.
        assertTrue(longEffect.hurts(30.0, 10.0, 10.0, PLAIN));
    }

    @Test
    void aRestartAboveVinesOrWaterFollowsTheTicksMovement() {
        FallRule rule = player(OptionalInt.empty());
        // Five blocks down a player falls about 0.84 blocks a tick.
        double step = rule.freeFallStep(5.0);
        assertTrue(step > 0.8 && step < 0.9, "step " + step);
        assertEquals(FallRule.CLIMB_SPEED, rule.restartAbove(true, false, 5.0, 1.8));
        assertEquals(0.0, rule.restartAbove(false, true, 5.0, 1.8));
        assertEquals(0.0, rule.restartAbove(false, false, 5.0, 1.8));
        // Forty blocks down a tick moves about two blocks, which can pass vines whole.
        double fast = rule.freeFallStep(40.0);
        assertTrue(fast > 2.0 && fast < 2.1, "step " + fast);
        assertEquals(fast, rule.restartAbove(true, false, 40.0, 1.8));
        assertEquals(fast - 1.8, rule.restartAbove(false, true, 40.0, 1.8), 1.0E-12);
    }

    @Test
    void withoutGravityOrDragTheWholeDropCounts() {
        FallRule weightless = new FallRule(SAFE_DISTANCE, 1.0, OptionalInt.of(1200), 0.0, DRAG);
        assertEquals(10.0, weightless.countedFall(10.0));
        assertEquals(0.0, weightless.freeFallStep(10.0));
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
