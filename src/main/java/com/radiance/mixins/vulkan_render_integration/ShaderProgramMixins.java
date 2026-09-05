package com.radiance.mixins.vulkan_render_integration;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ICompiledShaderExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gl.*;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Keep vanilla JSON parsing and replace only the OpenGL program operations. */
@Mixin(ShaderProgram.class)
public abstract class ShaderProgramMixins implements IShaderProgramExt {
    @Unique private static final AtomicInteger radiance$nextId = new AtomicInteger(1);
    @Shadow @Final private String name;
    @Shadow @Final private VertexFormat format;
    @Shadow @Final private ShaderStage vertexShader;
    @Shadow @Final private ShaderStage fragmentShader;
    @Shadow @Final private Map<String,Object> samplers;
    @Shadow @Final private List<String> samplerNames;
    @Shadow @Final private List<Integer> loadedSamplerIds;
    @Shadow @Final private List<GlUniform> uniforms;
    @Shadow @Final private List<Integer> loadedUniformIds;
    @Shadow @Final private Map<String,GlUniform> loadedUniforms;
    @Redirect(method={"<init>(Lnet/minecraft/resource/ResourceFactory;Ljava/lang/String;Lnet/minecraft/client/render/VertexFormat;)V", "<init>(Lnet/minecraft/resource/ResourceFactory;Lnet/minecraft/util/Identifier;Lnet/minecraft/client/render/VertexFormat;)V"}, at=@At(value="INVOKE",target="Lnet/minecraft/client/gl/GlProgramManager;createProgram()I"))
    private int radiance$createProgram() { return radiance$nextId.getAndIncrement(); }
    @Redirect(method={"<init>(Lnet/minecraft/resource/ResourceFactory;Ljava/lang/String;Lnet/minecraft/client/render/VertexFormat;)V", "<init>(Lnet/minecraft/resource/ResourceFactory;Lnet/minecraft/util/Identifier;Lnet/minecraft/client/render/VertexFormat;)V"}, at=@At(value="INVOKE",target="Lnet/minecraft/client/gl/GlUniform;bindAttribLocation(IILjava/lang/CharSequence;)V"))
    private void radiance$bindAttribute(int program, int index, CharSequence name) {}
    @Redirect(method={"<init>(Lnet/minecraft/resource/ResourceFactory;Ljava/lang/String;Lnet/minecraft/client/render/VertexFormat;)V", "<init>(Lnet/minecraft/resource/ResourceFactory;Lnet/minecraft/util/Identifier;Lnet/minecraft/client/render/VertexFormat;)V"}, at=@At(value="INVOKE",target="Lnet/minecraft/client/gl/GlProgramManager;linkProgram(Lnet/minecraft/client/gl/ShaderProgramSetupView;)V"))
    private void radiance$link(ShaderProgramSetupView program) {}
    @Inject(method="loadReferences",at=@At("HEAD"),cancellable=true)
    private void radiance$references(CallbackInfo ci) {
        loadedSamplerIds.clear(); loadedUniformIds.clear(); loadedUniforms.clear();
        for (int i=0; i<samplerNames.size(); i++) loadedSamplerIds.add(i);
        for (int i=0; i<uniforms.size(); i++) {
            GlUniform uniform=uniforms.get(i);
            uniform.setLocation(i); loadedUniformIds.add(i); loadedUniforms.put(uniform.getName(),uniform);
        }
        ci.cancel();
    }
    @Inject(method={"bind","unbind"},at=@At("HEAD"),cancellable=true)
    private void radiance$skipGl(CallbackInfo ci) { ci.cancel(); }
    @Inject(method="close",at=@At("HEAD"),cancellable=true)
    private void radiance$close(CallbackInfo ci) { uniforms.forEach(GlUniform::close); ci.cancel(); }
    public String radiance$getShaderName() { return name.contains(":") ? name : "minecraft:" + name; }
    public void radiance$setShaderName(String value) { throw new UnsupportedOperationException(); }
    public VertexFormat radiance$getVertexFormat() { return format; }
    public void radiance$setVertexFormat(VertexFormat value) { throw new UnsupportedOperationException(); }
    public String radiance$getVertexSource() { return ((ICompiledShaderExt)(Object)vertexShader).radiance$getResolvedSource(); }
    public void radiance$setVertexSource(String value) { ((ICompiledShaderExt)(Object)vertexShader).radiance$setResolvedSource(value); }
    public String radiance$getFragmentSource() { return ((ICompiledShaderExt)(Object)fragmentShader).radiance$getResolvedSource(); }
    public void radiance$setFragmentSource(String value) { ((ICompiledShaderExt)(Object)fragmentShader).radiance$setResolvedSource(value); }
    public List<String> radiance$getSamplerNamesValue() { return samplerNames; }
    public List<GlUniform> radiance$getUniformsValue() { return uniforms; }
    public Object2IntMap<String> radiance$getSamplerTexturesValue() {
        Object2IntMap<String> result=new Object2IntOpenHashMap<>();
        samplers.forEach((key,value)-> {
            if(value instanceof Integer id) result.put(key,id.intValue());
            else if(value instanceof AbstractTexture texture) result.put(key,texture.getGlId());
            else if(value instanceof Framebuffer buffer) result.put(key,buffer.getColorAttachment());
        });
        return result;
    }
}
