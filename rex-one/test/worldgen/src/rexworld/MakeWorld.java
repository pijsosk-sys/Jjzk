package rexworld;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldBuilder;

/**
 * Creates a test world and leaves it in saves/.
 *
 * <p>By default that is Fabric's standard flat world (seed 1, peaceful), which is what the Auto
 * Build tests want: a blank table to build on. With -Drexworld.normal=true it creates an ordinary
 * generated world instead - real terrain, trees, caves, ore and mobs - which is the only honest
 * place to test a bot that is supposed to play the game.
 */
public final class MakeWorld implements FabricClientGameTest {
   @Override
   public void runTest(ClientGameTestContext context) {
      boolean normal = Boolean.getBoolean("rexworld.normal");
      TestWorldBuilder builder = context.worldBuilder();
      if (normal) {
         // Fabric's "consistent settings" are what pick the flat preset; without them the world
         // type is the ordinary one. The seed is fixed so a failure can be looked at again.
         builder = builder.setUseConsistentSettings(false)
            .adjustSettings(settings -> settings.method_48716(System.getProperty("rexworld.seed", "4815162342")));
      }
      try (TestSingleplayerContext world = builder.create()) {
         world.getClientWorld().waitForChunksRender();
         System.out.println("[rexworld] " + (normal ? "normal" : "flat") + " world created");
      }
   }
}
