package dev.donut.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.donut.client.autobuild.Schematic;
import dev.donut.client.autobuild.Schematic.Block;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_10799;
import net.minecraft.class_11231;
import net.minecraft.class_11244;
import net.minecraft.class_2338;
import net.minecraft.class_2682;
import net.minecraft.class_332;
import net.minecraft.class_4588;
import net.minecraft.class_8030;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

@Environment(EnvType.CLIENT)
final class SchematicModelPreview {
   private static final int MAX_VOXELS = 6000;
   private final List<SchematicModelPreview.Voxel> voxels = new ArrayList<>();
   private Schematic schematic;
   private double yaw = Math.PI / 4;
   private double pitch = 0.48;
   private double zoom = 1.0;
   private boolean sampled;

   void setSchematic(Schematic value) {
      if (this.schematic != value) {
         this.schematic = value;
         this.voxels.clear();
         this.sampled = false;
         this.yaw = Math.PI / 4;
         this.pitch = 0.48;
         this.zoom = 1.0;
         if (value != null) {
            List<Block> candidates = value.blocks();
            if (candidates.size() <= 250000) {
               Set<class_2338> occupied = new HashSet<>();

               for (Block block : candidates) {
                  occupied.add(block.pos());
               }

               candidates = candidates.stream()
                  .filter(
                     blockx -> {
                        class_2338 p = blockx.pos();
                        return !occupied.contains(p.method_10084())
                           || !occupied.contains(p.method_10074())
                           || !occupied.contains(p.method_10095())
                           || !occupied.contains(p.method_10072())
                           || !occupied.contains(p.method_10078())
                           || !occupied.contains(p.method_10067());
                     }
                  )
                  .toList();
            }

            int stride = Math.max(1, (candidates.size() + 6000 - 1) / 6000);
            this.sampled = stride > 1;

            for (int i = 0; i < candidates.size(); i += stride) {
               Block block = candidates.get(i);
               int rgb = block.state().method_26205(class_2682.field_12294, block.pos()).field_16011;
               if (rgb == 0) {
                  rgb = 8954558;
               }

               this.voxels.add(new SchematicModelPreview.Voxel(block.pos(), rgb));
            }
         }
      }
   }

   boolean sampled() {
      return this.sampled;
   }

   void rotate(double dx, double dy) {
      this.yaw += dx * 0.012;
      this.pitch = Math.clamp(this.pitch + dy * 0.009, 0.12, 1.3);
   }

   void zoom(double scroll) {
      this.zoom = Math.clamp(this.zoom * Math.pow(1.12, scroll), 0.25, 5.0);
   }

   void reset() {
      this.yaw = Math.PI / 4;
      this.pitch = 0.48;
      this.zoom = 1.0;
   }

   void render(class_332 g, int x, int y, int width, int height) {
      if (this.schematic != null && !this.voxels.isEmpty()) {
         class_2338 d = this.schematic.dimensions();
         double cy = Math.cos(this.yaw);
         double sy = Math.sin(this.yaw);
         double cp = Math.cos(this.pitch);
         double sp = Math.sin(this.pitch);
         double spanX = Math.abs(cy) * d.method_10263() + Math.abs(sy) * d.method_10260();
         double spanY = Math.abs(sp * sy) * d.method_10263() + cp * d.method_10264() + Math.abs(sp * cy) * d.method_10260();
         double unit = Math.min((width - 30) / Math.max(1.0, spanX), (height - 26) / Math.max(1.0, spanY)) * this.zoom;
         List<SchematicModelPreview.Face> faces = new ArrayList<>(this.voxels.size() * 3);
         int[][][] templates = new int[][][]{
            {{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}},
            sy >= 0.0 ? new int[][]{{1, 0, 0}, {1, 0, 1}, {1, 1, 1}, {1, 1, 0}} : new int[][]{{0, 0, 1}, {0, 0, 0}, {0, 1, 0}, {0, 1, 1}},
            cy >= 0.0 ? new int[][]{{1, 0, 1}, {0, 0, 1}, {0, 1, 1}, {1, 1, 1}} : new int[][]{{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}}
         };

         for (SchematicModelPreview.Voxel voxel : this.voxels) {
            int[] colors = new int[]{this.shade(voxel.color, 1.08), this.shade(voxel.color, 0.72), this.shade(voxel.color, 0.88)};

            for (int face = 0; face < 3; face++) {
               float[] points = new float[8];
               double depth = 0.0;

               for (int i = 0; i < 4; i++) {
                  double px = voxel.pos.method_10263() + templates[face][i][0] - d.method_10263() / 2.0;
                  double py = voxel.pos.method_10264() + templates[face][i][1] - d.method_10264() / 2.0;
                  double pz = voxel.pos.method_10260() + templates[face][i][2] - d.method_10260() / 2.0;
                  double horizontal = cy * px - sy * pz;
                  double forward = sy * px + cy * pz;
                  points[i * 2] = (float)(x + width / 2.0 + horizontal * unit);
                  points[i * 2 + 1] = (float)(y + height / 2.0 + (forward * sp - py * cp) * unit);
                  depth += forward * cp + py * sp;
               }

               faces.add(new SchematicModelPreview.Face(points, UiPaint.withOpacity(colors[face]), depth));
            }
         }

         faces.sort(Comparator.comparingDouble(SchematicModelPreview.Face::depth));
         submit(g, new SchematicModelPreview.ModelMesh(g, x, y, width, height, faces));
      }
   }

   // The GUI render state and scissor stack are widened to public at runtime by the jar's access
   // widener; this source compiles against the plain game jar, so it reaches them reflectively.
   private static java.lang.reflect.Field stateField;
   private static java.lang.reflect.Method addElement;
   private static java.lang.reflect.Field scissorField;
   private static java.lang.reflect.Method peekScissor;

   private static void submit(class_332 g, class_11244 element) {
      try {
         if (stateField == null) {
            stateField = class_332.class.getDeclaredField("field_59826");
            stateField.setAccessible(true);
         }
         Object state = stateField.get(g);
         if (addElement == null) {
            addElement = state.getClass().getMethod("method_70919", class_11244.class);
            addElement.setAccessible(true);
         }
         addElement.invoke(state, element);
      } catch (ReflectiveOperationException failure) {
         throw new IllegalStateException("Cannot draw the schematic preview", failure);
      }
   }

   private static class_8030 scissor(class_332 g) {
      try {
         if (scissorField == null) {
            scissorField = class_332.class.getDeclaredField("field_44659");
            scissorField.setAccessible(true);
         }
         Object stack = scissorField.get(g);
         if (peekScissor == null) {
            peekScissor = stack.getClass().getDeclaredMethod("method_70863");
            peekScissor.setAccessible(true);
         }
         return (class_8030)peekScissor.invoke(stack);
      } catch (ReflectiveOperationException failure) {
         return null;
      }
   }

   private int shade(int rgb, double amount) {
      return 0xFF000000
         | Math.min(255, (int)((rgb >> 16 & 0xFF) * amount)) << 16
         | Math.min(255, (int)((rgb >> 8 & 0xFF) * amount)) << 8
         | Math.min(255, (int)((rgb & 0xFF) * amount));
   }

   @Environment(EnvType.CLIENT)
   private record Face(float[] points, int color, double depth) {
   }

   @Environment(EnvType.CLIENT)
   private static final class ModelMesh implements class_11244 {
      private final Matrix3x2fc pose;
      private final class_8030 scissor;
      private final class_8030 bounds;
      private final List<SchematicModelPreview.Face> faces;

      ModelMesh(class_332 g, int x, int y, int width, int height, List<SchematicModelPreview.Face> faces) {
         this.pose = new Matrix3x2f(g.method_51448());
         this.scissor = scissor(g);
         class_8030 transformed = new class_8030(x, y, width, height).method_71523(this.pose);
         this.bounds = this.scissor == null ? transformed : transformed.method_49701(this.scissor);
         this.faces = faces;
      }

      @Override
      public void method_70917(class_4588 consumer) {
         for (SchematicModelPreview.Face face : this.faces) {
            for (int i = 3; i >= 0; i--) {
               consumer.method_70815(this.pose, face.points[i * 2], face.points[i * 2 + 1]).method_39415(face.color);
            }
         }
      }

      @Override
      public RenderPipeline comp_4055() {
         return class_10799.field_56879;
      }

      @Override
      public class_11231 comp_4056() {
         return class_11231.method_70899();
      }

      @Override
      public class_8030 comp_4069() {
         return this.scissor;
      }

      @Override
      public class_8030 comp_4274() {
         return this.bounds;
      }
   }

   @Environment(EnvType.CLIENT)
   private record Voxel(class_2338 pos, int color) {
   }
}
