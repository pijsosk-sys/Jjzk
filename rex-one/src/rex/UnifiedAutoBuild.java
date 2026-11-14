package rex;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.CreativeBuildMode;
import dev.donut.client.autobuild.UnifiedSchematics;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.gui.AutoBuilderScreen;
import dev.krypton.client.KryptonClient;
import dev.krypton.impl.modules.client.KryptonPlus;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public final class UnifiedAutoBuild {
   private static ClientConfig config;
   private static Runnable save;

   private UnifiedAutoBuild() {
   }

   public static void bind(ClientConfig var0, Object var1) {
      config = var0;

      try {
         Method var2 = var1.getClass().getDeclaredMethod("save");
         var2.setAccessible(true);
         save = () -> {
            try {
               var2.invoke(var1);
            } catch (ReflectiveOperationException var3x) {
               throw new IllegalStateException("Could not save builder settings.", var3x);
            }
         };
      } catch (ReflectiveOperationException var3) {
         throw new IllegalStateException("Builder settings are unavailable.", var3);
      }
   }

   public static int binding(String var0, int var1) {
      if (var0.equals("open_gui")) {
         return 346;
      } else {
         return var0.equals("open_autobuilder") ? 79 : var1;
      }
   }

   public static boolean busy() {
      return AutoBuilderController.get().isRunning();
   }

   public static String status() {
      AutoBuilderController var0 = AutoBuilderController.get();
      return "Donut + REX: " + var0.status() + " | " + var0.confirmedBlocks() + " confirmed | " + var0.reason();
   }

   public static void pauseUnified() {
      AutoBuilderController.get().pause();
   }

   public static void acquire() {
      KryptonClient var0 = KryptonClient.instance;
      if (var0 != null) {
         AutoBuild var1 = var0.modules().get(AutoBuild.class);
         if (var1 != null && var1.isEnabled()) {
            var1.setEnabled(false);
         }

         if (RexOverlord.instance != null && RexOverlord.instance.isEnabled()) {
            RexOverlord.instance.setEnabled(false);
         }

         try {
            if (LayerBuild.instance != null && LayerBuild.instance.engine != null && !LayerBuild.instance.engine.paused && !LayerBuild.instance.engine.finished
               )
             {
               LayerBuild.instance.engine.pause();
            }
         } catch (Exception var3) {
            throw new IllegalStateException("Could not release the layer builder.", var3);
         }

         releaseBaritone();
      }
   }

   private static void releaseBaritone() {
      try {
         Method var0 = Class.forName("baritone.api.BaritoneAPI").getMethod("getProvider");
         Object var1 = var0.invoke(null);
         Method var2 = var0.getReturnType().getMethod("getPrimaryBaritone");
         Object var3 = var2.invoke(var1);
         if (var3 == null) {
            return;
         }

         Method var4 = var2.getReturnType().getMethod("getPathingBehavior");
         Object var5 = var4.invoke(var3);
         var4.getReturnType().getMethod("cancelEverything").invoke(var5);
      } catch (ClassNotFoundException var6) {
      } catch (ReflectiveOperationException var7) {
         throw new IllegalStateException("Could not release Baritone movement.", var7);
      }
   }

   public static void openFromRex(AutoBuild var0) {
      var0.setEnabledSilently(false);

      try {
         open();
      } catch (LinkageError | Exception var2) {
         Game.message("Auto Build: " + var2.getMessage());
      }
   }

   public static void open() throws Exception {
      if (config != null && save != null) {
         MinecraftClient var0 = (MinecraftClient)Bridge.client();
         if (var0.player != null && var0.world != null) {
            acquire();
            AutoBuilderController.get().pause();
            KryptonPlus var1 = KryptonClient.instance.modules().get(KryptonPlus.class);
            if (var1 != null) {
               var1.setEnabledSilently(false);
            }

            KryptonClient.instance.previousScreen = null;
            var0.setScreen(new AutoBuilderScreen(null, config, save));
         } else {
            throw new IOException("Join a world before opening Auto Build.");
         }
      } else {
         throw new IOException("Builder is still initializing.");
      }
   }

   public static boolean command(String var0) {
      String var1 = var0.strip();
      String[] var2 = var1.split("\\s+", 3);
      if (var2.length != 0 && (var2[0].equalsIgnoreCase(".autobuild") || var2[0].equalsIgnoreCase(".build"))) {
         try {
            AutoBuilderController var3 = AutoBuilderController.get();
            String var4 = var2.length > 1 ? var2[1].toLowerCase(Locale.ROOT) : "setup";
            switch (var4) {
               case "setup":
               case "menu":
                  open();
                  break;
               case "start":
                  if (var3.schematic() == null) {
                     open();
                  } else {
                     ((MinecraftClient)Bridge.client()).setScreen((Screen)null);
                     if (var3.status() == AutoBuilderController.Status.PAUSED) {
                        var3.resume();
                     } else {
                        var3.start();
                     }
                  }
                  break;
               case "pause":
                  var3.pause();
                  break;
               case "resume":
                  ((MinecraftClient)Bridge.client()).setScreen((Screen)null);
                  var3.resume();
                  break;
               case "stop":
               case "cancel":
                  var3.cancel();
                  break;
               case "creative":
                  if (config == null) {
                     throw new IOException("Builder is still initializing.");
                  }

                  if (var2.length < 3) {
                     throw new IOException("Use .autobuild creative on | off");
                  }

                  if (!var2[2].equalsIgnoreCase("on") && !var2[2].equalsIgnoreCase("off")) {
                     throw new IOException("Use on or off.");
                  }

                  config.autoBuilder.creativeMode = var2[2].equalsIgnoreCase("on");
                  save.run();
                  Game.message(CreativeBuildMode.label(config.autoBuilder));
                  break;
               case "speed":
               case "tempo":
                  if (config == null) {
                     throw new IOException("Builder is still initializing.");
                  }

                  if (var2.length >= 3) {
                     String wanted = var2[2].trim().toUpperCase(Locale.ROOT);
                     boolean known = false;
                     for (dev.donut.client.autobuild.BuildTempo each : dev.donut.client.autobuild.BuildTempo.values()) {
                        known |= each.name().equals(wanted);
                     }

                     if (!known) {
                        throw new IOException("Use .autobuild speed safe | balanced | fast | turbo");
                     }

                     config.autoBuilder.tempo = wanted;
                     CreativeBuildMode.configure(config.autoBuilder);
                     save.run();
                  }

                  dev.donut.client.autobuild.BuildTempo now = dev.donut.client.autobuild.BuildTempo.parse(config.autoBuilder.tempo);
                  Game.message("Build Tempo: " + now.label + " (" + now.describe() + ")");
                  break;
               case "list":
                  Path var7 = ((File)Bridge.field(Bridge.client(), "field_1697")).toPath().resolve("schematics/donut");

                  for (Path var9 : UnifiedSchematics.list(var7)) {
                     Game.message(var9.getFileName().toString());
                  }
                  break;
               case "status":
               case "doctor":
                  Game.message(
                     "Donut + REX Auto Build: "
                        + var3.status()
                        + " | "
                        + var3.confirmedBlocks()
                        + " confirmed | "
                        + var3.reason()
                        + " | Tempo " + AutoBuilderController.tempo().label + "; Creative fetch="
                        + (config != null && config.autoBuilder.creativeMode)
                  );
                  break;
               default:
                  Game.message(".autobuild setup | start | pause | resume | stop | status | list | speed safe/balanced/fast/turbo | creative on/off");
            }
         } catch (LinkageError | Exception var10) {
            Game.message("Auto Build: " + var10.getMessage());
         }

         return true;
      } else {
         return false;
      }
   }
}
