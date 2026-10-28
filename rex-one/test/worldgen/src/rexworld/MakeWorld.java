package rexworld;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Creates Fabric's standard flat test world (seed 1, peaceful) and leaves it in saves/. */
public final class MakeWorld implements FabricClientGameTest {
   @Override
   public void runTest(ClientGameTestContext context) {
      try (TestSingleplayerContext world = context.worldBuilder().create()) {
         world.getClientWorld().waitForChunksRender();
         System.out.println("[rexworld] flat world created");
      }
   }
}
