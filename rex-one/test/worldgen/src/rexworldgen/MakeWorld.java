package rexworldgen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Creates Fabric's standard flat test world, waits for chunks, then quits. The save is kept. */
public final class MakeWorld implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            context.waitTicks(40);
        }
    }
}
