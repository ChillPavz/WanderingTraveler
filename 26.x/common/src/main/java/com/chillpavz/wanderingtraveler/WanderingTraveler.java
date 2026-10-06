package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.platform.Services;

/**
 * Shared entry point.
 *
 * <p>Everything this mod does is client side, so the loader modules call this from
 * their CLIENT entry point. There are NO MIXINS on purpose: a client tick hook and a
 * HUD hook are first-class events on both loaders, and the incumbent's three
 * {@code required: true} mixins - one of them into a method whose signature moves
 * between versions - are the most plausible mechanical reason it skipped seven
 * consecutive Minecraft versions. The version spread is the product, so the thing that
 * makes it cheap is worth protecting.
 */
public final class WanderingTraveler {

    public static void init() {
        Constants.LOG.info("{} starting on {}", Constants.MOD_NAME,
                Services.PLATFORM.getPlatformName());
    }

    private WanderingTraveler() {
    }
}
