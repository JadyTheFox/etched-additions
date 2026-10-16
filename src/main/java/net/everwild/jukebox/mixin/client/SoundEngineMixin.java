package net.everwild.jukebox.mixin.client;

import net.everwild.jukebox.JukeboxSettings;
import net.everwild.jukebox.client.ClientJukebox;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.everwild.jukebox.client.SppCompat;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies each jukebox's loudness, hearing range and pause to its song. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    /** Used every tick for ticking sounds (Etched's streams), so the loudness sticks. */
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void everwild$loudness(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        JukeboxSettings s = ClientJukebox.forSound(sound);
        if (s != null) cir.setReturnValue(cir.getReturnValueF() * s.volume());
    }

    /** Right after a song starts: set its range/volume, and pause it if the jukebox is paused. */
    @Inject(method = "play", at = @At("TAIL"))
    private void everwild$afterPlay(SoundInstance sound, CallbackInfo ci) {
        JukeboxSettings s = ClientJukebox.forSound(sound);
        if (s != null) ClientJukebox.applyToSound(sound, s);
    }

    /**
     * Same spot Etched listens on: a sound finished naturally and its channel is being removed.
     * If it's a Sound Physics Perfected copy of an Etched song, tell Etched the song ended.
     */
    @ModifyArg(method = "tickNonPaused", at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0))
    private Object everwild$sppSongFinished(Object sound) {
        if (sound instanceof SoundInstance s) {
            try { SppCompat.onSoundFinished(s); } catch (Throwable ignored) {}
        }
        return sound;
    }

    /** Sounds stopped on purpose (track switches, SPP cleanup) must not count as "song finished". */
    @Inject(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"))
    private void everwild$stoppedOnPurpose(SoundInstance sound, CallbackInfo ci) {
        SppCompat.markStoppedOnPurpose(sound);
    }
}
