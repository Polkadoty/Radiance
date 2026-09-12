// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.shaders.Uniform;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.util.*;

/** GL-private framebuffer draws. The Vulkan scene/presentation path stays separate. */
public final class OffscreenRenderer {
    private static final Map<ShaderInstance, Program> PROGRAMS = new IdentityHashMap<>();
    private static int vao, vertexBuffer, indexBuffer;
    private OffscreenRenderer() {}

    public static boolean isPrivateTarget() {
        if (!HybridContext.ENABLED) return false;
        HybridContext.assertCurrent();
        int target = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        return target != 0 && !HybridTargets.isPresentationTarget(target);
    }

    public static void draw(MeshData mesh) {
        ShaderInstance shader = Objects.requireNonNull(RenderSystem.getShader(), "Offscreen draw has no shader");
        ShaderProxy.syncState(shader, mesh.drawState().mode());
        var ext = (IShaderProgramExt)(Object)shader;
        Program program = program(shader);
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        List<Integer> oldTextures = new ArrayList<>();
        try {
            GL20.glUseProgram(program.id);
            for (Uniform uniform : ext.radiance$getUniformsValue()) uploadUniform(program.id, uniform);
            var textures = ext.radiance$getSamplerTexturesValue();
            for (String name : ext.radiance$getSamplerNamesValue()) {
                int location = GL20.glGetUniformLocation(program.id, name);
                if (location < 0) continue;
                int unit = oldTextures.size();
                if (unit >= GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))
                    throw new IllegalStateException("Too many offscreen shader samplers");
                int texture = textures.getInt(name);
                if (texture == 0 && name.matches("Sampler[0-9]+"))
                    texture = RenderSystem.getShaderTexture(Integer.parseInt(name.substring(7)));
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                oldTextures.add(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                GL20.glUniform1i(location, unit);
            }
            drawGeometry(mesh, program);
            HudCompositor.diagnoseAlpha(ext.radiance$getShaderName());
        } finally {
            for (int i = 0; i < oldTextures.size(); i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTextures.get(i));
            }
            GL13.glActiveTexture(oldActive);
            GL20.glUseProgram(oldProgram);
        }
    }

    private static Program program(ShaderInstance shader) {
        HybridContext.assertCurrent();
        return PROGRAMS.computeIfAbsent(shader,key->{
            var ext=(IShaderProgramExt)(Object)key;
            Program created=compile(ext.radiance$getVertexSource(),ext.radiance$getFragmentSource());
            System.out.println("[Hybrid] OpenGL offscreen shader linked: "+ext.radiance$getShaderName());return created;
        });
    }
    public static int programId(ShaderInstance shader){return program(shader).id;}
    /** Vanilla bare draws reuse the bound program/uniforms and current texture bindings. */
    public static void drawBound(MeshData mesh) {
        int id=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        Program program=PROGRAMS.values().stream().filter(p->p.id==id).findFirst()
            .orElseThrow(()->new IllegalStateException("Bare Minecraft draw has no managed GL program: "+id));
        drawGeometry(mesh,program);
        HudCompositor.diagnoseAlpha("bare draw program "+id);
    }

    static void uploadUniform(int program, Uniform uniform) {
        int location = GL20.glGetUniformLocation(program, uniform.getName());
        if (location < 0) return;
        int type = uniform.getType();
        IntBuffer ints = type < 4 ? uniform.getIntBuffer().duplicate().clear().limit(uniform.getCount()) : null;
        FloatBuffer floats = type >= 4 ? uniform.getFloatBuffer().duplicate().clear().limit(uniform.getCount()) : null;
        switch (type) {
            case 0 -> GL20.glUniform1iv(location, ints);
            case 1 -> GL20.glUniform2iv(location, ints);
            case 2 -> GL20.glUniform3iv(location, ints);
            case 3 -> GL20.glUniform4iv(location, ints);
            case 4 -> GL20.glUniform1fv(location, floats);
            case 5 -> GL20.glUniform2fv(location, floats);
            case 6 -> GL20.glUniform3fv(location, floats);
            case 7 -> GL20.glUniform4fv(location, floats);
            case 8 -> GL20.glUniformMatrix2fv(location, false, floats);
            case 9 -> GL20.glUniformMatrix3fv(location, false, floats);
            case 10 -> GL20.glUniformMatrix4fv(location, false, floats);
            default -> throw new IllegalArgumentException("Unsupported offscreen uniform type: " + type);
        }
    }

    record Attribute(int location, boolean integer) {}
    record Program(int id, Map<String, Attribute> attributes) {}

    static Program compile(String vertex, String fragment) {
        int vs = compileStage(GL20.GL_VERTEX_SHADER, vertex);
        int fs = 0, program = 0;
        try {
            fs = compileStage(GL20.GL_FRAGMENT_SHADER, fragment);
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vs); GL20.glAttachShader(program, fs);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
                throw new IllegalStateException("Hybrid GL shader link: " + GL20.glGetProgramInfoLog(program));
            Map<String, Attribute> attributes = new HashMap<>();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer size = stack.mallocInt(1), type = stack.mallocInt(1);
                for (int i = 0; i < GL20.glGetProgrami(program, GL20.GL_ACTIVE_ATTRIBUTES); i++) {
                    String name = GL20.glGetActiveAttrib(program, i, 1024, size, type);
                    int t = type.get(0);
                    boolean integer = t == GL11.GL_INT || t == GL11.GL_UNSIGNED_INT
                        || (t >= GL20.GL_INT_VEC2 && t <= GL20.GL_INT_VEC4)
                        || (t >= GL30.GL_UNSIGNED_INT_VEC2 && t <= GL30.GL_UNSIGNED_INT_VEC4);
                    if (size.get(0) != 1 || t == GL20.GL_FLOAT_MAT2 || t == GL20.GL_FLOAT_MAT3 || t == GL20.GL_FLOAT_MAT4)
                        throw new IllegalStateException("Matrix/array vertex input requires multiple locations: " + name);
                    attributes.put(name, new Attribute(GL20.glGetAttribLocation(program, name), integer));
                }
            }
            return new Program(program, attributes);
        } catch (RuntimeException e) {
            if (program != 0) GL20.glDeleteProgram(program);
            throw e;
        } finally {
            GL20.glDeleteShader(vs);
            if (fs != 0) GL20.glDeleteShader(fs);
        }
    }

    private static int compileStage(int type, String source) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, Objects.requireNonNull(source, "Missing resolved GL shader source"));
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
            String error = GL20.glGetShaderInfoLog(id); GL20.glDeleteShader(id);
            throw new IllegalStateException("Hybrid GL shader compile: " + error);
        }
        return id;
    }

    static void drawGeometry(MeshData mesh, Program program) {
        if (vao == 0) { vao = GL30.glGenVertexArrays(); vertexBuffer = GL15.glGenBuffers(); indexBuffer = GL15.glGenBuffers(); }
        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        List<Integer> enabled = new ArrayList<>();
        try {
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mesh.vertexBuffer(), GL15.GL_STREAM_DRAW);
            var format = mesh.drawState().format();
            for (var entry : program.attributes.entrySet()) {
                var element = format.getElementMapping().get(entry.getKey());
                if (element == null) throw new IllegalStateException("Missing GL vertex input " + entry.getKey() + " in " + format);
                var attr = entry.getValue();
                GL20.glEnableVertexAttribArray(attr.location); enabled.add(attr.location);
                long offset = format.getOffset(element);
                if (attr.integer) GL30.glVertexAttribIPointer(attr.location, element.count(), element.type().glType(), format.getVertexSize(), offset);
                else GL20.glVertexAttribPointer(attr.location, element.count(), element.type().glType(),
                    element.usage() == VertexFormatElement.Usage.COLOR || element.usage() == VertexFormatElement.Usage.NORMAL,
                    format.getVertexSize(), offset);
            }
            var state = mesh.drawState();
            ByteBuffer indices = mesh.indexBuffer();
            if (indices != null) {
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STREAM_DRAW);
                GL11.glDrawElements(state.mode().asGLMode, state.indexCount(), state.indexType().asGLType, 0L);
            } else if (state.mode() == VertexFormat.Mode.QUADS || state.mode() == VertexFormat.Mode.LINES) {
                IntBuffer generated = MemoryUtil.memAllocInt(state.indexCount());
                try {
                    boolean lines = state.mode() == VertexFormat.Mode.LINES;
                    for (int i = 0; i < state.vertexCount(); i += 4) {
                        generated.put(i).put(i+1).put(i+2);
                        if (lines) generated.put(i+3).put(i+2).put(i+1);
                        else generated.put(i+2).put(i+3).put(i);
                    }
                    generated.flip();
                    GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
                    GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, generated, GL15.GL_STREAM_DRAW);
                    GL11.glDrawElements(state.mode().asGLMode, state.indexCount(), GL11.GL_UNSIGNED_INT, 0L);
                } finally { MemoryUtil.memFree(generated); }
            } else GL11.glDrawArrays(state.mode().asGLMode, 0, state.vertexCount());
        } finally {
            for (int location : enabled) GL20.glDisableVertexAttribArray(location);
            GL30.glBindVertexArray(oldVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldArray);
        }
    }

    public static void release(ShaderInstance shader) {
        Program p = PROGRAMS.remove(shader);
        if (p != null) { HybridContext.assertCurrent(); GL20.glDeleteProgram(p.id); }
    }

    public static void close() {
        HybridContext.assertCurrent();
        for (Program p : PROGRAMS.values()) GL20.glDeleteProgram(p.id);
        PROGRAMS.clear();
        if (vao != 0) { GL30.glDeleteVertexArrays(vao); GL15.glDeleteBuffers(vertexBuffer); GL15.glDeleteBuffers(indexBuffer); }
        vao = vertexBuffer = indexBuffer = 0;
    }
}
