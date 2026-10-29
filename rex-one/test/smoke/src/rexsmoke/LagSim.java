package rexsmoke;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.PlacementTransform;
import dev.donut.client.autobuild.Schematic;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.class_2338;
import net.minecraft.class_2535;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_634;
import net.minecraft.server.MinecraftServer;

/**
 * Makes the singleplayer test world behave like a real server, which it otherwise never does:
 *
 * <ul>
 *   <li>{@code -Drexsmoke.ping=200} delays every packet each way by half that many milliseconds,
 *       {@code -Drexsmoke.jitter=60} adds up to that many more at random (order is kept, as TCP does);
 *   <li>{@code -Drexsmoke.rollback=0.05} has the server refuse that share of fresh blueprint blocks,
 *       the way an anticheat or a protected region answers with the old block;
 *   <li>{@code -Drexsmoke.rubberband=12} sets the player back to where they stood a second ago every
 *       that many seconds, as a server does when it disagrees with a movement.
 * </ul>
 */
final class LagSim {
   static final int PING = Integer.getInteger("rexsmoke.ping", 0);
   static final int JITTER = Integer.getInteger("rexsmoke.jitter", 0);
   static final double ROLLBACK = Double.parseDouble(System.getProperty("rexsmoke.rollback", "0"));
   static final int RUBBERBAND = Integer.getInteger("rexsmoke.rubberband", 0);

   static volatile Schematic schematic;
   static volatile boolean running;
   static int rollbacks;
   static int setbacks;

   private static Channel installedOn;
   private static boolean[] matched;
   private static final ArrayDeque<double[]> trail = new ArrayDeque<>();
   private static int serverTicks;
   private static final Random RANDOM = new Random(7);

   private LagSim() {
   }

   static boolean active() {
      return PING > 0 || JITTER > 0 || ROLLBACK > 0 || RUBBERBAND > 0;
   }

   static String describe() {
      return String.format(Locale.ROOT, "ping %d ms (+%d jitter), rollback %.0f%% (%d refused), rubberband %ds (%d setbacks)",
         PING, JITTER, ROLLBACK * 100, rollbacks, RUBBERBAND, setbacks);
   }

   static void register() {
      if (ROLLBACK > 0 || RUBBERBAND > 0) {
         ServerTickEvents.END_SERVER_TICK.register(LagSim::serverTick);
      }
   }

   /** Puts the delay handler on the client's connection once it exists (again after a rejoin). */
   static void install(class_310 client) {
      if (PING <= 0 && JITTER <= 0) {
         return;
      }
      class_634 handler = client.method_1562();
      if (handler == null) {
         return;
      }
      try {
         Field field = class_2535.class.getDeclaredField("field_11651");
         field.setAccessible(true);
         Channel channel = (Channel)field.get(handler.method_48296());
         if (channel == null || channel == installedOn || channel.pipeline().get("packet_handler") == null) {
            return;
         }
         installedOn = channel;
         channel.pipeline().addBefore("packet_handler", "rexsmoke_lag", new Delay());
         System.out.println("[rexsmoke] lag simulator on: " + describe());
      } catch (ReflectiveOperationException error) {
         throw new IllegalStateException(error);
      }
   }

   /**
    * Holds each packet back for half the ping plus jitter. Each direction is one queue drained in
    * order, as TCP delivers: scheduling every packet on its own could swap two that were due within
    * the same moment, which made the server see a hotbar change after the click it came before.
    */
   private static final class Delay extends ChannelDuplexHandler {
      private final ArrayDeque<Object[]> in = new ArrayDeque<>();
      private final ArrayDeque<Object[]> out = new ArrayDeque<>();
      private long lastIn;
      private long lastOut;
      private final Random random = new Random(11);

      private long due(boolean inbound) {
         long due = System.nanoTime() + (PING / 2L + (JITTER > 0 ? this.random.nextInt(JITTER + 1) : 0)) * 1_000_000L;
         long last = inbound ? this.lastIn : this.lastOut;
         if (due < last) {
            due = last;
         }
         if (inbound) {
            this.lastIn = due;
         } else {
            this.lastOut = due;
         }
         return due;
      }

      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) {
         this.queue(ctx, this.in, new Object[]{this.due(true), msg, null});
      }

      @Override
      public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
         this.queue(ctx, this.out, new Object[]{this.due(false), msg, promise});
      }

      private void queue(ChannelHandlerContext ctx, ArrayDeque<Object[]> queue, Object[] entry) {
         queue.addLast(entry);
         ctx.executor().schedule(() -> this.drain(ctx, queue), Math.max(0L, (Long)entry[0] - System.nanoTime()), TimeUnit.NANOSECONDS);
      }

      private void drain(ChannelHandlerContext ctx, ArrayDeque<Object[]> queue) {
         long now = System.nanoTime();
         while (!queue.isEmpty() && (Long)queue.peekFirst()[0] <= now) {
            Object[] entry = queue.pollFirst();
            if (entry[2] == null) {
               ctx.fireChannelRead(entry[1]);
            } else {
               ctx.writeAndFlush(entry[1], (ChannelPromise)entry[2]);
            }
         }
      }
   }

   private static void serverTick(MinecraftServer server) {
      Schematic blueprint = schematic;
      if (!running || blueprint == null) {
         matched = null;
         trail.clear();
         return;
      }
      class_3218 world = server.method_30002();
      if (ROLLBACK > 0) {
         // A block that turned right this tick is refused: put back what was there before, in the
         // same tick, so the client hears only the old block, as from a server that never took it.
         List<Schematic.Block> blocks = blueprint.blocks();
         if (matched == null || matched.length != blocks.size()) {
            matched = new boolean[blocks.size()];
            for (int i = 0; i < blocks.size(); i++) {
               matched[i] = matches(world, blocks.get(i));
            }
         }
         for (int i = 0; i < blocks.size(); i++) {
            boolean now = matches(world, blocks.get(i));
            if (now && !matched[i] && RANDOM.nextDouble() < ROLLBACK) {
               class_2338 pos = SmokeDriverAccess.ORIGIN_TRANSFORM.apply(blocks.get(i).pos());
               world.method_8652(pos, net.minecraft.class_2246.field_10124.method_9564(), 3);
               rollbacks++;
               now = false;
            }
            matched[i] = now;
         }
      }
      List<class_3222> players = server.method_3760().method_14571();
      if (RUBBERBAND > 0 && !players.isEmpty()) {
         class_3222 player = players.get(0);
         trail.addLast(new double[]{player.method_23317(), player.method_23318(), player.method_23321()});
         if (trail.size() > 20) {
            trail.removeFirst();
         }
         if (++serverTicks % (RUBBERBAND * 20) == 0 && trail.size() == 20) {
            double[] back = trail.peekFirst();
            String command = String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f", back[0], back[1], back[2]);
            server.method_3734().method_44252(server.method_3739(), command);
            setbacks++;
         }
      }
   }

   private static boolean matches(class_3218 world, Schematic.Block block) {
      class_2680 desired = SmokeDriverAccess.ORIGIN_TRANSFORM.apply(block.state());
      return AutoBuilderController.matches(desired, world.method_8320(SmokeDriverAccess.ORIGIN_TRANSFORM.apply(block.pos())));
   }

   /** The placement the driver builds at. */
   static final class SmokeDriverAccess {
      static final PlacementTransform ORIGIN_TRANSFORM = new PlacementTransform(new class_2338(0, -60, 0), 0, false);
   }
}
