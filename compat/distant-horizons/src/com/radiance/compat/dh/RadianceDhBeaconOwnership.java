package com.radiance.compat.dh;
import com.seibel.distanthorizons.core.config.Config;

/** Radiance owns nearby beacon rendering; distant DH generic beacon geometry is unsupported. */
public final class RadianceDhBeaconOwnership {
    private RadianceDhBeaconOwnership() {}
    public static void configure() {
        Config.Client.Advanced.Graphics.GenericRendering.enableBeaconRendering.setApiValue(false);
    }
}
