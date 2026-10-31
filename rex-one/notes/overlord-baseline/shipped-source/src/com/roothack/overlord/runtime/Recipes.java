package com.roothack.overlord.runtime;
import java.util.*;
public final class Recipes{
 public static final List<String> GEAR=List.of("helmet","chestplate","leggings","boots","sword","pickaxe");
 public record Recipe(String output,int count,Map<Integer,String> cells){public Map<String,Integer> needs(){Map<String,Integer> out=new LinkedHashMap<>();cells.values().forEach(i->out.merge(i,1,Integer::sum));return out;}public boolean small(){return cells.keySet().stream().allMatch(i->i%3<2&&i/3<2);}}
 private static final Map<String,Recipe> ALL=new LinkedHashMap<>();
 private static void add(String out,int count,Object...pairs){Map<Integer,String> cells=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)cells.put((Integer)pairs[i],(String)pairs[i+1]);ALL.put(out,new Recipe(out,count,Collections.unmodifiableMap(cells)));}
 private static void shape(String out,int count,String material,int...cells){Object[] p=new Object[cells.length*2];for(int i=0;i<cells.length;i++){p[i*2]=cells[i];p[i*2+1]=material;}add(out,count,p);}
 static{
  shape("planks",4,"logs",0);shape("stick",4,"planks",0,3);shape("crafting_table",1,"planks",0,1,3,4);shape("furnace",1,"cobblestone",0,1,2,3,5,6,7,8);
  for(String tier:List.of("wooden","stone","iron","diamond")){String m=switch(tier){case "wooden"->"planks";case "stone"->"cobblestone";case "iron"->"iron_ingot";default->"diamond";};add(tier+"_pickaxe",1,0,m,1,m,2,m,4,"stick",7,"stick");add(tier+"_sword",1,1,m,4,m,7,"stick");if(tier.equals("iron")||tier.equals("diamond")){shape(tier+"_helmet",1,m,0,1,2,3,5);shape(tier+"_chestplate",1,m,0,2,3,4,5,6,7,8);shape(tier+"_leggings",1,m,0,1,2,3,5,6,8);shape(tier+"_boots",1,m,3,5,6,8);}}
  add("bucket",1,0,"iron_ingot",2,"iron_ingot",4,"iron_ingot");add("flint_and_steel",1,0,"iron_ingot",4,"flint");add("smithing_table",1,0,"iron_ingot",1,"iron_ingot",3,"planks",4,"planks",6,"planks",7,"planks");
  shape("paper",3,"sugar_cane",0,1,2);add("book",1,0,"paper",1,"paper",3,"paper",4,"leather");add("bookshelf",1,0,"planks",1,"planks",2,"planks",3,"book",4,"book",5,"book",6,"planks",7,"planks",8,"planks");
  add("enchanting_table",1,1,"book",3,"diamond",4,"obsidian",5,"diamond",6,"obsidian",7,"obsidian",8,"obsidian");
  add("netherite_ingot",1,0,"netherite_scrap",1,"netherite_scrap",2,"netherite_scrap",3,"netherite_scrap",4,"gold_ingot",5,"gold_ingot",6,"gold_ingot",7,"gold_ingot");
  add("netherite_upgrade_smithing_template",2,0,"diamond",1,"netherite_upgrade_smithing_template",2,"diamond",3,"diamond",4,"netherrack",5,"diamond",6,"diamond",7,"diamond",8,"diamond");
 }
 public static Recipe get(String id){return ALL.get(id);}public static Collection<Recipe> all(){return ALL.values();}
 public static int count(Map<String,Integer> stock,String id){if(id.equals("logs"))return stock.entrySet().stream().filter(e->e.getKey().endsWith("_log")||e.getKey().endsWith("_stem")).mapToInt(Map.Entry::getValue).sum();if(id.equals("planks"))return stock.entrySet().stream().filter(e->e.getKey().endsWith("_planks")).mapToInt(Map.Entry::getValue).sum();return stock.getOrDefault(id,0);}
 public static String actual(Map<String,Integer> stock,String id){if(!id.equals("logs")&&!id.equals("planks"))return id;return stock.keySet().stream().filter(k->id.equals("logs")?(k.endsWith("_log")||k.endsWith("_stem")):k.endsWith("_planks")).sorted().findFirst().orElse(id);}
 public static boolean complete(Map<String,Integer> stock,Set<String> enchanted){return GEAR.stream().allMatch(g->stock.getOrDefault("netherite_"+g,0)>0&&enchanted.contains("netherite_"+g));}
 public static int gridSlot(int index,boolean table){if(index<0||index>8||!table&&(index%3>=2||index/3>=2))throw new IllegalArgumentException("Recipe does not fit grid");return 1+(table?index:(index/3)*2+index%3);}
}
