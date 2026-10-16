package net.everwild.jukebox.network;

import net.everwild.jukebox.EverwildJukebox;
import net.everwild.jukebox.JukeboxSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class Packets {
    private Packets() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EverwildJukebox.MOD_ID, path));
    }

    /** Client -> server: "set this jukebox's settings". */
    public record SetSettings(BlockPos pos, JukeboxSettings settings) implements CustomPacketPayload {
        public static final Type<SetSettings> TYPE = typeOf("set_settings");
        public static final StreamCodec<ByteBuf, SetSettings> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetSettings::pos, JukeboxSettings.STREAM_CODEC, SetSettings::settings, SetSettings::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> clients: "this jukebox now has these settings". */
    public record SettingsSync(BlockPos pos, JukeboxSettings settings) implements CustomPacketPayload {
        public static final Type<SettingsSync> TYPE = typeOf("settings_sync");
        public static final StreamCodec<ByteBuf, SettingsSync> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SettingsSync::pos, JukeboxSettings.STREAM_CODEC, SettingsSync::settings, SettingsSync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client -> server: "skip my boombox" (+1 next, -1 previous). */
    public record BoomboxSkip(int direction) implements CustomPacketPayload {
        public static final Type<BoomboxSkip> TYPE = typeOf("boombox_skip");
        public static final StreamCodec<ByteBuf, BoomboxSkip> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BoomboxSkip::direction, BoomboxSkip::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> clients: "this entity's boombox skipped". */
    public record BoomboxSkipped(int entityId, int direction) implements CustomPacketPayload {
        public static final Type<BoomboxSkipped> TYPE = typeOf("boombox_skipped");
        public static final StreamCodec<ByteBuf, BoomboxSkipped> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BoomboxSkipped::entityId, ByteBufCodecs.VAR_INT, BoomboxSkipped::direction, BoomboxSkipped::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
