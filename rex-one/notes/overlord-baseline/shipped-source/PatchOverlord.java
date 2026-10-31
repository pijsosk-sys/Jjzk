import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
public class PatchOverlord{
 public static void main(String[] args)throws Exception{
  byte[] input=Files.readAllBytes(Path.of(args[0]));int major=(input[6]&255)*256+(input[7]&255);input[6]=0;input[7]=61;
  ClassReader reader=new ClassReader(input);ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
  int[] edits={0};reader.accept(new ClassVisitor(Opcodes.ASM8,writer){
   @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){MethodVisitor parent=super.visitMethod(access,name,descriptor,signature,exceptions);if(!name.equals("registerAll")||!descriptor.equals("()V"))return parent;edits[0]++;return new MethodVisitor(Opcodes.ASM8,parent){@Override public void visitCode(){super.visitCode();visitVarInsn(Opcodes.ALOAD,0);visitTypeInsn(Opcodes.NEW,"rex/RexOverlord");visitInsn(Opcodes.DUP);visitMethodInsn(Opcodes.INVOKESPECIAL,"rex/RexOverlord","<init>","()V",false);visitMethodInsn(Opcodes.INVOKEVIRTUAL,"dev/krypton/core/managers/ModuleManager","register","(Ldev/krypton/core/module/Module;)V",false);}};}
  },0);if(edits[0]!=1)throw new IllegalStateException("Expected exactly one module registration point");byte[] output=writer.toByteArray();output[6]=(byte)(major>>8);output[7]=(byte)major;Files.write(Path.of(args[1]),output);System.out.println("Registered REX Overlord in ModuleManager");
 }
}
