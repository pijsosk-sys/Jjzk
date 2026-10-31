package com.roothack.overlord.runtime;
import java.util.*;
public final class Structures{
 public record P(int x,int y,int z){P add(int x,int y,int z){return new P(this.x+x,this.y+y,this.z+z);}}
 private final Autopilot brain;private final Map<String,P> stations=new HashMap<>();private P frame,enchant;private String frameDimension="";private final Map<String,P> portals=new HashMap<>();private final Set<String> visitedChests=new HashSet<>();private int scan;private P chest;private int chestTicks;
 public Structures(Autopilot brain){this.brain=brain;}
 public static P feet()throws Exception{return new P((int)Math.floor(Access.real(Access.player(),"method_23317")),(int)Math.floor(Access.real(Access.player(),"method_23318")),(int)Math.floor(Access.real(Access.player(),"method_23321")));}
 private double distance(P p)throws Exception{return Math.hypot(Math.hypot(Access.real(Access.player(),"method_23317")-p.x-.5,Access.real(Access.player(),"method_23321")-p.z-.5),Access.real(Access.player(),"method_23318")-p.y);}
 private Object ray(double x,double y,double z)throws Exception{Object player=Access.player(),eye=Access.call(player,"method_33571"),end=Access.make("net.minecraft.class_243",x,y,z);Object context=Access.make("net.minecraft.class_3959",eye,end,Access.field(Access.type("net.minecraft.class_3959$class_3960"),"field_17559"),Access.field(Access.type("net.minecraft.class_3959$class_242"),"field_1348"),player);return Access.call(Access.world(),"method_17742",context);}
 private boolean same(Object pos,P p)throws Exception{return Access.num(pos,"method_10263")==p.x&&Access.num(pos,"method_10264")==p.y&&Access.num(pos,"method_10260")==p.z;}
 private Object face(P target,boolean placing)throws Exception{
  for(int[] d:new int[][]{{0,-1,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0}}){P support=placing?target.add(d[0],d[1],d[2]):target;if(Access.air(support.x,support.y,support.z))continue;double x=support.x+.5-d[0]*.499,y=support.y+.5-d[1]*.499,z=support.z+.5-d[2]*.499;Object eye=Access.call(Access.player(),"method_33571");if(Math.pow(((Number)Access.field(eye,"field_1352")).doubleValue()-x,2)+Math.pow(((Number)Access.field(eye,"field_1351")).doubleValue()-y,2)+Math.pow(((Number)Access.field(eye,"field_1350")).doubleValue()-z,2)>20.25)continue;
   Object hit=ray(x+d[0]*.002,y+d[1]*.002,z+d[2]*.002);if(!Access.type("net.minecraft.class_3965").isInstance(hit)||!Access.call(hit,"method_17783").toString().equals("BLOCK"))continue;Object pos=Access.call(hit,"method_17777");Object destination=placing?Access.call(pos,"method_10093",Access.call(hit,"method_17780")):pos;if(same(destination,target))return hit;
  }return null;
 }
 public boolean select(String item)throws Exception{Object inv=Access.inv();int index=-1;for(int i=0;i<36;i++)if(Access.id(Access.call(inv,"method_5438",i)).equals(item)){index=i;break;}if(index<0)return false;if(index>=9){Access.close();Object h=Access.field(Access.player(),"field_7498");List<?> slots=(List<?>)Access.field(h,"field_7761");for(int i=0;i<slots.size();i++)if(Access.field(slots.get(i),"field_7871")==inv&&((Number)Access.field(slots.get(i),"field_7875")).intValue()==index){brain.ops.click(h,i,8,"field_7791");Access.call(inv,"method_61496",8);return false;}return false;}Access.call(inv,"method_61496",index);return true;}
 public boolean use(P target,boolean placing,String item)throws Exception{Object hit=face(target,placing);if(hit==null){brain.near(target.x,target.y,target.z);return false;}if(item!=null&&!select(item))return false;Object point=Access.call(hit,"method_17784"),eye=Access.call(Access.player(),"method_33571");double dx=((Number)Access.field(point,"field_1352")).doubleValue()-((Number)Access.field(eye,"field_1352")).doubleValue(),dy=((Number)Access.field(point,"field_1351")).doubleValue()-((Number)Access.field(eye,"field_1351")).doubleValue(),dz=((Number)Access.field(point,"field_1350")).doubleValue()-((Number)Access.field(eye,"field_1350")).doubleValue();float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));Access.call(Access.player(),"method_36456",yaw);Access.call(Access.player(),"method_36457",pitch);
  Object net=Access.call(Access.mc(),"method_1562");Object packet=Access.make("net.minecraft.class_2828$class_2831",yaw,pitch,(Boolean)Access.call(Access.player(),"method_24828"),(Boolean)Access.field(Access.player(),"field_5976"));Access.call(net,"method_52787",packet);
  Object result=Access.call(Access.field(Access.mc(),"field_1761"),"method_2896",Access.player(),Access.field(Access.type("net.minecraft.class_1268"),"field_5808"),hit);brain.delay=8;return (Boolean)Access.call(result,"method_23665");
 }
 public boolean available(String id)throws Exception{P p=stations.get(Access.dimension()+id);return p!=null&&Access.block(p.x,p.y,p.z).equals(id);}
 public boolean station(String id,String handler)throws Exception{if(Access.type("net.minecraft."+handler).isInstance(brain.ops.handler()))return true;if(Access.field(Access.mc(),"field_1755")!=null){Access.close();return false;}String key=Access.dimension()+id;P p=stations.get(key);if(p!=null&&!Access.block(p.x,p.y,p.z).equals(id)){stations.remove(key);p=null;}
  if(p==null){P f=feet();outer:for(int dy=-2;dy<=2;dy++)for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)if(Access.block(f.x+x,f.y+dy,f.z+z).equals(id)){p=f.add(x,dy,z);break outer;}if(p!=null)stations.put(key,p);}
  if(p!=null){use(p,false,null);return false;}
  P f=feet();for(int[] d:new int[][]{{2,0},{-2,0},{0,2},{0,-2},{2,1},{-2,-1}}){P t=f.add(d[0],0,d[1]);if(Access.air(t.x,t.y,t.z)&&!Access.air(t.x,t.y-1,t.z)&&Access.air(t.x,t.y+1,t.z)){use(t,true,id);if(Access.block(t.x,t.y,t.z).equals(id))stations.put(key,t);return false;}}
  brain.status="Need flat ground for "+id;return false;
 }
 public void rememberPortal()throws Exception{P f=feet();for(int x=-4;x<=4;x++)for(int y=-4;y<=4;y++)for(int z=-4;z<=4;z++)if(Access.block(f.x+x,f.y+y,f.z+z).equals("nether_portal")){portals.put(Access.dimension(),f.add(x,y,z));return;}}
 public boolean portal(boolean nether)throws Exception{
  boolean in=Access.dimension().endsWith("the_nether");if(in==nether)return true;
  if(!frameDimension.equals(Access.dimension())){frame=null;frameDimension=Access.dimension();}
  P known=portals.get(Access.dimension());if(known!=null&&Access.block(known.x,known.y,known.z).equals("nether_portal")){brain.go(known.x,known.y,known.z);return false;}
  // Prefer an existing nearby portal, including the generated return portal.
  P f=feet();for(int n=0;n<256;n++){int k=(scan++%15625),x=k%25-12,z=k/25%25-12,y=k/625-12;if(Access.block(f.x+x,f.y+y,f.z+z).equals("nether_portal")){portals.put(Access.dimension(),f.add(x,y,z));brain.go(f.x+x,f.y+y,f.z+z);return false;}}
  if(frame==null){if(!brain.ensure("obsidian",14,0)||!brain.ensure("flint_and_steel",1,0))return false;f=feet();P candidate=f.add(3,0,0);boolean clear=true;for(int x=0;x<4;x++)for(int y=0;y<5;y++)if(!Access.air(candidate.x+x,candidate.y+y,candidate.z))clear=false;if(!clear){brain.explore();brain.status="Searching flat space for a portal";return false;}for(int x=0;x<4;x++)if(Access.air(candidate.x+x,candidate.y-1,candidate.z))clear=false;if(!clear){brain.explore();return false;}frame=candidate;}
  List<P> blocks=new ArrayList<>();for(int x=0;x<4;x++)blocks.add(frame.add(x,0,0));for(int y=1;y<4;y++){blocks.add(frame.add(0,y,0));blocks.add(frame.add(3,y,0));}for(int x=0;x<4;x++)blocks.add(frame.add(x,4,0));
  for(P p:blocks)if(!Access.block(p.x,p.y,p.z).equals("obsidian")){if(!brain.ensure("obsidian",1,0))return false;use(p,true,"obsidian");return false;}
  P inside=frame.add(1,1,0);if(!Access.block(inside.x,inside.y,inside.z).equals("nether_portal")){if(!brain.ensure("flint_and_steel",1,0))return false;use(inside,true,"flint_and_steel");return false;}portals.put(Access.dimension(),inside);brain.go(inside.x,inside.y,inside.z);return false;
 }
 public boolean template()throws Exception{
  if(brain.ops.lootTemplate())return false;
  if(Access.type("net.minecraft.class_1707").isInstance(brain.ops.handler())){if(chest!=null)visitedChests.add(Access.dimension()+chest);Access.close();chest=null;}
  P f=feet();if(chest!=null){if(++chestTicks>600){visitedChests.add(Access.dimension()+chest);chest=null;}else{use(chest,false,null);return false;}}
  for(int n=0;n<256;n++){int k=scan++%15625;P p=f.add(k%25-12,k/625-12,k/25%25-12);String id=Access.block(p.x,p.y,p.z);if((id.equals("chest")||id.equals("trapped_chest"))&&!visitedChests.contains(Access.dimension()+p)){chest=p;chestTicks=0;use(p,false,null);return false;}}
  brain.explore();brain.status="Exploring the Nether for a bastion upgrade-template chest";return false;
 }
 public boolean enchanting()throws Exception{
  if(enchant==null){if(!brain.ensure("enchanting_table",1,0)||!brain.ensure("bookshelf",15,0))return false;P f=feet();P candidate=f.add(3,0,3);boolean clear=true;for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<2;y++)if(!Access.air(candidate.x+x,candidate.y+y,candidate.z+z))clear=false;if(!clear){brain.explore();brain.status="Finding an empty 5x5 enchanting area";return false;}enchant=candidate;}
  if(!Access.block(enchant.x,enchant.y,enchant.z).equals("enchanting_table")){if(!brain.ensure("enchanting_table",1,0))return false;use(enchant,true,"enchanting_table");return false;}
  int placed=0;for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){if(Math.abs(x)!=2&&Math.abs(z)!=2)continue;if(placed++>=15)continue;P p=enchant.add(x,0,z);if(!Access.block(p.x,p.y,p.z).equals("bookshelf")){if(!brain.ensure("bookshelf",1,0))return false;use(p,true,"bookshelf");return false;}}
  stations.put(Access.dimension()+"enchanting_table",enchant);return station("enchanting_table","class_1718");
 }
}
