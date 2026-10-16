package net.everwild.jukebox;

import gg.moonflower.etched.common.item.BoomboxItem;
import net.everwild.jukebox.network.Packets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(EverwildJukebox.MOD_ID)
public class EverwildJukebox {
    public static final String MOD_ID = "etched_additions";
    /** How close you must be to change a jukebox's settings. */
    private static final double REACH_SQR = 10 * 10;

    public EverwildJukebox(IEventBus modBus) {
        modBus.addListener(EverwildJukebox::registerPackets);
        NeoForge.EVENT_BUS.addListener(EverwildJukebox::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EverwildJukebox::onChunkSent);
    }

    // ---------------------------------------------------------------- networking

    private static void registerPackets(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(Packets.SetSettings.TYPE, Packets.SetSettings.CODEC, EverwildJukebox::handleSetSettings);
        r.playToServer(Packets.BoomboxSkip.TYPE, Packets.BoomboxSkip.CODEC, EverwildJukebox::handleBoomboxSkip);
        r.playToClient(Packets.SettingsSync.TYPE, Packets.SettingsSync.CODEC, ClientPacketBridge::settingsSync);
        r.playToClient(Packets.BoomboxSkipped.TYPE, Packets.BoomboxSkipped.CODEC, ClientPacketBridge::boomboxSkipped);
    }

    private static void handleSetSettings(Packets.SetSettings pkt, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        BlockPos pos = pkt.pos();
        if (!level.isLoaded(pos) || player.distanceToSqr(pos.getCenter()) > REACH_SQR) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!JukeboxSettings.isSupported(be)) return;
        apply(level, be, pkt.settings());
    }

    private static void handleBoomboxSkip(Packets.BoomboxSkip pkt, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        int dir = pkt.direction() < 0 ? -1 : 1;
        if (!hasBoombox(player)) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new Packets.BoomboxSkipped(player.getId(), dir));
    }

    private static boolean hasBoombox(Player player) {
        for (ItemStack stack : player.getInventory().items) if (stack.getItem() instanceof BoomboxItem) return true;
        for (ItemStack stack : player.getInventory().offhand) if (stack.getItem() instanceof BoomboxItem) return true;
        return player.containerMenu != null && player.containerMenu.getCarried().getItem() instanceof BoomboxItem;
    }

    /** Save on the server and tell everyone who can see that chunk. */
    public static void apply(ServerLevel level, BlockEntity be, JukeboxSettings settings) {
        JukeboxSettings.write(be, settings);
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(be.getBlockPos()), new Packets.SettingsSync(be.getBlockPos(), settings));
    }

    /** Players who start seeing a chunk get the settings of any customised jukeboxes in it. */
    private static void onChunkSent(ChunkWatchEvent.Sent event) {
        for (BlockEntity be : event.getChunk().getBlockEntities().values()) {
            if (!JukeboxSettings.isSupported(be)) continue;
            JukeboxSettings s = JukeboxSettings.read(be);
            if (!s.isDefault()) PacketDistributor.sendToPlayer(event.getPlayer(), new Packets.SettingsSync(be.getBlockPos(), s));
        }
    }

    // ---------------------------------------------------------------- interactions

    /**
     * Sneak + right-click with empty hands: pause / resume.
     * Sneak + right-click holding a stick: open the settings screen (client side).
     */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return;
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!JukeboxSettings.isSupported(be)) return;

        boolean emptyHands = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
        boolean stick = player.getItemInHand(event.getHand()).is(Items.STICK);
        if (!emptyHands && !stick) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (stick) {
            if (event.getLevel().isClientSide()) ClientPacketBridge.openSettings(event.getPos());
            return;
        }
        if (emptyHands && !event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel level) {
            JukeboxSettings now = JukeboxSettings.read(be).withPaused(!JukeboxSettings.read(be).paused());
            apply(level, be, now);
            player.displayClientMessage(Component.literal(now.paused() ? "Paused" : "Playing").withStyle(ChatFormatting.GOLD), true);
        }
    }
}
