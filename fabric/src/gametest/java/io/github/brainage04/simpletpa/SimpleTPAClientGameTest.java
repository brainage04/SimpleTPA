package io.github.brainage04.simpletpa;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * Runs the showcase film and checks that clicking [ACCEPT] in a real client's chat teleports. The
 * client sends a clicked command only when it parses there, also without permissions, and shows a
 * confirmation screen otherwise (ClientPacketListener.verifyCommand, client
 * ClientPacketListener.java:2703-2719); a server GameTest cannot reach that.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SimpleTPAClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        SimpleTPAFilm.run(context, SimpleTPAClientGameTest::assertLandedOn);
    }

    private static void assertLandedOn(ServerPlayer friend, ServerPlayer player) {
        if (!friend.position().equals(player.position())
                || friend.getYRot() != player.getYRot()
                || friend.getXRot() != player.getXRot()) {
            throw new AssertionError(
                    "%s did not land on %s after [ACCEPT] was clicked: %s facing %.1f/%.1f, %s facing %.1f/%.1f."
                            .formatted(
                                    friend.getScoreboardName(),
                                    player.getScoreboardName(),
                                    friend.position(),
                                    friend.getYRot(),
                                    friend.getXRot(),
                                    player.position(),
                                    player.getYRot(),
                                    player.getXRot()));
        }
    }
}
