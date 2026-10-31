package com.roothack.overlord.runtime;
import java.util.*;
public final class InventoryOps{
 private record Click(int slot,int button,String action){}
 private final ArrayDeque<Click> queue=new ArrayDeque<>();private Object locked;private long next;private int wait;private String output="",mode="";public String status="";
 public boolean busy(){return !queue.isEmpty()||!output.isEmpty();}
 public void clear(){queue.clear();output="";locked=null;}
 public Object handler()throws Exception{return Access.field(Access.player(),"field_7512");}
 public Object stack(Object h,int slot)throws Exception{return Access.call(Access.call(h,"method_7611",slot),"method_7677");}
 private boolean empty(Object s)throws Exception{return (Boolean)Access.call(s,"method_7960");}
 public int source(Object h,String id)throws Exception{List<?> slots=(List<?>)Access.field(h,"field_7761");for(int i=0;i<slots.size();i++){Object slot=slots.get(i);if(Access.field(slot,"field_7871")!=Access.inv())continue;if(Access.id(Access.call(slot,"method_7677")).equals(id))return i;}return -1;}
 public void click(Object h,int slot,int button,String action)throws Exception{Access.call(Access.field(Access.mc(),"field_1761"),"method_2906",((Number)Access.field(h,"field_7763")).intValue(),slot,button,Access.field(Access.type("net.minecraft.class_1713"),action),Access.player());}
 public boolean pump(long tick)throws Exception{
  if(!busy())return false;if(handler()!=locked){clear();throw new IllegalStateException("Workstation closed during inventory transaction");}if(tick<next)return true;next=tick+2;
  if(!queue.isEmpty()){var c=queue.removeFirst();click(locked,c.slot,c.button,c.action);return true;}
  int slot=mode.equals("smith")?3:0;Object s=stack(locked,slot);
  boolean ready=Access.id(s).equals(output)||output.equals("planks")&&Access.id(s).endsWith("_planks");
  if(mode.equals("enchant")){
   if(ready&&(Boolean)Access.call(s,"method_7942")){click(locked,0,0,"field_7794");clear();return true;}
   int[] levels=(int[])Access.field(locked,"field_7808");int xp=((Number)Access.field(Access.player(),"field_7520")).intValue();
   if(levels[2]>=30&&xp>=levels[2]&&Access.num(stack(locked,1),"method_7947")>=3&&wait%10==0)Access.call(Access.field(Access.mc(),"field_1761"),"method_2900",((Number)Access.field(locked,"field_7763")).intValue(),2);
  }else if(ready){click(locked,slot,0,"field_7794");clear();return true;}
  if(++wait>100){clear();throw new IllegalStateException("Server did not return the expected crafting/enchanting result");}return true;
 }
 private boolean clean(Object h,int first,int last)throws Exception{if(!empty(Access.call(h,"method_34255")))throw new IllegalStateException("Clear the cursor stack before automation");for(int i=first;i<=last;i++)if(!empty(stack(h,i))){click(h,i,0,"field_7794");return false;}return true;}
 private boolean prepare(Object h,Map<Integer,String> targets,String expected,String kind)throws Exception{
  Map<String,List<Integer>> pools=new HashMap<>();Map<Integer,Integer> left=new HashMap<>();List<?> slots=(List<?>)Access.field(h,"field_7761");
  for(int i=0;i<slots.size();i++){Object slot=slots.get(i);if(Access.field(slot,"field_7871")!=Access.inv())continue;Object s=Access.call(slot,"method_7677");pools.computeIfAbsent(Access.id(s),k->new ArrayList<>()).add(i);left.put(i,Access.num(s,"method_7947"));}
  ArrayDeque<Click> planned=new ArrayDeque<>();
  for(var e:targets.entrySet()){int from=-1;for(int i:pools.getOrDefault(e.getValue(),List.of()))if(left.get(i)>0){from=i;left.put(i,left.get(i)-1);break;}if(from<0)return false;planned.add(new Click(from,0,"field_7790"));planned.add(new Click(e.getKey(),1,"field_7790"));planned.add(new Click(from,0,"field_7790"));}
  locked=h;queue.addAll(planned);output=expected;mode=kind;wait=0;return true;
 }
 public boolean craft(Recipes.Recipe recipe,Map<String,Integer> stock)throws Exception{Object h=handler();boolean table=Access.type("net.minecraft.class_1714").isInstance(h),small=Access.type("net.minecraft.class_1723").isInstance(h);if(!table&&!small||!table&&!recipe.small())return false;if(!clean(h,1,table?9:4))return false;Map<Integer,String> cells=new LinkedHashMap<>();Map<String,Integer> available=new HashMap<>(stock);for(var e:recipe.cells().entrySet()){String id=Recipes.actual(available,e.getValue());if(available.getOrDefault(id,0)<=0)return false;cells.put(Recipes.gridSlot(e.getKey(),table),id);available.computeIfPresent(id,(k,v)->v<=1?null:v-1);}return prepare(h,cells,recipe.output(),"craft");}
 public boolean smith(String base)throws Exception{Object h=handler();if(!Access.type("net.minecraft.class_4862").isInstance(h)||!clean(h,0,2))return false;return prepare(h,Map.of(0,"netherite_upgrade_smithing_template",1,base,2,"netherite_ingot"),base.replace("diamond_","netherite_"),"smith");}
 public boolean enchant(String id)throws Exception{Object h=handler();if(!Access.type("net.minecraft.class_1718").isInstance(h)||!clean(h,0,1))return false;Map<Integer,String> cells=new LinkedHashMap<>();cells.put(0,id);int lapis=source(h,"lapis_lazuli");if(lapis<0||Access.num(stack(h,lapis),"method_7947")<3)return false;
  if(!prepare(h,cells,id,"enchant"))return false;queue.add(new Click(lapis,0,"field_7790"));for(int i=0;i<3;i++)queue.add(new Click(1,1,"field_7790"));queue.add(new Click(lapis,0,"field_7790"));return true;}
 public boolean smelt(String input)throws Exception{Object h=handler();if(!Access.type("net.minecraft.class_1720").isInstance(h))return false;if(!empty(stack(h,2))){click(h,2,0,"field_7794");return true;}int source=source(h,input);if(empty(stack(h,0))&&source>=0){click(h,source,0,"field_7794");return true;}if(empty(stack(h,1))){source=source(h,"coal");if(source<0)source=source(h,"charcoal");if(source>=0){click(h,source,0,"field_7794");return true;}}return true;}
 public boolean lootTemplate()throws Exception{Object h=handler();if(!Access.type("net.minecraft.class_1707").isInstance(h))return false;List<?> slots=(List<?>)Access.field(h,"field_7761");for(int i=0;i<slots.size();i++){Object slot=slots.get(i);if(Access.field(slot,"field_7871")==Access.inv())continue;String id=Access.id(Access.call(slot,"method_7677"));if(Set.of("netherite_upgrade_smithing_template","diamond","gold_ingot","netherite_ingot","obsidian").contains(id)){click(h,i,0,"field_7794");return true;}}return false;}
}
