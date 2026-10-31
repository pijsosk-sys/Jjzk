package rex;
import dev.krypton.core.module.Module;
import dev.krypton.core.module.Category;
import dev.krypton.core.event.*;
import dev.krypton.core.setting.BooleanSetting;
import com.roothack.overlord.runtime.Access;
/** Progression bridge to the Baritone component bundled in REX. */
public final class RexOverlord extends Module{
 public static RexOverlord instance;
 public final BooleanSetting hud=new BooleanSetting("Progression HUD",true);
 private Object process;private boolean registered;public String status="Ready; pathfinder bundled";
 public RexOverlord(){super("REX Overlord","Survival progression toward enchanted netherite armor, sword and pickaxe. Pathfinder included.",-1,Category.MISC);instance=this;addSettings(hud);}
 @Override public void onEnable(){super.onEnable();try{
  Class.forName("baritone.api.BaritoneAPI");
  var builder=dev.krypton.client.KryptonClient.instance.modules().get(AutoBuild.class);if(builder!=null&&builder.isEnabled())builder.setEnabled(false);
  if(BoatFly.instance!=null&&BoatFly.instance.isEnabled())BoatFly.instance.setEnabled(false);
  if(!registered){Object provider=Access.call(Access.type("baritone.api.BaritoneAPI"),"getProvider"),baritone=Access.call(provider,"getPrimaryBaritone");if(baritone==null)throw new IllegalStateException("Baritone is not ready");
   process=Access.make("com.roothack.overlord.process.ProgressionProcess");Access.call(process,"attach",baritone);Access.call(Access.call(baritone,"getPathingControlManager"),"registerProcess",process);registered=true;
  }
  Access.call(process,"start");status="Planning from inventory";Game.message("REX Overlord started. #overlord stop stops progression.");
 }catch(Exception|LinkageError e){status=e instanceof ClassNotFoundException?"Bundled pathfinder did not load; check the Fabric log":"Cannot start: "+e.getMessage();Game.message("REX Overlord: "+status);setEnabled(false);}}
 @Override public void onDisable(){if(process!=null)try{Access.call(process,"stop");}catch(Exception e){System.err.println("[REX] Overlord stop: "+e);}super.onDisable();}
 @EventHandler public void tick(ClientTickEvent event){try{if(process==null)return;Access.call(process,"defendTick");status=String.valueOf(Access.call(process,"displayName0"));if(!Boolean.TRUE.equals(Access.call(process,"isEnabled")))setEnabled(false);}catch(Exception|LinkageError e){status="Stopped: "+e.getMessage();Game.message("REX Overlord "+status);setEnabled(false);}}
 @EventHandler public void render(Render2DEvent event){if(!hud.get())return;try{if(Bridge.field(Bridge.client(),"field_1755")!=null)return;var p=dev.krypton.render.GuiPainter.wca;p.tne(event.sqx);try{p.fhp(new dev.krypton.util.Color(22,12,31,205),16,268,426,324,5);p.gh("REX Overlord",26,278,new dev.krypton.util.Color(255,171,69));String line=status;while(line.length()>1&&p.yuj(line)>380)line=line.substring(0,line.length()-1);p.gh(line,26,300,new dev.krypton.util.Color(231,216,239));}finally{p.edi();}}catch(Exception ignored){}}
 public static boolean command(String text){
  OverlordInput.Command input=OverlordInput.parse(text);if(input==null)return false;
  try{
   if(instance==null){var manager=dev.krypton.client.KryptonClient.instance.modules();RexOverlord existing=manager.get(RexOverlord.class);if(existing!=null)instance=existing;else{var register=manager.getClass().getDeclaredMethod("register",Module.class);register.setAccessible(true);register.invoke(manager,new RexOverlord());}}
   if(instance==null){Game.message("REX Overlord could not be registered");return true;}
   switch(input.action()){
    case "start","resume"->{if(instance.isEnabled())Game.message("REX Overlord is already running: "+instance.status);else instance.setEnabled(true);}
    case "stop","pause"->{instance.setEnabled(false);instance.status="Stopped";Game.message("REX Overlord stopped");}
    case "status"->{boolean available;try{Class.forName("baritone.api.BaritoneAPI",false,RexOverlord.class.getClassLoader());available=true;}catch(ClassNotFoundException|LinkageError e){available=false;}Game.message("REX Overlord: "+(instance.isEnabled()?"ON":"OFF")+" | Baritone "+(available?"available":"unavailable (bundled component failed to load)")+" | "+instance.status);}
    default->Game.message("REX Overlord commands: #overlord start | stop | status");
   }
  }catch(Exception|LinkageError failure){Game.message("REX Overlord command failed: "+failure.getClass().getSimpleName()+": "+failure.getMessage());}
  return true;
 }
}
