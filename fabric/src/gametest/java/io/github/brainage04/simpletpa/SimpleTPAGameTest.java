package io.github.brainage04.simpletpa;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Runs the loader-neutral GameTests on Fabric; {@link SimpleTPATest} covers the same flows with Carpet fake players. */
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
	public void denyFlows(GameTestHelper helper) {
		SimpleTPAGameTests.denyFlows(helper);
	}

	@GameTest
	public void instantAutoAcceptFlow(GameTestHelper helper) {
		SimpleTPAGameTests.instantAutoAcceptFlow(helper);
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
}
