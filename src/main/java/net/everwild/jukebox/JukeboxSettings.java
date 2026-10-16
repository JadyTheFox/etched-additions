package net.everwild.jukebox;

import gg.moonflower.etched.common.blockentity.AlbumJukeboxBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Per-jukebox settings, saved in the block entity's NeoForge persistent data on the server
 * and mirrored to clients with {@link net.everwild.jukebox.network.SettingsSyncPacket}.
 *
 * @param volume    loudness 0..1
 * @param range     how far the music carries, in blocks (4..64; the server only sends music to players within 64)
 * @param paused    paused in place
 * @param loop      replay when the song ends (vanilla jukebox + Etched discs)
 * @param repeatOne album jukebox: replay the same track instead of moving to the next one
 */
public record JukeboxSettings(float volume, int range, boolean paused, boolean loop, boolean repeatOne) {

    public static final int MIN_RANGE = 4;
    public static final int MAX_RANGE = 64;
    public static final JukeboxSettings DEFAULT = new JukeboxSettings(1.0F, MAX_RANGE, false, true, false);
    private static final String KEY = "everwild_jukebox";

    public static final StreamCodec<ByteBuf, JukeboxSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, JukeboxSettings::volume,
            ByteBufCodecs.VAR_INT, JukeboxSettings::range,
            ByteBufCodecs.BOOL, JukeboxSettings::paused,
            ByteBufCodecs.BOOL, JukeboxSettings::loop,
            ByteBufCodecs.BOOL, JukeboxSettings::repeatOne,
            JukeboxSettings::new);

    public JukeboxSettings {
        volume = Mth.clamp(volume, 0.0F, 1.0F);
        range = Mth.clamp(range, MIN_RANGE, MAX_RANGE);
    }

    public JukeboxSettings withPaused(boolean p) { return new JukeboxSettings(volume, range, p, loop, repeatOne); }

    public boolean isDefault() { return this.equals(DEFAULT); }

    /** Vanilla jukebox or Etched album jukebox. */
    public static boolean isSupported(@Nullable BlockEntity be) {
        return be instanceof JukeboxBlockEntity || be instanceof AlbumJukeboxBlockEntity;
    }

    public static JukeboxSettings read(@Nullable BlockEntity be) {
        if (be == null) return DEFAULT;
        CompoundTag root = be.getPersistentData();
        if (!root.contains(KEY)) return DEFAULT;
        CompoundTag t = root.getCompound(KEY);
        return new JukeboxSettings(
                t.contains("volume") ? t.getFloat("volume") : DEFAULT.volume(),
                t.contains("range") ? t.getInt("range") : DEFAULT.range(),
                t.getBoolean("paused"),
                !t.contains("loop") || t.getBoolean("loop"),
                t.getBoolean("repeatOne"));
    }

    public static void write(BlockEntity be, JukeboxSettings s) {
        CompoundTag root = be.getPersistentData();
        if (s.isDefault()) {
            root.remove(KEY);
        } else {
            CompoundTag t = new CompoundTag();
            t.putFloat("volume", s.volume());
            t.putInt("range", s.range());
            t.putBoolean("paused", s.paused());
            t.putBoolean("loop", s.loop());
            t.putBoolean("repeatOne", s.repeatOne());
            root.put(KEY, t);
        }
        be.setChanged();
    }
}
