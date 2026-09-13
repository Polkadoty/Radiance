// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;

import com.radiance.hybrid.MapScreenCompositor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Screen.class, remap = false)
public abstract class MapScreenRenderMixin {
    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void hybrid$beginMap(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MapScreenCompositor.begin((Screen)(Object)this, graphics);
    }
    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void hybrid$endMap(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MapScreenCompositor.end(graphics);
    }
}
