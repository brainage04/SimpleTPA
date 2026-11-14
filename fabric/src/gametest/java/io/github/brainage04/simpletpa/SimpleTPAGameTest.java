package io.github.brainage04.simpletpa;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class SimpleTPAGameTest {
    @GameTest
    public void acceptFlows(GameTestHelper helper) {
        SimpleTPAGameTests.acceptFlows(helper);
    }

    @GameTest
    public void tpaAliasAcceptFlow(GameTestHelper helper) {
        SimpleTPAGameTests.tpaAliasAcceptFlow(helper);
    }

    @GameTest
    public void tpaTabCompletion(GameTestHelper helper) {
        SimpleTPAGameTests.tpaTabCompletion(helper);
    }

    @GameTest
    public void requestsExpire(GameTestHelper helper) {
        SimpleTPAGameTests.requestsExpire(helper);
    }

    @GameTest
    public void denyFlows(GameTestHelper helper) {
        SimpleTPAGameTests.denyFlows(helper);
    }

    @GameTest
    public void namedDenyFlow(GameTestHelper helper) {
        SimpleTPAGameTests.namedDenyFlow(helper);
    }

    @GameTest
    public void responseNeedsOneOnlineRequest(GameTestHelper helper) {
        SimpleTPAGameTests.responseNeedsOneOnlineRequest(helper);
    }

    @GameTest
    public void instantAutoAcceptFlow(GameTestHelper helper) {
        SimpleTPAGameTests.instantAutoAcceptFlow(helper);
    }

    @GameTest
    public void autoAcceptAnswersPendingRequest(GameTestHelper helper) {
        SimpleTPAGameTests.autoAcceptAnswersPendingRequest(helper);
    }

    @GameTest
    public void autoAcceptWaitsForBlockedTarget(GameTestHelper helper) {
        SimpleTPAGameTests.autoAcceptWaitsForBlockedTarget(helper);
    }

    @GameTest
    public void acceptWaitsForBlockedTarget(GameTestHelper helper) {
        SimpleTPAGameTests.acceptWaitsForBlockedTarget(helper);
    }

    @GameTest
    public void acceptWaitsForSenderWhoCannotMove(GameTestHelper helper) {
        SimpleTPAGameTests.acceptWaitsForSenderWhoCannotMove(helper);
    }

    @GameTest
    public void teleportsWhenNobodyFalls(GameTestHelper helper) {
        SimpleTPAGameTests.teleportsWhenNobodyFalls(helper);
    }

    @GameTest(maxTicks = 40)
    public void teleportsOntoHappyGhast(GameTestHelper helper) {
        SimpleTPAGameTests.teleportsOntoHappyGhast(helper);
    }

    @GameTest(maxTicks = 40)
    public void refusesLongDrop(GameTestHelper helper) {
        SimpleTPAGameTests.refusesLongDrop(helper);
    }

    @GameTest(maxTicks = 40)
    public void acceptsShortDrop(GameTestHelper helper) {
        SimpleTPAGameTests.acceptsShortDrop(helper);
    }

    @GameTest(maxTicks = 40)
    public void teleportsBetweenDimensions(GameTestHelper helper) {
        SimpleTPAGameTests.teleportsBetweenDimensions(helper);
    }

    @GameTest(maxTicks = 40)
    public void smallFallMultiplierAcceptsAHurtlessDrop(GameTestHelper helper) {
        SimpleTPAGameTests.smallFallMultiplierAcceptsAHurtlessDrop(helper);
    }

    @GameTest(maxTicks = 40)
    public void resettingLandingsAndSlowFallingAcceptADrop(GameTestHelper helper) {
        SimpleTPAGameTests.resettingLandingsAndSlowFallingAcceptADrop(helper);
    }

    @GameTest(maxTicks = 40)
    public void creativeSkipsLandingChecks(GameTestHelper helper) {
        SimpleTPAGameTests.creativeSkipsLandingChecks(helper);
    }

    @GameTest(maxTicks = 40)
    public void safetyCommandShowsAndSets(GameTestHelper helper) {
        SimpleTPAGameTests.safetyCommandShowsAndSets(helper);
    }

    @GameTest
    public void requestButtons(GameTestHelper helper) {
        SimpleTPAGameTests.requestButtons(helper);
    }

    @GameTest
    public void tpautoAlias(GameTestHelper helper) {
        SimpleTPAGameTests.tpautoAlias(helper);
    }

    @GameTest
    public void removeOfflinePlayer(GameTestHelper helper) {
        SimpleTPAGameTests.removeOfflinePlayer(helper);
    }

    @GameTest
    public void whitelistNamesFollowRenames(GameTestHelper helper) {
        SimpleTPAGameTests.whitelistNamesFollowRenames(helper);
    }

    @GameTest
    public void removePrefersOnlinePlayer(GameTestHelper helper) {
        SimpleTPAGameTests.removePrefersOnlinePlayer(helper);
    }

    @GameTest
    public void ambiguousNameIsNotRemoved(GameTestHelper helper) {
        SimpleTPAGameTests.ambiguousNameIsNotRemoved(helper);
    }

    @GameTest
    public void acceptAllNeedsConfirmation(GameTestHelper helper) {
        SimpleTPAGameTests.acceptAllNeedsConfirmation(helper);
    }

    @GameTest(maxTicks = SimpleTPAGameTests.ACCEPT_ALL_EXPIRY_MAX_TICKS)
    public void acceptAllConfirmationExpires(GameTestHelper helper) {
        SimpleTPAGameTests.acceptAllConfirmationExpires(helper);
    }

    @GameTest
    public void acceptAllTeleports(GameTestHelper helper) {
        SimpleTPAGameTests.acceptAllTeleports(helper);
    }

    @GameTest
    public void acceptAllToggleOff(GameTestHelper helper) {
        SimpleTPAGameTests.acceptAllToggleOff(helper);
    }

    @GameTest(environment = SimpleTPAGameTests.AUTO_ACCEPT_OFF_ENVIRONMENT)
    public void acceptAllGameruleOff(GameTestHelper helper) {
        SimpleTPAGameTests.acceptAllGameruleOff(helper);
    }

    @GameTest
    public void acceptAllPersistence(GameTestHelper helper) {
        SimpleTPAGameTests.acceptAllPersistence(helper);
    }

    @GameTest
    public void unreadableWhitelistIsNotOverwritten(GameTestHelper helper) {
        SimpleTPAGameTests.unreadableWhitelistIsNotOverwritten(helper);
    }

    @GameTest
    public void whitelistFromPreviousVersionLoads(GameTestHelper helper) {
        SimpleTPAGameTests.whitelistFromPreviousVersionLoads(helper);
    }

    @GameTest
    public void whitelistNotSavedBeforeLoad(GameTestHelper helper) {
        SimpleTPAGameTests.whitelistNotSavedBeforeLoad(helper);
    }

    @GameTest
    public void addWildcardRejected(GameTestHelper helper) {
        SimpleTPAGameTests.addWildcardRejected(helper);
    }

    @GameTest
    public void combinedServerHelp(GameTestHelper helper) {
        SimpleTPAGameTests.combinedServerHelp(helper);
    }

    @GameTest
    public void gameruleRegistration(GameTestHelper helper) {
        SimpleTPAGameTests.gameruleRegistration(helper);
    }

    @GameTest
    public void legacyGameRuleMigrates(GameTestHelper helper) {
        SimpleTPAGameTests.legacyGameRuleMigrates(helper);
    }

    @GameTest
    public void legacyGameRuleYieldsToCurrentRule(GameTestHelper helper) {
        SimpleTPAGameTests.legacyGameRuleYieldsToCurrentRule(helper);
    }

    @GameTest
    public void legacyGameRuleSurvivesCorruptFile(GameTestHelper helper) {
        SimpleTPAGameTests.legacyGameRuleSurvivesCorruptFile(helper);
    }
}
