package net.everwild.jukebox.client;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.platform.InputConstants;
import gg.moonflower.etched.api.record.PlayableRecord;
import gg.moonflower.etched.api.sound.SoundTracker;
import gg.moonflower.etched.client.screen.AlbumJukeboxScreen;
import net.everwild.jukebox.EverwildJukebox;
import net.everwild.jukebox.JukeboxSettings;
import net.everwild.jukebox.mixin.client.LevelRendererAccess;
import net.everwild.jukebox.mixin.client.SoundEngineAccess;
import net.everwild.jukebox.mixin.client.SoundManagerAccess;
import net.everwild.jukebox.network.Packets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client half: remembers jukebox settings, applies them to the sounds, boombox keys and GUI buttons. */
public final class ClientJukebox {
    private ClientJukebox() {}

    private static final Map<BlockPos, JukeboxSettings> SETTINGS = new HashMap<>();

    public static JukeboxSettings get(BlockPos pos) {
        return SETTINGS.getOrDefault(pos, JukeboxSettings.DEFAULT);
    }

    /** Settings for the jukebox a RECORDS sound is coming from, or null if it isn't a jukebox song. */
    @Nullable
    public static JukeboxSettings forSound(SoundInstance sound) {
        if (sound.getSource() != SoundSource.RECORDS) return null;
        SoundInstance o = SppCompat.unwrap(sound);          // Sound Physics copies -> the jukebox's own sound
        BlockPos pos = BlockPos.containing(o.getX(), o.getY(), o.getZ());
        JukeboxSettings s = SETTINGS.get(pos);
        if (s != null) return s;
        // untouched jukeboxes still get the full vanilla range: Etched streams album songs with a
        // ~16 block falloff, so they faded out a few steps from the counter
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && JukeboxSettings.isSupported(mc.level.getBlockEntity(pos)) ? JukeboxSettings.DEFAULT : null;
    }

    // ------------------------------------------------------------ settings in / out

    public static void onSettings(BlockPos pos, JukeboxSettings s) {
        if (s.isDefault()) SETTINGS.remove(pos); else SETTINGS.put(pos.immutable(), s);
        applyToPlaying(pos, s);
    }

    /** Instant local preview while dragging a slider (the server copy follows when you let go). */
    public static void applyLocalPreview(BlockPos pos, JukeboxSettings s) {
        onSettings(pos, s);
    }

    public static void send(BlockPos pos, JukeboxSettings s) {
        PacketDistributor.sendToServer(new Packets.SetSettings(pos, s));
    }

    public static void openSettings(BlockPos pos) {
        Minecraft.getInstance().setScreen(new JukeboxSettingsScreen(pos, Minecraft.getInstance().screen));
    }

    // ------------------------------------------------------------ talking to the sound engine

    @Nullable
    private static SoundInstance playingAt(BlockPos pos) {
        return ((LevelRendererAccess) Minecraft.getInstance().levelRenderer).everwild$playingJukeboxSongs().get(pos);
    }

    private static SoundEngine engine() {
        return ((SoundManagerAccess) Minecraft.getInstance().getSoundManager()).everwild$soundEngine();
    }

    /** Push volume / range / pause onto a sound that's already playing. */
    public static void applyToPlaying(BlockPos pos, JukeboxSettings s) {
        SoundInstance sound = playingAt(pos);
        if (sound == null) return;
        applyToSound(sound, s);
        // with Sound Physics Perfected the audible sound is its copies, so apply to those too
        for (SoundInstance key : List.copyOf(((SoundEngineAccess) engine()).everwild$instanceToChannel().keySet())) {
            if (key != sound && SppCompat.isCopy(key) && SppCompat.unwrap(key) == sound) applyToSound(key, s);
        }
    }

    public static void applyToSound(SoundInstance sound, JukeboxSettings s) {
        SoundEngine engine = engine();
        ChannelAccess.ChannelHandle handle = ((SoundEngineAccess) engine).everwild$instanceToChannel().get(sound);
        if (handle == null) return;
        float volume = ((SoundEngineAccess) engine).everwild$calculateVolume(sound); // already includes our loudness
        handle.execute(ch -> {
            ch.setVolume(volume);
            ch.linearAttenuation(s.range());
            if (s.paused()) ch.pause(); else ch.unpause();
        });
    }

    // ------------------------------------------------------------ boombox skipping

    /** What each entity's boombox is currently playing (captured from Etched's SoundTracker). */
    public record EntityPlayback(ItemStack record, int track, int attenuation, boolean loop, @Nullable UUID storage) {}
    private static final Map<Integer, EntityPlayback> ENTITY_PLAYBACK = new HashMap<>();

    public static void rememberEntityPlayback(int entityId, EntityPlayback p) {
        ENTITY_PLAYBACK.put(entityId, p);
    }

    public static void onBoomboxSkipped(int entityId, int dir) {
        EntityPlayback p = ENTITY_PLAYBACK.get(entityId);
        Minecraft mc = Minecraft.getInstance();
        if (p == null || mc.level == null || SoundTracker.getEntitySound(entityId) == null) return;
        int count = Math.max(1, PlayableRecord.getTrackCount(mc.level.registryAccess(), p.record()));
        int next = Math.floorMod(p.track() + dir, count);
        SoundTracker.playEntityRecord(p.record(), entityId, next, p.attenuation(), true);
    }

    // ------------------------------------------------------------ events

    public static final KeyMapping NEXT_TRACK = new KeyMapping("key.etched_additions.boombox_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.etched_additions");
    public static final KeyMapping PREV_TRACK = new KeyMapping("key.etched_additions.boombox_previous", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.etched_additions");

    @EventBusSubscriber(modid = EverwildJukebox.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(NEXT_TRACK);
            event.register(PREV_TRACK);
        }
    }

    @EventBusSubscriber(modid = EverwildJukebox.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private static int tick;

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            while (NEXT_TRACK.consumeClick()) PacketDistributor.sendToServer(new Packets.BoomboxSkip(1));
            while (PREV_TRACK.consumeClick()) PacketDistributor.sendToServer(new Packets.BoomboxSkip(-1));

            // keep paused jukeboxes paused (the game un-pauses every sound when you leave the pause menu,
            // and a song that starts while paused needs to be caught once its channel exists)
            if (++tick % 10 == 0) {
                for (Map.Entry<BlockPos, JukeboxSettings> e : SETTINGS.entrySet()) {
                    if (e.getValue().paused()) applyToPlaying(e.getKey(), e.getValue());
                }
            }
        }

        @SubscribeEvent
        public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            SETTINGS.clear();
            ENTITY_PLAYBACK.clear();
        }

        /** Extra buttons on Etched's album jukebox screen, in a column to its right. */
        @SubscribeEvent
        public static void screenInit(ScreenEvent.Init.Post event) {
            if (!(event.getScreen() instanceof AlbumJukeboxScreen screen)) return;
            BlockPos pos = screen.getMenu().getPos();
            int x = ((AbstractContainerScreen<?>) screen).getGuiLeft() + ((AbstractContainerScreen<?>) screen).getXSize() + 4;
            int y = ((AbstractContainerScreen<?>) screen).getGuiTop() + 4;

            Button pause = Button.builder(pauseLabel(pos), b -> {
                JukeboxSettings s = get(pos).withPaused(!get(pos).paused());
                send(pos, s);
                b.setMessage(Component.literal(s.paused() ? "> Play" : "|| Pause"));
            }).bounds(x, y, 70, 20).build();

            Button repeat = Button.builder(repeatLabel(get(pos).repeatOne()), b -> {
                JukeboxSettings o = get(pos);
                JukeboxSettings s = new JukeboxSettings(o.volume(), o.range(), o.paused(), o.loop(), !o.repeatOne());
                send(pos, s);
                b.setMessage(repeatLabel(s.repeatOne()));
            }).bounds(x, y + 24, 70, 20).build();

            Button settings = Button.builder(Component.literal("Settings..."), b -> openSettings(pos)).bounds(x, y + 48, 70, 20).build();

            event.addListener(pause);
            event.addListener(repeat);
            event.addListener(settings);
        }

        private static Component pauseLabel(BlockPos pos) {
            return Component.literal(get(pos).paused() ? "> Play" : "|| Pause");
        }
    }

    static Component repeatLabel(boolean one) {
        return Component.literal(one ? "Repeat: One" : "Repeat: All");
    }
}
