// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;
import com.radiance.hybrid.HudCompositor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Gui.class,remap=false)
public abstract class HudRenderMixin {
    @Inject(method="renderItemHotbar",at=@At("HEAD"))
    private void hybrid$nativeHotbar(GuiGraphics graphics,DeltaTracker delta,CallbackInfo ci){HudCompositor.beginNativeHotbar(graphics);}
    @Inject(method="renderItemHotbar",at=@At("RETURN"))
    private void hybrid$finishNativeHotbar(GuiGraphics graphics,DeltaTracker delta,CallbackInfo ci){HudCompositor.endNativeHotbar(graphics);}
    @Inject(method="render",at=@At("HEAD"))
    private void hybrid$begin(GuiGraphics graphics,DeltaTracker delta,CallbackInfo ci){HudCompositor.begin(graphics);}
    @Inject(method="render",at=@At("RETURN"))
    private void hybrid$end(GuiGraphics graphics,DeltaTracker delta,CallbackInfo ci){HudCompositor.end(graphics);}
    @Inject(method="renderVignette",at=@At("HEAD"))
    private void hybrid$sceneVignette(GuiGraphics graphics,net.minecraft.world.entity.Entity camera,CallbackInfo ci){HudCompositor.beginSceneEffect(graphics);}
    @Inject(method="renderVignette",at=@At("RETURN"))
    private void hybrid$restoreHud(GuiGraphics graphics,net.minecraft.world.entity.Entity camera,CallbackInfo ci){HudCompositor.endSceneEffect(graphics);}
}
