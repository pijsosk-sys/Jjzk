package com.roothack.overlord.combat;
import java.util.*;
/** Tick-driven defence with one movement owner and native attack cooldown. */
public final class MobDefense {
 public interface View {
  boolean ready()throws Exception;Object world()throws Exception;List<?> nearby(double radius)throws Exception;
  boolean valid(Object target)throws Exception;boolean visible(Object target)throws Exception;double distance(Object target)throws Exception;
  double reach()throws Exception;double cooldown()throws Exception;boolean equip()throws Exception;
  boolean aim(Object target,double reach)throws Exception;void attack(Object target)throws Exception;
  Object approach(Object target)throws Exception;String name(Object target)throws Exception;void release()throws Exception;
 }
 private final View view;private Object world,target;private long tick,lastScan=-100,lastAttack=-100,lastEquip=-100;private boolean active;private int attacks;public String status="Mob defence ready";
 public MobDefense(){this(new MinecraftDefense());}public MobDefense(View view){this.view=Objects.requireNonNull(view);}
 public boolean tick()throws Exception{
  tick++;
  if(!view.ready()){active=false;target=null;status="Mob defence idle (world/menu/player)";return false;}
  Object currentWorld=view.world();if(world!=currentWorld){view.release();world=currentWorld;target=null;lastScan=-100;lastAttack=-100;}
  if(target!=null&&(!view.valid(target)||!view.visible(target)||view.distance(target)>8))target=null;
  if(tick-lastScan>=3){
   lastScan=tick;Object nearest=null;double best=8;
   for(Object candidate:view.nearby(8))if(view.valid(candidate)&&view.visible(candidate)){double d=view.distance(candidate);if(Double.isFinite(d)&&d>=0&&d<best){best=d;nearest=candidate;}}
   if(target==null||nearest!=null&&view.distance(target)>best+.75)target=nearest;
  }
  active=target!=null;
  if(!active){view.release();status="No visible hostile mobs nearby";return false;}
  String name=view.name(target);double nativeReach=view.reach();double reach=Double.isFinite(nativeReach)&&nativeReach>0?Math.min(3,nativeReach):0;if(reach==0){status="No valid melee interaction range";return true;}
  if(view.distance(target)>reach){status="Approaching "+name;return true;}
  if(view.equip())lastEquip=tick;
  if(!view.aim(target,reach)){status="Aiming at "+name;return true;}
  if(tick-lastEquip<2||tick-lastAttack<2||view.cooldown()<.95){status="Defending against "+name+" (weapon cooldown)";return true;}
  // Recheck at the actual attack point, rather than trusting a cached scan.
  if(view.valid(target)&&view.visible(target)&&view.distance(target)<=reach){view.attack(target);lastAttack=tick;attacks++;status="Fighting "+name+" | attacks="+attacks;}
  return true;
 }
 public Object pathingCommand()throws Exception{return active&&target!=null&&view.valid(target)?view.approach(target):null;}
 public void stop(){try{view.release();}catch(Exception ignored){}active=false;target=world=null;lastScan=lastAttack=lastEquip=-100;status="Mob defence stopped";}
}
