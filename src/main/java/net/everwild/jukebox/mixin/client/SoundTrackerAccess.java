package net.everwild.jukebox.mixin.client;

import gg.moonflower.etched.api.sound.SoundTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(value = SoundTracker.class, remap = false)
public interface SoundTrackerAccess {
    @Accessor("FAILED_URLS")
    static Set<String> everwild$failedUrls() { throw new AssertionError(); }
}
