package com.radiance.compat.dh;

import com.seibel.distanthorizons.core.config.Config;

/** Radiance owns sky/cloud geometry; prevent DH's cloud objects from being constructed at all. */
public final class RadianceDhCloudOwnership {
    private RadianceDhCloudOwnership() {}
    public static void configure() {
        Config.Client.Advanced.Graphics.GenericRendering.enableCloudRendering.setApiValue(false);
        // DH 3.2.0 AbstractDhLevel.runRepoReliantSetup checks this BEFORE constructing clouds.
        // The boolean alone is insufficient: CloudRenderHandler tests it only at pre-render time.
        Config.Client.Advanced.Graphics.GenericRendering.dimensionEnabledCloudRenderingCsv.setApiValue("");
    }
}
