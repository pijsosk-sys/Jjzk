package dev.donut.client.render;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.PlacementTransform;
import dev.donut.client.autobuild.Schematic;
import dev.donut.client.autobuild.Schematic.Block;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1923;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import rex.SchematicGhost;

@Environment(EnvType.CLIENT)
final class SchematicGhosts {
   private CompletableFuture<SchematicGhosts.Index> pending;
   private SchematicGhosts.Index current;
   private Schematic requested;
   private PlacementTransform requestedTransform;
   private final AtomicInteger generation = new AtomicInteger();

   List<SchematicGhosts.Ghost> nearby(class_243 camera) {
      AutoBuilderController builder = AutoBuilderController.get();
      Schematic schematic = builder.schematic();
      if (builder.previewVisible() && schematic != null) {
         PlacementTransform transform = builder.transform();
         if (schematic != this.requested || !transform.equals(this.requestedTransform)) {
            if (this.pending != null) {
               this.pending.cancel(false);
            }

            this.requested = schematic;
            this.requestedTransform = transform;
            this.current = null;
            int request = this.generation.incrementAndGet();
            this.pending = CompletableFuture.supplyAsync(() -> this.index(schematic, transform, request));
         }

         if (this.pending != null && this.pending.isDone()) {
            if (!this.pending.isCompletedExceptionally()) {
               this.current = this.pending.join();
            }

            this.pending = null;
         }

         if (this.current != null && this.current.schematic == schematic && this.current.transform.equals(transform)) {
            int cx = (int)Math.floor(camera.field_1352) >> 4;
            int cz = (int)Math.floor(camera.field_1350) >> 4;
            List<SchematicGhosts.Ghost> visible = new ArrayList<>();
            List<class_1923> columns = new ArrayList<>(169);

            for (int x = cx - 3; x <= cx + 3; x++) {
               for (int z = cz - 3; z <= cz + 3; z++) {
                  columns.add(new class_1923(x, z));
               }
            }

            columns.sort(Comparator.comparingInt(p -> (p.field_9181 - cx) * (p.field_9181 - cx) + (p.field_9180 - cz) * (p.field_9180 - cz)));
            int[] layers = SchematicGhost.layerRange();

            for (class_1923 column : columns) {
               for (SchematicGhosts.Ghost ghost : this.current.columns.getOrDefault(column.method_8324(), List.of())) {
                  int y = ghost.pos.method_10264();
                  if (layers != null && (y < layers[0] || y > layers[1])) {
                     continue;
                  }
                  // 48 blocks and 4,096 ghosts: further out they only cost frames.
                  if (!(class_243.method_24953(ghost.pos).method_1025(camera) > 2304.0)) {
                     visible.add(ghost);
                     if (visible.size() >= 4096) {
                        return visible;
                     }
                  }
               }
            }

            return visible;
         } else {
            return List.of();
         }
      } else {
         this.current = null;
         this.requested = null;
         if (this.pending != null) {
            this.generation.incrementAndGet();
            this.pending.cancel(false);
         }

         this.pending = null;
         return List.of();
      }
   }

   private SchematicGhosts.Index index(Schematic schematic, PlacementTransform transform, int request) {
      Map<Long, List<SchematicGhosts.Ghost>> columns = new HashMap<>();

      for (Block block : schematic.blocks()) {
         if (request != this.generation.get()) {
            throw new CancellationException();
         }

         class_2338 pos = transform.apply(block.pos());
         columns.computeIfAbsent(class_1923.method_8331(pos.method_10263() >> 4, pos.method_10260() >> 4), key -> new ArrayList<>())
            .add(new SchematicGhosts.Ghost(pos, transform.apply(block.state())));
      }

      return new SchematicGhosts.Index(schematic, transform, columns);
   }

   @Environment(EnvType.CLIENT)
   record Ghost(class_2338 pos, class_2680 state) {
   }

   @Environment(EnvType.CLIENT)
   private record Index(Schematic schematic, PlacementTransform transform, Map<Long, List<SchematicGhosts.Ghost>> columns) {
   }
}
