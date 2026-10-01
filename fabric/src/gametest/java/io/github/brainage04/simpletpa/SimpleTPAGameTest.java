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
	public void denyFlows(GameTestHelper helper) {
		SimpleTPAGameTests.denyFlows(helper);
	}

	@GameTest
	public void instantAutoAcceptFlow(GameTestHelper helper) {
		SimpleTPAGameTests.instantAutoAcceptFlow(helper);
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
