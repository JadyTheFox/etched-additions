package net.everwild.jukebox.mixin;

import net.everwild.jukebox.JukeboxSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server side of vanilla discs in vanilla jukeboxes: loop instead of stopping, and freeze the song timer while paused. */
@Mixin(JukeboxSongPlayer.class)
public abstract class JukeboxSongPlayerMixin {

    @Shadow @Final private BlockPos blockPos;
    @Shadow @Nullable private Holder<JukeboxSong> song;

    @Shadow public abstract void play(LevelAccessor level, Holder<JukeboxSong> song);

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void everwild$pause(LevelAccessor level, @Nullable BlockState state, CallbackInfo ci) {
        if (!level.isClientSide() && JukeboxSettings.read(level.getBlockEntity(this.blockPos)).paused()) ci.cancel();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/JukeboxSongPlayer;stop(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void everwild$loop(JukeboxSongPlayer self, LevelAccessor level, @Nullable BlockState state) {
        if (!level.isClientSide() && this.song != null && JukeboxSettings.read(level.getBlockEntity(this.blockPos)).loop()) {
            this.play(level, this.song);
        } else {
            self.stop(level, state);
        }
    }
}
