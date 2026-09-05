package com.radiance.mixins.vulkan_render_integration;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ICompiledShaderExt;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gl.ShaderStage;
import net.minecraft.client.gl.GlImportProcessor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ShaderStage.class)
public abstract class CompiledShaderMixins implements ICompiledShaderExt {
    @Unique private static final AtomicInteger radiance$nextId = new AtomicInteger(1);
    @Unique private String radiance$source;
    @Shadow private int glRef;
    @Shadow @Final private ShaderStage.Type type;
    @Shadow @Final private String name;
    @Inject(method="createFromResource", at=@At("HEAD"), cancellable=true)
    private static void radiance$compile(ShaderStage.Type type, String name, InputStream stream,
            String domain, GlImportProcessor loader, CallbackInfoReturnable<ShaderStage> cir) throws IOException {
        ShaderStage stage = new ShaderStage(type, radiance$nextId.getAndIncrement(), name);
        ((ICompiledShaderExt)(Object)stage).radiance$setResolvedSource(
            String.join("", loader.readSource(new String(stream.readAllBytes(), StandardCharsets.UTF_8))));
        type.getLoadedShaders().put(name, stage);
        cir.setReturnValue(stage);
    }
    @Inject(method="release", at=@At("HEAD"), cancellable=true)
    private void radiance$release(CallbackInfo ci) {
        glRef = -1;
        type.getLoadedShaders().remove(name);
        ci.cancel();
    }
    public String radiance$getResolvedSource() { return radiance$source; }
    public void radiance$setResolvedSource(String source) { radiance$source = source; }
}
