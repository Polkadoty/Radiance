import com.radiance.compat.sable.callbacks.SableRenderFeatureBoundary;
import com.radiance.compat.sable.callbacks.mixin.*;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class CallbackBoundaryTest {
    private static final class Water extends SableWaterEffectsMixin {}
    private static Method method(Class<?> type, String name) {
        for(Method m:type.getDeclaredMethods())if(m.getName().equals(name)){m.setAccessible(true);return m;}
        throw new AssertionError(name);
    }
    public static void main(String[] args) throws Exception {
        boolean enabled=Boolean.getBoolean("radiance.sableBridge");
        CallbackMixinPlugin plugin=new CallbackMixinPlugin();
        if(args.length>0 && args[0].equals("invalid")) {
            try {plugin.onLoad("test");throw new AssertionError("inconsistent backend accepted");}
            catch(IllegalStateException expected) { System.out.println("Mismatched backend rejected before mixins");return; }
        }
        plugin.onLoad("test");
        assert plugin.shouldApplyMixin("target","mixin")==enabled;
        for(var feature:SableRenderFeatureBoundary.Feature.values()) {
            assert !SableRenderFeatureBoundary.effectiveEnabled(false,feature);
            assert SableRenderFeatureBoundary.effectiveEnabled(true,feature)==!enabled;
        }
        System.setProperty("radiance.sableBridge",Boolean.toString(!enabled));
        assert SableRenderFeatureBoundary.enabled()==enabled;
        for(Class<?> type:new Class<?>[]{SableShadowEffectsMixin.class,SableWaterEffectsMixin.class,SableDirectionalShaderMixin.class}) {
            var cir=new CallbackInfoReturnable<Boolean>("test",true,true);
            method(type,"radiance$disabled").invoke(null,cir);
            assert cir.isCancelled()==enabled;
            assert cir.getReturnValue()==!enabled;
            assert (Boolean)method(type,"radiance$requested").invoke(null,true)==!enabled;
        }
        var ci=new CallbackInfo("shadow",true);
        method(SableShadowEffectsMixin.class,"radiance$skipEffect").invoke(null,ci);
        assert ci.isCancelled()==enabled;
        var water=new CallbackInfo("water",true);
        method(SableWaterEffectsMixin.class,"radiance$skipEffect").invoke(new Water(),water);
        assert water.isCancelled()==enabled;
        var framebuffer=new CallbackInfoReturnable<Object>("fbo",true,new Object());
        method(SableShadowEffectsMixin.class,"radiance$noFramebuffer").invoke(null,framebuffer);
        assert framebuffer.isCancelled()==enabled;
        assert (framebuffer.getReturnValue()==null)==enabled;
        if(enabled) {
            // No Veil singleton, renderer, GL context or native window is available here.
            for(String name:new String[]{"radiance$editor","radiance$processors","radiance$shadowStage"})
                method(SableClientCallbacksMixin.class,name).invoke(null,null,null);
            assert SableRenderFeatureBoundary.omittedFeatures().size()==5;
            try {SableRenderFeatureBoundary.omittedFeatures().clear();throw new AssertionError();}
            catch(UnsupportedOperationException expected) {}
        } else assert SableRenderFeatureBoundary.omittedFeatures().isEmpty();
        System.out.println("Actual callback handlers: flags, cancellation, nullable FBO result, immutable selection and reporting passed; enabled="+enabled);
    }
}
