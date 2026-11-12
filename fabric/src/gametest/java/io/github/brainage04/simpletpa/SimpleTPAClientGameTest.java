package io.github.brainage04.simpletpa;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

@SuppressWarnings("UnstableApiUsage")
public final class SimpleTPAClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        SimpleTPAFilm.run(context);
    }
}
