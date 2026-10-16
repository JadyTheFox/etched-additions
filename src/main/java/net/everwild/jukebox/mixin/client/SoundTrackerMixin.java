package net.everwild.jukebox.mixin.client;

import gg.moonflower.etched.api.record.TrackData;
import gg.moonflower.etched.api.sound.SoundTracker;
import gg.moonflower.etched.common.blockentity.AlbumJukeboxBlockEntity;
import net.everwild.jukebox.client.ClientJukebox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.CommonLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;

/** Hooks into Etched's (client-side) music player. */
@Mixin(value = SoundTracker.class, remap = false)
public abstract class SoundTrackerMixin {

    /**
     * Etched disc in a vanilla jukebox: after the last track Etched just stops.
     * If looping is on (default) wrap back to track 1, as long as at least one track can actually play
     * (otherwise Etched's "skip broken track" recursion would spin forever).
     */
    @ModifyVariable(method = "playBlockRecord(Lnet/minecraft/core/BlockPos;[Lgg/moonflower/etched/api/record/TrackData;ILjava/util/UUID;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int everwild$loopBlockRecord(int track, BlockPos pos, TrackData[] tracks, int trackArg, UUID storageId) {
        if (track < tracks.length || tracks.length == 0 || !ClientJukebox.get(pos).loop()) return track;
        Set<String> failed = SoundTrackerAccess.everwild$failedUrls();
        for (TrackData t : tracks) {
            if (TrackData.isValidURL(t.url()) && !failed.contains(t.url())) return 0;
        }
        return track;
    }

    /** Minecart jukeboxes (and anything else Etched plays on an entity) loop too. */
    @ModifyVariable(method = "playEntityRecord(Lnet/minecraft/world/item/ItemStack;IIIZLjava/util/UUID;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static boolean everwild$alwaysLoop(boolean loop) {
        return true;
    }

    /** Remember what each entity (boombox holder) is playing, so the skip keys know where they are. */
    @Inject(method = "playEntityRecord(Lnet/minecraft/world/item/ItemStack;IIIZLjava/util/UUID;)V", at = @At("HEAD"))
    private static void everwild$remember(ItemStack record, int entityId, int track, int attenuation, boolean loop, UUID storageId, CallbackInfo ci) {
        ClientJukebox.rememberEntityPlayback(entityId, new ClientJukebox.EntityPlayback(record, track, attenuation, true, storageId));
    }

    /** Album jukebox "Repeat: One": replay the same track instead of moving on. */
    @Inject(method = "playNextRecord", at = @At("HEAD"), cancellable = true)
    private static void everwild$repeatOne(CommonLevelAccessor level, BlockPos pos, CallbackInfo ci) {
        if (!ClientJukebox.get(pos).repeatOne()) return;
        if (level.getBlockEntity(pos) instanceof AlbumJukeboxBlockEntity jukebox) {
            SoundTracker.playAlbum(jukebox, jukebox.getBlockState(), level, pos, true);
            ci.cancel();
        }
    }
}
