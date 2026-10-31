package com.roothack.overlord.runtime;
import java.util.*;
import com.roothack.overlord.stages.Stage;
public final class Autopilot{
 public final InventoryOps ops=new InventoryOps();public final Structures structures=new Structures(this);
 public String status="Stopped";public Stage stage=Stage.GATHER_WOOD;public boolean running,done;public int delay;
 private Object baritone,command,useKey;private long ticks,lastChange;private Map<String,Integer> stock=Map.of(),previous=Map.of();private String mineKey="",lastError="";private int retries;private Structures.P exploreTarget;private int explorations;private Object world;
 public void attach(Object baritone){this.baritone=baritone;}
 public void start(){release();delay=0;exploreTarget=null;running=true;done=false;lastChange=ticks;retries=0;lastError="";ops.clear();status="Planning from inventory";}
 public void stop(){running=false;release();ops.clear();try{cancelMine();Access.call(Access.call(baritone,"getCustomGoalProcess"),"onLostControl");}catch(Exception ignored){}status="Stopped";}
 public void suspendForDefense(){release();}
 private void release(){if(useKey!=null)try{Access.call(useKey,"method_23481",false);}catch(Exception ignored){}useKey=null;}
 private void cancelMine()throws Exception{if(baritone!=null)Access.call(Access.call(baritone,"getMineProcess"),"cancel");mineKey="";}
 public void go(int x,int y,int z)throws Exception{cancelMine();Access.close();command=Access.command("SET_GOAL_AND_PATH",Access.make("baritone.api.pathing.goals.GoalBlock",x,y,z));}
 public void near(int x,int y,int z)throws Exception{cancelMine();Access.close();command=Access.command("SET_GOAL_AND_PATH",Access.make("baritone.api.pathing.goals.GoalNear",x,y,z,2));}
 public void explore()throws Exception{var here=Structures.feet();if(exploreTarget==null||Math.hypot(here.x()-exploreTarget.x(),here.z()-exploreTarget.z())<4){double angle=explorations++*2.399963229728653;int radius=32+(int)Math.sqrt(explorations)*24;exploreTarget=here.add((int)(Math.cos(angle)*radius),0,(int)(Math.sin(angle)*radius));}go(exploreTarget.x(),exploreTarget.y(),exploreTarget.z());}
 private void mine(int target,Integer y,String...names)throws Exception{Access.close();Object mine=Access.call(baritone,"getMineProcess");String key=Arrays.toString(names);boolean active=key.equals(mineKey)&&Boolean.TRUE.equals(Access.call(mine,"isActive"));if(!active&&y!=null&&Math.abs(Structures.feet().y()-y)>3){cancelMine();command=Access.command("SET_GOAL_AND_PATH",Access.make("baritone.api.pathing.goals.GoalYLevel",y));status="Reaching mining layer "+y;return;}if(!active){Access.call(mine,"mineByName",target,names);mineKey=key;}status="Gathering "+Arrays.toString(names);}
 private int count(String id){return Recipes.count(stock,id);}private boolean havePick(String tier){return count(tier+"_pickaxe")>0;}
 private boolean overworld()throws Exception{return structures.portal(false);}private boolean nether()throws Exception{return structures.portal(true);}
 public Object tick(boolean calcFailed,boolean safe)throws Exception{
  ticks++;command=Access.command("DEFER",null);
  if(!running)return Access.command("CANCEL_AND_SET_GOAL",null);
  Object player=Access.player(),w=Access.world();if(player==null||w==null){release();return Access.command("REQUEST_PAUSE",null);}
  if(world!=w){world=w;ops.clear();lastChange=ticks;structures.rememberPortal();}
  if(!(Boolean)Access.call(player,"method_5805")){stop();status="Stopped after death; #overlord start replans from recovered inventory";return Access.command("CANCEL_AND_SET_GOAL",null);}
  if(calcFailed){cancelMine();exploreTarget=null;if(++retries>3){stop();status="Path repeatedly failed; move to accessible terrain and #overlord start";Access.message(status);return Access.command("REQUEST_PAUSE",null);}}
  if(ops.pump(ticks)){status="Waiting for workstation result";return Access.command("REQUEST_PAUSE",null);}
  if(delay>0){delay--;return Access.command("REQUEST_PAUSE",null);}
  stock=Access.counts();if(!stock.equals(previous)){previous=new HashMap<>(stock);lastChange=ticks;retries=0;}
  if(ticks-lastChange>2400){cancelMine();lastChange=ticks;exploreTarget=null;if(++retries>3){stop();status="No progress after three recovery attempts; #overlord start resumes from inventory";Access.message(status);return Access.command("REQUEST_PAUSE",null);}explore();return command;}
  if(food())return Access.command("REQUEST_PAUSE",null);
  if(!havePick("netherite")&&!havePick("diamond")&&!havePick("iron")&&!havePick("stone")){stage=Stage.CRAFT_BASICS;if(!ensure("wooden_pickaxe",1,0))return command;}
  if(!havePick("netherite")&&!havePick("diamond")&&!havePick("iron")){stage=Stage.CRAFT_STONE;if(!ensure("stone_pickaxe",1,0))return command;}
  if(!havePick("netherite")&&!havePick("diamond")){stage=Stage.CRAFT_IRON;if(!ensure("iron_pickaxe",1,0)||!ensure("iron_sword",1,0))return command;for(String g:List.of("helmet","chestplate","leggings","boots"))if(count("diamond_"+g)==0&&count("netherite_"+g)==0&&!ensure("iron_"+g,1,0))return command;}
  stage=Stage.CRAFT_DIAMOND;for(String g:Recipes.GEAR)if(count("netherite_"+g)==0&&!ensure("diamond_"+g,1,0))return command;
  int upgrades=(int)Recipes.GEAR.stream().filter(g->count("netherite_"+g)==0).count();
  if(upgrades>0){stage=Stage.CRAFT_NETHERITE;
   if(!ensure("netherite_upgrade_smithing_template",upgrades,0)||!ensure("netherite_ingot",upgrades,0)||!ensure("smithing_table",1,0))return command;
   if(!structures.station("smithing_table","class_4862"))return command;
   for(String g:Recipes.GEAR)if(count("netherite_"+g)==0){ops.smith("diamond_"+g);status="Upgrading "+g;return Access.command("REQUEST_PAUSE",null);}
  }
  Set<String> enchanted=new HashSet<>();for(String g:Recipes.GEAR)if(Access.enchanted("netherite_"+g))enchanted.add("netherite_"+g);
  if(!Recipes.complete(stock,enchanted)){
   stage=Stage.SETUP_ENCHANTING;if(!overworld()||!ensure("lapis_lazuli",3,0))return command;
   int xp=((Number)Access.field(player,"field_7520")).intValue();if(xp<30){stage=Stage.GATHER_XP;mine(count("coal")+16,48,"coal_ore");status="Collecting XP for the next level-30 enchantment";return command;}
   if(Structures.feet().y()<60){cancelMine();command=Access.command("SET_GOAL_AND_PATH",Access.make("baritone.api.pathing.goals.GoalYLevel",64));status="Reaching the surface for the enchanting area";return command;}
   if(!structures.enchanting())return command;stage=Stage.ENCHANT_GEAR;
   for(String g:Recipes.GEAR){String id="netherite_"+g;if(!enchanted.contains(id)){ops.enchant(id);status="Enchanting "+id;return Access.command("REQUEST_PAUSE",null);}}
  }
  stage=Stage.DONE;equip();done=true;running=false;cancelMine();Access.message("Complete: enchanted netherite armor, sword and pickaxe verified in inventory/equipment.");return Access.command("CANCEL_AND_SET_GOAL",null);
 }
 public boolean ensure(String id,int target,int depth)throws Exception{
  if(count(id)>=target)return true;if(Set.of("crafting_table","furnace","smithing_table").contains(id)&&structures.available(id))return true;if(depth>24)throw new IllegalStateException("Ingredient cycle: "+id);status="Need "+target+" "+id;
  if(id.equals("netherite_upgrade_smithing_template")&&count(id)==0){if(!nether())return false;stage=Stage.GATHER_ANCIENT_DEBRIS;structures.template();return false;}
  Recipes.Recipe recipe=Recipes.get(id);
  if(recipe!=null){Map<String,Integer> needs=recipe.needs();for(var e:needs.entrySet())if(!ensure(e.getKey(),e.getValue(),depth+1))return false;
   if(!recipe.small()){if(!ensure("crafting_table",1,depth+1)||!structures.station("crafting_table","class_1714"))return false;}else if(!Access.type("net.minecraft.class_1714").isInstance(ops.handler())&&!Access.type("net.minecraft.class_1723").isInstance(ops.handler())){Access.close();return false;}
   cancelMine();ops.craft(recipe,stock);delay=2;return false;
  }
  String raw=switch(id){case "iron_ingot"->"raw_iron";case "gold_ingot"->"raw_gold";case "netherite_scrap"->"ancient_debris";case "cooked_beef"->"beef";default->null;};
  if(raw!=null){
   Object h=ops.handler();if(Access.type("net.minecraft.class_1720").isInstance(h)&&(!((Boolean)Access.call(ops.stack(h,2),"method_7960"))||Access.id(ops.stack(h,0)).equals(raw))){cancelMine();ops.smelt(raw);delay=4;return false;}
   int missing=target-count(id);if(!ensure(raw,missing,depth+1)||!ensure("furnace",1,depth+1)||!ensure("coal",Math.max(1,(missing+7)/8),depth+1))return false;if(!structures.station("furnace","class_1720"))return false;cancelMine();ops.smelt(raw);delay=4;return false;}
  switch(id){
   case "logs"->{if(!overworld())return false;stage=Stage.GATHER_WOOD;mine(Math.max(target,8),null,"oak_log","birch_log","spruce_log","jungle_log","acacia_log","dark_oak_log","cherry_log","mangrove_log");}
   case "cobblestone"->{if(!havePick("stone")&&!havePick("iron")&&!havePick("diamond")&&!havePick("netherite")&&!ensure("wooden_pickaxe",1,depth+1))return false;stage=Stage.GATHER_STONE;mine(Math.max(target,32),null,"stone","cobblestone");}
   case "coal"->{if(!havePick("stone")&&!havePick("iron")&&!havePick("diamond")&&!havePick("netherite")&&!ensure("wooden_pickaxe",1,depth+1))return false;if(!overworld())return false;mine(Math.max(target,16),48,"coal_ore");}
   case "raw_iron"->{if(!havePick("iron")&&!havePick("diamond")&&!havePick("netherite")&&!ensure("stone_pickaxe",1,depth+1))return false;if(!overworld())return false;stage=Stage.GATHER_IRON;mine(Math.max(target,8),16,"iron_ore","deepslate_iron_ore");}
   case "diamond","raw_gold","lapis_lazuli"->{if(!havePick("diamond")&&!havePick("netherite")&&!ensure("iron_pickaxe",1,depth+1))return false;if(!overworld())return false;String[] names=id.equals("diamond")?new String[]{"diamond_ore","deepslate_diamond_ore"}:id.equals("raw_gold")?new String[]{"gold_ore","deepslate_gold_ore"}:new String[]{"lapis_ore","deepslate_lapis_ore"};stage=Stage.GATHER_DIAMONDS;mine(target,id.equals("diamond")?-54:-16,names);}
   case "obsidian"->{if(!havePick("netherite")&&!ensure("diamond_pickaxe",1,depth+1))return false;stage=Stage.BUILD_PORTAL;mine(target,null,"obsidian");}
   case "ancient_debris"->{if(!havePick("netherite")&&!ensure("diamond_pickaxe",1,depth+1))return false;if(!nether())return false;stage=Stage.GATHER_ANCIENT_DEBRIS;mine(target,15,"ancient_debris");}
   case "netherrack"->{if(!nether())return false;mine(Math.max(target,8),null,"netherrack");}
   case "flint"->{if(!overworld())return false;mine(target,null,"gravel");}
   case "sugar_cane"->{if(!overworld())return false;mine(target,null,"sugar_cane");}
   case "leather","beef"->{if(!overworld())return false;hunt();}
   default->throw new IllegalStateException("No acquisition route for "+id);
  }return false;
 }
 private boolean food()throws Exception{Object food=Access.call(Access.player(),"method_7344");int hunger=Access.num(food,"method_7586");if(hunger>=18){release();return false;}
  for(String id:List.of("cooked_beef","cooked_porkchop","cooked_chicken","bread","baked_potato","beef","porkchop","apple"))if(count(id)>0){cancelMine();if(!structures.select(id))return true;useKey=Access.field(Access.field(Access.mc(),"field_1690"),"field_1904");Access.call(useKey,"method_23481",true);status="Eating before continuing";return true;}
  release();if(hunger<=6){status="Gathering food";if(count("beef")>0){ensure("cooked_beef",1,0);}else hunt();return true;}return false;
 }
 private void hunt()throws Exception{Object p=Access.player(),box=Access.call(Access.call(p,"method_5829"),"method_1014",48.0);List<?> nearby=(List<?>)Access.call(Access.world(),"method_8333",p,box,(java.util.function.Predicate<Object>)e->{try{return Access.type("net.minecraft.class_1430").isInstance(e)&&(Boolean)Access.call(e,"method_5805");}catch(Exception ex){return false;}});Object target=null;double nearest=Double.MAX_VALUE;for(Object e:nearby){double d=Access.real(p,"method_5858",e);if(d<nearest){nearest=d;target=e;}}if(target==null){explore();status="Exploring for cows and leather";return;}if(nearest>6.25){near((int)Access.real(target,"method_23317"),(int)Access.real(target,"method_23318"),(int)Access.real(target,"method_23321"));return;}cancelMine();String sword=count("netherite_sword")>0?"netherite_sword":count("diamond_sword")>0?"diamond_sword":count("iron_sword")>0?"iron_sword":null;if(sword!=null)structures.select(sword);if(Access.real(p,"method_7261",0.0f)>=.95){Access.call(Access.field(Access.mc(),"field_1761"),"method_2918",p,target);Access.call(p,"method_6104",Access.field(Access.type("net.minecraft.class_1268"),"field_5808"));delay=10;}}
 private void equip()throws Exception{Access.close();Object h=Access.field(Access.player(),"field_7498");for(int i=0;i<4;i++){String id="netherite_"+Recipes.GEAR.get(i);int slot=5+i;Object worn=ops.stack(h,slot);if(!Access.id(worn).equals(id)){if(!(Boolean)Access.call(worn,"method_7960"))ops.click(h,slot,0,"field_7794");int source=ops.source(h,id);if(source>=0)ops.click(h,source,0,"field_7794");}}structures.select("netherite_sword");}
}
