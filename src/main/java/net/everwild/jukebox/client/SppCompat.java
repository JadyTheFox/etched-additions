package net.everwild.jukebox.client;

import gg.moonflower.etched.api.sound.SoundStopListener;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Compatibility with Sound Physics Perfected (SPP).
 * <p>
 * SPP cancels positional sounds (like jukebox songs) and plays its own echo/muffle copies instead.
 * Etched only learns a song ended when ITS sound ends, which never happens, so albums never advance
 * and nothing loops. Here we trace SPP's copies back to Etched's original and pass the "song ended"
 * signal on, so Etched works with sound physics on. No hard dependency: everything is by class name,
 * and if SPP isn't installed this all quietly does nothing.
 */
public final class SppCompat {
    private SppCompat() {}

    private static final Logger LOG = LoggerFactory.getLogger("etched_additions/spp");
    private static final String TICKABLE = "com.redsmods.sound_physics_perfected.wrappers.RedTickableInstance";
    private static final String RED_SOUND = "com.redsmods.sound_physics_perfected.RedSoundInstance";

    private static boolean init, available;
    @Nullable private static Class<?> tickableClass, redSoundClass;
    @Nullable private static Field wrappedField;
    @Nullable private static Method getOriginal;

    /** Copies that were stopped on purpose (track switch, SPP cleanup): their ending is NOT "song finished". */
    private static final Set<SoundInstance> STOPPED_ON_PURPOSE = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    /** Originals we've already reported as finished (one signal per song, however many copies end). */
    private static final Set<SoundInstance> REPORTED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private static void init() {
        if (init) return;
        init = true;
        try {
            tickableClass = Class.forName(TICKABLE);
            wrappedField = tickableClass.getDeclaredField("wrapped");
            wrappedField.setAccessible(true);
            available = true;
        } catch (Throwable ignored) {
            // SPP not installed (or changed) -> nothing to do
        }
        try {
            redSoundClass = Class.forName(RED_SOUND);
            getOriginal = redSoundClass.getMethod("getOriginal");
        } catch (Throwable ignored) {
        }
        if (available) LOG.info("Sound Physics Perfected found: jukebox songs keep their sound physics and still loop / advance.");
    }

    public static boolean isCopy(SoundInstance s) {
        init();
        return (tickableClass != null && tickableClass.isInstance(s)) || (redSoundClass != null && redSoundClass.isInstance(s));
    }

    /** The sound SPP made this copy from (or the sound itself if it isn't an SPP copy). */
    public static SoundInstance unwrap(SoundInstance s) {
        init();
        for (int guard = 0; guard < 4; guard++) {
            Object inner = null;
            try {
                if (tickableClass != null && tickableClass.isInstance(s)) inner = wrappedField.get(s);
                else if (redSoundClass != null && redSoundClass.isInstance(s)) inner = getOriginal.invoke(s);
            } catch (Throwable ignored) {
            }
            if (!(inner instanceof SoundInstance next) || next == s) return s;
            s = next;
        }
        return s;
    }

    public static void markStoppedOnPurpose(SoundInstance s) {
        if (isCopy(s)) STOPPED_ON_PURPOSE.add(s);
    }

    /** A sound's channel just finished and was removed by the sound engine. */
    public static void onSoundFinished(SoundInstance s) {
        if (!isCopy(s)) return;
        if (STOPPED_ON_PURPOSE.remove(s)) return;           // stopped on purpose -> not the end of the song
        SoundInstance original = unwrap(s);
        if (original == s || !(original instanceof SoundStopListener listener)) return;
        if (!REPORTED.add(original)) return;                 // already told Etched for this song
        listener.onStop();                                   // Etched: next track / repeat / loop
    }
}
