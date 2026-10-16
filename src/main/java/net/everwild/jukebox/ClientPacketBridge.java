package net.everwild.jukebox;

import net.everwild.jukebox.client.ClientJukebox;
import net.everwild.jukebox.network.Packets;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Only common types in signatures, so the dedicated server can load this class safely;
 * the client-only code behind it is only touched when these run (which is only ever on a client).
 */
public final class ClientPacketBridge {
    private ClientPacketBridge() {}

    public static void settingsSync(Packets.SettingsSync pkt, IPayloadContext ctx) {
        ClientJukebox.onSettings(pkt.pos(), pkt.settings());
    }

    public static void boomboxSkipped(Packets.BoomboxSkipped pkt, IPayloadContext ctx) {
        ClientJukebox.onBoomboxSkipped(pkt.entityId(), pkt.direction());
    }

    public static void openSettings(BlockPos pos) {
        ClientJukebox.openSettings(pos);
    }
}
