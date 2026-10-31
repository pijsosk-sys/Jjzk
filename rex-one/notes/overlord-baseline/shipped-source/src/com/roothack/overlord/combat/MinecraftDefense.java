package com.roothack.overlord.combat;
import com.roothack.overlord.runtime.Access;
import java.util.*;
import rex.*;
/** Minecraft boundary: visible hostile mobs only, normal look and interaction APIs. */
public final class MinecraftDefense implements MobDefense.View {
 private Object equippedPlayer;private int original=-1,managed=-1;
 private Object player()throws Exception{return Access.player();}
 public Object world()throws Exception{return Access.world();}
 public boolean ready()throws Exception{Object p=player();return p!=null&&world()!=null&&Access.field(Access.mc(),"field_1761")!=null&&Access.field(Access.mc(),"field_1755")==null&&(Boolean)Access.call(p,"method_5805");}
 public List<?> nearby(double radius)throws Exception{return (List<?>)Access.call(world(),"method_8333",player(),Access.call(Access.call(player(),"method_5829"),"method_1014",radius),(java.util.function.Predicate<Object>)e->{try{return valid(e);}catch(Exception failure){return false;}});}
 public boolean valid(Object e)throws Exception{return e!=null&&Access.type("net.minecraft.class_1569").isInstance(e)&&(Boolean)Access.call(e,"method_5805");}
 public boolean visible(Object e)throws Exception{return (Boolean)Access.call(player(),"method_6057",e);}
 public double distance(Object e)throws Exception{
  Object eye=Access.call(player(),"method_33571"),box=Access.call(e,"method_5829");double x=num(eye,"field_1352"),y=num(eye,"field_1351"),z=num(eye,"field_1350");
  return CombatTools.distance(CombatTools.closest(x,y,z,num(box,"field_1323"),num(box,"field_1322"),num(box,"field_1321"),num(box,"field_1320"),num(box,"field_1325"),num(box,"field_1324")),x,y,z);
 }
 private double num(Object object,String field)throws Exception{return ((Number)Access.field(object,field)).doubleValue();}
 public double reach()throws Exception{return Reach.vanillaEntityRange(player());}
 public double cooldown()throws Exception{return Access.real(player(),"method_7261",0.0f);}
 public boolean equip()throws Exception{
  Object p=player(),inv=Access.call(p,"method_31548");int selected=Access.num(inv,"method_67532");List<CombatTools.Weapon> weapons=new ArrayList<>();
  for(int i=0;i<9;i++){Object stack=Access.call(inv,"method_5438",i);String id="minecraft:"+Access.id(stack);if(CombatTools.weapon(id))weapons.add(new CombatTools.Weapon(i,id));}
  var best=CombatTools.chooseWeapon(weapons,CombatTools.WeaponPreference.SMART,false,selected);
  if(best.isEmpty()||best.get().slot()==selected)return false;
  if(equippedPlayer!=p||original<0){equippedPlayer=p;original=selected;}managed=best.get().slot();Access.call(inv,"method_61496",managed);return true;
 }
 public boolean aim(Object e,double range)throws Exception{
  Object p=player(),eye=Access.call(p,"method_33571"),box=Access.call(e,"method_5829");
  double x=(num(box,"field_1323")+num(box,"field_1320"))*.5,y=(num(box,"field_1322")+num(box,"field_1325"))*.5,z=(num(box,"field_1321")+num(box,"field_1324"))*.5;
  double dx=x-num(eye,"field_1352"),dy=y-num(eye,"field_1351"),dz=z-num(eye,"field_1350");
  Access.call(p,"method_36456",(float)(Math.toDegrees(Math.atan2(dz,dx))-90));Access.call(p,"method_36457",(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));
  InteractionFaces.syncLook(Access.mc(),p);return CombatAccess.rayEntity(p,e,range);
 }
 public void attack(Object e)throws Exception{Object p=player();Access.call(Access.field(Access.mc(),"field_1761"),"method_2918",p,e);Access.call(p,"method_6104",Access.field(Access.type("net.minecraft.class_1268"),"field_5808"));}
 public Object approach(Object e)throws Exception{
  double reach=Math.max(0,Math.min(3,reach()));if(distance(e)<=reach)return Access.command("REQUEST_PAUSE",null);
  return Access.command("SET_GOAL_AND_PATH",Access.make("baritone.api.pathing.goals.GoalNear",(int)Math.floor(Access.real(e,"method_23317")),(int)Math.floor(Access.real(e,"method_23318")),(int)Math.floor(Access.real(e,"method_23321")),1));
 }
 public String name(Object e){try{return String.valueOf(Access.call(Access.call(e,"method_5477"),"getString"));}catch(Exception ignored){return e.getClass().getSimpleName();}}
 public void release()throws Exception{
  Object p=player();if(p!=null&&p==equippedPlayer&&original>=0&&Access.num(Access.call(p,"method_31548"),"method_67532")==managed)Access.call(Access.call(p,"method_31548"),"method_61496",original);
  equippedPlayer=null;original=managed=-1;
 }
}
