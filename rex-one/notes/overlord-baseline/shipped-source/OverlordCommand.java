package rex;

import baritone.api.command.ICommand;
import baritone.api.command.argument.IArgConsumer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Real Baritone registration supplies help and chat completions. */
public final class OverlordCommand implements ICommand {
 public List<String> getNames(){return List.of("overlord","overloard");}
 public String getShortDesc(){return "REX survival progression: start, stop, status";}
 public List<String> getLongDesc(){return List.of("#overlord start | stop | status", "The pathfinder is bundled with REX46.");}
 public void execute(String label,IArgConsumer args){
  args.requireMax(1);
  RexOverlord.command("#overlord "+(args.hasAny()?args.getString():"status"));
 }
 public Stream<String> tabComplete(String label,IArgConsumer args){
  if(!args.hasAtMostOne())return Stream.empty();
  String prefix=args.hasAny()?args.peekString().toLowerCase(Locale.ROOT):"";
  return Stream.of("start","stop","status").filter(s->s.startsWith(prefix));
 }
}
