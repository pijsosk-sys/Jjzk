package rex.path;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2338;

/** A finished route: the cells to stand in, and how to get into each one. */
@Environment(EnvType.CLIENT)
public final class Path {
   public static final byte WALK = 0;
   public static final byte DIAGONAL = 1;
   public static final byte JUMP = 2;
   public static final byte FALL = 3;
   public static final byte SWIM = 4;
   public static final byte BREAK = 5;
   public static final byte BRIDGE = 6;

   public final long[] cells;
   /** How the matching cell is entered; index 0 is where the walk started. */
   public final byte[] moves;
   public final boolean complete;

   public Path(long[] cells, byte[] moves, boolean complete) {
      this.cells = cells;
      this.moves = moves;
      this.complete = complete;
   }

   public int length() {
      return this.cells.length;
   }

   public class_2338 cell(int index) {
      return class_2338.method_10092(this.cells[index]);
   }

   public long last() {
      return this.cells[this.cells.length - 1];
   }

   public static String name(byte move) {
      return switch (move) {
         case DIAGONAL -> "diagonal";
         case JUMP -> "jump";
         case FALL -> "drop";
         case SWIM -> "swim";
         case BREAK -> "break";
         case BRIDGE -> "bridge";
         default -> "walk";
      };
   }

   /** The cells as block positions, for drawing. */
   public List<class_2338> positions() {
      java.util.ArrayList<class_2338> out = new java.util.ArrayList<>(this.cells.length);
      for (long cell : this.cells) {
         out.add(class_2338.method_10092(cell));
      }
      return out;
   }
}
