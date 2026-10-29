package dev.donut.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.class_5590;
import com.mojang.blaze3d.vertex.VertexFormat.class_5595;
import com.mojang.blaze3d.vertex.VertexFormat.class_5596;
import dev.donut.client.autobuild.MaterialTracker;
import dev.donut.client.chunkfinder.ChunkFinderScanner;
import dev.donut.client.chunkfinder.FoundChunk;
import dev.donut.client.chunkfinder.FoundChunk.Layer;
import dev.donut.client.config.ChunkFinderSettings;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.FinderSettings;
import dev.donut.client.config.RenderMode;
import dev.donut.client.finder.FinderScanner;
import dev.donut.client.finder.FoundBlock;
import dev.donut.client.finder.TargetKind;
import dev.donut.client.render.SchematicGhosts.Ghost;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_10789;
import net.minecraft.class_10799;
import net.minecraft.class_11285;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_265;
import net.minecraft.class_2338;
import net.minecraft.class_638;
import net.minecraft.class_2680;
import net.minecraft.class_276;
import net.minecraft.class_287;
import net.minecraft.class_290;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_9799;
import net.minecraft.class_9801;
import net.minecraft.class_4587.class_4665;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
public final class HighlightRenderer implements AutoCloseable {
   private final RenderStateDataKey<List<HighlightRenderer.DrawBox>> frame = RenderStateDataKey.create(() -> "Donut finder highlights");
   private static final RenderPipeline FILL_VISIBLE = pipeline("fill_visible", false, false);
   private static final RenderPipeline FILL_THROUGH = pipeline("fill_through", false, true);
   private static final RenderPipeline LINE_VISIBLE = pipeline("line_visible", true, false);
   private static final RenderPipeline LINE_THROUGH = pipeline("line_through", true, true);
   private static final Vector4f WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
   private static final Vector3f ZERO = new Vector3f();
   private static final Matrix4f IDENTITY = new Matrix4f();
   private static final int[][] FACES = new int[][]{{0, 4, 6, 2}, {5, 1, 3, 7}, {4, 0, 1, 5}, {2, 6, 7, 3}, {1, 0, 2, 3}, {4, 5, 7, 6}};
   private final ClientConfig config;
   private final FinderScanner scanner;
   private final ChunkFinderScanner chunkScanner;
   private final SchematicGhosts ghosts = new SchematicGhosts();
   private final HighlightRenderer.Batch fillVisible = new HighlightRenderer.Batch(FILL_VISIBLE);
   private final HighlightRenderer.Batch fillThrough = new HighlightRenderer.Batch(FILL_THROUGH);
   private final HighlightRenderer.Batch lineVisible = new HighlightRenderer.Batch(LINE_VISIBLE);
   private final HighlightRenderer.Batch lineThrough = new HighlightRenderer.Batch(LINE_THROUGH);
   private boolean registered;
   private boolean closed;

   public HighlightRenderer(ClientConfig config, FinderScanner scanner) {
      this(config, scanner, null);
   }

   public HighlightRenderer(ClientConfig config, FinderScanner scanner, ChunkFinderScanner chunkScanner) {
      this.config = config;
      this.scanner = scanner;
      this.chunkScanner = chunkScanner;
   }

   public void register() {
      if (!this.registered) {
         this.registered = true;
         WorldRenderEvents.END_EXTRACTION.register(this::extract);
         WorldRenderEvents.END_MAIN.register(this::draw);
      }
   }

   private static RenderPipeline pipeline(String name, boolean lines, boolean throughWalls) {
      Builder builder = RenderPipeline.builder()
         .withLocation(class_2960.method_60655("donut", "pipeline/" + name))
         .withVertexShader(lines ? "core/rendertype_lines" : "core/position_color")
         .withFragmentShader("core/position_color")
         .withUniform("DynamicTransforms", class_10789.field_60031)
         .withUniform("Projection", class_10789.field_60031)
         .withVertexFormat(lines ? class_290.field_63455 : class_290.field_1576, lines ? class_5596.field_27377 : class_5596.field_27382)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(!lines)
         .withDepthWrite(false)
         .withDepthTestFunction(throughWalls ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST);
      if (lines) {
         builder.withUniform("Globals", class_10789.field_60031).withUniform("Fog", class_10789.field_60031);
      }

      // Registering a pipeline is widened to public at runtime by the jar's access widener; this
      // source compiles against the plain game jar, so it calls it reflectively.
      try {
         java.lang.reflect.Method register = class_10799.class.getDeclaredMethod("method_67887", RenderPipeline.class);
         register.setAccessible(true);
         return (RenderPipeline)register.invoke(null, builder.build());
      } catch (ReflectiveOperationException failure) {
         throw new IllegalStateException("Cannot register the highlight pipeline " + name, failure);
      }
   }

   private void extract(WorldExtractionContext context) {
      // Fabric adds FabricRenderState to the world render state at runtime.
      FabricRenderState state = (FabricRenderState)(Object)context.worldState();
      if (this.closed) {
         state.setData(this.frame, List.of());
      } else {
         class_243 camera = context.worldState().field_63082.field_63078;
         List<HighlightRenderer.DrawBox> boxes = new ArrayList<>();

         for (FoundBlock block : this.scanner.snapshot()) {
            FinderSettings settings = block.kind().isSpawner() ? this.config.spawner : this.config.storage;
            if (settings.enabled && block.kind().included(settings)) {
               double distance = class_243.method_24953(block.pos()).method_1022(camera);
               if (!(distance > settings.range) && context.world().method_22340(block.pos())) {
                  class_2680 currentState = context.world().method_8320(block.pos());
                  if (TargetKind.from(currentState) == block.kind()) {
                     class_265 shape = currentState.method_26218(context.world(), block.pos());
                     if (!shape.method_1110()) {
                        class_238 bounds = shape.method_1107().method_996(block.pos()).method_1014(0.003);
                        if (context.frustum().method_23093(bounds)) {
                           double fade = settings.distanceFade ? fade(distance, settings.range) : 1.0;
                           int color = color(settings, block.kind());
                           int fill = settings.mode != RenderMode.OUTLINE ? argb(color, settings.fillOpacity * fade) : 0;
                           int outline = settings.mode != RenderMode.FILL ? argb(color, settings.outlineOpacity * fade) : 0;
                           if (fill >>> 24 != 0 || outline >>> 24 != 0) {
                              boxes.add(
                                 new HighlightRenderer.DrawBox(
                                    bounds.method_989(-camera.field_1352, -camera.field_1351, -camera.field_1350),
                                    fill,
                                    outline,
                                    (float)Math.clamp(settings.lineWidth, 0.5, 8.0),
                                    settings.throughWalls
                                 )
                              );
                           }
                        }
                     }
                  }
               }
            }
         }

         if (this.chunkScanner != null && this.config.chunkFinder.enabled && this.config.chunkFinder.alpha > 0) {
            ChunkFinderSettings settings = this.config.chunkFinder;

            for (FoundChunk chunk : this.chunkScanner.snapshot()) {
               if (context.world().method_8393(chunk.chunkX(), chunk.chunkZ())) {
                  double x = chunk.chunkX() * 16.0;
                  double z = chunk.chunkZ() * 16.0;
                  double opacity = settings.alpha / 255.0;

                  for (Layer layer : chunk.layers()) {
                     class_238 bounds = new class_238(x, layer.minY() - 0.05, z, x + 16.0, layer.maxY() + 1.05, z + 16.0);
                     if (context.frustum().method_23093(bounds)) {
                        boxes.add(
                           new HighlightRenderer.DrawBox(
                              bounds.method_989(-camera.field_1352, -camera.field_1351, -camera.field_1350),
                              argb(settings.color, opacity * 0.06),
                              argb(settings.color, opacity),
                              1.0F,
                              true
                           )
                        );
                     }
                  }
               }
            }
         }

         // The schematic ghost. Working out every ghost box (a world lookup, shape and colour each)
         // on every frame, and drawing all six faces and twelve edges of thousands of boxes, took a
         // big farm down to a couple of frames a second. The boxes are now worked out a few times a
         // second, faces shared by two ghost cubes are left out, and only nearby ghosts get edges;
         // each frame just culls them to the view.
         this.refreshGhosts(context.world(), camera);
         for (HighlightRenderer.GhostBox ghost : this.ghostBoxes) {
            if (context.frustum().method_23093(ghost.bounds)) {
               boxes.add(
                  new HighlightRenderer.DrawBox(
                     ghost.bounds.method_989(-camera.field_1352, -camera.field_1351, -camera.field_1350), ghost.fill, ghost.outline, 1.3F, false, ghost.faces
                  )
               );
            }
         }

         state.setData(this.frame, List.copyOf(boxes));
      }
   }

   private static final int[][] NEIGHBOURS = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
   private static final long GHOST_REFRESH_NANOS = 400_000_000L;
   private static final double GHOST_EDGE_RANGE = 18.0;
   private List<HighlightRenderer.GhostBox> ghostBoxes = List.of();
   private long ghostsAt;
   private class_243 ghostsCamera;
   private Object ghostsWorld;
   // Outline boxes by block state; a few dozen states cover a whole farm.
   private final java.util.Map<class_2680, class_238> ghostShapes = new java.util.IdentityHashMap<>();

   private void refreshGhosts(class_638 world, class_243 camera) {
      long now = System.nanoTime();
      if (world == this.ghostsWorld && this.ghostsCamera != null && now - this.ghostsAt < GHOST_REFRESH_NANOS
         && this.ghostsCamera.method_1025(camera) < 36.0) {
         return;
      }
      this.ghostsAt = now;
      this.ghostsCamera = camera;
      this.ghostsWorld = world;
      List<Ghost> nearby = this.ghosts.nearby(camera);
      if (nearby.isEmpty()) {
         this.ghostBoxes = List.of();
         return;
      }
      List<Ghost> shown = new ArrayList<>();
      List<class_238> shapes = new ArrayList<>();
      List<class_2680> actuals = new ArrayList<>();
      it.unimi.dsi.fastutil.longs.LongOpenHashSet cubes = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
      for (Ghost ghost : nearby) {
         boolean loaded = MaterialTracker.isLoaded(world, ghost.pos());
         class_2680 actual = loaded ? world.method_8320(ghost.pos()) : null;
         if (actual != null && actual.equals(ghost.state())) {
            continue;
         }
         class_238 box = this.ghostShapes.computeIfAbsent(ghost.state(), ghostState -> {
            class_265 shape = ghostState.method_26218(world, ghost.pos());
            return shape.method_1110() ? new class_238(0.0, 0.0, 0.0, 1.0, 1.0, 1.0) : shape.method_1107();
         });
         shown.add(ghost);
         shapes.add(box);
         actuals.add(actual);
         if (box.field_1323 <= 0.0 && box.field_1322 <= 0.0 && box.field_1321 <= 0.0 && box.field_1320 >= 1.0 && box.field_1325 >= 1.0 && box.field_1324 >= 1.0) {
            cubes.add(ghost.pos().method_10063());
         }
      }
      List<HighlightRenderer.GhostBox> result = new ArrayList<>(shown.size());
      double edges = GHOST_EDGE_RANGE * GHOST_EDGE_RANGE;
      for (int i = 0; i < shown.size(); i++) {
         Ghost ghost = shown.get(i);
         class_2338 pos = ghost.pos();
         int faces = 63;
         if (cubes.contains(pos.method_10063())) {
            for (int face = 0; face < 6; face++) {
               int[] d = NEIGHBOURS[face];
               if (cubes.contains(class_2338.method_10064(pos.method_10263() + d[0], pos.method_10264() + d[1], pos.method_10260() + d[2]))) {
                  faces &= ~(1 << face);
               }
            }
            if (faces == 0) {
               continue; // buried inside other ghost cubes: nothing of it can be seen
            }
         }
         class_2680 actual = actuals.get(i);
         boolean loaded = actual != null;
         boolean conflict = actual != null && !actual.method_45474() && !actual.method_27852(ghost.state().method_26204());
         int color = !loaded ? 12028159 : (conflict ? 16737659 : ghost.state().method_26205(world, pos).field_16011);
         if (color == 0) {
            color = this.config.accentColor;
         }
         class_238 bounds = shapes.get(i).method_996(pos).method_1014(0.002);
         boolean near = class_243.method_24953(pos).method_1025(camera) < edges;
         result.add(new HighlightRenderer.GhostBox(bounds, argb(color, this.config.autoBuilder.previewOpacity), near || conflict ? argb(color, 0.8) : 0, faces));
      }
      this.ghostBoxes = result;
   }

   private static int color(FinderSettings settings, TargetKind kind) {
      if (!settings.perTypeColors) {
         return settings.color;
      } else {
         return switch (kind) {
            case CHEST, TRAPPED_CHEST, ENDER_CHEST -> settings.chestColor;
            case SHULKER -> settings.shulkerColor;
            case DROPPER -> settings.dropperColor;
            case SPAWNER, TRIAL_SPAWNER -> settings.color;
            default -> throw new MatchException(null, null);
         };
      }
   }

   private static double fade(double distance, double range) {
      double progress = Math.clamp((distance / Math.max(1.0, range) - 0.5) * 2.0, 0.0, 1.0);
      return 1.0 - progress * progress * (3.0 - 2.0 * progress);
   }

   private static int argb(int rgb, double opacity) {
      return (int)Math.round(Math.clamp(opacity, 0.0, 1.0) * 255.0) << 24 | rgb & 16777215;
   }

   private void draw(WorldRenderContext context) {
      if (!this.closed) {
         List<HighlightRenderer.DrawBox> boxes = (List<HighlightRenderer.DrawBox>)((FabricRenderState)(Object)context.worldState()).getData(this.frame);
         if (boxes != null && !boxes.isEmpty()) {
            class_4665 pose = context.matrices().method_23760();

            for (HighlightRenderer.DrawBox box : boxes) {
               if (box.fill >>> 24 != 0) {
                  filled((box.throughWalls ? this.fillThrough : this.fillVisible).begin(), pose.method_23761(), box.bounds, box.fill, box.faces);
               }

               if (box.outline >>> 24 != 0) {
                  outlined((box.throughWalls ? this.lineThrough : this.lineVisible).begin(), pose, box.bounds, box.outline, box.width);
               }
            }

            this.fillVisible.flush();
            this.fillThrough.flush();
            this.lineVisible.flush();
            this.lineThrough.flush();
         }
      }
   }

   private static void filled(class_287 buffer, Matrix4fc pose, class_238 bounds, int color, int faces) {
      for (int index = 0; index < FACES.length; index++) {
         if ((faces & 1 << index) == 0) {
            continue;
         }
         for (int corner : FACES[index]) {
            buffer.method_22918(pose, x(bounds, corner), y(bounds, corner), z(bounds, corner)).method_39415(color);
         }
      }
   }

   private static void outlined(class_287 buffer, class_4665 pose, class_238 bounds, int color, float width) {
      for (int corner = 0; corner < 8; corner++) {
         for (int axis = 1; axis <= 4; axis *= 2) {
            if ((corner & axis) == 0) {
               int other = corner | axis;
               float normalX = axis == 1 ? 1.0F : 0.0F;
               float normalY = axis == 2 ? 1.0F : 0.0F;
               float normalZ = axis == 4 ? 1.0F : 0.0F;
               buffer.method_56824(pose, x(bounds, corner), y(bounds, corner), z(bounds, corner))
                  .method_39415(color)
                  .method_60831(pose, normalX, normalY, normalZ)
                  .method_75298(width);
               buffer.method_56824(pose, x(bounds, other), y(bounds, other), z(bounds, other))
                  .method_39415(color)
                  .method_60831(pose, normalX, normalY, normalZ)
                  .method_75298(width);
            }
         }
      }
   }

   private static float x(class_238 box, int corner) {
      return (float)((corner & 1) == 0 ? box.field_1323 : box.field_1320);
   }

   private static float y(class_238 box, int corner) {
      return (float)((corner & 2) == 0 ? box.field_1322 : box.field_1325);
   }

   private static float z(class_238 box, int corner) {
      return (float)((corner & 4) == 0 ? box.field_1321 : box.field_1324);
   }

   @Override
   public void close() {
      if (!this.closed) {
         this.closed = true;
         this.fillVisible.close();
         this.fillThrough.close();
         this.lineVisible.close();
         this.lineThrough.close();
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Batch implements AutoCloseable {
      private final RenderPipeline pipeline;
      private final class_9799 allocator = new class_9799(65536);
      private class_287 builder;
      private class_11285 vertices;

      private Batch(RenderPipeline pipeline) {
         this.pipeline = pipeline;
      }

      private class_287 begin() {
         if (this.builder == null) {
            this.builder = new class_287(this.allocator, this.pipeline.getVertexFormatMode(), this.pipeline.getVertexFormat());
         }

         return this.builder;
      }

      private void flush() {
         if (this.builder != null) {
            class_9801 mesh = this.builder.method_60800();
            this.builder = null;
            class_9801 var2 = mesh;

            try {
               int bytes = mesh.method_60818().remaining();
               if (this.vertices == null || this.vertices.method_71312() < bytes) {
                  if (this.vertices != null) {
                     this.vertices.close();
                  }

                  this.vertices = new class_11285(() -> "Donut highlights", 34, bytes);
               }

               try (MappedView mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.vertices.method_71119().slice(0L, bytes), false, true)) {
                  MemoryUtil.memCopy(mesh.method_60818(), mapped.data());
               }

               class_5595 indexType;
               GpuBuffer indices;
               if (this.pipeline.getVertexFormatMode() == class_5596.field_27382) {
                  mesh.method_60819(this.allocator, RenderSystem.getProjectionType().method_65045());
                  indices = this.pipeline.getVertexFormat().uploadImmediateIndexBuffer(mesh.method_60821());
                  indexType = mesh.method_60822().comp_753();
               } else {
                  class_5590 sequential = RenderSystem.getSequentialBuffer(this.pipeline.getVertexFormatMode());
                  indices = sequential.method_68274(mesh.method_60822().comp_751());
                  indexType = sequential.method_31924();
               }

               GpuBufferSlice transform = RenderSystem.getDynamicUniforms()
                  .method_71106(RenderSystem.getModelViewMatrix(), HighlightRenderer.WHITE, HighlightRenderer.ZERO, HighlightRenderer.IDENTITY);
               class_276 target = class_310.method_1551().method_1522();

               try (RenderPass pass = RenderSystem.getDevice()
                     .createCommandEncoder()
                     .createRenderPass(() -> "Donut finder overlay", target.method_71639(), OptionalInt.empty(), target.method_71640(), OptionalDouble.empty())) {
                  pass.setPipeline(this.pipeline);
                  RenderSystem.bindDefaultUniforms(pass);
                  pass.setUniform("DynamicTransforms", transform);
                  pass.setVertexBuffer(0, this.vertices.method_71119());
                  pass.setIndexBuffer(indices, indexType);
                  pass.drawIndexed(0, 0, mesh.method_60822().comp_751(), 1);
               }

               this.vertices.method_71121();
            } catch (Throwable var16) {
               if (mesh != null) {
                  try {
                     var2.close();
                  } catch (Throwable var11) {
                     var16.addSuppressed(var11);
                  }
               }

               throw var16;
            }

            if (mesh != null) {
               mesh.close();
            }
         }
      }

      @Override
      public void close() {
         this.builder = null;
         this.allocator.close();
         if (this.vertices != null) {
            this.vertices.close();
            this.vertices = null;
         }
      }
   }

   @Environment(EnvType.CLIENT)
   /** A ghost box in world space, worked out a few times a second; faces is a bit per face to fill. */
   private record GhostBox(class_238 bounds, int fill, int outline, int faces) {
   }

   private record DrawBox(class_238 bounds, int fill, int outline, float width, boolean throughWalls, int faces) {
      DrawBox(class_238 bounds, int fill, int outline, float width, boolean throughWalls) {
         this(bounds, fill, outline, width, throughWalls, 63);
      }

   }
}
