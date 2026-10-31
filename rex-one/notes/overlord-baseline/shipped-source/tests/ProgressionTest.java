import com.roothack.overlord.runtime.*;import java.util.*;
public class ProgressionTest{
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  int diamonds=0;for(String g:Recipes.GEAR)diamonds+=Recipes.get("diamond_"+g).needs().get("diamond");check(diamonds==29,"complete diamond gear needs 29, not 10");
  check(Recipes.get("netherite_ingot").needs().get("netherite_scrap")*6==24,"six upgrades need 24 scraps");check(Recipes.get("netherite_ingot").needs().get("gold_ingot")*6==24,"six upgrades need 24 gold ingots");
  check(Recipes.get("netherite_upgrade_smithing_template").count()==2,"template duplication yields two");check(Recipes.get("netherite_upgrade_smithing_template").needs().get("diamond")==7,"duplication consumes seven diamonds");check(Recipes.get("netherite_upgrade_smithing_template").needs().get("netherite_upgrade_smithing_template")==1,"seed template cannot be crafted from nothing");
  check(Recipes.gridSlot(0,false)==1&&Recipes.gridSlot(1,false)==2&&Recipes.gridSlot(3,false)==3&&Recipes.gridSlot(4,false)==4,"2x2 grid indices fixed");
  try{Recipes.gridSlot(7,false);throw new AssertionError("invalid small grid");}catch(IllegalArgumentException ok){checks++;}
  check(Recipes.get("book").small(),"books fit player grid");check(!Recipes.get("paper").small(),"paper needs table");check(Recipes.count(Map.of("oak_planks",2,"birch_planks",3),"planks")==5,"mixed plank inventory aggregated");
  Map<String,Integer> gear=new HashMap<>();Set<String> enchanted=new HashSet<>();for(String g:Recipes.GEAR)gear.put("netherite_"+g,1);
  check(!Recipes.complete(gear,enchanted),"unenchant gear is never marked done");enchanted.add("netherite_sword");check(!Recipes.complete(gear,enchanted),"one sword does not stand in for a full set");for(String g:Recipes.GEAR)enchanted.add("netherite_"+g);check(Recipes.complete(gear,enchanted),"six enchanted items complete progression");gear.remove("netherite_boots");check(!Recipes.complete(gear,enchanted),"lost boots invalidate completion");
  int books=Recipes.get("bookshelf").needs().get("book")*15;check(books==45,"15 shelves require 45 books");check(books*Recipes.get("book").needs().get("paper")==135,"paper budget");
  for(var recipe:Recipes.all()){check(recipe.count()>0,"positive yield");for(var e:recipe.cells().entrySet()){check(e.getKey()>=0&&e.getKey()<9,"valid recipe cell");check(e.getValue()!=null&&!e.getValue().isEmpty(),"real ingredient");}}
  System.out.println("Progression: "+checks+" checks passed");
 }
}
