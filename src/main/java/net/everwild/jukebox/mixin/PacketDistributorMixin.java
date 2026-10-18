package net.everwild.jukebox.mixin;

import net.everwild.jukebox.JukeboxSettings;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Etched sends its disc songs to players within 64 blocks; stretch that to the jukebox broadcast range. */
@Mixin(value = PacketDistributor.class, remap = false)
public abstract class PacketDistributorMixin {
    @ModifyVariable(method = "sendToPlayersNear", at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
    private static double everwild$farEtched(double radius, ServerLevel level, @Nullable ServerPlayer except,
                                             double x, double y, double z, double r,
                                             CustomPacketPayload payload, CustomPacketPayload... payloads) {
        if (radius == 64.0 && payload.getClass().getName().equals("gg.moonflower.etched.common.network.play.ClientboundPlayBlockMusicPacket"))
            return JukeboxSettings.BROADCAST_RANGE;
        return radius;
    }
}
