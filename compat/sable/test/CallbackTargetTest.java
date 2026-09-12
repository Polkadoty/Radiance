import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Verify every selector/static ABI and redirected invocation against the installed, original Sable jar. */
public final class CallbackTargetTest {
    private static Object value(AnnotationNode annotation,String key) {
        for(int i=0;i<annotation.values.size();i+=2)if(annotation.values.get(i).equals(key))return annotation.values.get(i+1);
        return null;
    }
    private static List<AnnotationNode> annotations(MethodNode m) {
        List<AnnotationNode> all=new ArrayList<>();
        if(m.visibleAnnotations!=null)all.addAll(m.visibleAnnotations);
        if(m.invisibleAnnotations!=null)all.addAll(m.invisibleAnnotations);
        return all;
    }
    private static ClassNode read(byte[] data) {
        ClassNode c=new ClassNode();new ClassReader(data).accept(c,0);return c;
    }
    private static MethodNode target(ClassNode c,String selector) {
        int at=selector.indexOf('(');assert at>0:selector;
        var found=c.methods.stream().filter(m->m.name.equals(selector.substring(0,at))&&m.desc.equals(selector.substring(at))).toList();
        assert found.size()==1:c.name+selector;return found.getFirst();
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        int selectors=0,redirects=0;
        try(JarFile sable=new JarFile(args[0]);var sources=Files.walk(Path.of(args[1]))) {
            for(Path path:sources.filter(p->p.toString().endsWith("Mixin.class")).toList()) {
                var mixin=read(Files.readAllBytes(path));
                var annotation=mixin.invisibleAnnotations.stream().filter(a->a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
                var type=((List<Type>)value(annotation,"value")).getFirst();
                assert !type.getInternalName().contains("Container"):"CPU container must not be intercepted";
                var entry=sable.getJarEntry(type.getInternalName()+".class");assert entry!=null:type;
                var original=read(sable.getInputStream(entry).readAllBytes());
                for(var method:mixin.methods)for(var inject:annotations(method)) {
                    if(!inject.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/"))continue;
                    for(String selector:(List<String>)value(inject,"method")) {
                        MethodNode target=target(original,selector);selectors++;
                        assert (target.access&Opcodes.ACC_STATIC)==(method.access&Opcodes.ACC_STATIC):selector;
                        assert !Set.of("update","addRegion","removeRegion","onUpdate").contains(target.name):"CPU/config owner collision";
                        if(inject.desc.endsWith("/ModifyVariable;")) {
                            assert target.desc.equals("(Z)V") && method.desc.equals("(Z)Z");
                            assert Boolean.TRUE.equals(value(inject,"argsOnly"));
                        }
                        if(inject.desc.endsWith("/Redirect;")) {
                            AnnotationNode at=(AnnotationNode)value(inject,"at");
                            String callee=(String)value(at,"target");
                            int end=callee.indexOf(';');String owner=callee.substring(1,end);String nameDesc=callee.substring(end+1);
                            int matches=0;
                            for(AbstractInsnNode i:target.instructions)if(i instanceof MethodInsnNode call && call.owner.equals(owner)&&(call.name+call.desc).equals(nameDesc)) {
                                matches++;
                                assert method.desc.equals("(L"+owner+";"+call.desc.substring(1)):method.desc;
                            }
                            assert matches==1:callee+" count "+matches;
                            redirects++;
                        }
                    }
                }
            }
            // The remaining Sable init call keeps gizmo selection/math and its vanilla buffer drawing.
            ClassNode client=read(sable.getInputStream(sable.getJarEntry("dev/ryanhcode/sable/SableClient.class")).readAllBytes());
            var init=target(client,"init()V");boolean gizmo=false;
            for(AbstractInsnNode i:init.instructions)if(i instanceof MethodInsnNode c&&c.owner.endsWith("/SableClientGizmoHandler")&&c.name.equals("init"))gizmo=true;
            assert gizmo;
        }
        assert selectors==16:selectors;
        assert redirects==3:redirects;
        System.out.println(selectors+" exact original Sable injection targets and "+redirects+" singular callback redirects verified; CPU container/config ownership preserved");
    }
}
