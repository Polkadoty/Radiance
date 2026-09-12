// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;
import com.mojang.blaze3d.pipeline.RenderTarget;
public interface HybridMainTargetAccess {
    RenderTarget hybrid$swapMainTarget(RenderTarget target);
}
