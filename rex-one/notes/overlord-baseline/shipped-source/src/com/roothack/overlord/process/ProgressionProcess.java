package com.roothack.overlord.process;
import baritone.api.IBaritone;import baritone.api.process.*;
import com.roothack.overlord.runtime.*;import com.roothack.overlord.stages.Stage;
/** Retains the uploaded command/entrypoint ABI, with inventory-driven progression. */
public final class ProgressionProcess implements IBaritoneProcess{
 private IBaritone baritone;private final Autopilot brain=new Autopilot();private final com.roothack.overlord.combat.MobDefense combat=new com.roothack.overlord.combat.MobDefense();private String combatError="";
 public void attach(IBaritone b){baritone=b;brain.attach(b);try{Object settings=Access.call(Access.type("baritone.api.BaritoneAPI"),"getSettings");for(String name:new String[]{"allowSprint","smoothLook","antiCheatCompatibility"}){try{Object setting=Access.field(settings,name);setting.getClass().getField("value").set(setting,true);}catch(Exception ignored){}}}catch(Exception e){Access.message("Attachment: "+e.getMessage());}}
 public void start(){if(baritone!=null){combat.stop();combatError="";brain.start();}}public void stop(){combat.stop();brain.stop();}
 public boolean isEnabled(){return brain.running;}public boolean isAttached(){return baritone!=null;}public boolean isCamouflage(){return false;}
 public Stage currentStage(){return brain.stage;}
 public boolean isActive(){if(!brain.running||baritone==null)return false;try{return Access.player()!=null;}catch(Exception e){return false;}}
 public boolean defendTick(){if(!brain.running)return false;try{if(Access.player()==null||Access.world()==null){stop();return false;}boolean defending=combat.tick();if(defending)brain.suspendForDefense();return defending;}catch(Exception|LinkageError e){String reason=e.toString();if(!reason.equals(combatError)){combatError=reason;Access.message("Mob defence failed: "+reason);}combat.stop();return false;}}
 public PathingCommand onTick(boolean failed,boolean safe){try{Object defence=combat.pathingCommand();if(defence!=null)return (PathingCommand)defence;return (PathingCommand)brain.tick(failed,safe);}catch(Exception|LinkageError e){brain.stop();brain.status="Stopped: "+e.getMessage();Access.message(brain.status);try{return (PathingCommand)Access.command("CANCEL_AND_SET_GOAL",null);}catch(Exception fatal){throw new IllegalStateException(fatal);}}}
 public void onLostControl(){}public double priority(){return 10;}public boolean isTemporary(){return false;}
 public String displayName0(){return "Overlord "+brain.stage+": "+brain.status+" | "+(combatError.isEmpty()?combat.status:combatError);}
}
