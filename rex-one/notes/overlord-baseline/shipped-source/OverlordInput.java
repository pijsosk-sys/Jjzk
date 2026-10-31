package rex;
import java.util.Locale;
/** Shared parser for menu-independent local chat routing. */
public final class OverlordInput {
 public record Command(String action){}
 public static Command parse(String text){
  if(text==null)return null;
  String cleaned=text.replace('\u00a0',' ').replace('\u200b', ' ').replace("\ufeff","").strip();
  if(cleaned.isEmpty())return null;
  String[] words=cleaned.split("\\s+");String prefix=words[0].toLowerCase(Locale.ROOT);int action=1;
  if(prefix.equals(".rex")&&words.length>1&&words[1].equalsIgnoreCase("overlord"))action=2;
  else if(!prefix.equals("#overlord")&&!prefix.equals("#overloard")&&!prefix.equals(".overloard")&&!prefix.equals(".overlord")&&!prefix.equals("/rexoverlord"))return null;
  return new Command(words.length>action?words[action].toLowerCase(Locale.ROOT):"status");
 }
}
