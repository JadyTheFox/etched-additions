package net.everwild.jukebox.mixin;

import net.everwild.jukebox.JukeboxSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla only tells players within 64 blocks that a jukebox started or stopped, so anyone further
 * out never hears it no matter the range setting. Jukebox start/stop events go out further here.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    private static final int JUKEBOX_PLAY = 1010, JUKEBOX_STOP = 1011;

    @Inject(method = "levelEvent", at = @At("HEAD"), cancellable = true)
    private void everwild$farJukebox(@Nullable Player except, int type, BlockPos pos, int data, CallbackInfo ci) {
        if (type != JUKEBOX_PLAY && type != JUKEBOX_STOP) return;
        ServerLevel self = (ServerLevel) (Object) this;
        self.getServer().getPlayerList().broadcast(except, pos.getX(), pos.getY(), pos.getZ(),
                JukeboxSettings.BROADCAST_RANGE, self.dimension(), new ClientboundLevelEventPacket(type, pos, data, false));
        ci.cancel();
    }
}
